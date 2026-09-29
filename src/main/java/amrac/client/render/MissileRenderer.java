package amrac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import amrac.client.render.models.MissileModel;
import amrac.entities.MissileEntity;

public final class MissileRenderer extends EntityRenderer<MissileEntity,
    MissileRenderer.State> {
    private static final float MODEL_SCALE = 4.0F;
    private static final float LENGTH = 3.6F;
    private static final float RADIUS = 0.16F;
    private static final int PLUME_SEGMENTS = 8;
    private static final int PLUME_STAGES = 7;
    private static final float PLUME_LENGTH = 2.6F;

    public static final class State extends EntityRenderState {
        float yaw;
        float pitch;
        boolean boosting;
        MissileKits.Kit kit = MissileKits.forProfile(null);
    }

    public MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MissileEntity missile, State state,
                                   float partialTicks) {
        super.extractRenderState(missile, state, partialTicks);
        state.yaw = -Mth.lerp(partialTicks, missile.yRotO, missile.getYRot());
        state.pitch = -Mth.lerp(partialTicks, missile.xRotO, missile.getXRot());
        state.boosting = missile.getMotorState() == MissileEntity.MOTOR_BOOST;
        state.kit = MissileKits.forProfile(missile.profile().id);
    }

    @Override
    public void submit(State state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));

        poseStack.pushPose();
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        MissileModel model = state.kit.model();
        Identifier texture = state.kit.texture();
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, model.renderType(texture),
            (rootPose, buffer) -> model.renderToBuffer(
                PlaneMeshRenderer.poseFrom(rootPose), buffer, light,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F));
        poseStack.popPose();

        if (state.boosting) {
            float age = state.ageInTicks;
            collector.submitCustomGeometry(poseStack, RenderTypes.lightning(),
                (rootPose, buffer) -> renderPlume(buffer, rootPose.pose(), age));
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void renderPlume(VertexConsumer builder, Matrix4f matrix,
                                    float age) {
        float flicker = 0.86F + 0.14F * Mth.sin(age * 1.7F);
        float base = LENGTH * 0.5F;
        for (int stage = 0; stage + 1 < PLUME_STAGES; stage++) {
            float t0 = stage / (float) (PLUME_STAGES - 1);
            float t1 = (stage + 1) / (float) (PLUME_STAGES - 1);
            float z0 = base + PLUME_LENGTH * t0 * flicker;
            float z1 = base + PLUME_LENGTH * t1 * flicker;
            float r0 = RADIUS * 0.92F * (1.0F - t0 * 0.85F);
            float r1 = RADIUS * 0.92F * (1.0F - t1 * 0.85F);
            int a0 = (int) (230 * (1.0F - t0));
            int a1 = (int) (230 * (1.0F - t1));
            for (int segment = 0; segment < PLUME_SEGMENTS; segment++) {
                double b0 = 2.0D * Math.PI * segment / PLUME_SEGMENTS;
                double b1 = 2.0D * Math.PI * (segment + 1) / PLUME_SEGMENTS;
                float c0 = (float) Math.cos(b0), s0 = (float) Math.sin(b0);
                float c1 = (float) Math.cos(b1), s1 = (float) Math.sin(b1);
                plumeVertex(builder,matrix,c0*r0,s0*r0,z0,t0,a0);
                plumeVertex(builder,matrix,c1*r0,s1*r0,z0,t0,a0);
                plumeVertex(builder,matrix,c1*r1,s1*r1,z1,t1,a1);
                plumeVertex(builder,matrix,c0*r1,s0*r1,z1,t1,a1);
            }
        }
    }

    private static void plumeVertex(VertexConsumer builder, Matrix4f matrix,
                                    float x, float y, float z, float t,
                                    int alpha) {
        int green = (int) Mth.lerp(t, 250.0F, 120.0F);
        int blue = (int) Mth.lerp(t, 220.0F, 30.0F);
        builder.addVertex(matrix, x, y, z)
            .setColor(255, green, blue, Math.max(0, alpha));
    }
}
