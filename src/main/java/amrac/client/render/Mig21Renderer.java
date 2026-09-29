package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.Mig21Model;
import amrac.entities.Mig21Entity;

public final class Mig21Renderer extends PlaneMeshRenderer<Mig21Entity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/mig21.png");
    private static final float[][] PYLONS = {
        { 8.24F, 19.58F, 4.08F}, {10.60F, 19.58F, 7.78F}
    };

    public Mig21Renderer(EntityRendererProvider.Context context) {
        super(context, new Mig21Model(), TEXTURE, 1.2F, PYLONS,
            17.84F / 16.0F, 25.76F / 16.0F, 0.0F,
            1.45F / 16.0F, 0.32F, 2.85F);
    }
}
