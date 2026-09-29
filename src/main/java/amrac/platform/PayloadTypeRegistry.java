package amrac.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class PayloadTypeRegistry {
    private static final PayloadTypeRegistry SERVERBOUND =
        new PayloadTypeRegistry(true);
    private static final PayloadTypeRegistry CLIENTBOUND =
        new PayloadTypeRegistry(false);

    private final boolean serverbound;

    private PayloadTypeRegistry(boolean serverbound) {
        this.serverbound = serverbound;
    }

    public static PayloadTypeRegistry serverboundPlay() {
        return SERVERBOUND;
    }

    public static PayloadTypeRegistry clientboundPlay() {
        return CLIENTBOUND;
    }

    public <T extends CustomPacketPayload> void register(
        CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        if (serverbound) {
            Platform.backend().registerServerboundPayload(type, codec);
        } else {
            Platform.backend().registerClientboundPayload(type, codec);
        }
    }
}
