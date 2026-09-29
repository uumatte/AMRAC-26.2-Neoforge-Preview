package amrac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import amrac.blocks.MissileRackBlock;
import amrac.blocks.MissileRackBlockEntity;

public final class MissileRackRenderer
        implements BlockEntityRenderer<MissileRackBlockEntity,
            MissileRackRenderer.State> {
    private static final float MODEL_SCALE = 4.0F;

    private static final float CRADLE_HEIGHT = 0.95F;

    public static final class State extends BlockEntityRenderState {
        String profileId = "";
        Direction along = Direction.EAST;
    }

    public MissileRackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MissileRackBlockEntity rack, State state,
                                   float partialTicks, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(rack, state, partialTicks,
            cameraPos, crumbling);
        state.profileId = rack.profileId();
        state.along = rack.getBlockState().hasProperty(MissileRackBlock.FACING)
            ? rack.getBlockState().getValue(MissileRackBlock.FACING).getClockWise()
            : Direction.EAST;
    }

    @Override
    public void submit(State state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.profileId.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5D + state.along.getStepX() * 1.5D, CRADLE_HEIGHT,
            0.5D + state.along.getStepZ() * 1.5D);

        poseStack.mulPose(Axis.YP.rotationDegrees(-state.along.toYRot()));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        PylonMissiles.submitOne(poseStack, collector, state.lightCoords,
            state.profileId);
        poseStack.popPose();
    }
}
