package amrac.entities.ai;

import amrac.platform.ServerEntityEvents;
import amrac.platform.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiPilotService {
    private static final Map<UUID, AiPilotBrain> BRAINS =
        new LinkedHashMap<>();

    private AiPilotService() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof AiPilotEntity pilot && !pilot.isRemoved()) {
                brainFor(pilot.getUUID());
                remember(pilot, level);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (!(entity instanceof AiPilotEntity pilot)) {
                return;
            }
            AiPilotBrain brain = BRAINS.get(pilot.getUUID());
            if (brain != null) {
                UUID flying = brain.flownAircraft();
                if (flying != null
                    && amrac.entities.AircraftVirtualService
                        .isVirtual(flying)) {
                    return;
                }
            }
            BRAINS.remove(pilot.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(AiPilotService::tick);
    }

    public static AiPilotBrain brainFor(UUID id) {
        return BRAINS.computeIfAbsent(id, AiPilotBrain::new);
    }

    @Nullable
    public static String displayName(ServerLevel level, UUID pilotId) {
        if (pilotId == null) {
            return null;
        }
        AiPilotBrain brain = existing(pilotId);
        if (brain != null) {
            return AiCallsignPolicy.displayName(brain.callsign(), brain.team());
        }
        if (level == null) {
            return null;
        }
        AiPilotRoster.Entry entry = AiPilotRoster.of(level).pilot(pilotId);
        return entry == null ? null
            : AiCallsignPolicy.displayName(entry.callsign(), entry.team());
    }

    @Nullable
    public static AiPilotBrain existing(UUID id) {
        return BRAINS.get(id);
    }

    @Nullable
    public static AiPilotBrain flying(UUID aircraftId) {
        if (aircraftId == null) {
            return null;
        }
        for (AiPilotBrain brain : BRAINS.values()) {
            if (aircraftId.equals(brain.flownAircraft())) {
                return brain;
            }
        }
        return null;
    }

    public static java.util.List<AiPilotBrain> allBrains() {
        return new java.util.ArrayList<>(BRAINS.values());
    }

    public static int activeCount() {
        return BRAINS.size();
    }

    public static void forget(UUID id) {
        BRAINS.remove(id);
    }

    public static java.util.List<UUID> knownPilots() {
        return new ArrayList<>(BRAINS.keySet());
    }

    public static int forgetAll() {
        int count = BRAINS.size();
        BRAINS.clear();
        return count;
    }

    private static void tick(MinecraftServer server) {
        if (BRAINS.isEmpty()) {
            return;
        }
        List<UUID> orphaned = new ArrayList<>();
        for (Map.Entry<UUID, AiPilotBrain> entry : BRAINS.entrySet()) {
            AiPilotEntity body = findBody(server, entry.getKey());
            if (body == null || !body.isAlive()) {
                UUID flying = entry.getValue().flownAircraft();
                boolean virtual = flying != null
                    && amrac.entities.AircraftVirtualService
                        .isVirtual(flying);
                if (!virtual && !onRoster(server, entry.getKey())) {
                    orphaned.add(entry.getKey());
                }
                continue;
            }
            if (body.level() instanceof ServerLevel level) {
                entry.getValue().tick(level, body);
                if (server.getTickCount() % 20L == 0L) {
                    remember(body, level);
                }
            }
        }
        for (UUID id : orphaned) {
            BRAINS.remove(id);
        }
    }

    private static boolean carriesRounds(ServerLevel level,
                                         AiPilotRoster.Entry entry) {
        if (entry.saved().isEmpty()) {
            return false;
        }
        AiPilotEntity pilot = amrac.AmracEntities
            .AI_PILOT.create(level,
                net.minecraft.world.entity.EntitySpawnReason.LOAD);
        if (pilot == null) {
            return false;
        }
        try {
            pilot.readAdditionalSaveData(
                net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING,
                    level.registryAccess(), entry.saved().get()));
            for (int slot = 0; slot < pilot.pilotInventory()
                    .getContainerSize(); slot++) {
                if (pilot.pilotInventory().getItem(slot).getItem()
                        instanceof amrac.items.MissileItem) {
                    return true;
                }
            }
            return false;
        } finally {
            pilot.discard();
        }
    }

    public static void serviceFromBackpack(
            amrac.entities.PlaneEntity plane) {
        if (plane == null || plane.level().isClientSide()) {
            return;
        }
        for (net.minecraft.world.entity.Entity passenger
                : plane.getPassengers()) {
            if (passenger instanceof AiPilotEntity pilot) {
                AiPilotBrain.serviceAircraft(pilot, plane);
                return;
            }
        }
    }

    @Nullable
    private static net.minecraft.nbt.CompoundTag[] serviceRemotely(
            ServerLevel level, AiPilotRoster.Parked aircraft,
            AiPilotRoster.Entry entry) {
        if (entry.saved().isEmpty()) {
            return null;
        }
        net.minecraft.world.entity.EntityType<?> type =
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getValue(aircraft.type());
        if (type == null) {
            return null;
        }
        if (!(type.create(level, net.minecraft.world.entity.EntitySpawnReason
                .LOAD) instanceof amrac.entities.PlaneEntity
                plane)) {
            return null;
        }
        AiPilotEntity pilot = amrac.AmracEntities
            .AI_PILOT.create(level,
                net.minecraft.world.entity.EntitySpawnReason.LOAD);
        if (pilot == null) {
            plane.discard();
            return null;
        }
        try {
            plane.readAdditionalSaveData(
                net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING,
                    level.registryAccess(), aircraft.saved()));
            pilot.readAdditionalSaveData(
                net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING,
                    level.registryAccess(), entry.saved().get()));
            AiPilotBrain.serviceAircraft(pilot, plane);
            net.minecraft.world.level.storage.TagValueOutput planeOut =
                net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING,
                        level.registryAccess());
            plane.addAdditionalSaveData(planeOut);
            net.minecraft.world.level.storage.TagValueOutput pilotOut =
                net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING,
                        level.registryAccess());
            pilot.addAdditionalSaveData(pilotOut);
            return new net.minecraft.nbt.CompoundTag[] {
                planeOut.buildResult(), pilotOut.buildResult()};
        } finally {
            plane.discard();
            pilot.discard();
        }
    }

    public static int pruneDeadPilots(ServerLevel level) {
        AiPilotRoster roster = AiPilotRoster.of(level);
        List<UUID> doomed = new ArrayList<>();
        for (AiPilotRoster.Entry entry : roster.pilots()) {
            if (level.getEntity(entry.pilotId()) != null) {
                continue;
            }
            AiPilotBrain brain = existing(entry.pilotId());
            if (brain != null && brain.flownAircraft() != null) {
                continue;
            }
            if (entry.aircraftId().isPresent()) {
                doomed.add(entry.pilotId());
                continue;
            }
            if (entry.saved().isPresent()) {
                continue;
            }
            doomed.add(entry.pilotId());
        }
        for (UUID id : doomed) {
            roster.forgetPilot(id);
            forget(id);
        }
        return doomed.size();
    }

    public static AiLaunchPolicy.Choice launch(ServerLevel level,
                                               AiPilotRoster.Entry entry) {
        AiPilotRoster roster = AiPilotRoster.of(level);
        AiPilotRank rank = entry.rankValue();
        AiPilotBrain existing = existing(entry.pilotId());
        boolean flying = existing != null && existing.flownAircraft() != null;

        boolean bringsRounds = carriesRounds(level, entry);
        List<AiLaunchPolicy.Candidate> candidates = new ArrayList<>();
        for (AiPilotRoster.Parked aircraft : roster.parked()) {
            candidates.add(new AiLaunchPolicy.Candidate(aircraft.aircraftId(),
                aircraft.flightModelId(),
                Math.sqrt(aircraft.position().distanceToSqr(entry.position())),
                false, aircraft.armed() || bringsRounds));
        }
        AiLaunchPolicy.Choice choice =
            AiLaunchPolicy.choose(rank, candidates, flying);
        if (!choice.launched()) {
            return choice;
        }
        AiPilotRoster.Parked aircraft = roster.parked(choice.aircraftId());
        if (aircraft == null) {
            return new AiLaunchPolicy.Choice(null,
                AiPilotIdleReason.NO_AIRCRAFT);
        }

        net.minecraft.nbt.CompoundTag planeSaved = aircraft.saved();
        net.minecraft.nbt.CompoundTag pilotSaved = entry.saved().orElse(null);
        net.minecraft.nbt.CompoundTag[] serviced =
            serviceRemotely(level, aircraft, entry);
        if (serviced != null) {
            planeSaved = serviced[0];
            pilotSaved = serviced[1];
        }

        // A remote take-off gives the airborne copy a new UUID, records the old id in
        // AiPilotRoster.retired (saved with the world) and calls scheduleSave at once. Miss any of
        // the three and the old aircraft and pilot reappear when the chunk reloads.
        UUID flyingAircraft = UUID.randomUUID();
        UUID flyingPilot = UUID.randomUUID();
        roster.retire(aircraft.aircraftId());
        roster.retire(entry.pilotId());
        amrac.entities.AircraftRegistry.forget(aircraft.aircraftId());
        forget(entry.pilotId());

        amrac.entities.VirtualAircraftState state =
            new amrac.entities.VirtualAircraftState(
                flyingAircraft, level.dimension(), aircraft.type(),
                aircraft.position(), net.minecraft.world.phys.Vec3.ZERO,
                aircraft.yaw(), 0.0F, flyingPilot,
                pilotSaved, planeSaved,
                level.getServer().getTickCount());
        state.flightModelId = aircraft.flightModelId();
        state.team = entry.team();
        state.powered = true;
        state.attitude = com.mojang.math.Axis.YP.rotationDegrees(-aircraft.yaw());
        state.onGround = true;
        state.groundAltitude = aircraft.position().y;
        state.gearPosition = 1.0F;

        String[] loadout = amrac.weapons.MissileLoadout
            .decode(planeSaved.getStringOr("loadout", ""));
        state.loadout = loadout;
        state.storesMassKilograms = amrac.weapons
            .MissileLoadout.storesMass(loadout);
        state.storesDragArea = amrac.weapons
            .MissileLoadout.storesDragArea(loadout);

        AiPilotBrain brain = brainFor(flyingPilot);
        brain.reset();
        brain.seed(rank, entry.callsign(), entry.team(),
            flyingAircraft, aircraft.flightModelId());
        amrac.entities.AircraftVirtualService.adopt(state);
        roster.unpark(aircraft.aircraftId());
        roster.forgetPilot(entry.pilotId());
        roster.remember(new AiPilotRoster.Entry(flyingPilot,
            entry.callsign(), entry.rank(), entry.variant(), entry.team(),
            entry.position(), true,
            java.util.Optional.of(flyingAircraft),
            java.util.Optional.ofNullable(pilotSaved)));
        level.getDataStorage().scheduleSave();
        return choice;
    }

    public static void remember(AiPilotEntity pilot, ServerLevel level) {
        remember(pilot, level, flownBy(pilot.getUUID()));
    }

    public static void rememberAboard(AiPilotEntity pilot, ServerLevel level,
                                      UUID aircraft) {
        remember(pilot, level, aircraft);
    }

    private static void remember(AiPilotEntity pilot, ServerLevel level,
                                 @Nullable UUID aircraft) {
        net.minecraft.world.level.storage.TagValueOutput output =
            net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING,
                level.registryAccess());
        pilot.addAdditionalSaveData(output);
        AiPilotRoster.of(level).remember(new AiPilotRoster.Entry(
            pilot.getUUID(), pilot.callsign(), pilot.rank().ordinal(),
            pilot.variant().ordinal(), pilot.teamName(), pilot.position(),
            pilot.isMissionActive(),
            java.util.Optional.ofNullable(aircraft),
            java.util.Optional.of(output.buildResult())));
    }

    @Nullable
    private static UUID flownBy(UUID pilotId) {
        AiPilotBrain brain = BRAINS.get(pilotId);
        return brain == null ? null : brain.flownAircraft();
    }

    private static boolean onRoster(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (AiPilotRoster.of(level).pilot(id) != null) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static AiPilotEntity findBody(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof AiPilotEntity pilot) {
                return pilot;
            }
        }
        return null;
    }
}
