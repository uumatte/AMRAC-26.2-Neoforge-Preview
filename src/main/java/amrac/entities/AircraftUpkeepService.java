package amrac.entities;

import amrac.platform.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PlaneEntity.tickUpkeepWhileUnticked copies the server-side upkeep in tick() and runs only while
 * the aircraft is not ticked. Changing that upkeep (fuel, gun cooldown, hurt timer, gear/flap
 * travel) means changing the copy.
 */
public final class AircraftUpkeepService {
    private static final Map<UUID, Integer> LAST_TICK = new HashMap<>();

    private static final Set<UUID> TICKED = new HashSet<>();

    public static boolean isTicking(PlaneEntity plane) {
        return plane == null || !LAST_TICK.containsKey(plane.getUUID())
            || TICKED.contains(plane.getUUID());
    }

    private AircraftUpkeepService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AircraftUpkeepService::tick);
    }

    private static void tick(MinecraftServer server) {
        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (AircraftRegistry.Record record
                    : AircraftRegistry.simulated(level)) {
                if (record.presence != AircraftRegistry.Presence.LIVE) {
                    continue;
                }
                PlaneEntity plane = AircraftRegistry.liveEntity(record.id);
                if (plane == null || !plane.isAlive()) {
                    continue;
                }
                seen.add(record.id);
                Integer previous = LAST_TICK.put(record.id, plane.tickCount);
                boolean advanced = previous == null
                    || previous != plane.tickCount;
                if (advanced) {
                    TICKED.add(record.id);
                    continue;
                }
                TICKED.remove(record.id);
                upkeep(level, plane);
            }
        }
        LAST_TICK.keySet().retainAll(seen);
        TICKED.retainAll(seen);
    }

    private static void upkeep(ServerLevel level, PlaneEntity plane) {
        if (!(plane.getControllingPassenger() instanceof ServerPlayer)) {
            if (AircraftVirtualService.handOver(plane, level)) {
                LAST_TICK.remove(plane.getUUID());
                return;
            }
        }
        plane.tickUpkeepWhileUnticked();
    }
}
