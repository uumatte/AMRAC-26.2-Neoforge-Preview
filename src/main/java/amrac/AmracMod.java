package amrac;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Register only inside init(), straight into the vanilla registries: AmracNeoForge runs it from the
 * first RegisterEvent, while they are still open. Order is fixed: blocks, block entities, items
 * (block entity types and BlockItems reference blocks).
 */
public final class AmracMod {
    public static final String MODID = "amrac";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static void sendOverlay(Player player, Component message,
                                   boolean overlay) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, overlay);
        }
    }

    public static final String FLIGHT_MODEL_DIRECTORY = MODID + "/flightmodel";

    private static void loadFlightModels() {
        amrac.physics.aircraft.FlightModelRegistry
            .setErrorHandler((message, error) -> LOGGER.error(message, error));
        amrac.physics.aircraft.FlightModelRegistry.instance()
            .configure(amrac.platform.Platform.configDir()
                .resolve(FLIGHT_MODEL_DIRECTORY));
        amrac.entities.ai.AiPilotSettingsFiles.register(
            amrac.platform.Platform.configDir().resolve(FLIGHT_MODEL_DIRECTORY));
    }

    private AmracMod() {
    }

    public static void init() {
        AmracConfig.load();
        loadFlightModels();
        int flightModelDocuments = amrac.physics.aircraft
            .FlightModelRegistry.instance().documentsForSync().size();
        LOGGER.info("Flight-model document sync ready: {} document(s)",
            flightModelDocuments);
        AmracDataSerializers.register();
        AmracEntities.register();
        AmracBlocks.register();
        AmracBlockEntities.register();
        AmracMenus.register();
        amrac.entities.AircraftRegistry.register();
        amrac.platform.Platform.registerAttributes(AmracEntities.AI_PILOT,
            amrac.entities.ai.AiPilotEntity::createAttributes);
        AmracItems.register();
        AmracSounds.register();
        AmracCommands.register();
        amrac.network.PlaneNetworking.registerServerReceivers();
        amrac.platform.ServerPlayConnectionEvents.JOIN
            .register((player, server) ->
                amrac.network.PlaneNetworking.sendFlightModel(player));
        amrac.platform.ServerPlayConnectionEvents.DISCONNECT
            .register((player, server) -> {
                java.util.UUID id = player.getUUID();
                amrac.gps.GpsService.forget(id);
                amrac.runway.RunwayBuilder.forget(id);
                amrac.hangar.HangarBuilder.forget(id);
                amrac.structure.StructurePreview.forget(id);
            });
        amrac.platform.ServerLifecycleEvents.SERVER_STARTED
            .register(amrac.gps.PinService::load);
        amrac.platform.ServerLifecycleEvents.SERVER_STOPPING
            .register(stopping -> amrac.gps.PinService.save());
        amrac.platform.ServerLifecycleEvents.SERVER_STARTED
            .register(started -> amrac.physics.aircraft
                .FlightModelRegistry.instance().setChangeListener(() ->
                    started.execute(() -> amrac.network
                        .PlaneNetworking.broadcastFlightModel(started))));
        amrac.platform.ServerLifecycleEvents.SERVER_STOPPING
            .register(stopping -> amrac.physics.aircraft
                .FlightModelRegistry.instance().setChangeListener(null));
        amrac.platform.ServerTickEvents.END_SERVER_TICK
            .register(server -> amrac.physics.aircraft
                .FlightModelRegistry.instance().refresh());

        amrac.entities.PlaneRadarService.register();
        amrac.weapons.VirtualMissileService.register();
        amrac.weapons.MissileWarningService.register();
        amrac.weapons.MissileTrackService.register();
        amrac.weapons.CountermeasureService.register();
        amrac.weapons.MissileSeekerService.register();
        amrac.trace.BvrTraceRecorder.register();
        amrac.entities.ai.AiPilotService.register();
        amrac.entities.ai.AiCommandLaunchService.register();
        amrac.entities.ai.WorldBoundaryWarningService.register();
        amrac.entities.AircraftVirtualService.register();
        amrac.entities.AircraftTrackService.register();
        amrac.entities.AircraftUpkeepService.register();
        amrac.entities.AircraftPurgeService.register();
        amrac.runway.RunwayBuilder.register();
        amrac.hangar.HangarBuilder.register();
        amrac.structure.StructurePreview.register();
        LOGGER.info("AMRAC initialised");
    }
}
