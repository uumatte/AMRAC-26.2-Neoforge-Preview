package amrac.physics.aircraft;

public final class FlapPolicy {
    private FlapPolicy() {
    }

    public static final double FULL_LIFT_INCREMENT = 0.55D;

    public static final double FULL_DRAG_INCREMENT = 0.055D;

    public static final double DESTRUCTION_SPEED = 4.0D;

    public static final double DESTRUCTION_SPEED_BLOCKS_PER_SECOND =
        DESTRUCTION_SPEED * 20.0D;

    public static final int TRAVEL_TICKS = 20;

    public static double liftIncrement(double position) {
        return FULL_LIFT_INCREMENT * clampPosition(position);
    }

    public static double dragIncrement(double position) {
        return FULL_DRAG_INCREMENT * clampPosition(position);
    }

    private static double clampPosition(double position) {
        if (!Double.isFinite(position)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, position));
    }
}
