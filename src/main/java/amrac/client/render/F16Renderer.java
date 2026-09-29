package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.F16bModel;
import amrac.entities.F16Entity;

public final class F16Renderer extends PlaneMeshRenderer<F16Entity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/f16b.png");
    private static final float[][] PYLONS = {
        {12.20F, 19.05F, 9.48F}, {15.72F, 19.12F, 11.28F}
    };

    public F16Renderer(EntityRendererProvider.Context context) {
        super(context, new F16bModel(), TEXTURE, 0.8F, PYLONS,
            17.68F / 16.0F, 28.18F / 16.0F, 0.0F,
            2.10F / 16.0F, 0.35F, 3.10F);
    }
}
