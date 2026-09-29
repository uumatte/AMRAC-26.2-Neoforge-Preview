package amrac.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class ServerEntityEvents {
    public static final Event<Load> ENTITY_LOAD = new Event<>();

    public static final Event<Unload> ENTITY_UNLOAD = new Event<>();

    private ServerEntityEvents() {
    }

    @FunctionalInterface
    public interface Load {
        void onLoad(Entity entity, ServerLevel level);
    }

    @FunctionalInterface
    public interface Unload {
        void onUnload(Entity entity, ServerLevel level);
    }
}
