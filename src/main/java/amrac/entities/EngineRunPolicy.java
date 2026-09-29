package amrac.entities;

public final class EngineRunPolicy {
    private EngineRunPolicy() {
    }

    public static boolean hasRunCommand(boolean hasControllingPilot,
                                        int throttle) {
        return hasControllingPilot && throttle > 0;
    }

    public static boolean nextStarted(boolean wasStarted,
                                      boolean hasControllingPilot,
                                      int throttle) {
        return hasControllingPilot &&
            (wasStarted || hasRunCommand(true, throttle));
    }

    public static boolean shouldRun(boolean started,
                                    boolean hasControllingPilot) {
        return started && hasControllingPilot;
    }
}
