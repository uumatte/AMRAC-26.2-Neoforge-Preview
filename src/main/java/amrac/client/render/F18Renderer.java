package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.F18Model;
import amrac.entities.F18Entity;

public final class F18Renderer extends PlaneMeshRenderer<F18Entity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/f18.png");
    private static final float[][] PYLONS = {
        {9.30F, 17.80F, 7.50F}, {14.00F, 17.75F, 9.50F},
        {19.00F, 17.70F, 11.50F}
    };

    public F18Renderer(EntityRendererProvider.Context context) {
        super(context, new F18Model(), TEXTURE, 1.0F, PYLONS,
            16.40F / 16.0F, 34.00F / 16.0F, 2.12F / 16.0F,
            1.28F / 16.0F, 0.30F, 2.40F);
    }
}
