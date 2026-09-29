package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.J8IIModel;
import amrac.entities.J8IIEntity;

public final class J8IIRenderer extends PlaneMeshRenderer<J8IIEntity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/j8ii.png");
    private static final float[][] PYLONS = {
        {7.12F, 19.13F, 2.51F}, {14.82F, 19.05F, 13.13F}
    };

    public J8IIRenderer(EntityRendererProvider.Context context) {
        super(context, new J8IIModel(), TEXTURE, 1.2F, PYLONS,
            15.56F / 16.0F, 30.86F / 16.0F, 1.84F / 16.0F,
            1.40F / 16.0F, 0.32F, 2.85F);
    }
}
