package amrac.platform.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientPlayNetworking {
    private ClientPlayNetworking() {
    }

    public static void send(CustomPacketPayload payload) {
        ClientPlatform.backend().sendToServer(payload);
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
        CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        ClientPlatform.backend().registerClientReceiver(type, handler);
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public record Context(Minecraft client) {
    }
}
