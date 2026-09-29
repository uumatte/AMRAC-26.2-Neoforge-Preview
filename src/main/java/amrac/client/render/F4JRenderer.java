package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.F4JModel;
import amrac.entities.F4JEntity;

public final class F4JRenderer extends PlaneMeshRenderer<F4JEntity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/f4j.png");
    private static final float[][] PYLONS = {
        {8.28F, 20.87F, -3.69F}, {13.48F, 19.98F, 4.96F}
    };

    public F4JRenderer(EntityRendererProvider.Context context) {
        super(context, new F4JModel(), TEXTURE, 1.0F, PYLONS,
            17.72F / 16.0F, 20.68F / 16.0F, 2.48F / 16.0F,
            1.52F / 16.0F, 0.32F, 2.85F);
    }
}
