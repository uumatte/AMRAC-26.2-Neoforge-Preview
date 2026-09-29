package amrac.physics.aircraft;

import java.util.Map;

public record FuelProfile(double capacityLitres, double referenceFuelFraction,
                          CurveInterpolator throttleThrustCurve,
                          CurveInterpolator throttleFlowCurve,
                          double afterburnerFlowMultiplier) {
    public static final double KILOGRAMS_PER_LITRE = 0.8D;

    public static final FuelProfile NONE = new FuelProfile(0.0D, 0.5D,
        linear(), flatFlow(), 3.0D);

    public double thrustFraction(double throttle) {
        double value = throttleThrustCurve.interpolate(clampUnit(throttle));
        return Double.isFinite(value) && value > 0.0D ? Math.min(value, 1.0D) : 0.0D;
    }

    public double flowLitresPerSecond(double throttle, double afterburnerSpool) {
        double base = throttleFlowCurve.interpolate(clampUnit(throttle));
        if (!Double.isFinite(base) || base <= 0.0D) {
            return 0.0D;
        }
        double spool = clampUnit(afterburnerSpool);
        double burner = 1.0D + (afterburnerFlowMultiplier - 1.0D) * spool;
        return base * Math.max(burner, 1.0D);
    }

    public static double massOf(double litres) {
        return Math.max(litres, 0.0D) * KILOGRAMS_PER_LITRE;
    }

    public double massOffsetKilograms(double litres) {
        if (capacityLitres <= 0.0D) {
            return 0.0D;
        }
        double reference = capacityLitres * clampUnit(referenceFuelFraction);
        return massOf(clamp(litres, 0.0D, capacityLitres)) - massOf(reference);
    }

    public boolean isMetered() {
        return capacityLitres > 0.0D;
    }

    public static FuelProfile fromJson(Map<String, Object> root) {
        Map<String, Object> fuel = Json.object(root, "fuel");
        if (fuel == null) {
            return NONE;
        }
        double capacity = Json.number(fuel, "capacityLitres", 0.0D);
        double reference = Json.number(fuel, "referenceFuelFraction", 0.5D);
        double[][] thrust = Json.curve(fuel, "throttleThrustCurve");
        double[][] flow = Json.curve(fuel, "throttleFlowCurve");
        double burner = Json.number(fuel, "afterburnerFlowMultiplier", 3.0D);
        return new FuelProfile(
            Math.max(capacity, 0.0D),
            clampUnit(reference),
            thrust != null && thrust.length >= 2
                ? CurveInterpolator.of(thrust) : linear(),
            flow != null && flow.length >= 2
                ? CurveInterpolator.of(flow) : flatFlow(),
            Double.isFinite(burner) && burner >= 1.0D ? burner : 3.0D);
    }

    private static CurveInterpolator linear() {
        return CurveInterpolator.of(new double[][] {{0.0D, 0.0D}, {1.0D, 1.0D}});
    }

    private static CurveInterpolator flatFlow() {
        return CurveInterpolator.of(new double[][] {{0.0D, 0.0D}, {1.0D, 1.0D}});
    }

    private static double clampUnit(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    private static double clamp(double value, double low, double high) {
        if (!Double.isFinite(value)) {
            return low;
        }
        return value < low ? low : Math.min(value, high);
    }
}
