package amrac.entities;

public final class BattlefieldFlightModelPolicy {
    public static final double FULL_CONTROL_SPEED_RATIO = 3.85D;

    private BattlefieldFlightModelPolicy() {
    }

    public static double controlAuthority(double wingFlowSpeed,
                                          double stallSpeed) {
        if (!Double.isFinite(wingFlowSpeed) ||
            !Double.isFinite(stallSpeed) || stallSpeed <= 1.0E-6D) {
            return 0.0D;
        }

        double speed = Math.max(wingFlowSpeed, 0.0D);
        double fullControlSpeed = Math.max(stallSpeed *
            FULL_CONTROL_SPEED_RATIO, 1.0E-9D);
        double speedRatio = speed / fullControlSpeed;
        return clamp(speedRatio * speedRatio, 0.0D, 1.0D);
    }

    private static double clamp(double value, double minimum,
                                double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
