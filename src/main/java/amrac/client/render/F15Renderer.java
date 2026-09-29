package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.F15Model;
import amrac.entities.F15Entity;

public final class F15Renderer extends PlaneMeshRenderer<F15Entity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/f15.png");
    private static final float[][] PYLONS = {
        {13.20F, 15.96F,  8.74F}, { 7.60F, 16.24F,  2.34F},
        { 8.32F, 16.64F, 14.74F}
    };

    public F15Renderer(EntityRendererProvider.Context context) {
        super(context, new F15Model(), TEXTURE, 1.1F, PYLONS,
            16.22F / 16.0F, 33.54F / 16.0F, 3.28F / 16.0F,
            2.00F / 16.0F, 0.32F, 2.85F);
    }
}
