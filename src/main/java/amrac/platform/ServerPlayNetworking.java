package amrac.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ServerPlayNetworking {
    private ServerPlayNetworking() {
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        Platform.backend().sendToPlayer(player, payload);
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
        CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        Platform.backend().registerServerReceiver(type, handler);
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public record Context(ServerPlayer player, MinecraftServer server) {
    }
}
