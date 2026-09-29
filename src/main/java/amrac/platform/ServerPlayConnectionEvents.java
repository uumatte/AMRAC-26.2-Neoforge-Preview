package amrac.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPlayConnectionEvents {
    public static final Event<PlayerCallback> JOIN = new Event<>();

    public static final Event<PlayerCallback> DISCONNECT = new Event<>();

    private ServerPlayConnectionEvents() {
    }

    @FunctionalInterface
    public interface PlayerCallback {
        void onPlayer(ServerPlayer player, MinecraftServer server);
    }
}
