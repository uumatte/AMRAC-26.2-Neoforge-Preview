package amrac.physics;

public final class TickRate {
    public static final int TICKS_PER_SECOND = 20;

    public static final double SECONDS_PER_TICK = 1.0D / TICKS_PER_SECOND;

    public static final double TICKS_PER_SECOND_SQUARED =
        (double) TICKS_PER_SECOND * TICKS_PER_SECOND;

    private TickRate() {
    }
}
