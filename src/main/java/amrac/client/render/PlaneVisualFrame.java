package amrac.client.render;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import amrac.entities.PlaneEntity;

/**
 * The model's visual pivot offset is defined only here; renderers, camera, riders and the gun/bomb
 * points depend on it. Scale the pivot height with the airframe if the model grows.
 */
public final class PlaneVisualFrame {
    public static final double ENTITY_RENDER_Y_OFFSET = 0.375D;
    public static final double MODEL_ORIGIN_Y_OFFSET = -0.5D;
    public static final double FIRST_PERSON_PRE_ROTATION_Y_OFFSET = -0.7D;

    private PlaneVisualFrame() {
    }

    public static double worldRotationPivotHeight(PlaneEntity plane,
                                                  boolean firstPerson) {
        double base = ENTITY_RENDER_Y_OFFSET - MODEL_ORIGIN_Y_OFFSET -
            (firstPerson ? FIRST_PERSON_PRE_ROTATION_Y_OFFSET : 0.0D);
        double scale = plane == null ? 1.0D : plane.getModelScale();
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            scale = 1.0D;
        }
        return base * scale;
    }

    public static double passengerEyeHeight(PlaneEntity plane, Player player) {
        return plane.getPassengersRidingOffset() -
            player.getVehicleAttachmentPoint(plane).y() + player.getEyeHeight();
    }

    public static Quaternionf planeWorldRotation(Quaternionf attitude) {
        return new Quaternionf(attitude.x(), -attitude.y(), -attitude.z(),
            attitude.w());
    }

    public static Vec3 rotateToWorld(Quaternionf attitude, double x, double y,
                                     double z) {
        Vector3f offset = new Vector3f((float) x, (float) y, (float) z);
        offset.rotate(planeWorldRotation(attitude));
        return new Vec3(offset.x(), offset.y(), offset.z());
    }

    public static Vec3 visualAnchor(PlaneEntity plane, Quaternionf attitude,
                                    float partialTicks, boolean firstPerson,
                                    double localHeight) {
        Vec3 planePosition = interpolatedRenderPosition(plane, partialTicks);
        double pivot = worldRotationPivotHeight(plane, firstPerson);
        Vec3 localOffset = rotateToWorld(attitude, 0.0D, localHeight - pivot, 0.0D);
        return planePosition.add(0.0D, pivot, 0.0D).add(localOffset);
    }

    public static Vec3 interpolatedRenderPosition(Entity entity, float partialTicks) {
        return new Vec3(
            Mth.lerp(partialTicks, entity.xOld, entity.getX()),
            Mth.lerp(partialTicks, entity.yOld, entity.getY()),
            Mth.lerp(partialTicks, entity.zOld, entity.getZ()));
    }
}
