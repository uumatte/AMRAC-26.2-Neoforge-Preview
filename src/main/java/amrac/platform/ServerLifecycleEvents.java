package amrac.platform;

import net.minecraft.server.MinecraftServer;

public final class ServerLifecycleEvents {
    public static final Event<ServerCallback> SERVER_STARTING = new Event<>();
    public static final Event<ServerCallback> SERVER_STARTED = new Event<>();
    public static final Event<ServerCallback> SERVER_STOPPING = new Event<>();
    public static final Event<ServerCallback> SERVER_STOPPED = new Event<>();

    private ServerLifecycleEvents() {
    }

    @FunctionalInterface
    public interface ServerCallback {
        void onServer(MinecraftServer server);
    }
}
