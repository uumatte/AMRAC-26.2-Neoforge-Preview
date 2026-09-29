package amrac.entities;

import amrac.platform.ServerEntityEvents;
import amrac.platform.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class AircraftRegistry {
    public enum Presence {
        LIVE,
        VIRTUAL,
        REMEMBERED
    }

    public static final class Record {
        public final UUID id;
        public final ResourceKey<Level> dimension;
        public Presence presence = Presence.REMEMBERED;
        public Vec3 position = Vec3.ZERO;
        public Vec3 velocity = Vec3.ZERO;
        public float width = 6.0F;
        public float height = 3.0F;
        public int entityId = -1;
        public boolean airborne;
        public boolean crewed;
        public boolean playerCrewed;
        public String team = "";
        public long stamp;

        @Nullable
        public VirtualAircraftState parked;

        Record(UUID id, ResourceKey<Level> dimension) {
            this.id = id;
            this.dimension = dimension;
        }

        public boolean simulated() {
            return presence == Presence.LIVE || presence == Presence.VIRTUAL;
        }
    }

    public static final long MEMORY_TICKS = 20L * 60L * 5L;

    private static final Map<ResourceKey<Level>, Map<UUID, Record>> BY_LEVEL =
        new HashMap<>();
    private static final Map<UUID, PlaneEntity> LIVE = new LinkedHashMap<>();

    private AircraftRegistry() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof PlaneEntity plane) {
                LIVE.put(plane.getUUID(), plane);
                stamp(plane, level.getServer().getTickCount());
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof PlaneEntity plane) {
                LIVE.remove(plane.getUUID());
                // The removal event fires for destruction and for virtualisation alike (virtualise
                // ends with discard); ask isVirtual first, or every handover drops the aircraft
                // from the registry.
                if (AircraftVirtualService.isVirtual(plane.getUUID())) {
                    return;
                }
                Entity.RemovalReason reason = plane.getRemovalReason();
                if (reason != null && reason.shouldDestroy()) {
                    forget(plane.getUUID());
                    return;
                }
                Record record = records(level.dimension()).get(plane.getUUID());
                if (record != null && record.presence == Presence.LIVE) {
                    if (adoptDeparting(plane, level)) {
                        return;
                    }
                    record.presence = Presence.REMEMBERED;
                    record.entityId = -1;
                    if (!AircraftVirtualService.isVirtual(plane.getUUID())) {
                        record.parked = AircraftVirtualService.snapshot(plane,
                            level.getServer().getTickCount());
                        if (record.parked != null && !record.crewed) {
                            amrac.entities.ai.AiPilotRoster
                                .of(level)
                                .park(new amrac.entities.ai
                                    .AiPilotRoster.Parked(
                                    plane.getUUID(),
                                    record.parked.type, plane.position(),
                                    plane.getYRot(),
                                    String.valueOf(plane.flightModelId()),
                                    amrac.entities.ai
                                        .AiPilotCombatPolicy.armed(
                                            record.parked.loadout),
                                    record.parked.saved));
                        }
                    }
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(AircraftRegistry::tick);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (PlaneEntity plane : new ArrayList<>(LIVE.values())) {
            if (plane.isAlive() && plane.level() instanceof ServerLevel level) {
                stamp(plane, now);
            } else {
                LIVE.remove(plane.getUUID());
            }
        }
        if (now % 100L == 0L) {
            forgetStaleRecords(now);
        }
    }

    private static void stamp(PlaneEntity plane, long now) {
        Record record = records(plane.level().dimension())
            .computeIfAbsent(plane.getUUID(),
                id -> new Record(id, plane.level().dimension()));
        record.presence = Presence.LIVE;
        record.position = plane.getBoundingBox().getCenter();
        record.velocity = plane.getDeltaMovement();
        record.entityId = plane.getId();
        net.minecraft.world.phys.AABB box = plane.getBoundingBox();
        record.width = (float) (box.maxX - box.minX);
        record.height = (float) (box.maxY - box.minY);
        record.airborne = !plane.getOnGround() && !onLoadedWater(plane);
        List<Entity> passengers = plane.getPassengers();
        record.crewed = !passengers.isEmpty();
        record.playerCrewed = passengers.stream()
            .anyMatch(p -> p instanceof net.minecraft.world.entity.player.Player);
        record.team = teamOf(plane, passengers);
        record.stamp = now;
    }

    private static boolean onLoadedWater(PlaneEntity plane) {
        net.minecraft.world.phys.AABB box = plane.getBoundingBox();
        if (!plane.level().hasChunksAt(
                net.minecraft.util.Mth.floor(box.minX) - 1, net.minecraft.util.Mth.floor(box.minZ) - 1,
                net.minecraft.util.Mth.floor(box.maxX) + 1, net.minecraft.util.Mth.floor(box.maxZ) + 1)) {
            return false;
        }
        return plane.isOnWater();
    }

    private static String teamOf(PlaneEntity plane, List<Entity> passengers) {
        for (Entity passenger : passengers) {
            if (passenger instanceof amrac.entities.ai.AiPilotEntity pilot) {
                return pilot.teamName();
            }
            if (passenger instanceof net.minecraft.world.entity.player.Player player
                    && plane.level() instanceof ServerLevel level) {
                net.minecraft.world.scores.PlayerTeam team =
                    level.getScoreboard().getPlayersTeam(player.getScoreboardName());
                return team == null ? "" : team.getName();
            }
        }
        return "";
    }

    private static boolean adoptDeparting(PlaneEntity plane, ServerLevel level) {
        boolean underOrders = false;
        for (Entity passenger : plane.getPassengers()) {
            if (passenger instanceof amrac.entities.ai
                    .AiPilotEntity pilot && pilot.isMissionActive()) {
                underOrders = true;
                break;
            }
        }
        if (!underOrders) {
            return false;
        }
        amrac.entities.ai.AiPilotService
            .serviceFromBackpack(plane);
        VirtualAircraftState state = AircraftVirtualService.snapshot(plane,
            level.getServer().getTickCount());
        if (state == null) {
            return false;
        }
        AircraftVirtualService.adopt(state);
        return true;
    }

    private static void forgetStaleRecords(long now) {
        for (Map<UUID, Record> level : BY_LEVEL.values()) {
            level.values().removeIf(record ->
                record.presence == Presence.REMEMBERED
                    && now - record.stamp > MEMORY_TICKS);
        }
    }

    private static Map<UUID, Record> records(ResourceKey<Level> dimension) {
        return BY_LEVEL.computeIfAbsent(dimension, key -> new LinkedHashMap<>());
    }

    public static Collection<Record> all(ServerLevel level) {
        return Collections.unmodifiableCollection(
            records(level.dimension()).values());
    }

    public static List<Record> simulated(ServerLevel level) {
        List<Record> out = new ArrayList<>();
        for (Record record : records(level.dimension()).values()) {
            if (record.simulated()) {
                out.add(record);
            }
        }
        return out;
    }

    @Nullable
    public static PlaneEntity liveEntity(UUID id) {
        PlaneEntity plane = LIVE.get(id);
        return plane != null && plane.isAlive() ? plane : null;
    }

    @Nullable
    public static Record record(ServerLevel level, UUID id) {
        return records(level.dimension()).get(id);
    }

    @Nullable
    public static Record nearest(ServerLevel level, Vec3 from,
                                 Predicate<Record> test) {
        Record best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Record record : records(level.dimension()).values()) {
            if (!test.test(record)) {
                continue;
            }
            double distance = record.position.distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = record;
            }
        }
        return best;
    }

    public static void publishVirtual(VirtualAircraftState state, long now) {
        Record record = records(state.dimension)
            .computeIfAbsent(state.id, id -> new Record(id, state.dimension));
        record.presence = Presence.VIRTUAL;
        record.position = state.position;
        record.velocity = state.velocity;
        record.entityId = -1;
        record.airborne = true;
        record.crewed = state.pilotId != null;
        record.playerCrewed = false;
        record.team = state.team;
        record.stamp = now;
    }

    public static void forget(UUID id) {
        LIVE.remove(id);
        for (Map<UUID, Record> level : BY_LEVEL.values()) {
            level.remove(id);
        }
    }

    public static List<Record> everything() {
        List<Record> out = new ArrayList<>();
        for (Map<UUID, Record> level : BY_LEVEL.values()) {
            out.addAll(level.values());
        }
        return out;
    }

    public static void forgetEverything() {
        LIVE.clear();
        BY_LEVEL.clear();
    }
}
