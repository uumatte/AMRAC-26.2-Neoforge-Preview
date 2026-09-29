package amrac.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import amrac.platform.client.ClientTickEvents;
import amrac.platform.client.LevelExtractionEvents;
import amrac.platform.client.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import amrac.network.PlaneNetworking;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MissileTrails {
    private static final Map<Integer, Trail> TRAILS = new LinkedHashMap<>();
    private static volatile RenderSnapshot renderSnapshot = RenderSnapshot.EMPTY;

    private MissileTrails() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                TRAILS.clear();
                return;
            }
            if (client.isPaused()) {
                return;
            }
            age();
        });
        LevelExtractionEvents.END_EXTRACTION.register(context ->
            extractRenderSnapshot(context.camera().position()));
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            RenderSnapshot snapshot = renderSnapshot;
            if (snapshot.segments().isEmpty()
                || IrisCompat.renderingShadowPass()) {
                return;
            }
            context.submitNodeCollector().submitCustomGeometry(
                context.poseStack(), RenderTypes.lightning(),
                (pose, builder) -> render(builder, pose.pose(), snapshot));
        });
    }

    private static final class Node {
        final Vec3 position;
        final float strength;
        final boolean broken;
        int age;

        Node(Vec3 position, float strength, boolean broken) {
            this.position = position;
            this.strength = strength;
            this.broken = broken;
        }
    }

    private static final class Trail {
        final Deque<Node> nodes = new ArrayDeque<>();
        int orphanTicks;
    }

    public static void acceptReported(List<PlaneNetworking.MissileTrack> tracks) {
        for (PlaneNetworking.MissileTrack track : tracks) {
            if (!track.powered()) {
                continue;
            }
            if (TRAILS.size() >= MissileTrailPolicy.MAX_TRAILS
                && !TRAILS.containsKey(track.key())) {
                continue;
            }
            Trail trail = TRAILS.computeIfAbsent(track.key(), id -> new Trail());
            trail.orphanTicks = 0;

            Vec3 position = new Vec3(track.x(), track.y(), track.z());
            Node previous = trail.nodes.peekLast();
            double step = previous == null ? 0.0D
                : previous.position.distanceTo(position);
            if (previous != null && step < 1.0E-4D) {
                continue;
            }
            float strength = previous == null ? 0.0F
                : MissileTrailPolicy.speedStrength(step);
            boolean broken = previous == null
                || MissileTrailPolicy.breaksRibbon(step);
            trail.nodes.addLast(new Node(position, strength, broken));
            while (trail.nodes.size() > MissileTrailPolicy.MAX_NODES) {
                trail.nodes.removeFirst();
            }
        }
    }

    private static void age() {
        Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Trail trail = it.next().getValue();
            trail.orphanTicks++;
            trail.nodes.removeIf(node ->
                ++node.age > MissileTrailPolicy.NODE_LIFETIME_TICKS);
            if (trail.nodes.isEmpty()) {
                it.remove();
            }
        }
    }

    private static void extractRenderSnapshot(Vec3 eye) {
        List<RenderSegment> segments = new ArrayList<>();
        for (Trail trail : TRAILS.values()) {
            List<Node> nodes = new ArrayList<>(trail.nodes);
            for (int i = 1; i < nodes.size(); i++) {
                Node a = nodes.get(i - 1);
                Node b = nodes.get(i);
                if (b.broken) {
                    continue;
                }
                segments.add(new RenderSegment(
                    new RenderNode(a.position, a.strength, a.age),
                    new RenderNode(b.position, b.strength, b.age)));
            }
        }
        renderSnapshot = new RenderSnapshot(eye, FarImagePolicy.drawRadius(
                Minecraft.getInstance().options.getEffectiveRenderDistance()),
            List.copyOf(segments));
    }

    private static void render(VertexConsumer builder, Matrix4f matrix,
                               RenderSnapshot snapshot) {
        for (RenderSegment segment : snapshot.segments()) {
            ribbon(builder, matrix, snapshot.eye(), snapshot.drawRadius(),
                segment.a(), segment.b());
        }
    }

    private static void ribbon(VertexConsumer builder, Matrix4f matrix, Vec3 eye,
                               double drawRadius, RenderNode a, RenderNode b) {
        Vec3 along = b.position().subtract(a.position());
        if (along.lengthSqr() < 1.0E-9D) {
            return;
        }
        Vec3 toEye = eye.subtract(a.position());
        Vec3 side = along.cross(toEye);
        if (side.lengthSqr() < 1.0E-9D) {
            return;
        }
        side = side.normalize();

        double distanceA = toEye.length();
        double distanceB = eye.distanceTo(b.position());
        float widthA = MissileTrailPolicy.widthAt(a.age(), a.strength(),
            distanceA);
        float widthB = MissileTrailPolicy.widthAt(b.age(), b.strength(),
            distanceB);
        int alphaA = MissileTrailPolicy.alpha(a.age(), a.strength());
        int alphaB = MissileTrailPolicy.alpha(b.age(), b.strength());
        if (alphaA <= 0 && alphaB <= 0) {
            return;
        }

        double pullA = FarImagePolicy.pull(distanceA, drawRadius);
        double pullB = FarImagePolicy.pull(distanceB, drawRadius);
        Vec3 a0 = a.position().subtract(eye).add(side.scale(widthA))
            .scale(pullA);
        Vec3 a1 = a.position().subtract(eye).subtract(side.scale(widthA))
            .scale(pullA);
        Vec3 b0 = b.position().subtract(eye).add(side.scale(widthB))
            .scale(pullB);
        Vec3 b1 = b.position().subtract(eye).subtract(side.scale(widthB))
            .scale(pullB);

        vertex(builder, matrix, a1, a.age(), alphaA);
        vertex(builder, matrix, a0, a.age(), alphaA);
        vertex(builder, matrix, b0, b.age(), alphaB);
        vertex(builder, matrix, b1, b.age(), alphaB);
    }

    private static void vertex(VertexConsumer builder, Matrix4f matrix, Vec3 at,
                               int age, int alpha) {
        float t = MissileTrailPolicy.ageFraction(age);
        int red = (int) (255 - 55 * t);
        int green = (int) (240 - 70 * t);
        int blue = (int) (225 - 60 * t);
        builder.addVertex(matrix, (float) at.x, (float) at.y, (float) at.z)
            .setColor(red, green, blue, alpha);
    }

    public static void clear() {
        TRAILS.clear();
        renderSnapshot = RenderSnapshot.EMPTY;
    }

    private record RenderNode(Vec3 position, float strength, int age) {
    }

    private record RenderSegment(RenderNode a, RenderNode b) {
    }

    private record RenderSnapshot(Vec3 eye, double drawRadius,
                                  List<RenderSegment> segments) {
        private static final RenderSnapshot EMPTY =
            new RenderSnapshot(Vec3.ZERO, FarImagePolicy.MIN_DRAW_RADIUS,
                List.of());
    }
}
