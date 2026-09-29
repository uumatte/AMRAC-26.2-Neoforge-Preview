package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.Mig23Model;
import amrac.entities.Mig23Entity;

public final class Mig23Renderer extends PlaneMeshRenderer<Mig23Entity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/mig23.png");
    private static final float[][] PYLONS = {
        {4.60F, 17.94F, 12.00F}, {7.60F, 17.75F, 14.60F}
    };

    public Mig23Renderer(EntityRendererProvider.Context context) {
        super(context, new Mig23Model(), TEXTURE, 1.0F, PYLONS,
            19.00F / 16.0F, 33.40F / 16.0F, 0.00F,
            1.96F / 16.0F, 0.34F, 2.90F);
    }
}
