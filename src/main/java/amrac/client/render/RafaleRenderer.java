package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.RafaleModel;
import amrac.entities.RafaleEntity;

public final class RafaleRenderer extends PlaneMeshRenderer<RafaleEntity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/rafale.png");
    private static final float[][] PYLONS = {
        {9.40F, 19.02F, 9.86F}, {13.40F, 19.10F, 12.46F},
        {16.40F, 19.18F, 14.66F}
    };

    public RafaleRenderer(EntityRendererProvider.Context context) {
        super(context, new RafaleModel(), TEXTURE, 0.95F, PYLONS,
            16.94F / 16.0F, 26.22F / 16.0F, 1.92F / 16.0F,
            1.52F / 16.0F, 0.28F, 2.30F);
    }
}
