package amrac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import amrac.blocks.RefuelerBlockEntity;
import amrac.refuel.RefuelPolicy;

public final class RefuelerRenderer
        implements BlockEntityRenderer<RefuelerBlockEntity,
            RefuelerRenderer.State> {
    private static final int SEGMENTS = 12;
    private static final float RADIUS = 0.055F;
    private static final float SAG_PER_BLOCK = 0.075F;

    private static final int CORE_R = 38, CORE_G = 40, CORE_B = 45;
    private static final int LIT_R = 74, LIT_G = 77, LIT_B = 84;

    public static final class State extends BlockEntityRenderState {
        boolean coupled;
        float startX, startY, startZ;
        float endX, endY, endZ;
        float extension;
    }

    public RefuelerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RefuelerBlockEntity bowser, State state,
                                   float partialTicks, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(bowser, state, partialTicks,
            cameraPos, crumbling);
        state.coupled = false;
        Level level = bowser.getLevel();
        if (level == null || !bowser.coupled()) {
            return;
        }
        Entity plane = level.getEntity(bowser.coupledTo());
        if (plane == null) {
            return;
        }

        Vec3 origin = Vec3.atLowerCornerOf(bowser.getBlockPos());
        Vec3 from = bowser.nozzle().subtract(origin);
        Vec3 to = plane.getPosition(partialTicks)
            .add(0.0D, plane.getBbHeight() * 0.45D, 0.0D)
            .subtract(origin);

        state.coupled = true;
        state.startX = (float) from.x;
        state.startY = (float) from.y;
        state.startZ = (float) from.z;
        state.endX = (float) to.x;
        state.endY = (float) to.y;
        state.endZ = (float) to.z;
        state.extension = RefuelPolicy.extension(bowser.elapsed() + partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.coupled || state.extension <= 0.0F) {
            return;
        }
        State snapshot = state;
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.leash(),
            (rootPose, buffer) -> hose(buffer, rootPose.pose(), snapshot, light));
    }

    private static void hose(VertexConsumer buffer, Matrix4f matrix, State s,
                             int light) {
        float reach = s.extension;
        float span = (float) Math.sqrt(
            sq(s.endX - s.startX) + sq(s.endY - s.startY) + sq(s.endZ - s.startZ));
        float sag = span * SAG_PER_BLOCK;

        float[] previous = point(s, 0.0F, sag);
        for (int segment = 1; segment <= SEGMENTS; segment++) {
            float t = reach * segment / SEGMENTS;
            float[] current = point(s, t, sag);
            tube(buffer, matrix, previous, current, light);
            previous = current;
        }
    }

    private static float[] point(State s, float t, float sag) {
        float droop = sag * 4.0F * t * (1.0F - t);
        return new float[] {
            Mth.lerp(t, s.startX, s.endX),
            Mth.lerp(t, s.startY, s.endY) - droop,
            Mth.lerp(t, s.startZ, s.endZ)
        };
    }

    private static void tube(VertexConsumer buffer, Matrix4f matrix,
                             float[] from, float[] to, int light) {
        float dx = to[0] - from[0];
        float dz = to[2] - from[2];
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        float sideX = horizontal > 1.0E-4F ? -dz / horizontal : 1.0F;
        float sideZ = horizontal > 1.0E-4F ? dx / horizontal : 0.0F;

        quad(buffer, matrix, from, to, sideX * RADIUS, 0.0F, sideZ * RADIUS,
            0.0F, RADIUS, 0.0F, LIT_R, LIT_G, LIT_B, light);
        quad(buffer, matrix, from, to, -sideX * RADIUS, 0.0F, -sideZ * RADIUS,
            0.0F, RADIUS, 0.0F, CORE_R, CORE_G, CORE_B, light);
        quad(buffer, matrix, from, to, 0.0F, RADIUS, 0.0F,
            sideX * RADIUS, 0.0F, sideZ * RADIUS, LIT_R, LIT_G, LIT_B, light);
        quad(buffer, matrix, from, to, 0.0F, -RADIUS, 0.0F,
            sideX * RADIUS, 0.0F, sideZ * RADIUS, CORE_R, CORE_G, CORE_B, light);
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix,
                             float[] from, float[] to,
                             float ox, float oy, float oz,
                             float wx, float wy, float wz,
                             int r, int g, int b, int light) {
        vertex(buffer, matrix, from, ox - wx, oy - wy, oz - wz, r, g, b, light);
        vertex(buffer, matrix, to, ox - wx, oy - wy, oz - wz, r, g, b, light);
        vertex(buffer, matrix, to, ox + wx, oy + wy, oz + wz, r, g, b, light);
        vertex(buffer, matrix, from, ox + wx, oy + wy, oz + wz, r, g, b, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               float[] at, float dx, float dy, float dz,
                               int r, int g, int b, int light) {
        buffer.addVertex(matrix, at[0] + dx, at[1] + dy, at[2] + dz)
            .setColor(r, g, b, 255)
            .setLight(light);
    }

    private static float sq(float value) {
        return value * value;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
