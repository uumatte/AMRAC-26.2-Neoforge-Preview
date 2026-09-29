package amrac.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import amrac.entities.PlaneEntity;
import amrac.gps.GpsContact;
import amrac.gps.GpsPin;
import amrac.gps.GpsPolicy;

public final class AirborneGps {
    private static final int MARGIN = 6;
    private static final int BASE_RADIUS = 32;
    private static final int MIN_RADIUS = 18;
    private static final int RINGS = 2;

    private static final int PANEL = 0xB0080E12;
    private static final int EDGE = 0xFF3C4650;
    private static final int RING = 0x38FFFFFF;
    private static final int AXIS = 0x28FFFFFF;
    private static final int NOSE = 0xFFDCE6EC;
    private static final int OWN = 0xFFEAF6F1;
    private static final int TARGET = 0xFFE8503C;
    private static final int MISSILE = 0xFFE8C23E;
    private static final int MISSILE_ACTIVE = 0xFF4ADE6A;
    private static final int PIN = 0xFF4FC3F7;
    private static final int LABEL = 0xFF93A1AC;

    private static boolean visible = true;

    private AirborneGps() {
    }

    public static boolean visible() {
        return visible;
    }

    public static void toggle() {
        visible = !visible;
    }

    public static void render(Minecraft minecraft, GuiGraphicsExtractor graphics,
                              PlaneEntity plane, int width, int height) {
        if (!visible || minecraft.player == null) {
            return;
        }

        int radius = GpsPolicy.dialRadius(height, MARGIN,
            BASE_RADIUS, MIN_RADIUS);
        int centreX = width - MARGIN - radius - 2;
        int centreY = height - MARGIN - radius - 2;
        Vec3 own = plane.position();
        Vec3 forward = plane.getBodyDirection(0.0F, 0.0F, 1.0F);
        double heading = GpsPolicy.headingRadians(forward.x, forward.z);

        double range = GpsRange.airborne();

        drawDisc(graphics, centreX, centreY, radius, PANEL);
        drawCircle(graphics, centreX, centreY, radius, EDGE);
        for (int ring = 1; ring < RINGS; ring++) {
            drawCircle(graphics, centreX, centreY,
                radius * ring / RINGS, RING);
        }
        graphics.verticalLine(centreX, centreY - radius, centreY + radius, AXIS);
        graphics.horizontalLine(centreX - radius, centreX + radius, centreY, AXIS);
        graphics.fill(centreX - 1, centreY - radius - 3, centreX + 2,
            centreY - radius + 2, NOSE);

        double[] out = new double[2];
        for (GpsPin pin : GpsData.pins()) {
            if (plot(pin.x() - own.x, pin.z() - own.z, range, radius, heading, out)) {
                mark(graphics, centreX + (int) Math.round(out[0]),
                    centreY + (int) Math.round(out[1]), PIN, true);
            }
        }
        for (GpsContact missile : GpsData.ownMissiles()) {
            if (plot(missile.x() - own.x, missile.z() - own.z, range, radius, heading, out)) {
                int x = centreX + (int) Math.round(out[0]);
                int y = centreY + (int) Math.round(out[1]);
                graphics.fill(x - 1, y - 1, x + 1, y + 1,
                    missile.seekerActive() ? MISSILE_ACTIVE : MISSILE);
            }
        }
        for (PlaneRadar.Lock lock : PlaneRadar.locks()) {
            if (plot(lock.position.x - own.x, lock.position.z - own.z,
                    range, radius, heading, out)) {
                mark(graphics, centreX + (int) Math.round(out[0]),
                    centreY + (int) Math.round(out[1]), TARGET, false);
            }
        }

        graphics.fill(centreX - 1, centreY - 2, centreX + 2, centreY + 3, OWN);

        String scale = String.format(Locale.ROOT, "%.0fKb", range / 1000.0D);
        graphics.text(minecraft.font, scale, centreX - radius,
            centreY + radius - 8, LABEL, false);
    }

    private static double horizontal(Vec3 target, Vec3 own) {
        return Math.hypot(target.x - own.x, target.z - own.z);
    }

    private static boolean plot(double deltaX, double deltaZ, double range,
                                int radius, double heading, double[] out) {
        if (!GpsPolicy.onDial(deltaX, deltaZ, range)) {
            return false;
        }
        GpsPolicy.projectRotated(deltaX, deltaZ, range, radius, heading, out);
        return true;
    }

    private static void mark(GuiGraphicsExtractor graphics, int x, int y,
                             int colour, boolean cross) {
        if (cross) {
            graphics.horizontalLine(x - 2, x + 2, y, colour);
            graphics.verticalLine(x, y - 2, y + 2, colour);
            return;
        }
        graphics.fill(x - 2, y - 2, x + 3, y - 1, colour);
        graphics.fill(x - 2, y + 2, x + 3, y + 3, colour);
        graphics.fill(x - 2, y - 2, x - 1, y + 3, colour);
        graphics.fill(x + 2, y - 2, x + 3, y + 3, colour);
    }

    private static void drawDisc(GuiGraphicsExtractor graphics, int centreX,
                                 int centreY, int radius, int colour) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt(
                (double) radius * radius - (double) dy * dy));
            graphics.fill(centreX - half, centreY + dy,
                centreX + half + 1, centreY + dy + 1, colour);
        }
    }

    private static void drawCircle(GuiGraphicsExtractor graphics, int centreX,
                                   int centreY, int radius, int colour) {
        if (radius <= 0) {
            return;
        }
        int steps = Math.max(48, radius * 4);
        for (int i = 0; i < steps; i++) {
            double angle = i * 2.0D * Math.PI / steps;
            int x = centreX + (int) Math.round(Math.cos(angle) * radius);
            int y = centreY + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }
}
