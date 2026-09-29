package amrac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import amrac.entities.MachineGunBulletEntity;

public final class MachineGunBulletRenderer extends EntityRenderer<
    MachineGunBulletEntity, MachineGunBulletRenderer.State> {
    private static final float HALF_WIDTH = 0.045F;
    private static final float HALF_LENGTH = 0.30F;

    public static final class State extends EntityRenderState {
        float yaw;
        float pitch;
        boolean hiddenAuthoritativeTwin;
    }

    public MachineGunBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MachineGunBulletEntity bullet, State state,
                                   float partialTicks) {
        super.extractRenderState(bullet, state, partialTicks);
        state.yaw = Mth.lerp(partialTicks, bullet.yRotO, bullet.getYRot());
        state.pitch = Mth.lerp(partialTicks, bullet.xRotO, bullet.getXRot());
        Minecraft minecraft = Minecraft.getInstance();
        state.hiddenAuthoritativeTwin = !bullet.isClientPrediction() &&
            minecraft.player != null && minecraft.player.getVehicle() != null &&
            bullet.getSourcePlaneId() == minecraft.player.getVehicle().getId();
    }

    @Override
    public void submit(State state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.hiddenAuthoritativeTwin) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-state.pitch));
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(),
            (pose, buffer) -> renderCuboid(buffer, pose.pose(), HALF_WIDTH,
                HALF_LENGTH, 255, 205, 74, 238));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void renderCuboid(VertexConsumer builder, Matrix4f pose,
                                     float halfWidth, float halfLength,
                                     int red, int green, int blue, int alpha) {
        float n = -halfWidth, p = halfWidth;
        float back = -halfLength, front = halfLength;
        quad(builder, pose, n,n,front, p,n,front, p,p,front, n,p,front,
            red,green,blue,alpha);
        quad(builder, pose, p,n,back, n,n,back, n,p,back, p,p,back,
            red,green,blue,alpha);
        quad(builder, pose, n,p,front, p,p,front, p,p,back, n,p,back,
            red,green,blue,alpha);
        quad(builder, pose, n,n,back, p,n,back, p,n,front, n,n,front,
            red,green,blue,alpha);
        quad(builder, pose, p,n,front, p,n,back, p,p,back, p,p,front,
            red,green,blue,alpha);
        quad(builder, pose, n,n,back, n,n,front, n,p,front, n,p,back,
            red,green,blue,alpha);
    }

    private static void quad(VertexConsumer builder, Matrix4f pose,
        float x1,float y1,float z1, float x2,float y2,float z2,
        float x3,float y3,float z3, float x4,float y4,float z4,
        int red,int green,int blue,int alpha) {
        vertex(builder,pose,x1,y1,z1,red,green,blue,alpha);
        vertex(builder,pose,x2,y2,z2,red,green,blue,alpha);
        vertex(builder,pose,x3,y3,z3,red,green,blue,alpha);
        vertex(builder,pose,x4,y4,z4,red,green,blue,alpha);
    }

    private static void vertex(VertexConsumer builder, Matrix4f pose,
                               float x, float y, float z,
                               int red, int green, int blue, int alpha) {
        builder.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
    }
}
