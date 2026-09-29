package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.Su30Model;
import amrac.entities.Su30Entity;

public final class Su30Renderer extends PlaneMeshRenderer<Su30Entity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/su30.png");
    private static final float[][] PYLONS = {
        {8.50F, 15.35F, 8.00F}, {12.00F, 15.38F, 12.00F},
        {18.00F, 15.43F, 17.00F}, {23.50F, 15.48F, 18.50F}
    };

    public Su30Renderer(EntityRendererProvider.Context context) {
        super(context, new Su30Model(), TEXTURE, 1.3F, PYLONS,
            16.30F / 16.0F, 36.40F / 16.0F, 5.20F / 16.0F,
            2.28F / 16.0F, 0.36F, 3.20F);
    }
}
