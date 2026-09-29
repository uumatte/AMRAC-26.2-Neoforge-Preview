package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.Su27Model;
import amrac.entities.Su27Entity;

public final class Su27Renderer extends PlaneMeshRenderer<Su27Entity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/su27.png");
    private static final float[][] PYLONS = {
        { 8.50F, 15.35F,  8.00F}, {12.00F, 15.38F, 12.00F},
        {18.00F, 15.43F, 17.00F}
    };

    public Su27Renderer(EntityRendererProvider.Context context) {
        super(context, new Su27Model(), TEXTURE, 1.2F, PYLONS,
            16.30F / 16.0F, 36.40F / 16.0F, 5.20F / 16.0F,
            2.28F / 16.0F, 0.32F, 2.85F);
    }
}
