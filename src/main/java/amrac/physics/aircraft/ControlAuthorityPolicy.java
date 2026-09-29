package amrac.physics.aircraft;

public final class ControlAuthorityPolicy {
    public static final double ABSOLUTE_FLOOR = 1.0E-3D;

    private ControlAuthorityPolicy() {
    }

    public static double referenceDynamicPressure(double referenceSpeed) {
        if (!Double.isFinite(referenceSpeed) || referenceSpeed <= 0.0D) {
            return 0.0D;
        }
        return 0.5D * AtmosphereModel.SEA_LEVEL_DENSITY * referenceSpeed
            * referenceSpeed;
    }

    public static double pressureRatio(double dynamicPressure,
                                       double referenceDynamicPressure) {
        if (!Double.isFinite(dynamicPressure) || dynamicPressure <= 0.0D) {
            return 0.0D;
        }
        if (!Double.isFinite(referenceDynamicPressure)
                || referenceDynamicPressure <= 1.0E-9D) {
            return 1.0D;
        }
        return dynamicPressure / referenceDynamicPressure;
    }

    public static double axisAuthority(double ratio, double exponent,
                                       double floor) {
        double bounded = Math.max(0.0D, finite(ratio, 0.0D));
        double power = finite(exponent, 1.0D);
        if (power <= 0.0D) {
            power = 1.0D;
        }
        double raw = bounded <= 0.0D ? 0.0D : Math.pow(bounded, power);
        double least = clamp(finite(floor, ABSOLUTE_FLOOR), ABSOLUTE_FLOOR, 1.0D);
        return clamp(raw, least, 1.0D);
    }

    public static double highPressureRollTaper(double ratio, double onsetRatio,
                                               double taper) {
        double onset = finite(onsetRatio, 0.0D);
        double strength = finite(taper, 0.0D);
        if (onset <= 1.0E-9D || strength <= 0.0D) {
            return 1.0D;
        }
        double excess = finite(ratio, 0.0D) / onset - 1.0D;
        if (excess <= 0.0D) {
            return 1.0D;
        }
        return 1.0D / (1.0D + strength * excess);
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
