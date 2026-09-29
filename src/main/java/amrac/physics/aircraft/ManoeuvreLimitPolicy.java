package amrac.physics.aircraft;

/**
 * The load limit and the AoA limit are this one control law, replacing three earlier
 * implementations. Don't add another limiter outside the flight model, or the stick is cut more
 * than once.
 */
public final class ManoeuvreLimitPolicy {
    public static final double MINIMUM_MANOEUVRE_SPEED = 25.0D;

    private static final double ALPHA_SEARCH_CEILING = 90.0D;

    private ManoeuvreLimitPolicy() {
    }

    public static double loadAvailable(double dynamicPressure, double wingArea,
                                       double liftCoefficient, double weight) {
        if (!Double.isFinite(weight) || weight <= 1.0E-6D) {
            return 0.0D;
        }
        double lift = finite(dynamicPressure, 0.0D) * finite(wingArea, 0.0D)
            * finite(liftCoefficient, 0.0D);
        return Math.max(0.0D, lift / weight);
    }

    public static double liftCoefficientForLoad(double load, double weight,
                                                double dynamicPressure,
                                                double wingArea) {
        double denominator = finite(dynamicPressure, 0.0D) * finite(wingArea, 0.0D);
        if (denominator <= 1.0E-9D) {
            return Double.MAX_VALUE;
        }
        return finite(load, 0.0D) * finite(weight, 0.0D) / denominator;
    }

    public static double angleOfAttackForLiftCoefficient(CurveInterpolator liftCurve,
                                                         boolean noseUp,
                                                         double target,
                                                         double ceiling) {
        double top = clamp(finite(ceiling, 0.0D), 0.0D, ALPHA_SEARCH_CEILING);
        if (liftCurve == null || !Double.isFinite(target) || target <= 0.0D) {
            return 0.0D;
        }
        if (liftMagnitude(liftCurve, noseUp, top) <= target) {
            return top;
        }
        double low = 0.0D;
        double high = top;
        for (int i = 0; i < 20; i++) {
            double middle = 0.5D * (low + high);
            if (liftMagnitude(liftCurve, noseUp, middle) >= target) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return high;
    }

    public static double liftMagnitude(CurveInterpolator liftCurve, boolean noseUp,
                                       double angleMagnitude) {
        if (liftCurve == null) {
            return 0.0D;
        }
        double angle = Math.max(0.0D, finite(angleMagnitude, 0.0D));
        double value = noseUp ? liftCurve.interpolate(angle)
            : -liftCurve.interpolate(-angle);
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    public static double targetAngleOfAttack(CurveInterpolator liftCurve,
                                             boolean noseUp,
                                             double limiterAngle,
                                             double structuralLoad,
                                             double weight,
                                             double dynamicPressure,
                                             double wingArea) {
        double limit = Math.max(0.0D, finite(limiterAngle, 0.0D));
        double needed = liftCoefficientForLoad(structuralLoad, weight,
            dynamicPressure, wingArea);
        if (!Double.isFinite(needed) || needed >= Double.MAX_VALUE) {
            return limit;
        }
        return Math.min(limit,
            angleOfAttackForLiftCoefficient(liftCurve, noseUp, needed, limit));
    }

    public static double flightPathPitchRate(double load, double upY,
                                             double gravity, double airspeed) {
        double speed = finite(airspeed, 0.0D);
        double g = finite(gravity, 9.81D);
        if (speed < MINIMUM_MANOEUVRE_SPEED * (g / AtmosphereModel.STANDARD_GRAVITY)) {
            return 0.0D;
        }
        double radians = g
            * (finite(load, 0.0D) - clamp(finite(upY, 0.0D), -1.0D, 1.0D)) / speed;
        return Math.toDegrees(radians);
    }

    public static double pullRateLimit(double angleOfAttack, double targetAngle,
                                       double limiterBand,
                                       double loadAvailable, double upY,
                                       double gravity, double airspeed,
                                       double captureSeconds) {
        double path = flightPathPitchRate(Math.max(loadAvailable, 0.0D), upY,
            gravity, airspeed);
        double capture = Math.max(finite(captureSeconds, 0.3D), 0.05D);
        double band = Math.max(finite(limiterBand, 0.0D), MINIMUM_LIMITER_BAND);
        double allowance = clamp(
            finite(targetAngle, 0.0D) - finite(angleOfAttack, 0.0D), 0.0D, band);
        return Math.max(0.0D, path + allowance / capture);
    }

    public static final double MINIMUM_LIMITER_BAND = 2.0D;

    public static double pushRateLimit(double angleOfAttack, double targetAngle,
                                       double limiterBand,
                                       double loadAvailable, double upY,
                                       double gravity, double airspeed,
                                       double captureSeconds) {
        return pullRateLimit(-angleOfAttack, targetAngle, limiterBand,
            loadAvailable, -upY, gravity, airspeed, captureSeconds);
    }

    public static double softLimit(double value, double ceiling, double softness) {
        double magnitude = finite(value, 0.0D);
        double top = finite(ceiling, 0.0D);
        if (top <= 0.0D) {
            return 0.0D;
        }
        double ease = clamp(finite(softness, 0.0D), 0.0D, 0.9D);
        double knee = top * (1.0D - ease);
        if (magnitude <= knee) {
            return magnitude;
        }
        double span = top - knee;
        if (span <= 1.0E-9D) {
            return Math.min(magnitude, top);
        }
        return knee + span * (1.0D - Math.exp(-(magnitude - knee) / span));
    }

    public static double limitPitchRate(double commandedRate, double pullLimit,
                                        double pushLimit, double softness) {
        double command = finite(commandedRate, 0.0D);
        if (command > 0.0D) {
            return softLimit(command, Math.max(pullLimit, 0.0D), softness);
        }
        if (command < 0.0D) {
            return -softLimit(-command, Math.max(pushLimit, 0.0D), softness);
        }
        return 0.0D;
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
