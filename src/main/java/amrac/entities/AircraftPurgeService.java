package amrac.entities;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import amrac.platform.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import amrac.entities.ai.AiPilotEntity;

public final class AircraftPurgeService {
    private static final Set<UUID> DOOMED = ConcurrentHashMap.newKeySet();

    private static volatile boolean sweeping;

    private AircraftPurgeService() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof PlaneEntity)
                    && !(entity instanceof AiPilotEntity)) {
                return;
            }
            boolean named = DOOMED.remove(entity.getUUID());
            boolean stale = amrac.entities.ai.AiPilotRoster.of(level)
                .claimRetired(entity.getUUID());
            if (named || stale || sweeping) {
                remove(entity);
            }
        });
    }

    public static void armSweep() {
        sweeping = true;
    }

    public static void disarmSweep() {
        sweeping = false;
    }

    public static boolean isSweeping() {
        return sweeping;
    }

    public static void doom(UUID id) {
        if (id != null) {
            DOOMED.add(id);
        }
    }

    public static int pending() {
        return DOOMED.size();
    }

    public static void cancel() {
        DOOMED.clear();
    }

    public static void remove(Entity entity) {
        if (entity instanceof PlaneEntity plane) {
            for (Entity rider : plane.getPassengers()) {
                if (!(rider instanceof Player)) {
                    DOOMED.remove(rider.getUUID());
                    strikeOff(rider);
                    rider.discard();
                }
            }
            plane.ejectPassengers();
            AircraftRegistry.forget(plane.getUUID());
        } else if (entity instanceof AiPilotEntity pilot) {
            amrac.entities.ai.AiPilotService
                .forget(pilot.getUUID());
            pilot.stopRiding();
        }
        strikeOff(entity);
        entity.discard();
    }

    private static void strikeOff(Entity entity) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        amrac.entities.ai.AiPilotRoster roster =
            amrac.entities.ai.AiPilotRoster.of(level);
        if (entity instanceof PlaneEntity) {
            roster.unpark(entity.getUUID());
        } else if (entity instanceof AiPilotEntity) {
            roster.forgetPilot(entity.getUUID());
            amrac.entities.ai.AiPilotService.forget(entity.getUUID());
        }
    }
}
