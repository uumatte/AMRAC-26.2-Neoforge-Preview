package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.Mig29Model;
import amrac.entities.Mig29Entity;

public final class Mig29Renderer extends PlaneMeshRenderer<Mig29Entity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/mig29.png");
    private static final float[][] PYLONS = {
        {9.50F, 18.95F, 9.37F}, {15.50F, 19.19F, 14.04F}
    };

    public Mig29Renderer(EntityRendererProvider.Context context) {
        super(context, new Mig29Model(), TEXTURE, 1.2F, PYLONS,
            17.84F / 16.0F, 29.04F / 16.0F, 3.28F / 16.0F,
            1.90F / 16.0F, 0.32F, 2.85F);
    }
}
