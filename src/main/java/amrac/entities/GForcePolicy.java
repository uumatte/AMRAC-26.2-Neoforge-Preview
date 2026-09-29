package amrac.entities;

public final class GForcePolicy {
    public static final double BLACKOUT_THRESHOLD_G = 8.0D;

    public static final double REDOUT_THRESHOLD_G = -2.0D;

    public static final double BLACKOUT_ONSET_SECONDS = 2.0D;

    public static final double BLACKOUT_FULL_SECONDS = 9.0D;

    public static final double REDOUT_ONSET_SECONDS = 1.0D;
    public static final double REDOUT_FULL_SECONDS = 6.0D;

    public static final double BLACKOUT_G_PER_DOUBLING = 2.0D;
    public static final double REDOUT_G_PER_DOUBLING = 1.0D;

    public static final double BLACKOUT_SNATCH_FROM_G = 10.0D;
    public static final double BLACKOUT_SNATCH_FULL_G = 13.0D;
    public static final double REDOUT_SNATCH_FROM_G = -3.5D;
    public static final double REDOUT_SNATCH_FULL_G = -6.0D;

    public static final double SNATCH_CEILING = 0.85D;

    public static final double RECOVERY_RATE = 0.5D;

    public static final double FADE_IN_SECONDS = 0.6D;
    public static final double FADE_OUT_SECONDS = 2.5D;

    private GForcePolicy() {
    }

    public static final class State {
        public double blackoutDose;
        public double redoutDose;
        public double blackout;
        public double redout;

        public void reset() {
            blackoutDose = 0.0D;
            redoutDose = 0.0D;
            blackout = 0.0D;
            redout = 0.0D;
        }
    }

    public static void step(State state, double loadFactor, double dt) {
        if (state == null || !Double.isFinite(dt) || dt <= 0.0D) {
            return;
        }
        double g = Double.isFinite(loadFactor) ? loadFactor : 1.0D;

        state.blackoutDose = dose(state.blackoutDose,
            g - BLACKOUT_THRESHOLD_G, BLACKOUT_G_PER_DOUBLING,
            BLACKOUT_FULL_SECONDS, dt);
        state.redoutDose = dose(state.redoutDose,
            REDOUT_THRESHOLD_G - g, REDOUT_G_PER_DOUBLING,
            REDOUT_FULL_SECONDS, dt);

        state.blackout = ease(state.blackout, blackoutTarget(state, g), dt);
        state.redout = ease(state.redout, redoutTarget(state, g), dt);
    }

    private static double dose(double dose, double over, double gPerDoubling,
                               double ceiling, double dt) {
        if (over >= 0.0D) {
            return Math.min(ceiling * 1.25D,
                dose + dt * (1.0D + over / gPerDoubling));
        }
        return Math.max(0.0D, dose - dt * RECOVERY_RATE);
    }

    public static double blackoutTarget(State state, double loadFactor) {
        double held = ramp(state.blackoutDose, BLACKOUT_ONSET_SECONDS,
            BLACKOUT_FULL_SECONDS);
        double snatched = SNATCH_CEILING * ramp(loadFactor,
            BLACKOUT_SNATCH_FROM_G, BLACKOUT_SNATCH_FULL_G);
        return Math.max(held, snatched);
    }

    public static double redoutTarget(State state, double loadFactor) {
        double held = ramp(state.redoutDose, REDOUT_ONSET_SECONDS,
            REDOUT_FULL_SECONDS);
        double snatched = SNATCH_CEILING * ramp(-loadFactor,
            -REDOUT_SNATCH_FROM_G, -REDOUT_SNATCH_FULL_G);
        return Math.max(held, snatched);
    }

    public static boolean isTunnelVision(State state) {
        return state.blackout > 0.75D;
    }

    private static double ramp(double value, double from, double to) {
        if (!Double.isFinite(value) || value <= from) {
            return 0.0D;
        }
        if (value >= to) {
            return 1.0D;
        }
        return (value - from) / (to - from);
    }

    private static double ease(double current, double target, double dt) {
        double rate = target > current
            ? 1.0D / FADE_IN_SECONDS : 1.0D / FADE_OUT_SECONDS;
        double step = rate * dt;
        if (Math.abs(target - current) <= step) {
            return target;
        }
        return current + Math.copySign(step, target - current);
    }
}
