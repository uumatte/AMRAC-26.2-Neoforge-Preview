package amrac.platform.client;

import amrac.platform.Event;
import net.minecraft.client.Minecraft;

public final class ClientTickEvents {
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();

    private ClientTickEvents() {
    }

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(Minecraft client);
    }
}
