package amrac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

import amrac.client.render.MissileKits.Kit;

public final class PylonMissiles {
    private static final float UNITS_PER_BLOCK = 16.0F;

    private PylonMissiles() {
    }

    public static void submitOne(PoseStack poseStack,
                                 SubmitNodeCollector collector, int packedLight,
                                 String profileId) {
        Kit kit = MissileKits.forProfile(profileId);
        collector.submitCustomGeometry(poseStack,
            kit.model().renderType(kit.texture()), (rootPose, buffer) ->
                kit.model().renderToBuffer(
                    PlaneMeshRenderer.poseFrom(rootPose), buffer,
                    packedLight, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, 1.0F));
    }

    public static void submit(PoseStack poseStack,
                              SubmitNodeCollector collector, int packedLight,
                              String[] loadout, float[][] stations) {
        if (loadout == null) {
            return;
        }
        for (int s = 0; s < stations.length; s++) {
            float[] station = stations[s];
            for (int side = 1; side >= -1; side -= 2) {
                int rail = s * 2 + (side > 0 ? 1 : 0);
                if (rail >= loadout.length || loadout[rail] == null) {
                    continue;
                }
                Kit kit = MissileKits.forProfile(loadout[rail]);
                poseStack.pushPose();
                poseStack.translate(side * station[0] / UNITS_PER_BLOCK,
                    (station[1] + kit.drop()) / UNITS_PER_BLOCK,
                    station[2] / UNITS_PER_BLOCK);
                submitOne(poseStack, collector, packedLight, loadout[rail]);
                poseStack.popPose();
            }
        }
    }
}
