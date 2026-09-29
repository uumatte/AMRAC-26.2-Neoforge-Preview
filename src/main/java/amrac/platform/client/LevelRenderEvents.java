package amrac.platform.client;

import amrac.platform.Event;

public final class LevelRenderEvents {
    public static final Event<CollectSubmits> COLLECT_SUBMITS = new Event<>();

    private LevelRenderEvents() {
    }

    @FunctionalInterface
    public interface CollectSubmits {
        void collectSubmits(LevelRenderContext context);
    }
}
