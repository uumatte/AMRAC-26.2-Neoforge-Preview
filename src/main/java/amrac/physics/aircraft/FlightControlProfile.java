package amrac.physics.aircraft;

import java.util.Map;

public record FlightControlProfile(double fullAuthoritySpeed,
                                   double rollTaperOnsetSpeed,
                                   double pitchAuthorityExponent,
                                   double rollAuthorityExponent,
                                   double yawAuthorityExponent,
                                   double rollHighPressureTaper,
                                   double angleOfAttackCaptureSeconds,
                                   double limitSoftness,
                                   double storesAsymmetryTrim) {
    public static final FlightControlProfile DEFAULT = new FlightControlProfile(
        0.0D, 0.0D, 0.70D, 0.55D, 0.88D, 0.25D, 0.30D, 0.25D, 0.65D);

    public static FlightControlProfile fromJson(Map<String, Object> root) {
        Map<String, Object> block = Json.object(root, "flightControl");
        if (block == null) {
            return DEFAULT;
        }
        return new FlightControlProfile(
            nonNegative(Json.number(block, "fullAuthoritySpeed",
                DEFAULT.fullAuthoritySpeed()), DEFAULT.fullAuthoritySpeed()),
            nonNegative(Json.number(block, "rollTaperOnsetSpeed",
                DEFAULT.rollTaperOnsetSpeed()), DEFAULT.rollTaperOnsetSpeed()),
            positive(Json.number(block, "pitchAuthorityExponent",
                DEFAULT.pitchAuthorityExponent()), DEFAULT.pitchAuthorityExponent()),
            positive(Json.number(block, "rollAuthorityExponent",
                DEFAULT.rollAuthorityExponent()), DEFAULT.rollAuthorityExponent()),
            positive(Json.number(block, "yawAuthorityExponent",
                DEFAULT.yawAuthorityExponent()), DEFAULT.yawAuthorityExponent()),
            nonNegative(Json.number(block, "rollHighPressureTaper",
                DEFAULT.rollHighPressureTaper()), DEFAULT.rollHighPressureTaper()),
            positive(Json.number(block, "angleOfAttackCaptureSeconds",
                DEFAULT.angleOfAttackCaptureSeconds()),
                DEFAULT.angleOfAttackCaptureSeconds()),
            unit(Json.number(block, "limitSoftness", DEFAULT.limitSoftness()),
                DEFAULT.limitSoftness()),
            unit(Json.number(block, "storesAsymmetryTrim",
                DEFAULT.storesAsymmetryTrim()), DEFAULT.storesAsymmetryTrim()));
    }

    public double rollTaperOnsetSpeed(double authoritySpeed) {
        if (rollTaperOnsetSpeed > 0.0D) {
            return rollTaperOnsetSpeed;
        }
        return 2.0D * Math.max(authoritySpeed, 0.0D);
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static double nonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0D ? value : fallback;
    }

    private static double unit(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0D && value <= 1.0D
            ? value : fallback;
    }
}
