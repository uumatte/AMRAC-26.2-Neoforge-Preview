package amrac.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import amrac.AmracMod;
import amrac.client.render.models.F15EModel;
import amrac.entities.F15EEntity;

/**
 * Pylon and plume coordinates in the aircraft renderers are copied by hand from the
 * tools/*MeshConverter output (the tools are not in this repository). Update them whenever a model
 * changes.
 */
public final class F15ERenderer extends PlaneMeshRenderer<F15EEntity> {
    public static final Identifier TEXTURE = AmracMod.id(
        "textures/entity/plane/f15e.png");
    private static final float[][] PYLONS = {
        {13.20F, 15.96F,  8.74F}, { 7.60F, 16.24F,  2.34F},
        { 8.32F, 16.64F, 14.74F}, {11.40F, 14.84F,  8.74F}
    };

    public F15ERenderer(EntityRendererProvider.Context context) {
        super(context, new F15EModel(), TEXTURE, 1.0F, PYLONS,
            16.22F / 16.0F, 33.54F / 16.0F, 3.28F / 16.0F,
            2.00F / 16.0F, 0.30F, 2.40F);
    }
}
