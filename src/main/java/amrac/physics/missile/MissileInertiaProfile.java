package amrac.physics.missile;

import amrac.physics.aircraft.Json;
import java.util.Map;

/** Game tuning for a symmetric body's pitch/yaw response; roll is not simulated. */
public record MissileInertiaProfile(boolean enabled, double turnInertia,
                                    double controlMoment, double damping,
                                    double referenceDynamicPressure) {
    public static final MissileInertiaProfile DEFAULT =
        new MissileInertiaProfile(true, 160.0D, 5000.0D, 1000.0D, 50000.0D);
    public static final MissileInertiaProfile DISABLED =
        new MissileInertiaProfile(false, 160.0D, 5000.0D, 1000.0D, 50000.0D);

    public MissileInertiaProfile {
        turnInertia = positive(turnInertia, 160.0D);
        controlMoment = positive(controlMoment, 5000.0D);
        damping = positive(damping, 1000.0D);
        referenceDynamicPressure = positive(referenceDynamicPressure, 50000.0D);
    }

    public static MissileInertiaProfile fromJson(Map<String, Object> section,
                                                 MissileInertiaProfile fallback) {
        if (section == null) {
            return fallback;
        }
        return new MissileInertiaProfile(Json.bool(section, "enabled", true),
            positive(Json.number(section, "turnInertia", fallback.turnInertia),
                fallback.turnInertia),
            positive(Json.number(section, "controlMoment", fallback.controlMoment),
                fallback.controlMoment),
            positive(Json.number(section, "damping", fallback.damping),
                fallback.damping),
            positive(Json.number(section, "referenceDynamicPressure",
                fallback.referenceDynamicPressure), fallback.referenceDynamicPressure));
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }
}
