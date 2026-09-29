package amrac.platform;

import java.nio.file.Path;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import org.jetbrains.annotations.Nullable;

public final class Platform {
    private static Backend backend;

    private Platform() {
    }

    public static void install(Backend loaderBackend) {
        if (backend != null) {
            throw new IllegalStateException("Platform backend already installed");
        }
        backend = loaderBackend;
    }

    static Backend backend() {
        if (backend == null) {
            throw new IllegalStateException(
                "No loader has installed the AMRAC platform backend");
        }
        return backend;
    }

    public static Path configDir() {
        return backend().configDir();
    }

    public static boolean isModLoaded(String modId) {
        return backend().isModLoaded(modId);
    }

    public static @Nullable Object sharedObject(String key) {
        return backend().sharedObject(key);
    }

    public static CreativeModeTab.Builder creativeTabBuilder() {
        return backend().creativeTabBuilder();
    }

    public static void registerAttributes(
        EntityType<? extends LivingEntity> type,
        Supplier<AttributeSupplier.Builder> attributes) {
        backend().registerAttributes(type, attributes);
    }

    public interface Backend {
        Path configDir();

        boolean isModLoaded(String modId);

        @Nullable Object sharedObject(String key);

        CreativeModeTab.Builder creativeTabBuilder();

        void registerAttributes(EntityType<? extends LivingEntity> type,
                                Supplier<AttributeSupplier.Builder> attributes);

        <T extends CustomPacketPayload> void registerServerboundPayload(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

        <T extends CustomPacketPayload> void registerClientboundPayload(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

        <T extends CustomPacketPayload> void registerServerReceiver(
            CustomPacketPayload.Type<T> type,
            ServerPlayNetworking.PlayPayloadHandler<T> handler);

        void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
    }
}
