package amrac.neoforge.client;

import amrac.AmracMod;
import amrac.client.AmracClient;
import amrac.platform.client.ClientPlatform;
import amrac.platform.client.ClientPlayConnectionEvents;
import amrac.platform.client.ClientPlayNetworking;
import amrac.platform.client.ClientTickEvents;
import amrac.platform.client.HudElement;
import amrac.platform.client.LevelExtractionContext;
import amrac.platform.client.LevelExtractionEvents;
import amrac.platform.client.LevelRenderContext;
import amrac.platform.client.LevelRenderEvents;
import amrac.platform.client.VanillaHudElements;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = AmracMod.MODID, dist = Dist.CLIENT)
public final class AmracNeoForgeClient {
    private static boolean initialised;

    private final NeoForgeClientBackend backend = new NeoForgeClientBackend();

    public AmracNeoForgeClient(IEventBus modBus) {
        ClientPlatform.install(backend);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            ensureInitialised();
            backend.keyMappings.forEach(event::register);
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> {
            ensureInitialised();
            backend.guiLayers.forEach(action -> action.accept(event));
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            ensureInitialised();
            backend.renderers.forEach(action -> action.accept(event));
        });
        modBus.addListener((RegisterMenuScreensEvent event) -> {
            ensureInitialised();
            backend.menuScreens.forEach(action -> action.accept(event));
        });
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            ensureInitialised();
            backend.receivers.forEach(action -> action.accept(event));
        });
        forwardGameEvents();
    }

    private static void ensureInitialised() {
        if (initialised) {
            return;
        }
        initialised = true;
        AmracClient.init();
    }

    private static void forwardGameEvents() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientTickEvent.Post event) -> ClientTickEvents
            .END_CLIENT_TICK.fire(listener ->
                listener.onEndTick(Minecraft.getInstance())));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
            ClientPlayConnectionEvents.DISCONNECT.fire(listener ->
                listener.onDisconnect(Minecraft.getInstance())));

        bus.addListener((ExtractLevelRenderStateEvent event) -> {
            LevelExtractionContext context = new LevelExtractionContext() {
                @Override
                public ClientLevel level() {
                    return event.getLevel();
                }

                @Override
                public Camera camera() {
                    return event.getCamera();
                }

                @Override
                public DeltaTracker deltaTracker() {
                    return event.getDeltaTracker();
                }
            };
            LevelExtractionEvents.END_EXTRACTION.fire(listener ->
                listener.endExtraction(context));
        });
        bus.addListener((SubmitCustomGeometryEvent event) -> {
            LevelRenderContext context = new LevelRenderContext() {
                @Override
                public SubmitNodeCollector submitNodeCollector() {
                    return event.getSubmitNodeCollector();
                }

                @Override
                public PoseStack poseStack() {
                    return event.getPoseStack();
                }
            };
            LevelRenderEvents.COLLECT_SUBMITS.fire(listener ->
                listener.collectSubmits(context));
        });
    }

    private static Identifier[] neoForgeIds(VanillaHudElements element) {
        return switch (element) {
            case ARMOR_BAR -> new Identifier[] {VanillaGuiLayers.ARMOR_LEVEL};
            case HEALTH_BAR -> new Identifier[] {VanillaGuiLayers.PLAYER_HEALTH};
            case FOOD_BAR -> new Identifier[] {VanillaGuiLayers.FOOD_LEVEL};
            case AIR_BAR -> new Identifier[] {VanillaGuiLayers.AIR_LEVEL};
            case EXPERIENCE_LEVEL ->
                new Identifier[] {VanillaGuiLayers.EXPERIENCE_LEVEL};
            case INFO_BAR -> new Identifier[] {
                VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND,
                VanillaGuiLayers.CONTEXTUAL_INFO_BAR,
            };
        };
    }

    private static final class NeoForgeClientBackend
        implements ClientPlatform.Backend {
        final List<KeyMapping> keyMappings = new ArrayList<>();
        final List<Consumer<RegisterGuiLayersEvent>> guiLayers = new ArrayList<>();
        final List<Consumer<EntityRenderersEvent.RegisterRenderers>> renderers =
            new ArrayList<>();
        final List<Consumer<RegisterMenuScreensEvent>> menuScreens =
            new ArrayList<>();
        final List<Consumer<RegisterClientPayloadHandlersEvent>> receivers =
            new ArrayList<>();

        @Override
        public void sendToServer(CustomPacketPayload payload) {
            ClientPacketDistributor.sendToServer(payload);
        }

        @Override
        public <T extends CustomPacketPayload> void registerClientReceiver(
            CustomPacketPayload.Type<T> type,
            ClientPlayNetworking.PlayPayloadHandler<T> handler) {
            receivers.add(event -> event.register(type, (payload, context) ->
                handler.receive(payload,
                    new ClientPlayNetworking.Context(Minecraft.getInstance()))));
        }

        @Override
        public void registerKeyMapping(KeyMapping mapping) {
            keyMappings.add(mapping);
        }

        @Override
        public void addHudElementLast(Identifier id, HudElement element) {
            guiLayers.add(event ->
                event.registerAboveAll(id, element::extractRenderState));
        }

        @Override
        public void replaceHudElement(VanillaHudElements element,
                                      UnaryOperator<HudElement> replacer) {
            for (Identifier id : neoForgeIds(element)) {
                guiLayers.add(event -> event.wrapLayer(id, original -> {
                    HudElement wrapped = replacer.apply(original::render);
                    return (GuiLayer) wrapped::extractRenderState;
                }));
            }
        }

        @Override
        public <T extends Entity> void registerEntityRenderer(
            EntityType<? extends T> type, EntityRendererProvider<T> provider) {
            renderers.add(event -> event.registerEntityRenderer(type, provider));
        }

        @Override
        public <T extends BlockEntity, S extends BlockEntityRenderState>
        void registerBlockEntityRenderer(
            BlockEntityType<? extends T> type,
            BlockEntityRendererProvider<T, S> provider) {
            renderers.add(event ->
                event.registerBlockEntityRenderer(type, provider));
        }

        @Override
        public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>>
        void registerMenuScreen(MenuType<? extends M> type,
                                MenuScreens.ScreenConstructor<M, U> constructor) {
            menuScreens.add(event -> event.register(type, constructor));
        }
    }
}
