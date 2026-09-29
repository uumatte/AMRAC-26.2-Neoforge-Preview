package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import amrac.network.PlaneNetworking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MissileSeekerView {
    private static final int STALE_TICKS = 10;

    private static final int HIT_HOLD_TICKS = 30;

    private static final int HIT_SCALE = 2;

    private static final int PANEL = 0xFF0A1410;
    private static final int FRAME = 0xFF35C878;
    private static final int FRAME_DIM = 0xFF1D6B41;
    private static final int TEXT = 0xFF9BF7C0;
    private static final int TARGET = 0xFFFF5555;
    private static final int LOST = 0xFFFFAA33;

    private static final class Round {
        PlaneNetworking.MissileSeeker frame;
        int age;
        int seenAt;
        boolean hit;
    }

    private static final Map<Integer, Round> ROUNDS = new LinkedHashMap<>();
    private static int clock;

    private MissileSeekerView() {
    }

    public static void acceptFrames(List<PlaneNetworking.MissileSeeker> frames) {
        for (PlaneNetworking.MissileSeeker frame : frames) {
            Round round = ROUNDS.computeIfAbsent(frame.key(), key -> {
                Round fresh = new Round();
                fresh.seenAt = clock;
                return fresh;
            });
            if (frame.hit()) {
                round.hit = true;
            } else {
                round.frame = frame;
            }
            round.age = 0;
        }
    }

    public static void tick() {
        clock++;
        ROUNDS.values().removeIf(round ->
            ++round.age > (round.hit ? HIT_HOLD_TICKS : STALE_TICKS));
    }

    public static void clear() {
        ROUNDS.clear();
    }

    public static PlaneNetworking.MissileSeeker current() {
        Round round = currentRound();
        return round == null ? null : round.frame;
    }

    private static Round currentRound() {
        Round best = null;
        for (Round round : ROUNDS.values()) {
            if (round.frame == null) {
                continue;
            }
            if (best == null || round.seenAt > best.seenAt) {
                best = round;
            }
        }
        return best;
    }

    public static void render(Minecraft minecraft, GuiGraphicsExtractor graphics,
                              Vec3 ownPosition) {
        Round round = currentRound();
        if (round == null) {
            return;
        }
        PlaneNetworking.MissileSeeker frame = round.frame;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int cx = width / 2;
        int cy = height / 2;
        int half = SeekerViewPolicy.faceRadius(width, height);

        graphics.fill(0, 0, width, height, PANEL);
        rim(graphics, cx, cy, half);
        int arm = Math.max(5, half / 22);
        int gap = Math.max(1, half / 60);
        graphics.fill(cx - gap - arm, cy, cx - gap, cy + 1, FRAME);
        graphics.fill(cx + gap + 1, cy, cx + gap + arm + 1, cy + 1, FRAME);
        graphics.fill(cx, cy - gap - arm, cx + 1, cy - gap, FRAME);
        graphics.fill(cx, cy + gap + 1, cx + 1, cy + gap + arm + 1, FRAME);

        Vec3 missile = new Vec3(frame.x(), frame.y(), frame.z());
        double missileToTarget = -1.0D;
        double ownToTarget = -1.0D;
        boolean offFace = false;

        if (frame.hasTarget()) {
            Vec3 target = new Vec3(frame.targetX(), frame.targetY(),
                frame.targetZ());
            missileToTarget = missile.distanceTo(target);
            ownToTarget = ownPosition.distanceTo(target);
            double[] mark = new double[2];
            boolean drawn = SeekerViewPolicy.project(
                new double[] {frame.axisX(), frame.axisY(), frame.axisZ()},
                new double[] {target.x - missile.x, target.y - missile.y,
                    target.z - missile.z}, mark);
            if (drawn && SeekerViewPolicy.onFace(mark[0], mark[1])) {
                int tx = cx + (int) Math.round(mark[0] * half);
                int ty = cy - (int) Math.round(mark[1] * half);
                int extent = (int) Math.round(
                    SeekerViewPolicy.boxExtent(missileToTarget) * half);
                targetBox(graphics, tx, ty, Math.max(2, extent),
                    cx - half, cy - half, cx + half, cy + half,
                    frame.locked());
            } else if (drawn) {
                offFace = true;
                double length = Math.hypot(mark[0], mark[1]);
                double ux = mark[0] / length;
                double uy = mark[1] / length;
                int tx = cx + (int) Math.round(ux * (half - 4));
                int ty = cy - (int) Math.round(uy * (half - 4));
                graphics.fill(tx - 2, ty - 2, tx + 3, ty + 3, LOST);
            }
        }

        List<String> lines = new ArrayList<>(5);
        lines.add(Component.translatable("amrac.seeker.target_range",
            distance(ownToTarget)).getString());
        lines.add(Component.translatable("amrac.seeker.missile_range",
            distance(missileToTarget)).getString());
        lines.add(Component.translatable("amrac.seeker.closure",
            frame.hasTarget()
                ? String.format(Locale.ROOT, "%+.0f", frame.closure()) : "--")
            .getString());
        lines.add(Component.translatable("amrac.seeker.speed",
            String.format(Locale.ROOT, "%.0f", frame.speed())).getString());
        double impact = SeekerViewPolicy.timeToImpact(missileToTarget,
            frame.closure());
        lines.add(Component.translatable("amrac.seeker.impact",
            impact < 0.0D ? "--"
                : String.format(Locale.ROOT, "%.1f", impact)).getString());

        int y = 6;
        for (String line : lines) {
            graphics.text(minecraft.font, line, 7, y, TEXT);
            y += minecraft.font.lineHeight + 1;
        }

        String state = offFace
            ? Component.translatable("amrac.seeker.off_face").getString()
            : frame.hasTarget()
                ? Component.translatable(frame.locked()
                    ? "amrac.seeker.locked"
                    : "amrac.seeker.tracking").getString()
                : Component.translatable("amrac.seeker.no_target")
                    .getString();
        graphics.text(minecraft.font, state,
            width - 7 - minecraft.font.width(state),
            height - minecraft.font.lineHeight - 6,
            frame.hasTarget() && !offFace ? TEXT : LOST);

        if (round.hit) {
            String hit = Component.translatable("amrac.seeker.hit")
                .getString();
            int w = minecraft.font.width(hit) * HIT_SCALE;
            int lh = minecraft.font.lineHeight * HIT_SCALE;
            int top = cy + Math.max(lh + 6, half / 3);
            int x0 = cx - w / 2 - 6;
            int x1 = cx + w / 2 + 6;
            int y0 = top - 5;
            int y1 = top + lh + 3;
            graphics.fill(x0, y0, x1, y1, PANEL);
            border(graphics, x0, y0, x1, y1, TARGET);
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx - w / 2, top);
            graphics.pose().scale(HIT_SCALE, HIT_SCALE);
            graphics.text(minecraft.font, hit, 0, 0, TARGET);
            graphics.pose().popMatrix();
        }
    }

    private static void rim(GuiGraphicsExtractor graphics, int cx, int cy,
                            int radius) {
        final int segments = 96;
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy - (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x, y, x + 2, y + 2, FRAME_DIM);
        }
    }

    private static void targetBox(GuiGraphicsExtractor graphics, int x, int y,
                                  int extent, int clipX0, int clipY0,
                                  int clipX1, int clipY1, boolean locked) {
        int colour = locked ? TARGET : LOST;
        int arm = Math.max(1, Math.min(extent, Math.round(extent * 0.42F)));
        int thick = Math.max(1, Math.round(extent * 0.14F));
        int left = x - extent;
        int right = x + extent;
        int top = y - extent;
        int bottom = y + extent;

        fillClipped(graphics, left, top, left + arm, top + thick,
            clipX0, clipY0, clipX1, clipY1, colour);
        fillClipped(graphics, left, top, left + thick, top + arm,
            clipX0, clipY0, clipX1, clipY1, colour);
        fillClipped(graphics, right - arm, top, right, top + thick,
            clipX0, clipY0, clipX1, clipY1, colour);
        fillClipped(graphics, right - thick, top, right, top + arm,
            clipX0, clipY0, clipX1, clipY1, colour);
        fillClipped(graphics, left, bottom - thick, left + arm, bottom,
            clipX0, clipY0, clipX1, clipY1, colour);
        fillClipped(graphics, left, bottom - arm, left + thick, bottom,
            clipX0, clipY0, clipX1, clipY1, colour);
        fillClipped(graphics, right - arm, bottom - thick, right, bottom,
            clipX0, clipY0, clipX1, clipY1, colour);
        fillClipped(graphics, right - thick, bottom - arm, right, bottom,
            clipX0, clipY0, clipX1, clipY1, colour);
    }

    private static void fillClipped(GuiGraphicsExtractor graphics, int x0,
                                    int y0, int x1, int y1, int clipX0,
                                    int clipY0, int clipX1, int clipY1,
                                    int colour) {
        int left = Math.max(x0, clipX0);
        int top = Math.max(y0, clipY0);
        int right = Math.min(x1, clipX1);
        int bottom = Math.min(y1, clipY1);
        if (left < right && top < bottom) {
            graphics.fill(left, top, right, bottom, colour);
        }
    }

    private static void border(GuiGraphicsExtractor graphics, int x0, int y0,
                               int x1, int y1, int colour) {
        graphics.fill(x0, y0, x1, y0 + 1, colour);
        graphics.fill(x0, y1 - 1, x1, y1, colour);
        graphics.fill(x0, y0, x0 + 1, y1, colour);
        graphics.fill(x1 - 1, y0, x1, y1, colour);
    }

    private static String distance(double blocks) {
        if (!(blocks >= 0.0D) || !Double.isFinite(blocks)) {
            return "--";
        }
        return blocks >= 1000.0D
            ? String.format(Locale.ROOT, "%.1f km", blocks / 1000.0D)
            : String.format(Locale.ROOT, "%.0f m", blocks);
    }
}
