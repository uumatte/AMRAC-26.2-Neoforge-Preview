package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.TyphoonModel;
import amrac.entities.TyphoonEntity;

public final class TyphoonRenderer
        extends PlaneMeshRenderer<TyphoonEntity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/typhoon.png");

    private static final float[][] PYLONS = {
        {6.60F, 19.27F, 4.48F}, {11.00F, 19.60F, 9.28F},
        {15.00F, 19.52F, 12.68F}
    };

    public TyphoonRenderer(EntityRendererProvider.Context context) {
        super(context, new TyphoonModel(), TEXTURE, 1.2F, PYLONS,
            17.62F / 16.0F, 27.88F / 16.0F, 2.16F / 16.0F,
            1.36F / 16.0F, 0.32F, 2.85F);
    }
}
