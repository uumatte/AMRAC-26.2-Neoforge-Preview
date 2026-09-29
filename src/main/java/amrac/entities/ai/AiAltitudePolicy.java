package amrac.entities.ai;

/**
 * MINIMUM_ALTITUDE must stay above AircraftLifecyclePolicy.VIRTUAL_ALTITUDE_FLOOR with margin, or a
 * manoeuvring AI flips between the live and virtual layers. Change one, check the other.
 */
public final class AiAltitudePolicy {
    public static final double MINIMUM_ALTITUDE = 1000.0D;

    public static final double FUEL_STARVED_CRASH_ALTITUDE = 600.0D;

    public static final double RECOVERY_BAND = 200.0D;

    public static final double LEVEL_OFF_BAND = 150.0D;

    private AiAltitudePolicy() {
    }

    public static double assignedAltitude(double wanted, double serviceCeiling) {
        double floor = AiPilotSettings.current().minimumAltitude;
        double target = Double.isFinite(wanted) ? wanted : floor;
        if (serviceCeiling > 0.0D && serviceCeiling != Double.MAX_VALUE) {
            target = Math.min(target, serviceCeiling * 0.9D);
        }
        return Math.max(target, floor);
    }

    public static final double ENGAGE_ALTITUDE_FADE = 0.25D;

    public static final double ENGAGE_BELOW_LAYER = 1500.0D;

    public static double engageAltitude(double assigned, double contactAltitude,
                                        double range, double engagementRange,
                                        double serviceCeiling) {
        if (!(engagementRange > 0.0D) || !Double.isFinite(contactAltitude)
                || !Double.isFinite(range)) {
            return assigned;
        }
        double fade = engagementRange * ENGAGE_ALTITUDE_FADE;
        double share = Math.max(0.0D, Math.min(1.0D,
            (engagementRange + fade - range) / fade));
        if (share <= 0.0D) {
            return assigned;
        }
        double wanted = assigned + (contactAltitude - assigned) * share;
        return assignedAltitude(Math.max(wanted, assigned - ENGAGE_BELOW_LAYER
                * amrac.physics.aircraft.SpeedScale.current()),
            serviceCeiling);
    }

    public static boolean belowFloor(double altitude, boolean climbingOut) {
        return !climbingOut && Double.isFinite(altitude)
            && altitude < AiPilotSettings.current().minimumAltitude;
    }

    public static double recoveryUrgency(double altitude, boolean climbingOut) {
        if (!belowFloor(altitude, climbingOut)) {
            return 0.0D;
        }
        double deficit = AiPilotSettings.current().minimumAltitude - altitude;
        return Math.min(1.0D, Math.max(0.0D, deficit
            / (RECOVERY_BAND * amrac.physics.aircraft.SpeedScale.current())));
    }

    public static boolean fuelStarvedCrash(double altitude, boolean powered) {
        return !powered && Double.isFinite(altitude)
            && altitude < AiPilotSettings.current().fuelStarvedCrashAltitude;
    }

    public static boolean levelledOff(double altitude, double assignedAltitude) {
        return Double.isFinite(altitude)
            && altitude >= assignedAltitude - LEVEL_OFF_BAND
                * amrac.physics.aircraft.SpeedScale.current();
    }
}
