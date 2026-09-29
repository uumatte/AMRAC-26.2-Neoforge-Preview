package amrac.entities.ai;

public final class AiThrottlePolicy {
    public static final double GUARD_MARGIN_BLOCKS_PER_SECOND = 40.0D;

    public static final double FULL_CLOSURE_FRACTION = 0.6D;

    public static final int LOOKAHEAD_TICKS = 30;

    public static final double ACCELERATION_SMOOTHING = 0.12D;

    private AiThrottlePolicy() {
    }

    public static double smoothAcceleration(double previousSmoothed,
                                            double speedDelta) {
        if (!Double.isFinite(speedDelta)) {
            return Double.isFinite(previousSmoothed) ? previousSmoothed : 0.0D;
        }
        if (!Double.isFinite(previousSmoothed)) {
            return speedDelta;
        }
        return previousSmoothed
            + (speedDelta - previousSmoothed) * ACCELERATION_SMOOTHING;
    }

    public static double maximumThrottleFraction(
            double speedBlocksPerSecond, double accelerationPerTick,
            double structuralLimitBlocksPerSecond) {
        if (!(structuralLimitBlocksPerSecond > 0.0D)
                || !Double.isFinite(structuralLimitBlocksPerSecond)) {
            return 1.0D;
        }
        double speed = Double.isFinite(speedBlocksPerSecond)
            ? Math.max(0.0D, speedBlocksPerSecond) : 0.0D;
        double trend = Double.isFinite(accelerationPerTick)
            ? accelerationPerTick : 0.0D;
        double predicted = speed + Math.max(0.0D, trend) * LOOKAHEAD_TICKS;

        double guardStart =
            structuralLimitBlocksPerSecond - AiPilotSettings.current().overspeedGuardMargin;
        if (predicted <= guardStart) {
            return 1.0D;
        }
        double closureEnd = guardStart
            + AiPilotSettings.current().overspeedGuardMargin * FULL_CLOSURE_FRACTION;
        if (predicted >= closureEnd) {
            return 0.0D;
        }
        double into = (predicted - guardStart) / (closureEnd - guardStart);
        return Math.max(0.0D, Math.min(1.0D, 1.0D - into));
    }

    public static int commandedThrottle(double maximumFraction, int maxThrottle,
                                        boolean afterburner) {
        double fraction = Double.isFinite(maximumFraction)
            ? Math.max(0.0D, Math.min(1.0D, maximumFraction)) : 1.0D;
        if (afterburner && fraction >= 1.0D) {
            return maxThrottle + 1;
        }
        return (int) Math.round(maxThrottle * fraction);
    }
}
