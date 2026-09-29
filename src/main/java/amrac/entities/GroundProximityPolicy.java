package amrac.entities;

public final class GroundProximityPolicy {
    public static final double MAX_SOUNDING_DEPTH = 320.0D;

    public static final double NO_GROUND = Double.POSITIVE_INFINITY;

    public static final double PULL_UP_HEIGHT = 60.0D;

    public static final double RADAR_CLUTTER_HEIGHT = 60.0D;

    public static final int TAKEOFF_PITCH_AUTHORITY = 25;

    public static final int TAKEOFF_LIMIT_RELEASE_TICKS = 100;

    private GroundProximityPolicy() {
    }

    public static int takeoffPitchAuthority(int requested,
                                            int ticksSinceAirborne) {
        if (ticksSinceAirborne >= TAKEOFF_LIMIT_RELEASE_TICKS) {
            return requested;
        }
        return Math.min(requested, TAKEOFF_PITCH_AUTHORITY);
    }

    public static boolean exceedsStructuralLimit(double speed, double limit,
                                                 boolean onGroundOrWater) {
        if (onGroundOrWater || !Double.isFinite(speed) || !Double.isFinite(limit)
            || limit <= 0.0D) {
            return false;
        }
        return speed > limit;
    }

    public static boolean shouldWarnPullUp(double heightAboveGround) {
        return Double.isFinite(heightAboveGround) &&
            heightAboveGround >= 0.0D &&
            heightAboveGround <= PULL_UP_HEIGHT;
    }

    public static boolean isPullUpInhibited(boolean onGroundOrWater,
                                            boolean gearDown) {
        return onGroundOrWater || gearDown;
    }

    public static boolean isTakingOff(boolean onGroundOrWater,
                                      int ticksSinceAirborne) {
        return onGroundOrWater || ticksSinceAirborne < 0 ||
            ticksSinceAirborne < TAKEOFF_LIMIT_RELEASE_TICKS;
    }

    public static boolean shouldWarnPullUp(double heightAboveGround,
                                           boolean onGroundOrWater,
                                           boolean gearDown) {
        return shouldWarnPullUp(heightAboveGround) &&
            !isPullUpInhibited(onGroundOrWater, gearDown);
    }

    public static boolean isLostInClutter(double heightAboveGround) {
        return Double.isFinite(heightAboveGround) &&
            heightAboveGround >= 0.0D &&
            heightAboveGround <= RADAR_CLUTTER_HEIGHT;
    }

    public static double warningUrgency(double heightAboveGround) {
        if (!shouldWarnPullUp(heightAboveGround)) {
            return 0.0D;
        }
        return 1.0D - heightAboveGround / PULL_UP_HEIGHT;
    }
}
