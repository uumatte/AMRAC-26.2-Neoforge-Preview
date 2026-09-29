package amrac.entities.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import amrac.platform.ServerLifecycleEvents;
import amrac.platform.ServerTickEvents;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import amrac.AmracEntities;
import amrac.AmracMod;
import amrac.blocks.AiCommandBlockEntity;
import amrac.entities.AircraftRegistry;
import amrac.entities.AircraftTypes;
import amrac.entities.AircraftVirtualService;
import amrac.entities.PlaneEntity;
import amrac.physics.aircraft.AircraftPhysicsProfile;
import amrac.physics.aircraft.CountermeasureProfile;
import amrac.physics.aircraft.FlightModelRegistry;
import amrac.weapons.MissileProfile;
import amrac.weapons.MissileProfiles;
import amrac.weapons.SummonLoadout;

public final class AiCommandLaunchService {
    public static final int AREA_RADIUS = 1;

    private static final int TICKET_RADIUS = AREA_RADIUS + 2;

    public static final int LOAD_TIMEOUT_TICKS = 20 * 30;

    public static final int HOLD_TIMEOUT_TICKS = 20 * 90;

    public static final double CLIMBED = 8.0D;

    private static final long TICKET_TIMEOUT_TICKS =
        LOAD_TIMEOUT_TICKS + HOLD_TIMEOUT_TICKS + 20L * 60L;

