package amrac.client.render.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public interface PlaneMeshModel {
    RenderType renderType(Identifier texture);

    void setGearPosition(float position);

    default void setControlPose(float pitchUp, float rollRight, float yawRight,
                                float flap, float brake) {
    }

    default void setDetail(int level) {
    }

    void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                        int packedLight, int packedOverlay,
                        float red, float green, float blue, float alpha);
}
