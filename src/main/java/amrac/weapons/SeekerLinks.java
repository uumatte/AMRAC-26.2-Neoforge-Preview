package amrac.weapons;

import amrac.entities.AircraftRegistry;
import amrac.entities.AircraftVirtualService;
import amrac.entities.PlaneEntity;
import amrac.entities.PlaneRadarService;
import amrac.entities.RadarPolicy;
import amrac.entities.VirtualAircraftState;
import amrac.physics.aircraft.AircraftPhysicsProfile;
import amrac.physics.aircraft.FlightModelRegistry;
import amrac.physics.aircraft.RadarProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The only check for whether the launcher can still illuminate or datalink, shared by the live and
 * virtual layers; two different checks once left one side guiding and the other ballistic after a
 * handover.
 */
public final class SeekerLinks {
    private SeekerLinks() {
    }

    public static SeekerPolicy.Link link(ServerLevel level,
                                         @Nullable UUID launcherId,
                                         @Nullable Vec3 targetPosition,
                                         @Nullable Vec3 targetVelocity) {
        if (launcherId == null) {
            return SeekerPolicy.Link.PERFECT;
        }
        if (targetPosition == null || targetVelocity == null) {
            return SeekerPolicy.Link.LOST;
        }
        AircraftRegistry.Record record = AircraftRegistry.record(level, launcherId);
        if (record == null || !record.simulated()) {
            return SeekerPolicy.Link.LOST;
        }
        PlaneEntity live = AircraftRegistry.liveEntity(launcherId);
        if (live != null) {
            return tracks(live, targetPosition, targetVelocity)
                ? SeekerPolicy.Link.TRACKING : SeekerPolicy.Link.LOST;
        }
        VirtualAircraftState virtual = AircraftVirtualService.get(launcherId);
        if (virtual == null) {
            return SeekerPolicy.Link.LOST;
        }
        return tracks(virtual, targetPosition, targetVelocity)
            ? SeekerPolicy.Link.TRACKING : SeekerPolicy.Link.LOST;
    }

    public static double launcherRange(@Nullable UUID launcherId,
                                       @Nullable Vec3 targetPosition) {
        if (launcherId == null || targetPosition == null) {
            return Double.NaN;
        }
        PlaneEntity live = AircraftRegistry.liveEntity(launcherId);
        if (live != null) {
            return live.position().distanceTo(targetPosition);
        }
        VirtualAircraftState virtual = AircraftVirtualService.get(launcherId);
        return virtual == null ? Double.NaN
            : virtual.position.distanceTo(targetPosition);
    }

    public static boolean tracks(PlaneEntity plane, Vec3 targetPosition,
                                 Vec3 targetVelocity) {
        if (!plane.isAlive() || !plane.hasRadar()) {
            return false;
        }
        if (plane.getControllingPassenger() instanceof ServerPlayer pilot
                && !PlaneRadarService.isRadarEnabled(pilot)) {
            return false;
        }
        return RadarPolicy.canTrack(plane.getRadarProfile(),
            plane.getBoundingBox().getCenter(),
            plane.getBodyDirection(0.0F, 0.0F, 1.0F),
            plane.getBodyDirection(0.0F, 1.0F, 0.0F),
            plane.getBodyDirection(1.0F, 0.0F, 0.0F),
            targetPosition, targetVelocity);
    }

    public static boolean tracks(VirtualAircraftState plane, Vec3 targetPosition,
                                 Vec3 targetVelocity) {
        AircraftPhysicsProfile profile = plane.flightModelId == null ? null
            : FlightModelRegistry.instance().profile(plane.flightModelId);
        RadarProfile set = profile == null ? null : profile.radar();
        return RadarPolicy.canTrack(set, plane.position,
            PlaneEntity.bodyDirection(plane.attitude, 0.0F, 0.0F, 1.0F),
            PlaneEntity.bodyDirection(plane.attitude, 0.0F, 1.0F, 0.0F),
            PlaneEntity.bodyDirection(plane.attitude, 1.0F, 0.0F, 0.0F),
            targetPosition, targetVelocity);
    }
}