    public static final TicketType TICKET = Registry.register(
        BuiltInRegistries.TICKET_TYPE, AmracMod.id("ai_launch"),
        new TicketType(TICKET_TIMEOUT_TICKS, TicketType.FLAG_LOADING
            | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    private enum Stage { LOADING, FLYING }

    private static final class Launch {
        final ServerLevel level;
        final BlockPos source;
        final AiLaunchOrder order;
        final Vec3 position;
        final ChunkPos centre;
        final long started;
        Stage stage = Stage.LOADING;
        UUID aircraft;
        long spawnedAt;

        Launch(ServerLevel level, BlockPos source, AiLaunchOrder order,
               Vec3 position, long started) {
            this.level = level;
            this.source = source.immutable();
            this.order = order;
            this.position = position;
            this.centre = ChunkPos.containing(BlockPos.containing(position));
            this.started = started;
        }
    }

    private record Hold(ResourceKey<Level> dimension, long chunk) {
    }

    private static final List<Launch> LAUNCHES = new ArrayList<>();
    private static final Map<Hold, Integer> HOLDS = new HashMap<>();

    private AiCommandLaunchService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AiCommandLaunchService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LAUNCHES.clear();
            HOLDS.clear();
        });
    }

    public static int pending() {
        return LAUNCHES.size();
    }

    public static AiLaunchCatalog catalog(ServerLevel level) {
        FlightModelRegistry.instance().refresh();
        List<AiLaunchCatalog.Airframe> airframes = new ArrayList<>();
        for (Map.Entry<String, EntityType<? extends PlaneEntity>> entry
                : AircraftTypes.byName().entrySet()) {
            PlaneEntity plane = entry.getValue().create(level,
                EntitySpawnReason.LOAD);
            if (plane == null) {
                continue;
            }
            try {
                Set<String> rounds = new LinkedHashSet<>();
                if (plane.hasMissiles()) {
                    for (MissileProfile profile : MissileProfiles.CARRIABLE) {
                        if (plane.canCarry(profile.id)) {
                            rounds.add(profile.id);
                        }
                    }
                }
                CountermeasureProfile load = standardLoad(plane);
                airframes.add(new AiLaunchCatalog.Airframe(entry.getKey(),
                    String.valueOf(plane.flightModelId()), plane.hasMissiles(),
                    plane.pylonCount(), rounds, load.chaff(), load.flare()));
            } finally {
                plane.discard();
            }
        }
        Map<AiPilotRank, Set<String>> clearances = new EnumMap<>(AiPilotRank.class);
        for (AiPilotRank rank : AiPilotRank.values()) {
            clearances.put(rank, rank.airframes());
        }
        return new AiLaunchCatalog(airframes, clearances);
    }

    public static void trigger(ServerLevel level, BlockPos source,
                               Direction facing, AiCommandBlockEntity block) {
        AiLaunchOrder order = block.order();
        Vec3 position = position(level, source, facing, order.position());
        AiLaunchCheck.Problem problem = AiLaunchCheck.problem(order,
            catalog(level),
            team -> level.getScoreboard().getPlayerTeam(team) != null,
            position != null);
        if (problem != null) {
            block.report(message(problem), false);
            return;
        }
        BlockPos at = BlockPos.containing(position);
        if (!level.isInWorldBounds(at)
                || !level.getWorldBorder().isWithinBounds(at)) {
            block.report(Component.translatable(
                "amrac.ai_command.outside_world"), false);
            return;
        }
        Launch launch = new Launch(level, source, order, position,
            level.getServer().getTickCount());
        hold(launch);
        LAUNCHES.add(launch);
        block.report(Component.translatable("amrac.ai_command.loading",
            where(position)), true);
    }

    @Nullable
    public static Vec3 position(ServerLevel level, BlockPos source,
                                Direction facing, String text) {
        CommandSourceStack stack = new CommandSourceStack(CommandSource.NULL,
            Vec3.atBottomCenterOf(source), new Vec2(0.0F, facing.toYRot()), level,
            LevelBasedPermissionSet.GAMEMASTER, "AI Command Block",
            Component.translatable("block.amrac.ai_command_block"),
            level.getServer(), null);
        try {
            StringReader reader = new StringReader(text == null ? "" : text);
            var coordinates = Vec3Argument.vec3().parse(reader);
            reader.skipWhitespace();
            if (reader.canRead()) {
                return null;
            }
            return coordinates.getPosition(stack);
        } catch (CommandSyntaxException invalid) {
            return null;
        }
    }

    public static boolean parses(String text) {
        try {
            StringReader reader = new StringReader(text == null ? "" : text);
            Vec3Argument.vec3().parse(reader);
            reader.skipWhitespace();
            return !reader.canRead();
        } catch (CommandSyntaxException invalid) {
            return false;
        }
    }

    public static CountermeasureProfile standardLoad(PlaneEntity plane) {
        AircraftPhysicsProfile profile = FlightModelRegistry.instance()
            .profile(plane.flightModelId());
        return profile == null ? CountermeasureProfile.NONE
            : profile.countermeasures();
    }

    public static Component message(AiLaunchCheck.Problem problem) {
        return Component.translatable(problem.key(), problem.args().toArray());
    }

    private static String where(Vec3 position) {
        return String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f",
            position.x, position.y, position.z);
    }

    private static void tick(MinecraftServer server) {
        if (LAUNCHES.isEmpty()) {
            return;
        }
        Iterator<Launch> iterator = LAUNCHES.iterator();
        while (iterator.hasNext()) {
            Launch launch = iterator.next();
            long now = server.getTickCount();
            String released;
            try {
                released = step(launch, now);
            } catch (RuntimeException failure) {
                AmracMod.LOGGER.error("AI command block launch at {} failed",
                    launch.source, failure);
                report(launch, Component.translatable(
                    "amrac.ai_command.failed"), false);
                released = "failed";
            }
            if (released != null) {
                release(launch);
                iterator.remove();
                AmracMod.LOGGER.info("AI command block at {}: area around"
                    + " chunk {} released after {} ticks ({})",
                    launch.source.toShortString(), launch.centre,
                    now - launch.started, released);
            }
        }
    }

    @Nullable
    private static String step(Launch launch, long now) {
        if (launch.stage == Stage.LOADING) {
            if (!areaReady(launch)) {
                if (now - launch.started > LOAD_TIMEOUT_TICKS) {
                    report(launch, Component.translatable(
                        "amrac.ai_command.load_timeout"), false);
                    return "the area did not load";
                }
                return null;
            }
            return spawn(launch, now) ? null : "nothing was put down";
        }

        if (AircraftVirtualService.isVirtual(launch.aircraft)) {
            return "handed to the virtual layer";
        }
        PlaneEntity plane = AircraftRegistry.liveEntity(launch.aircraft);
        if (plane == null || !plane.isAlive() || plane.level() != launch.level) {
            return "the aircraft is gone";
        }
        boolean airborne = !plane.getOnGround() && !plane.isOnWater();
        if (airborne && plane.getY() > launch.position.y + CLIMBED) {
            return "airborne";
        }
        ChunkPos at = plane.chunkPosition();
        if (Math.abs(at.x() - launch.centre.x()) > AREA_RADIUS
                || Math.abs(at.z() - launch.centre.z()) > AREA_RADIUS) {
            return "left the area";
        }
        boolean crewed = false;
        for (Entity passenger : plane.getPassengers()) {
            crewed |= passenger instanceof AiPilotEntity;
        }
        if (!crewed) {
            return "no pilot aboard";
        }
        return now - launch.spawnedAt > HOLD_TIMEOUT_TICKS
            ? "held as long as it may be" : null;
    }

    private static boolean areaReady(Launch launch) {
        for (int dx = -AREA_RADIUS; dx <= AREA_RADIUS; dx++) {
            for (int dz = -AREA_RADIUS; dz <= AREA_RADIUS; dz++) {
                if (!launch.level.areEntitiesActuallyLoadedAndTicking(
                        new ChunkPos(launch.centre.x() + dx,
                            launch.centre.z() + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean spawn(Launch launch, long now) {
        ServerLevel level = launch.level;
        AiLaunchOrder order = launch.order;
        EntityType<? extends PlaneEntity> type = AircraftTypes.byName(order.aircraft());
        PlaneEntity plane = type == null ? null
            : type.create(level, EntitySpawnReason.TRIGGERED);
        if (plane == null) {
            report(launch, Component.translatable(
                "amrac.ai_command.unknown_aircraft", order.aircraft()), false);
            return false;
        }
        plane.setPos(launch.position.x, launch.position.y, launch.position.z);
        plane.setPlacedYaw(order.heading());
        if (!level.noCollision(plane, plane.getBoundingBox())) {
            plane.discard();
            report(launch, Component.translatable("amrac.ai_command.blocked",
                where(launch.position)), false);
            return false;
        }
        settle(level, plane);
        SummonLoadout.Parsed loadout;
        try {
            loadout = SummonLoadout.parse(order.loadout());
        } catch (SummonLoadout.Invalid invalid) {
            plane.discard();
            report(launch, Component.translatable("amrac.ai_command."
                + invalid.reason(), invalid.token()), false);
            return false;
        }
        plane.setFuelLitres(plane.getFuelCapacityLitres()
            * order.fuelPercent() / 100.0D);
        String[] slots = new String[plane.pylonCount()];
        for (int i = 0; i < loadout.stations().size() && i < slots.length; i++) {
            slots[i] = loadout.stations().get(i);
        }
        plane.setLoadout(slots);
        FlightModelRegistry.instance().refresh();
        CountermeasureProfile load = standardLoad(plane);
        plane.setCountermeasures(load.chaff(), load.flare());
        level.addFreshEntity(plane);

        AiPilotEntity pilot = AmracEntities.AI_PILOT.create(level,
            EntitySpawnReason.TRIGGERED);
        if (pilot == null) {
            report(launch, Component.translatable("amrac.ai_command.failed"),
                false);
            return false;
        }
        pilot.setPos(plane.getX(), plane.getY(), plane.getZ());
        pilot.setVariant(AiPilotVariant.of(order.rank()));
        pilot.setRank(order.rank());
        if (!order.team().isEmpty()) {
            pilot.setTeamName(order.team());
        }
        pilot.setPersistenceRequired();
        level.addFreshEntity(pilot);
        pilot.startRiding(plane, true, true);
        pilot.startMission();
        AiPilotService.rememberAboard(pilot, level, plane.getUUID());

        launch.aircraft = plane.getUUID();
        launch.spawnedAt = now;
        launch.stage = Stage.FLYING;
        AmracMod.LOGGER.info("AI command block at {}: {} {} ({}) put down at"
            + " {} after {} ticks", launch.source.toShortString(),
            order.aircraft(), plane.getUUID().toString().substring(0, 8),
            pilot.callsign(), where(plane.position()), now - launch.started);
        report(launch, Component.translatable("amrac.ai_command.launched",
            plane.getPickResult().isEmpty() ? plane.getDisplayName()
                : plane.getPickResult().getHoverName(),
            pilot.callsign(), where(plane.position())), true);
        return true;
    }

    private static final double SETTLE_DEPTH = 8.0D;

    private static void settle(ServerLevel level, PlaneEntity plane) {
        Vec3 fall = Entity.collideBoundingBox(plane,
            new Vec3(0.0D, -SETTLE_DEPTH, 0.0D), plane.getBoundingBox(),
            level, List.of());
        if (fall.y > -1.0E-4D || fall.y <= -SETTLE_DEPTH + 1.0E-4D) {
            return;
        }
        double landing = plane.getY() + fall.y;
        for (int y = (int) Math.floor(plane.getY());
                y >= (int) Math.floor(landing); y--) {
            if (!level.getFluidState(BlockPos.containing(plane.getX(), y,
                    plane.getZ())).isEmpty()) {
                return;
            }
        }
        plane.setPos(plane.getX(), landing, plane.getZ());
    }

    private static void report(Launch launch, Component message,
                               boolean success) {
        if (launch.level.isLoaded(launch.source)
                && launch.level.getBlockEntity(launch.source)
                    instanceof AiCommandBlockEntity block) {
            block.report(message, success);
        }
    }

    private static void hold(Launch launch) {
        Hold key = new Hold(launch.level.dimension(), launch.centre.pack());
        HOLDS.merge(key, 1, Integer::sum);
        launch.level.getChunkSource().addTicketWithRadius(TICKET, launch.centre,
            TICKET_RADIUS);
    }

    private static void release(Launch launch) {
        Hold key = new Hold(launch.level.dimension(), launch.centre.pack());
        Integer count = HOLDS.get(key);
        if (count == null) {
            return;
        }
        if (count > 1) {
            HOLDS.put(key, count - 1);
            return;
        }
        HOLDS.remove(key);
        launch.level.getChunkSource().removeTicketWithRadius(TICKET,
            launch.centre, TICKET_RADIUS);
    }
}
