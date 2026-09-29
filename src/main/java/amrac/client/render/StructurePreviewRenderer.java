package amrac.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import amrac.platform.client.ClientTickEvents;
import amrac.platform.client.ClientPlayNetworking;
import amrac.platform.client.LevelExtractionEvents;
import amrac.platform.client.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import amrac.network.PlaneNetworking;

public final class StructurePreviewRenderer {
    private static final float LINE_WIDTH = 2.0F;

    private static volatile int[] box;
    private static int ticksLeft;
    private static volatile float[] corners;

    private StructurePreviewRenderer() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
            PlaneNetworking.STRUCTURE_PREVIEW, (payload, context) -> {
                FriendlyByteBuf buf = payload.buffer();
                int[] read = PlaneNetworking.readStructurePreview(buf);
                context.client().execute(() -> {
                    if (read == null) {
                        clear();
                    } else {
                        box = read;
                        ticksLeft = read[6];
                    }
                });
            });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                clear();
                return;
            }
            if (client.isPaused() || box == null) {
                return;
            }
            if (--ticksLeft <= 0) {
                clear();
            }
        });

        LevelExtractionEvents.END_EXTRACTION.register(context ->
            extract(context.camera().position()));

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            float[] snapshot = corners;
            if (snapshot == null) {
                return;
            }
            context.submitNodeCollector().submitCustomGeometry(
                context.poseStack(), RenderTypes.lines(),
                (pose, builder) -> render(builder, pose.pose(), snapshot));
        });
    }

    public static void clear() {
        box = null;
        corners = null;
        ticksLeft = 0;
    }

    private static void extract(Vec3 camera) {
        int[] current = box;
        if (current == null) {
            corners = null;
            return;
        }
        corners = new float[] {
            (float) (current[0] - camera.x),
            (float) (current[1] - camera.y),
            (float) (current[2] - camera.z),
            (float) (current[3] + 1 - camera.x),
            (float) (current[4] + 1 - camera.y),
            (float) (current[5] + 1 - camera.z),
        };
    }

    private static void render(VertexConsumer builder, Matrix4f matrix,
                               float[] c) {
        float x0 = c[0];
        float y0 = c[1];
        float z0 = c[2];
        float x1 = c[3];
        float y1 = c[4];
        float z1 = c[5];

        edge(builder, matrix, x0, y0, z0, x1, y0, z0);
        edge(builder, matrix, x1, y0, z0, x1, y0, z1);
        edge(builder, matrix, x1, y0, z1, x0, y0, z1);
        edge(builder, matrix, x0, y0, z1, x0, y0, z0);

        edge(builder, matrix, x0, y1, z0, x1, y1, z0);
        edge(builder, matrix, x1, y1, z0, x1, y1, z1);
        edge(builder, matrix, x1, y1, z1, x0, y1, z1);
        edge(builder, matrix, x0, y1, z1, x0, y1, z0);

        edge(builder, matrix, x0, y0, z0, x0, y1, z0);
        edge(builder, matrix, x1, y0, z0, x1, y1, z0);
        edge(builder, matrix, x1, y0, z1, x1, y1, z1);
        edge(builder, matrix, x0, y0, z1, x0, y1, z1);
    }

    private static void edge(VertexConsumer builder, Matrix4f matrix,
                             float ax, float ay, float az,
                             float bx, float by, float bz) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0F) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        builder.addVertex(matrix, ax, ay, az)
            .setColor(255, 255, 255, 255).setNormal(dx, dy, dz)
            .setLineWidth(LINE_WIDTH);
        builder.addVertex(matrix, bx, by, bz)
            .setColor(255, 255, 255, 255).setNormal(dx, dy, dz)
            .setLineWidth(LINE_WIDTH);
    }
}
