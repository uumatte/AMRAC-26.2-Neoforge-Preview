package amrac.entities;

public final class AttitudePolicy {
    private static final double STATIONARY = 1.0E-4D;

    private AttitudePolicy() {
    }

    public static double pitchDegrees(double forwardY) {
        if (!Double.isFinite(forwardY)) {
            return 0.0D;
        }
        return Math.toDegrees(Math.asin(clampUnit(forwardY)));
    }

    public static double bankDegrees(double rightY, double upY) {
        if (!Double.isFinite(rightY) || !Double.isFinite(upY)) {
            return 0.0D;
        }
        return Math.toDegrees(Math.atan2(-rightY, upY));
    }

    public static double climbAngleDegrees(double velocityY, double speed) {
        if (!Double.isFinite(velocityY) || !Double.isFinite(speed) ||
            speed < STATIONARY) {
            return 0.0D;
        }
        return Math.toDegrees(Math.asin(clampUnit(velocityY / speed)));
    }

    private static double clampUnit(double value) {
        return value < -1.0D ? -1.0D : Math.min(value, 1.0D);
    }
}
