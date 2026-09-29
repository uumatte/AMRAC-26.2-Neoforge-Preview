package amrac.neoforge;

import amrac.AmracMod;
import amrac.platform.CommandRegistrationCallback;
import amrac.platform.Platform;
import amrac.platform.ServerEntityEvents;
import amrac.platform.ServerLifecycleEvents;
import amrac.platform.ServerPlayConnectionEvents;
import amrac.platform.ServerPlayNetworking;
import amrac.platform.ServerTickEvents;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(AmracMod.MODID)
public final class AmracNeoForge {
    private static final String PROTOCOL_VERSION = "1";

    private final NeoForgeBackend backend = new NeoForgeBackend();
    private boolean initialised;

    public AmracNeoForge(IEventBus modBus) {
        Platform.install(backend);
        modBus.addListener(this::onRegister);
        modBus.addListener(backend::onAttributes);
        modBus.addListener(backend::onPayloads);
        forwardGameEvents();
    }

    private void onRegister(RegisterEvent event) {
        if (initialised || !event.getRegistryKey().equals(Registries.BLOCK)) {
            return;
        }
        initialised = true;
        AmracMod.init();
    }

    private static void forwardGameEvents() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post event) -> ServerTickEvents
            .END_SERVER_TICK.fire(listener -> listener.onEndTick(event.getServer())));

        bus.addListener((EntityJoinLevelEvent event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            ServerEntityEvents.ENTITY_LOAD.fire(listener ->
                listener.onLoad(event.getEntity(), level));

            // A load listener may remove the entity (a purged aircraft); cancel so it never joins.
            if (event.getEntity().isRemoved()) {
                event.setCanceled(true);
            }
        });
        bus.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                ServerEntityEvents.ENTITY_UNLOAD.fire(listener ->
                    listener.onUnload(event.getEntity(), level));
            }
        });

        bus.addListener((ServerStartingEvent event) -> ServerLifecycleEvents
            .SERVER_STARTING.fire(listener -> listener.onServer(event.getServer())));
        bus.addListener((ServerStartedEvent event) -> ServerLifecycleEvents
            .SERVER_STARTED.fire(listener -> listener.onServer(event.getServer())));
        bus.addListener((ServerStoppingEvent event) -> ServerLifecycleEvents
            .SERVER_STOPPING.fire(listener -> listener.onServer(event.getServer())));
        bus.addListener((ServerStoppedEvent event) -> ServerLifecycleEvents
            .SERVER_STOPPED.fire(listener -> listener.onServer(event.getServer())));

        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerPlayConnectionEvents.JOIN.fire(listener ->
                    listener.onPlayer(player, player.level().getServer()));
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerPlayConnectionEvents.DISCONNECT.fire(listener ->
                    listener.onPlayer(player, player.level().getServer()));
            }
        });

        bus.addListener((RegisterCommandsEvent event) -> CommandRegistrationCallback
            .EVENT.fire(listener -> listener.register(event.getDispatcher(),
                event.getBuildContext(), event.getCommandSelection())));
    }

    private static final class NeoForgeBackend implements Platform.Backend {
        private final List<Consumer<EntityAttributeCreationEvent>> attributes =
            new ArrayList<>();
        private final List<Consumer<PayloadRegistrar>> payloads = new ArrayList<>();
        private final java.util.Map<CustomPacketPayload.Type<?>,
            ServerPlayNetworking.PlayPayloadHandler<?>> receivers =
            new java.util.HashMap<>();

        @Override
        public Path configDir() {
            return FMLPaths.CONFIGDIR.get();
        }

        @Override
        public boolean isModLoaded(String modId) {
            return ModList.get().isLoaded(modId);
        }

        @Override
        public Object sharedObject(String key) {
            return null;
        }

        @Override
        public CreativeModeTab.Builder creativeTabBuilder() {
            return CreativeModeTab.builder();
        }

        @Override
        public void registerAttributes(EntityType<? extends LivingEntity> type,
                                       Supplier<AttributeSupplier.Builder> builder) {
            attributes.add(event -> event.put(type, builder.get().build()));
        }

        void onAttributes(EntityAttributeCreationEvent event) {
            attributes.forEach(action -> action.accept(event));
        }

        @Override
        public <T extends CustomPacketPayload> void registerServerboundPayload(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            payloads.add(registrar -> registrar.playToServer(type, codec,
                (payload, context) -> receive(type, payload,
                    (ServerPlayer) context.player())));
        }

        @Override
        public <T extends CustomPacketPayload> void registerClientboundPayload(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            payloads.add(registrar -> registrar.playToClient(type, codec));
        }

        @Override
        public <T extends CustomPacketPayload> void registerServerReceiver(
            CustomPacketPayload.Type<T> type,
            ServerPlayNetworking.PlayPayloadHandler<T> handler) {
            receivers.put(type, handler);
        }

        @SuppressWarnings("unchecked")
        private <T extends CustomPacketPayload> void receive(
            CustomPacketPayload.Type<T> type, T payload, ServerPlayer player) {
            var handler = (ServerPlayNetworking.PlayPayloadHandler<T>)
                receivers.get(type);
            if (handler == null) {
                AmracMod.LOGGER.warn("No server handler for payload {}",
                    type.id());
                return;
            }
            handler.receive(payload, new ServerPlayNetworking.Context(player,
                player.level().getServer()));
        }

        void onPayloads(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
            payloads.forEach(action -> action.accept(registrar));
        }

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
