package amrac.client.render;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import amrac.entities.ai.AiPilotEntity;
import amrac.entities.ai.AiPilotIdleReason;
import amrac.entities.ai.AiPilotVariant;

public final class AiPilotRenderer extends HumanoidMobRenderer<AiPilotEntity,
    AiPilotRenderer.State, SkeletonModel<AiPilotRenderer.State>> {
    private static final Identifier SKELETON = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/entity/skeleton/skeleton.png");
    private static final Identifier WITHER_SKELETON = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/entity/skeleton/wither_skeleton.png");

    public static final class State extends SkeletonRenderState {
        boolean wither;
    }

    public AiPilotRenderer(EntityRendererProvider.Context context) {
        super(context, new SkeletonModel<State>(
            context.bakeLayer(ModelLayers.SKELETON)), 0.5F);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AiPilotEntity pilot, State state,
                                   float partialTicks) {
        super.extractRenderState(pilot, state, partialTicks);
        state.wither = pilot.variant() == AiPilotVariant.WITHER_SKELETON;
        state.scale = state.wither ? 1.2F : 1.0F;
        AiPilotIdleReason reason = pilot.idleReason();
        if (reason.shown()) state.nameTag = Component.literal(reason.label());
    }

    @Override
    protected boolean shouldShowName(AiPilotEntity pilot, double distanceSq) {
        return pilot.idleReason().shown()
            || super.shouldShowName(pilot, distanceSq);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return state.wither ? WITHER_SKELETON : SKELETON;
    }
}
