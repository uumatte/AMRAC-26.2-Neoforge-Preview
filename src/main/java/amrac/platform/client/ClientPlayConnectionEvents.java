package amrac.platform.client;

import amrac.platform.Event;
import net.minecraft.client.Minecraft;

public final class ClientPlayConnectionEvents {
    public static final Event<Disconnect> DISCONNECT = new Event<>();

    private ClientPlayConnectionEvents() {
    }

    @FunctionalInterface
    public interface Disconnect {
        void onDisconnect(Minecraft client);
    }
}
