package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.J10cModel;
import amrac.entities.J10cEntity;

public final class J10cRenderer extends PlaneMeshRenderer<J10cEntity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/j10c.png");
    private static final float[][] PYLONS = {
        {7.80F, 21.19F, 8.20F}, {11.20F, 21.19F, 10.60F},
        {14.80F, 21.06F, 13.50F}
    };

    public J10cRenderer(EntityRendererProvider.Context context) {
        super(context, new J10cModel(), TEXTURE, 1.2F, PYLONS,
            17.83F / 16.0F, 30.40F / 16.0F, 0.0F,
            2.18F / 16.0F, 0.32F, 2.85F);
    }
}
