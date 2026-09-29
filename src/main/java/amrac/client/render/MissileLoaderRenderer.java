package amrac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
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
import amrac.AmracMod;
import amrac.client.render.models.MissileLoaderModel;
import amrac.entities.loader.LoaderPolicy;
import amrac.entities.loader.MissileLoaderEntity;

public final class MissileLoaderRenderer
        extends EntityRenderer<MissileLoaderEntity, MissileLoaderRenderer.State> {
    private static final Identifier TEXTURE =
        AmracMod.id("textures/entity/missile_loader.png");

    private static final MissileLoaderModel MODEL = new MissileLoaderModel();

    private static final int SHOWN_ROUNDS = 4;
    private static final float ROUND_SCALE = 1.5F;

    public static final class State extends EntityRenderState {
        float yaw;
        final String[] cargo = new String[LoaderPolicy.MAX_STATIONS];
        int cargoCount;
    }

    public MissileLoaderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.9F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MissileLoaderEntity truck, State state,
                                   float partialTicks) {
        super.extractRenderState(truck, state, partialTicks);
        state.yaw = Mth.rotLerp(partialTicks, truck.yRotO, truck.getYRot());
        state.cargoCount = 0;
        int stations = truck.airframe().stations();
        for (int slot = 0; slot < stations && state.cargoCount < SHOWN_ROUNDS;
                slot++) {
            var stack = truck.cargo().getItem(slot);
            if (stack.getItem()
                    instanceof amrac.items.MissileItem round) {
                state.cargo[state.cargoCount++] = round.profileId();
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);

        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE),
            (rootPose, buffer) -> MODEL.root().render(
                PlaneMeshRenderer.poseFrom(rootPose), buffer, light,
                OverlayTexture.NO_OVERLAY));
        poseStack.popPose();

        submitCargo(state, poseStack, collector);
        super.submit(state, poseStack, collector, camera);
    }

    private static void submitCargo(State state, PoseStack poseStack,
                                    SubmitNodeCollector collector) {
        for (int index = 0; index < state.cargoCount; index++) {
            String profileId = state.cargo[index];
            if (profileId == null) {
                continue;
            }
            float across = (index - (state.cargoCount - 1) * 0.5F) * 0.28F;

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.translate(across, 0.68D, 0.25D);
            poseStack.scale(ROUND_SCALE, ROUND_SCALE, ROUND_SCALE);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            PylonMissiles.submitOne(poseStack, collector, state.lightCoords,
                profileId);
            poseStack.popPose();
        }
    }
}
