package amrac.client.render.models;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class MissileLoaderModel {
    private static final int GROUND = 24;

    private final ModelPart root;

    public MissileLoaderModel() {
        root = build().bakeRoot();
    }

    public ModelPart root() {
        return root;
    }

    private static LayerDefinition build() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();

        parts.addOrReplaceChild("deck", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-10.0F, GROUND - 10, -6.0F, 20, 4, 20), PartPose.ZERO);

        parts.addOrReplaceChild("cab", CubeListBuilder.create()
            .texOffs(0, 25)
            .addBox(-8.0F, GROUND - 20, -14.0F, 16, 10, 8), PartPose.ZERO);

        parts.addOrReplaceChild("rail_left", CubeListBuilder.create()
            .texOffs(50, 25)
            .addBox(-10.0F, GROUND - 13, -6.0F, 2, 3, 18), PartPose.ZERO);
        parts.addOrReplaceChild("rail_right", CubeListBuilder.create()
            .texOffs(50, 25)
            .addBox(8.0F, GROUND - 13, -6.0F, 2, 3, 18), PartPose.ZERO);

        int wheel = 0;
        for (float x : new float[] {-12.0F, 7.0F}) {
            for (float z : new float[] {-9.0F, 5.0F}) {
                parts.addOrReplaceChild("wheel_" + wheel++, CubeListBuilder.create()
                    .texOffs(82, 0)
                    .addBox(x, GROUND - 6, z, 5, 6, 6), PartPose.ZERO);
            }
        }

        parts.addOrReplaceChild("boom", CubeListBuilder.create()
            .texOffs(92, 25)
            .addBox(-2.0F, GROUND - 22, 8.0F, 4, 12, 4), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 64);
    }
}
