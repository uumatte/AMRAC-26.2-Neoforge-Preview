package amrac;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import amrac.platform.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import amrac.entities.PlaneEntity;
import amrac.entities.ai.AiLaunchPolicy;
import amrac.entities.ai.AiPilotEntity;
import amrac.entities.ai.AiPilotIdleReason;
import amrac.entities.ai.AiPilotRoster;
import amrac.entities.ai.AiPilotService;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Map;

public final class AmracCommands {
    private AmracCommands() {
    }

    private static final Map<String, EntityType<? extends PlaneEntity>> AIRCRAFT =
        amrac.entities.AircraftTypes.byName();

    private static final SuggestionProvider<CommandSourceStack> AIRCRAFT_NAMES =
        (context, builder) ->
            SharedSuggestionProvider.suggest(AIRCRAFT.keySet(), builder);

    private static final SuggestionProvider<CommandSourceStack> LOADOUT_STORES =
        (context, builder) -> {
            String typed = builder.getRemaining();
            int cut = Math.max(typed.lastIndexOf(' '), typed.lastIndexOf(',')) + 1;
            String word = typed.substring(cut);
            if (word.contains("*")) {
                return builder.buildFuture();
            }
            PlaneEntity plane = null;
            EntityType<? extends PlaneEntity> type = AIRCRAFT.get(
                StringArgumentType.getString(context, "aircraft")
                    .toLowerCase(java.util.Locale.ROOT));
            if (type != null) {
                plane = type.create(context.getSource().getLevel(),
                    net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            }
            var offset = builder.createOffset(builder.getStart() + cut);
            String prefix = amrac.weapons.SummonLoadout.canonical(word);
            for (String store : amrac.weapons.SummonLoadout.storeNames()) {
                boolean missile = !store.equals(amrac.weapons.SummonLoadout.CHAFF)
                    && !store.equals(amrac.weapons.SummonLoadout.FLARE)
                    && !store.equals(amrac.weapons.SummonLoadout.EMPTY_STATION);
                if (plane != null && missile
                        && (!plane.hasMissiles() || !plane.canCarry(store))) {
                    continue;
                }
                if (plane != null && !missile
                        && !store.equals(amrac.weapons.SummonLoadout.EMPTY_STATION)
                        && plane.getCountermeasureCapacity() <= 0) {
                    continue;
                }
                if (prefix == null || store.startsWith(prefix)) {
                    offset.suggest(store);
                }
            }
            if (plane != null) {
                plane.discard();
            }
            return offset.buildFuture();
        };

    private static final SuggestionProvider<CommandSourceStack> TEAM_NAMES =
        (context, builder) -> SharedSuggestionProvider.suggest(
            context.getSource().getServer().getScoreboard().getTeamNames(), builder);

    private static final net.minecraft.server.permissions.PermissionCheck
        PERMISSION = Commands.LEVEL_GAMEMASTERS;

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registry, environment) -> dispatcher.register(root()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal(AmracMod.MODID)
            .then(Commands.literal("team")
                .then(Commands.literal("join")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(TEAM_NAMES)
                        .executes(AmracCommands::joinTeam)))
                .then(Commands.literal("leave")
                    .executes(AmracCommands::leaveTeam))
                .then(Commands.literal("create")
                    .requires(Commands.hasPermission(PERMISSION))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(AmracCommands::createTeam))))
            .then(Commands.literal("ai")
                .requires(Commands.hasPermission(PERMISSION))
                .then(Commands.literal("start")
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .then(Commands.argument("radius",
                                DoubleArgumentType.doubleArg(0.0D))
                            .executes(AmracCommands::startPilots))))
                .then(Commands.literal("list")
                    .executes(AmracCommands::listAircraft))
                .then(Commands.literal("debug")
                    .executes(AmracCommands::debugAi))
                .then(Commands.literal("record")
                    .executes(AmracCommands::recordAi)))
            .then(Commands.literal("summon")
                .requires(Commands.hasPermission(PERMISSION))
                .then(Commands.argument("aircraft", StringArgumentType.word())
                    .suggests(AIRCRAFT_NAMES)
                    .executes(context -> summon(context, null, -1.0D, null))
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(context -> summon(context,
                            Vec3Argument.getVec3(context, "pos"), -1.0D, null))
                        .then(Commands.argument("fuel",
                                DoubleArgumentType.doubleArg(0.0D, 100.0D))
                            .executes(context -> summon(context,
                                Vec3Argument.getVec3(context, "pos"),
                                DoubleArgumentType.getDouble(context, "fuel"), null))
                            .then(Commands.argument("loadout",
                                    StringArgumentType.greedyString())
                                .suggests(LOADOUT_STORES)
                                .executes(context -> summon(context,
                                    Vec3Argument.getVec3(context, "pos"),
                                    DoubleArgumentType.getDouble(context, "fuel"),
                                    StringArgumentType.getString(context,
                                        "loadout"))))))))
            .then(Commands.literal("trace")
                .requires(Commands.hasPermission(PERMISSION))
                .then(Commands.literal("start")
                    .executes(context -> startTrace(context, null, -1))
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> startTrace(context,
                            StringArgumentType.getString(context, "name"), -1))
                        .then(Commands.argument("seconds",
                                IntegerArgumentType.integer(1, 3600))
                            .executes(context -> startTrace(context,
                                StringArgumentType.getString(context, "name"),
                                IntegerArgumentType.getInteger(context,
                                    "seconds"))))))
                .then(Commands.literal("stop")
                    .executes(AmracCommands::stopTrace))
                .then(Commands.literal("status")
                    .executes(AmracCommands::traceStatus)))
            .then(Commands.literal("clear")
                .requires(Commands.hasPermission(PERMISSION))
                .executes(context -> clear(context, -1.0D))
                .then(Commands.argument("radius",
                        com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0D))
                    .executes(context -> clear(context,
                        com.mojang.brigadier.arguments.DoubleArgumentType
                            .getDouble(context, "radius")))))
            .then(Commands.literal("purge")
                .requires(Commands.hasPermission(PERMISSION))
                .executes(context -> purge(context, false))
                .then(Commands.literal("sweep")
                    .executes(context -> purge(context, true)))
                .then(Commands.literal("cancel")
                    .executes(AmracCommands::cancelPurge)));
    }

    private static int createTeam(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        var scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getPlayerTeam(name) != null) {
            source.sendFailure(Component.translatable("amrac.message.team_exists", name));
            return 0;
        }
        scoreboard.addPlayerTeam(name);
        source.sendSuccess(() -> Component.translatable("amrac.message.team_created", name), true);
        return 1;
    }

    private static int joinTeam(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("amrac.message.team_join_player_only"));
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        var scoreboard = source.getServer().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            source.sendFailure(Component.translatable("amrac.message.team_unknown", name));
            return 0;
        }
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        source.sendSuccess(() -> Component.translatable("amrac.message.team_joined", name), false);
        return 1;
    }

    private static int leaveTeam(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("amrac.message.team_leave_player_only"));
            return 0;
        }
        boolean removed = source.getServer().getScoreboard()
            .removePlayerFromTeam(player.getScoreboardName());
        if (!removed) {
            source.sendFailure(Component.translatable("amrac.message.team_none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("amrac.message.team_left"), false);
        return 1;
    }

    private static int startPilots(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 centre = Vec3Argument.getVec3(context, "pos");
        double radius = DoubleArgumentType.getDouble(context, "radius");
        double radiusSquared = radius * radius;

        int pruned = amrac.entities.ai.AiPilotService
            .pruneDeadPilots(level);

        AiPilotRoster roster = AiPilotRoster.of(level);
        Set<UUID> handled = new LinkedHashSet<>();
        Map<AiPilotIdleReason, Integer> refused = new LinkedHashMap<>();
        int started = 0;
        int airborne = 0;

        for (AiPilotRoster.Entry entry : roster.pilotsNear(centre, radius)) {
            handled.add(entry.pilotId());
            if (level.getEntity(entry.pilotId()) instanceof AiPilotEntity body) {
                body.startMission();
                ++started;
                continue;
            }
            AiLaunchPolicy.Choice choice = AiPilotService.launch(level, entry);
            if (choice.launched()) {
                ++started;
            } else if (choice.refusal() == AiPilotIdleReason.NONE) {
                ++airborne;
            } else {
                refused.merge(choice.refusal(), 1, Integer::sum);
            }
        }

        AABB bounds = new AABB(centre, centre).inflate(radius);
        for (AiPilotEntity pilot : level.getEntities(
                EntityTypeTest.forClass(AiPilotEntity.class), bounds,
                candidate -> candidate.position().distanceToSqr(centre)
                    <= radiusSquared)) {
            if (handled.add(pilot.getUUID())) {
                pilot.startMission();
                ++started;
            }
        }

        int launched = started;
        StringBuilder message = new StringBuilder("Started mission for ")
            .append(launched).append(" AI pilot(s)");
        if (airborne > 0) {
            message.append("; ").append(airborne).append(" already airborne");
        }
        if (pruned > 0) {
            message.append("; forgot ").append(pruned)
                .append(" pilot(s) that no longer exist");
        }
        for (Map.Entry<AiPilotIdleReason, Integer> reason : refused.entrySet()) {
            message.append("; ").append(reason.getValue()).append(" idle: ")
                .append(reason.getKey().label());
        }
        String text = message.toString();
        source.sendSuccess(() -> Component.literal(text), true);
        return launched;
    }

    private static int recordAi(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long now = source.getLevel().getGameTime();
        if (amrac.entities.ai.AiFlightRecorder.recording()) {
            java.util.List<String> log = amrac.entities.ai
                .AiFlightRecorder.stop();
            for (String line : log) {
                amrac.AmracMod.LOGGER.info("[AI] {}",
                    line);
            }
            for (String line : amrac.entities.ai
                    .AiDebugReport.lines(source.getLevel(),
                        source.getPlayer())) {
                amrac.AmracMod.LOGGER.info("[AI] {}",
                    line);
            }
            source.sendSuccess(() -> Component.translatable("amrac.message.ai_recording_stopped", log.size()), false);
            return log.size();
        }
        amrac.entities.ai.AiFlightRecorder.start(now);
        source.sendSuccess(() -> Component.translatable("amrac.message.ai_recording_started"),
            false);
        return 1;
    }

    private static int debugAi(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        java.util.List<String> report = amrac.entities.ai
            .AiDebugReport.lines(source.getLevel(), source.getPlayer());
        for (String line : report) {
            amrac.AmracMod.LOGGER.info("[AI] {}", line);
        }
        source.sendSuccess(() -> Component.translatable("amrac.message.ai_report_written", report.size()), false);
        return report.size();
    }

    private static int listAircraft(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Vec3 from = source.getPosition();
        List<amrac.entities.AircraftRegistry.Record> records =
            new ArrayList<>(amrac.entities.AircraftRegistry
                .all(source.getLevel()));
        records.sort(java.util.Comparator.comparingDouble(
            record -> record.position.distanceToSqr(from)));
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                "No aircraft on this level"), false);
            return 0;
        }
        for (var record : records) {
            var pilot = amrac.entities.ai.AiPilotService
                .flying(record.id);
            String line = String.format(java.util.Locale.ROOT,
                "%s  %s  xz %.0f %.0f  alt %.0f  %.0f b/s  range %.0f  %s%s",
                record.id.toString().substring(0, 8),
                record.presence,
                record.position.x, record.position.z, record.position.y,
                record.velocity.length() * 20.0D,
                Math.sqrt(record.position.distanceToSqr(from)),
                pilot == null ? (record.crewed ? "crewed" : "empty")
                    : pilot.rank().label(),
                record.team.isBlank() ? "" : " [" + record.team + "]");
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return records.size();
    }

    private static int summon(CommandContext<CommandSourceStack> context,
                              Vec3 requestedPosition, double fuelPercent,
                              String loadoutText) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "aircraft")
            .toLowerCase(java.util.Locale.ROOT);
        EntityType<? extends PlaneEntity> type = AIRCRAFT.get(name);
        if (type == null) {
            source.sendFailure(Component.translatable(
                "commands.amrac.summon.unknown", name,
                String.join(", ", AIRCRAFT.keySet())));
            return 0;
        }

        ServerLevel level = source.getLevel();
        PlaneEntity plane = type.create(level,
            net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (plane == null) {
            source.sendFailure(Component.translatable(
                "commands.amrac.summon.failed", name));
            return 0;
        }

        amrac.weapons.SummonLoadout.Parsed loadout = null;
        if (loadoutText != null) {
            try {
                loadout = amrac.weapons.SummonLoadout.parse(loadoutText);
            } catch (amrac.weapons.SummonLoadout.Invalid invalid) {
                source.sendFailure(Component.translatable(
                    "commands.amrac.summon." + invalid.reason(), invalid.token(),
                    String.join(", ", amrac.weapons.SummonLoadout.storeNames())));
                plane.discard();
                return 0;
            }
            Component refused = loadoutRefusal(plane, name, loadout);
            if (refused != null) {
                source.sendFailure(refused);
                plane.discard();
                return 0;
            }
        }

        Vec3 position = requestedPosition != null
            ? requestedPosition : source.getPosition();
        plane.setPos(position.x, position.y, position.z);
        if (fuelPercent >= 0.0D) {
            plane.setFuelLitres(plane.getFuelCapacityLitres() * fuelPercent / 100.0D);
        }
        if (loadout != null) {
            String[] slots = new String[plane.pylonCount()];
            for (int i = 0; i < loadout.stations().size(); i++) {
                slots[i] = loadout.stations().get(i);
            }
            plane.setLoadout(slots);
            plane.setCountermeasures(loadout.chaff(), loadout.flare());
        }
        plane.setPlacedYaw(source.getRotation().y);
        level.addFreshEntity(plane);

        String where = String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f",
            position.x, position.y, position.z);
        if (fuelPercent < 0.0D && loadout == null) {
            source.sendSuccess(() -> Component.translatable(
                "commands.amrac.summon.success", name, where), true);
        } else {
            String fuel = plane.isFuelMetered()
                ? String.format(java.util.Locale.ROOT, "%.0f / %.0f L",
                    plane.getFuelLitres(), plane.getFuelCapacityLitres())
                : "-";
            String stores = String.join(", ", java.util.Arrays.stream(
                    plane.getLoadout())
                .map(id -> id == null ? "-" : id).toList());
            source.sendSuccess(() -> Component.translatable(
                "commands.amrac.summon.success_fitted", name, where, fuel,
                stores, plane.getChaffCount(), plane.getFlareCount()), true);
        }
        return 1;
    }

    private static Component loadoutRefusal(PlaneEntity plane, String name,
                                         amrac.weapons.SummonLoadout.Parsed loadout) {
        amrac.weapons.LoadoutFit.Refusal refusal = amrac.weapons.LoadoutFit
            .check(loadout, plane.hasMissiles(), plane.pylonCount(),
                plane::canCarry, plane.getCountermeasureCapacity());
        if (refusal == null) {
            return null;
        }
        return switch (refusal.reason()) {
            case NO_MISSILES -> Component.translatable(
                "commands.amrac.summon.no_missiles", name);
            case TOO_MANY -> Component.translatable(
                "commands.amrac.summon.too_many", name, refusal.capacity(),
                refusal.given());
            case NOT_CARRIED -> Component.translatable(
                "commands.amrac.summon.not_carried", name, refusal.store());
            case COUNTERMEASURES_FULL -> Component.translatable(
                "commands.amrac.summon.countermeasures_full", name,
                refusal.capacity(), refusal.given());
        };
    }

    private static int clear(CommandContext<CommandSourceStack> context,
                             double radius) {
        CommandSourceStack source = context.getSource();
        Wiped wiped = wipe(source, radius);
        source.sendSuccess(() -> Component.translatable(
            "commands.amrac.clear.success", wiped.aircraft(), wiped.pilots(),
            wiped.queued()), true);
        return wiped.aircraft() + wiped.pilots() + wiped.queued();
    }

    private static int purge(CommandContext<CommandSourceStack> context,
                             boolean sweep) {
        CommandSourceStack source = context.getSource();
        Wiped wiped = wipe(source, -1.0D);
        source.sendSuccess(() -> Component.translatable(
            "commands.amrac.purge.success", wiped.aircraft() + wiped.pilots(),
            wiped.queued(), wiped.pilots()), true);
        if (sweep) {
            amrac.entities.AircraftPurgeService.armSweep();
            source.sendSuccess(() -> Component.translatable(
                "commands.amrac.purge.sweep_armed"), true);
        }
        return wiped.aircraft() + wiped.pilots() + wiped.queued();
    }

    private record Wiped(int aircraft, int pilots, int queued) {
    }

    /**
     * Removing aircraft and pilots must cover four places: loaded entities, the virtual layer,
     * unloaded chunks (the AircraftPurgeService pending set) and the AI roster (strikeOff), plus
     * AiPilotService.forget for the brain. Miss one and they come back, or a brain is orphaned.
     */
    private static Wiped wipe(CommandSourceStack source, double radius) {
        net.minecraft.server.MinecraftServer server = source.getServer();
        ServerLevel here = source.getLevel();
        Vec3 centre = source.getPosition();
        boolean everywhere = radius < 0.0D;
        double radiusSquared = radius * radius;
        java.util.function.BiPredicate<
                net.minecraft.resources.ResourceKey<Level>, Vec3> inScope =
            (dimension, position) -> everywhere
                || (dimension.equals(here.dimension())
                    && position.distanceToSqr(centre) <= radiusSquared);

        List<amrac.entities.AircraftRegistry.Record> remembered =
            new ArrayList<>();
        for (var record : amrac.entities.AircraftRegistry.everything()) {
            if (!record.simulated()
                    && inScope.test(record.dimension, record.position)) {
                remembered.add(record);
            }
        }

        Set<UUID> taken = new java.util.HashSet<>();
        Set<UUID> queued = new LinkedHashSet<>();
        int aircraft = 0;
        int pilots = 0;

        List<Entity> loaded = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (!everywhere && level != here) {
                continue;
            }
            loaded.addAll(level.getEntities(
                EntityTypeTest.forClass(PlaneEntity.class),
                plane -> inScope.test(level.dimension(), plane.position())));
            loaded.addAll(level.getEntities(
                EntityTypeTest.forClass(AiPilotEntity.class),
                pilot -> inScope.test(level.dimension(), pilot.position())));
        }
        for (Entity entity : loaded) {
            if (entity.isRemoved()) {
                continue;
            }
            if (entity instanceof PlaneEntity plane) {
                for (Entity rider : plane.getPassengers()) {
                    if (rider instanceof AiPilotEntity) {
                        taken.add(rider.getUUID());
                        ++pilots;
                    }
                }
                ++aircraft;
            } else {
                ++pilots;
            }
            taken.add(entity.getUUID());
            amrac.entities.AircraftPurgeService.remove(entity);
        }

        Set<UUID> virtualPilots = new java.util.HashSet<>();
        for (amrac.entities.VirtualAircraftState state
                : amrac.entities.AircraftVirtualService.all()) {
            if (inScope.test(state.dimension, state.position)) {
                taken.add(state.id);
                if (state.pilotId != null) {
                    virtualPilots.add(state.pilotId);
                    taken.add(state.pilotId);
                }
            }
        }
        aircraft += amrac.entities.AircraftVirtualService.discard(
            everywhere ? null : here, centre, radius);
        pilots += virtualPilots.size();

        for (var record : remembered) {
            if (taken.add(record.id)) {
                doomAndFetch(server, record.dimension, record.position,
                    record.id, queued);
            }
            amrac.entities.AircraftRegistry.forget(record.id);
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (!everywhere && level != here) {
                continue;
            }
            AiPilotRoster roster = AiPilotRoster.of(level);
            for (AiPilotRoster.Entry entry : roster.pilots()) {
                UUID id = entry.pilotId();
                if (!virtualPilots.contains(id)
                        && !inScope.test(level.dimension(), entry.position())) {
                    continue;
                }
                if (taken.add(id)) {
                    doomAndFetch(server, level.dimension(), entry.position(),
                        id, queued);
                }
                roster.forgetPilot(id);
                AiPilotService.forget(id);
            }
            for (AiPilotRoster.Parked parked : roster.parked()) {
                if (!inScope.test(level.dimension(), parked.position())) {
                    continue;
                }
                if (taken.add(parked.aircraftId())) {
                    doomAndFetch(server, level.dimension(), parked.position(),
                        parked.aircraftId(), queued);
                }
                roster.unpark(parked.aircraftId());
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (everywhere || level == here) {
                level.getDataStorage().scheduleSave();
            }
        }

        if (everywhere) {
            for (UUID id : AiPilotService.knownPilots()) {
                if (taken.add(id)) {
                    amrac.entities.AircraftPurgeService.doom(id);
                    queued.add(id);
                }
            }
            AiPilotService.forgetAll();
            amrac.entities.AircraftRegistry.forgetEverything();
        }
        return new Wiped(aircraft, pilots, queued.size());
    }

    private static void doomAndFetch(net.minecraft.server.MinecraftServer server,
                                     net.minecraft.resources.ResourceKey<Level> dimension,
                                     Vec3 position, UUID id, Set<UUID> queued) {
        amrac.entities.AircraftPurgeService.doom(id);
        queued.add(id);
        ServerLevel level = server.getLevel(dimension);
        if (level != null) {
            level.getChunk(
                net.minecraft.core.SectionPos.blockToSectionCoord(position.x),
                net.minecraft.core.SectionPos.blockToSectionCoord(position.z));
        }
    }

    private static int cancelPurge(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean wasSweeping = amrac.entities
            .AircraftPurgeService.isSweeping();
        amrac.entities.AircraftPurgeService.disarmSweep();
        int pending = amrac.entities.AircraftPurgeService
            .pending();
        amrac.entities.AircraftPurgeService.cancel();
        source.sendSuccess(() -> Component.translatable(
            "commands.amrac.purge.cancelled",
            wasSweeping ? "on" : "off", pending), true);
        return 1;
    }

    private static int startTrace(CommandContext<CommandSourceStack> context,
                                  String name, int seconds) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String stem = name == null || name.isBlank()
            ? "trace-" + level.getGameTime() : name;
        if (!stem.matches("[A-Za-z0-9._-]{1,64}")) {
            source.sendFailure(Component.translatable(
                "commands.amrac.trace.bad_name", stem));
            return 0;
        }
        int limit = seconds > 0 ? seconds * 20
            : amrac.trace.BvrTraceRecorder.DEFAULT_LIMIT_TICKS;
        var running = amrac.trace.BvrTraceRecorder.status();
        try {
            java.nio.file.Path file =
                amrac.trace.BvrTraceRecorder.start(level, stem, limit);
            if (running != null) {
                source.sendSuccess(() -> Component.translatable(
                    "commands.amrac.trace.replaced", running.name(),
                    running.frames()), true);
            }
            source.sendSuccess(() -> Component.translatable(
                "commands.amrac.trace.started", stem,
                String.format(java.util.Locale.ROOT, "%.0f", limit / 20.0D),
                file.toString()), true);
            return 1;
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable(
                "commands.amrac.trace.failed", e.getMessage()));
            return 0;
        }
    }

    private static int stopTrace(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var done = amrac.trace.BvrTraceRecorder.stop();
        if (done == null) {
            source.sendFailure(Component.translatable(
                "commands.amrac.trace.not_recording"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
            "commands.amrac.trace.stopped", done.name(), done.frames(),
            String.format(java.util.Locale.ROOT, "%.1f", done.frames() / 20.0D),
            String.format(java.util.Locale.ROOT, "%.2f",
                done.bytes() / 1048576.0D),
            done.file().toString()), true);
        return done.frames();
    }

    private static int traceStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var running = amrac.trace.BvrTraceRecorder.status();
        if (running == null) {
            source.sendSuccess(() -> Component.translatable(
                "commands.amrac.trace.idle"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
            "commands.amrac.trace.running", running.name(), running.frames(),
            running.limitTicks(), running.aircraft(), running.missiles(),
            String.format(java.util.Locale.ROOT, "%.2f",
                running.bytes() / 1048576.0D)), false);
        return running.frames();
    }
}
