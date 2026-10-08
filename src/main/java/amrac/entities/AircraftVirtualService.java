package amrac.entities;

import amrac.platform.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import amrac.AmracEntities;
import amrac.entities.ai.AiAltitudePolicy;
import amrac.entities.ai.AiCommand;
import amrac.entities.ai.AiPilotBrain;
import amrac.entities.ai.AiPilotEntity;
import amrac.entities.ai.AiPilotService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AircraftVirtualService {
    private static final Map<UUID, VirtualAircraftState> ACTIVE =
        new LinkedHashMap<>();

    private AircraftVirtualService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AircraftVirtualService::tick);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    @Nullable
    public static VirtualAircraftState get(UUID id) {
        return id == null ? null : ACTIVE.get(id);
    }

    public static boolean isVirtual(UUID id) {
        return ACTIVE.containsKey(id);
    }

    public static List<VirtualAircraftState> all() {
        return ACTIVE.isEmpty() ? List.of() : new ArrayList<>(ACTIVE.values());
    }

    public static void adopt(VirtualAircraftState state) {
        ACTIVE.put(state.id, state);
    }

    public static int discard(@Nullable ServerLevel level, Vec3 centre,
                              double radius) {
        double radiusSquared = radius * radius;
        List<UUID> doomed = new ArrayList<>();
        for (VirtualAircraftState state : ACTIVE.values()) {
            if (level != null && !state.dimension.equals(level.dimension())) {
                continue;
            }
            if (radius >= 0.0D
                    && state.position.distanceToSqr(centre) > radiusSquared) {
                continue;
            }
            doomed.add(state.id);
        }
        for (UUID id : doomed) {
            destroy(id);
        }
        return doomed.size();
    }

    public static boolean destroy(UUID id) {
        VirtualAircraftState state = ACTIVE.remove(id);
        AircraftRegistry.forget(id);
        if (state != null && state.pilotId != null) {
            AiPilotService.forget(state.pilotId);
        }
        return state != null;
    }

    private static void tick(MinecraftServer server) {
        double realiseDistance = AircraftLifecyclePolicy.realiseDistance(
            server.getPlayerList().getViewDistance(),
            server.getPlayerList().getSimulationDistance());
        handOverDepartingAircraft(server);
        if (ACTIVE.isEmpty()) {
            return;
        }
        List<VirtualAircraftState> arriving = new ArrayList<>();
        List<UUID> finished = new ArrayList<>();
        List<UUID> crashed = new ArrayList<>();

        for (VirtualAircraftState state : ACTIVE.values()) {
            ServerLevel level = server.getLevel(state.dimension);
            if (level == null) {
                finished.add(state.id);
                continue;
            }

            AiPilotBrain pilot = state.pilotId == null ? null
                : AiPilotService.existing(state.pilotId);
            double previousAltitude = state.position.y;
            step(state, pilot, level);

            if (state.powered) {
                holdAboveFloor(state, previousAltitude);
            }
            if (pilot != null) {
                pilot.releaseVirtualRounds(state);
            }
            if (AiAltitudePolicy.fuelStarvedCrash(state.position.y,
                    state.powered)) {
                announceFuelLoss(server, state);
                crashed.add(state.id);
                continue;
            }
            if (state.position.y < AircraftLifecyclePolicy.VIRTUAL_LOST_ALTITUDE) {
                crashed.add(state.id);
                continue;
            }
            if (!isFinite(state.position) || !isFinite(state.velocity)) {
                finished.add(state.id);
                continue;
            }

            publish(state, server.getTickCount());

            double nearest = nearestPlayerDistance(level, state.position);
            boolean terrainLoaded = level.isPositionEntityTicking(
                BlockPos.containing(state.position));
            boolean climbingOut = AircraftLifecyclePolicy.departing(
                pilot != null, state.position.y, state.onGround);
            if (AircraftLifecyclePolicy.shouldRealise(nearest, state.position.y,
                false, false, climbingOut, terrainLoaded, realiseDistance)) {
                arriving.add(state);
            }
        }

        for (UUID id : crashed) {
            destroy(id);
        }
        for (UUID id : finished) {
            ACTIVE.remove(id);
            AircraftRegistry.forget(id);
        }
        for (VirtualAircraftState state : arriving) {
            ACTIVE.remove(state.id);
            ServerLevel level = server.getLevel(state.dimension);
            if (level != null) {
                realise(level, state);
            }
        }
    }

    private static void handOverDepartingAircraft(MinecraftServer server) {
        long now = server.getTickCount();
        double virtualiseDistance = AircraftLifecyclePolicy.virtualiseDistance(
            server.getPlayerList().getViewDistance(),
            server.getPlayerList().getSimulationDistance());
        for (ServerLevel level : server.getAllLevels()) {
            List<AircraftRegistry.Record> live = new ArrayList<>();
            for (AircraftRegistry.Record record
                : AircraftRegistry.simulated(level)) {
                if (record.presence == AircraftRegistry.Presence.LIVE
                    && record.crewed && !record.playerCrewed) {
                    live.add(record);
                }
            }
            for (AircraftRegistry.Record record : live) {
                PlaneEntity plane = AircraftRegistry.liveEntity(record.id);
                if (plane == null || !plane.isAlive()) {
                    continue;
                }
                boolean onGround = plane.getOnGround() || plane.isOnWater();
                double nearest = nearestPlayerDistance(level, plane.position());
                boolean climbingOut = AircraftLifecyclePolicy.departing(
                    hasDestination(plane), plane.getY(), onGround);
                if (AircraftLifecyclePolicy.shouldVirtualise(nearest,
                    plane.getY(), false, onGround, climbingOut,
                    virtualiseDistance)) {
                    virtualise(plane, now);
                }
            }
        }
    }

    private static boolean hasDestination(PlaneEntity plane) {
        for (Entity passenger : plane.getPassengers()) {
            if (passenger instanceof AiPilotEntity pilot) {
                return pilot.isMissionActive();
            }
        }
        return false;
    }

    private static void captureFlightState(VirtualAircraftState state,
                                           PlaneEntity plane) {
        state.flightModelId = plane.flightModelId();
        state.attitude = new Quaternionf(plane.getQ());
        state.pitchRate = plane.getPitchAngularRate();
        state.yawRate = plane.getYawAngularRate();
        state.rollRate = plane.getRollAngularRate();
        state.throttle = plane.getThrottle();
        state.gearPosition = plane.getGearPosition();
        state.flapPosition = plane.getFlapPosition();
        state.speedBrakePosition = plane.getSpeedBrakePosition();
        state.afterburnerSpool = plane.afterburnerSpoolForModel();
        state.fuelMassOffsetKilograms = plane.getFuelMassOffsetKilograms();
        state.storesMassKilograms = plane.getStoresMassKilograms();
        state.storesDragArea = plane.getStoresDragArea();
        state.loadout = plane.getLoadout();
        state.powered = plane.isPowered();
        state.angleOfAttackLimiter = plane.isAngleOfAttackLimiterEnabled();
        for (Entity passenger : plane.getPassengers()) {
            if (passenger instanceof AiPilotEntity pilot) {
                state.team = pilot.teamName();
                break;
            }
            if (passenger instanceof net.minecraft.world.entity.player.Player
                    player && plane.level() instanceof ServerLevel level) {
                var team = level.getScoreboard()
                    .getPlayersTeam(player.getScoreboardName());
                state.team = team == null ? "" : team.getName();
                break;
            }
        }
    }

    private static void step(VirtualAircraftState state,
                             @Nullable AiPilotBrain pilot,
                             ServerLevel level) {
        AiCommand command = pilot == null ? null
            : pilot.virtualCommand(level, state);

        if (command != null) {
            var result = VirtualAircraftPhysics.stepReporting(state, command);
            if (result != null) {
                state.flightState = result.state();
                return;
            }
        }

        double[] position = {state.position.x, state.position.y, state.position.z};
        double[] velocity = {state.velocity.x, state.velocity.y, state.velocity.z};
        double goalX = position[0] + velocity[0] * 40.0D;
        double goalZ = position[2] + velocity[2] * 40.0D;
        double targetAltitude = Math.max(position[1],
            AircraftLifecyclePolicy.VIRTUAL_ALTITUDE_FLOOR + 20.0D);
        VirtualAircraftFlightPolicy.step(position, velocity, goalX, goalZ,
            targetAltitude, DERELICT_CRUISE_SPEED
                * amrac.physics.aircraft.SpeedScale.current());

        state.position = new Vec3(position[0], position[1], position[2]);
        state.velocity = new Vec3(velocity[0], velocity[1], velocity[2]);
        double[] attitude = new double[2];
        VirtualAircraftFlightPolicy.attitudeFor(velocity, attitude);
        state.yaw = (float) attitude[0];
        state.pitch = (float) attitude[1];
    }

    private static final double DERELICT_CRUISE_SPEED = 12.5D;

    private static void announceFuelLoss(MinecraftServer server,
                                         VirtualAircraftState state) {
        String pilot = state.pilotId == null ? null : callsignOf(state.pilotId);
        server.getPlayerList().broadcastSystemMessage(pilot == null
            ? net.minecraft.network.chat.Component.translatable(
                "amrac.message.fuel_lost_unknown")
            : net.minecraft.network.chat.Component.translatable(
                "amrac.message.fuel_lost", pilot), false);
    }

    @Nullable
    private static String callsignOf(UUID pilotId) {
        return AiPilotService.existing(pilotId) == null ? null
            : AiPilotService.displayName(null, pilotId);
    }

    @Nullable
    public static String shooterName(ServerLevel level, @Nullable UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        String pilot = AiPilotService.displayName(level, ownerId);
        if (pilot != null) {
            return pilot;
        }
        ServerPlayer player = level.getServer().getPlayerList()
            .getPlayer(ownerId);
        return player == null ? null : player.getName().getString();
    }

    public static void announceShotDown(MinecraftServer server, UUID aircraftId,
                                        @Nullable String killer,
                                        net.minecraft.network.chat.Component weapon) {
        VirtualAircraftState state = ACTIVE.get(aircraftId);
        if (server == null || state == null) {
            return;
        }
        if (!Boolean.TRUE.equals(server.overworld().getGameRules()
                .get(net.minecraft.world.level.gamerules.GameRules
                    .SHOW_DEATH_MESSAGES))) {
            return;
        }
        String victim = AiPilotService.displayName(
            server.getLevel(state.dimension), state.pilotId);
        if (victim == null || killer == null) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.translatable(
                "death.attack.plane_shot_down.item",
                net.minecraft.network.chat.Component.literal(victim),
                net.minecraft.network.chat.Component.literal(killer), weapon),
            false);
    }

    public static void holdAboveFloor(VirtualAircraftState state,
                                      double previousAltitude) {
        if (!AircraftLifecyclePolicy.mustHoldAboveFloor(state.position.y,
                state.velocity.y)) {
            return;
        }
        state.position = new Vec3(state.position.x,
            AircraftLifecyclePolicy.heldAltitude(state.position.y,
                previousAltitude),
            state.position.z);
        state.velocity = new Vec3(state.velocity.x, 0.0D, state.velocity.z);
        if (!state.heldAboveFloor) {
            state.heldAboveFloor = true;
            amrac.AmracMod.LOGGER.warn(
                "Virtual aircraft {} could not hold its height and is being "
                    + "held at the {} block floor; it would otherwise have "
                    + "fallen out of the world and been deleted",
                state.id, (int) AircraftLifecyclePolicy.VIRTUAL_ALTITUDE_FLOOR);
        }
    }

    private static void publish(VirtualAircraftState state, long now) {
        AircraftRegistry.publishVirtual(state, now);
    }

    public static void realise(ServerLevel level, VirtualAircraftState state) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(state.type);
        if (type == null || !(type.create(level, EntitySpawnReason.LOAD)
                instanceof PlaneEntity plane)) {
            return;
        }
        plane.setUUID(state.id);
        plane.setPos(state.position.x, state.position.y, state.position.z);
        plane.xo = state.position.x;
        plane.yo = state.position.y;
        plane.zo = state.position.z;
        plane.setDeltaMovement(state.velocity);
        plane.setYRot(state.yaw);
        plane.setXRot(state.pitch);
        plane.yRotO = state.yaw;
        plane.xRotO = state.pitch;
        plane.readAdditionalSaveData(TagValueInput.create(
            ProblemReporter.DISCARDING, level.registryAccess(), state.saved));
        plane.setQ(state.attitude);
        plane.setQ_Client(state.attitude);
        plane.setQ_prev(state.attitude);
        plane.setBodyAngularRates(state.pitchRate, state.yawRate,
            state.rollRate);
        if (state.loadout.length > 0) {
            plane.setLoadout(state.loadout);
        }
        AircraftRegistry.forget(state.id);
        level.addFreshEntity(plane);

        if (state.pilotId != null) {
            AiPilotEntity pilot = AmracEntities.AI_PILOT.create(
                level, EntitySpawnReason.LOAD);
            if (pilot != null) {
                pilot.setUUID(state.pilotId);
                pilot.setPos(state.position.x, state.position.y,
                    state.position.z);
                if (state.pilotSaved != null) {
                    pilot.readAdditionalSaveData(TagValueInput.create(
                        ProblemReporter.DISCARDING, level.registryAccess(),
                        state.pilotSaved));
                }
                level.addFreshEntity(pilot);
                pilot.startRiding(plane, true, true);
            }
        }
    }

    public static boolean virtualise(PlaneEntity plane, long now) {
        amrac.entities.ai.AiPilotService
            .serviceFromBackpack(plane);
        VirtualAircraftState state = snapshot(plane, now);
        if (state == null) {
            return false;
        }
        adopt(state);

        for (Entity passenger : new ArrayList<>(plane.getPassengers())) {
            passenger.stopRiding();
            passenger.discard();
        }
        plane.discard();
        return true;
    }

    @Nullable
    public static boolean handOver(PlaneEntity plane, ServerLevel level) {
        if (isVirtual(plane.getUUID())) {
            return false;
        }
        boolean underOrders = false;
        for (net.minecraft.world.entity.Entity passenger : plane.getPassengers()) {
            if (passenger instanceof amrac.entities.ai
                    .AiPilotEntity pilot && pilot.isMissionActive()) {
                underOrders = true;
                break;
            }
        }
        if (!underOrders) {
            return false;
        }
        return virtualise(plane, level.getServer().getTickCount());
    }

    public static VirtualAircraftState snapshot(PlaneEntity plane, long now) {
        if (!(plane.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID pilotId = null;
        CompoundTag pilotSaved = null;
        for (Entity passenger : plane.getPassengers()) {
            if (passenger instanceof AiPilotEntity pilot) {
                pilotId = pilot.getUUID();
                TagValueOutput pilotOutput = TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING, level.registryAccess());
                pilot.addAdditionalSaveData(pilotOutput);
                pilotSaved = pilotOutput.buildResult();
            } else {
                return null;
            }
        }
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, level.registryAccess());
        plane.addAdditionalSaveData(output);
        CompoundTag saved = output.buildResult();

        VirtualAircraftState state = new VirtualAircraftState(plane.getUUID(),
            level.dimension(),
            BuiltInRegistries.ENTITY_TYPE.getKey(plane.getType()),
            plane.position(), plane.getDeltaMovement(), plane.getYRot(),
            plane.getXRot(), pilotId, pilotSaved, saved, now);
        captureFlightState(state, plane);
        state.onGround = plane.getOnGround() || plane.isOnWater();
        state.groundAltitude = plane.getY();
        return state;
    }

    static double nearestPlayerDistance(ServerLevel level, Vec3 position) {
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            nearest = Math.min(nearest,
                AircraftLifecyclePolicy.horizontalLoadDistance(
                    player.getX() - position.x, player.getZ() - position.z));
        }
        return level.players().isEmpty() ? Double.NaN : nearest;
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y)
            && Double.isFinite(v.z);
    }
}
