package amrac.entities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// width and height are only read by the proximity fuse.
public record AircraftTargetSnapshot(UUID id, Vec3 position, Vec3 velocity,
                                     float width, float height,
                                     AircraftRegistry.Presence presence,
                                     boolean afterburner) {
    public static AircraftTargetSnapshot of(PlaneEntity plane) {
        AABB box = plane.getBoundingBox();
        // Fuse room for a pilot the server only hears in bursts.
        float grow = (float) (2.0D * plane.clientPositionSlack());
        return new AircraftTargetSnapshot(plane.getUUID(),
            box.getCenter().add(plane.silentClientOffset()),
            plane.getDeltaMovement(), (float) (box.maxX - box.minX) + grow,
            (float) (box.maxY - box.minY) + grow, AircraftRegistry.Presence.LIVE,
            plane.isAfterburnerLit());
    }

    @Nullable
    public static AircraftTargetSnapshot of(ServerLevel level,
                                            @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        AircraftRegistry.Record record = AircraftRegistry.record(level, id);
        if (record == null || !record.simulated()) {
            return null;
        }
        if (record.presence == AircraftRegistry.Presence.LIVE) {
            PlaneEntity live = AircraftRegistry.liveEntity(id);
            if (live != null && live.isAlive()) {
                return of(live);
            }
            return null;
        }
        VirtualAircraftState state = AircraftVirtualService.get(id);
        boolean afterburner = state != null && state.powered
            && state.afterburnerSpool > 0.5F;
        return new AircraftTargetSnapshot(id, record.position, record.velocity,
            record.width, record.height, record.presence, afterburner);
    }

    public AABB box() {
        return AABB.ofSize(position, width, height, width);
    }

    public boolean virtual() {
        return presence == AircraftRegistry.Presence.VIRTUAL;
    }
}
