package amrac.platform;

import net.minecraft.server.MinecraftServer;

public final class ServerTickEvents {
    public static final Event<EndTick> END_SERVER_TICK = new Event<>();

    private ServerTickEvents() {
    }

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }
}
