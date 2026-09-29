package amrac.physics.aircraft;

public final class AngularDynamics {
    private AngularDynamics() {
    }

    public static Result step(double rate, double commandedRate,
                              double stickFraction, double openLoopShare,
                              double inertia, double controlMoment,
                              double dampingMoment, double biasMoment,
                              double biasCompensation, double responseSeconds,
                              double maximumRate, double timeStep) {
        double dt = finite(timeStep, FlightModel.FIXED_TIME_STEP);
        if (dt <= 0.0D) {
            dt = FlightModel.FIXED_TIME_STEP;
        }
        double moment = Math.max(finite(inertia, 1.0D), 1.0D);
        double ceiling = Math.max(finite(maximumRate, 0.0D), 0.0D);
        double present = clamp(finite(rate, 0.0D), -ceiling, ceiling);
        double target = clamp(finite(commandedRate, 0.0D), -ceiling, ceiling);
        double available = Math.max(finite(controlMoment, 0.0D), 0.0D);
        double bias = finite(biasMoment, 0.0D);
        double trimmed = bias * clamp(finite(biasCompensation, 0.0D), 0.0D, 1.0D);

        double tau = Math.max(finite(responseSeconds, dt), dt);

        double damping = -Math.max(finite(dampingMoment, 0.0D), 0.0D) * present;
        double closedLoop = moment * Math.toRadians(target - present) / tau
            - damping - trimmed;
        double open = clamp(finite(stickFraction, 0.0D), -1.0D, 1.0D) * available;
        double blend = clamp(finite(openLoopShare, 0.0D), 0.0D, 1.0D);
        double demand = closedLoop + (open - closedLoop) * blend;
        double applied = clamp(demand, -available, available);
        boolean saturated = Math.abs(demand) > available + 1.0E-9D;

        double next = present + Math.toDegrees((applied + damping) / moment) * dt;

        if (!saturated && blend < 1.0E-9D) {
            if (present <= target && next > target) {
                next = target;
            } else if (present >= target && next < target) {
                next = target;
            }
        }

        next += Math.toDegrees(bias / moment) * dt;
        next = clamp(next, -ceiling, ceiling);
        if (Math.abs(next) < 1.0E-6D) {
            next = 0.0D;
        }
        return new Result(next, applied, demand, available, saturated);
    }

    public record Result(double rate, double moment, double demand,
                         double available, boolean saturated) {
        public double authorityMargin() {
            double asked = Math.abs(demand);
            if (asked <= 1.0E-9D) {
                return 1.0D;
            }
            return Math.min(1.0D, available / asked);
        }
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return value < minimum ? minimum : Math.min(value, maximum);
    }
}
