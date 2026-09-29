package amrac.network;

public final class RemotePlaneSmoothingPolicy {
    public static final int MAX_EXTRAPOLATION_TICKS = 3;
    public static final double POSITION_CORRECTION_BLEND = 0.45D;
    public static final float ATTITUDE_CORRECTION_BLEND = 0.55F;
    public static final double POSITION_SAMPLE_BLEND = 0.35D;
    public static final double MOTION_PACKET_BLEND = 0.65D;
    public static final double STALE_MOTION_RETENTION = 0.55D;
    public static final double MINIMUM_CORRECTION_DISTANCE = 0.20D;
    public static final double SPEED_CORRECTION_MULTIPLIER = 0.75D;
    public static final double TELEPORT_BASE_DISTANCE = 8.0D;
    public static final double TELEPORT_SPEED_MULTIPLIER = 4.0D;
    public static final double ATTITUDE_SNAP_RADIANS = Math.toRadians(80.0D);
    public static final double MAX_ANGULAR_EXTRAPOLATION_RADIANS =
        Math.toRadians(19.5D);

    private RemotePlaneSmoothingPolicy() {
    }

    public static double blend(double current, double sample, double amount) {
        if (!Double.isFinite(current) || !Double.isFinite(sample) ||
            !Double.isFinite(amount)) {
            return 0.0D;
        }
        double clampedAmount = clamp(amount, 0.0D, 1.0D);
        return current + (sample - current) * clampedAmount;
    }

    public static double correctionScale(double errorDistance, double speed) {
        if (!Double.isFinite(errorDistance) || errorDistance <= 1.0E-9D) {
            return 0.0D;
        }
        double finiteSpeed = Double.isFinite(speed) ? Math.max(speed, 0.0D) : 0.0D;
        double maximumCorrection = Math.max(MINIMUM_CORRECTION_DISTANCE,
            finiteSpeed * SPEED_CORRECTION_MULTIPLIER);
        double requestedCorrection = errorDistance * POSITION_CORRECTION_BLEND;
        return clamp(Math.min(requestedCorrection, maximumCorrection) /
            errorDistance, 0.0D, 1.0D);
    }

    public static boolean shouldSnapPosition(double errorDistance, double speed,
                                             boolean teleport) {
        if (teleport || !Double.isFinite(errorDistance)) {
            return true;
        }
        double finiteSpeed = Double.isFinite(speed) ? Math.max(speed, 0.0D) : 0.0D;
        double snapDistance = TELEPORT_BASE_DISTANCE +
            finiteSpeed * TELEPORT_SPEED_MULTIPLIER;
        return errorDistance > snapDistance;
    }

    public static double staleMotionRetention(int ticksWithoutSnapshot) {
        return ticksWithoutSnapshot > MAX_EXTRAPOLATION_TICKS
            ? STALE_MOTION_RETENTION : 1.0D;
    }

    public static boolean shouldSnapAttitude(double angularErrorRadians) {
        return !Double.isFinite(angularErrorRadians) ||
            angularErrorRadians > ATTITUDE_SNAP_RADIANS;
    }

    public static double angularStepFraction(double angularDeltaRadians) {
        if (!Double.isFinite(angularDeltaRadians) || angularDeltaRadians <= 1.0E-9D) {
            return 1.0D;
        }
        return clamp(MAX_ANGULAR_EXTRAPOLATION_RADIANS /
            angularDeltaRadians, 0.0D, 1.0D);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
