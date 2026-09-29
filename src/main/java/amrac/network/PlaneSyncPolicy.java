package amrac.network;

public final class PlaneSyncPolicy {
    public static final int MAX_POSITION_EXTRAPOLATION_TICKS = 10;

    public static int extrapolationTicks(long ticksSincePositionChanged) {
        if (ticksSincePositionChanged <= 0L) {
            return 0;
        }
        return (int) Math.min(ticksSincePositionChanged,
            MAX_POSITION_EXTRAPOLATION_TICKS);
    }
    public static final int ENTITY_UPDATE_INTERVAL_TICKS = 1;

    public static final int UPGRADE_UPDATE_INTERVAL_TICKS = 3;

    public static final float PROJECTILE_HIT_MARGIN = 0.5F;

    public static final double IDLE_GROUND_STOP_EPSILON = 0.002D;

    private PlaneSyncPolicy() {
    }

    public static boolean shouldSuppressIdleGroundGravity(
        boolean hasPassengers, boolean onSolidGround, boolean propulsionActive,
        double previousSpeedSquared, double integratedHorizontalSpeedSquared,
        double integratedVerticalSpeed, double gravity) {
        if (hasPassengers || !onSolidGround || propulsionActive ||
            !Double.isFinite(previousSpeedSquared) ||
            !Double.isFinite(integratedHorizontalSpeedSquared) ||
            !Double.isFinite(integratedVerticalSpeed) || !Double.isFinite(gravity)) {
            return false;
        }

        double stopThresholdSquared = IDLE_GROUND_STOP_EPSILON *
            IDLE_GROUND_STOP_EPSILON;
        return previousSpeedSquared < stopThresholdSquared &&
            integratedHorizontalSpeedSquared < stopThresholdSquared &&
            integratedVerticalSpeed <= 0.0D &&
            integratedVerticalSpeed >= Math.min(gravity, 0.0D) -
                IDLE_GROUND_STOP_EPSILON;
    }
}
