package amrac.physics.aircraft;

import java.util.Map;

/**
 * The console addresses parameters by leaf name only, so document keys must be unambiguous across
 * the file (rollInertia, not roll).
 */
public record InertiaProfile(double rollInertia, double pitchInertia,
                             double yawInertia,
                             double rollControlMoment, double pitchControlMoment,
                             double yawControlMoment,
                             double rollDamping, double pitchDamping,
                             double yawDamping,
                             double[] stationSpan, double storesArm,
                             double fuelRollArm, double fuelPitchArm) {
    private static final double ASSUMED_ASPECT_RATIO = 3.2D;

    private static final double LENGTH_OVER_SPAN = 1.6D;

    private static final double ROLL_GYRATION_OVER_SPAN = 0.11D;
    private static final double PITCH_GYRATION_OVER_LENGTH = 0.175D;
    private static final double YAW_GYRATION_OVER_LENGTH = 0.19D;

    private static final double ROLL_SPIN_UP_SECONDS = 0.35D;
    private static final double PITCH_SPIN_UP_SECONDS = 0.55D;
    private static final double YAW_SPIN_UP_SECONDS = 0.80D;

    private static final double ROLL_DAMPING_SECONDS = 0.90D;
    private static final double PITCH_DAMPING_SECONDS = 1.60D;
    private static final double YAW_DAMPING_SECONDS = 1.80D;

    private static final double[] STATION_SEMI_SPAN_FRACTION =
        {0.85D, 0.62D, 0.44D, 0.30D, 0.20D};

    private static final double FUEL_ROLL_ARM_OVER_SEMI_SPAN = 0.30D;
    private static final double FUEL_PITCH_ARM_OVER_LENGTH = 0.10D;

    private static final double DEFAULT_STORES_ARM = 0.5D;

    private static final int DEFAULT_STATIONS = 10;

    public static InertiaProfile forAirframe(double mass, double wingArea,
                                             double maxRollRate,
                                             double maxPitchRate,
                                             double maxYawRate) {
        double weight = positive(mass, 12000.0D);
        double area = positive(wingArea, 100.0D);
        double span = Math.sqrt(ASSUMED_ASPECT_RATIO * area);
        double length = LENGTH_OVER_SPAN * span;
        double semiSpan = 0.5D * span;

        double roll = weight * square(ROLL_GYRATION_OVER_SPAN * span);
        double pitch = weight * square(PITCH_GYRATION_OVER_LENGTH * length);
        double yaw = weight * square(YAW_GYRATION_OVER_LENGTH * length);

        double[] stations = new double[DEFAULT_STATIONS];
        for (int i = 0; i < stations.length; i++) {
            int pair = Math.min(i / 2, STATION_SEMI_SPAN_FRACTION.length - 1);
            double magnitude = semiSpan * STATION_SEMI_SPAN_FRACTION[pair];
            stations[i] = (i % 2 == 0) ? -magnitude : magnitude;
        }

        return new InertiaProfile(roll, pitch, yaw,
            momentFor(roll, maxRollRate, ROLL_SPIN_UP_SECONDS),
            momentFor(pitch, maxPitchRate, PITCH_SPIN_UP_SECONDS),
            momentFor(yaw, maxYawRate, YAW_SPIN_UP_SECONDS),
            dampingFor(roll, ROLL_DAMPING_SECONDS),
            dampingFor(pitch, PITCH_DAMPING_SECONDS),
            dampingFor(yaw, YAW_DAMPING_SECONDS),
            stations, DEFAULT_STORES_ARM,
            FUEL_ROLL_ARM_OVER_SEMI_SPAN * semiSpan,
            FUEL_PITCH_ARM_OVER_LENGTH * length);
    }

    public static InertiaProfile fromJson(Map<String, Object> root,
                                          InertiaProfile fallback) {
        Map<String, Object> block = Json.object(root, "inertia");
        if (block == null) {
            return fallback;
        }
        double[] stations = Json.numbers(block, "stationSpanMetres");
        return new InertiaProfile(
            positive(Json.number(block, "rollInertia", fallback.rollInertia()),
                fallback.rollInertia()),
            positive(Json.number(block, "pitchInertia", fallback.pitchInertia()),
                fallback.pitchInertia()),
            positive(Json.number(block, "yawInertia", fallback.yawInertia()),
                fallback.yawInertia()),
            positive(Json.number(block, "rollControlMoment",
                fallback.rollControlMoment()), fallback.rollControlMoment()),
            positive(Json.number(block, "pitchControlMoment",
                fallback.pitchControlMoment()), fallback.pitchControlMoment()),
            positive(Json.number(block, "yawControlMoment",
                fallback.yawControlMoment()), fallback.yawControlMoment()),
            nonNegative(Json.number(block, "rollDamping", fallback.rollDamping()),
                fallback.rollDamping()),
            nonNegative(Json.number(block, "pitchDamping", fallback.pitchDamping()),
                fallback.pitchDamping()),
            nonNegative(Json.number(block, "yawDamping", fallback.yawDamping()),
                fallback.yawDamping()),
            stations != null ? stations : fallback.stationSpan(),
            nonNegative(Math.abs(Json.number(block, "storesArmMetres",
                fallback.storesArm())), fallback.storesArm()),
            nonNegative(Math.abs(Json.number(block, "fuelRollArmMetres",
                fallback.fuelRollArm())), fallback.fuelRollArm()),
            nonNegative(Math.abs(Json.number(block, "fuelPitchArmMetres",
                fallback.fuelPitchArm())), fallback.fuelPitchArm()));
    }

    public double stationSpanMetres(int station) {
        if (stationSpan == null || station < 0 || station >= stationSpan.length) {
            return 0.0D;
        }
        double value = stationSpan[station];
        return Double.isFinite(value) ? value : 0.0D;
    }

    public double rollInertiaWith(double fuelOffset, double storesRollInertia) {
        return combine(rollInertia,
            finite(fuelOffset) * square(fuelRollArm) + nonNegative(storesRollInertia));
    }

    public double pitchInertiaWith(double fuelOffset, double storesPitchInertia) {
        return combine(pitchInertia,
            finite(fuelOffset) * square(fuelPitchArm)
                + nonNegative(storesPitchInertia));
    }

    public double yawInertiaWith(double fuelOffset, double storesRollInertia,
                                 double storesPitchInertia) {
        return combine(yawInertia,
            finite(fuelOffset) * (square(fuelRollArm) + square(fuelPitchArm))
                + nonNegative(storesRollInertia) + nonNegative(storesPitchInertia));
    }

    private static double combine(double base, double added) {
        double resolved = positive(base, 1.0D);
        return Math.max(0.25D * resolved, resolved + finite(added));
    }

    private static double momentFor(double inertia, double rateDegrees,
                                    double seconds) {
        double rate = positive(rateDegrees, 60.0D);
        return inertia * Math.toRadians(rate) / Math.max(seconds, 0.05D);
    }

    private static double dampingFor(double inertia, double seconds) {
        return inertia * Math.toRadians(1.0D) / Math.max(seconds, 0.05D);
    }

    private static double square(double value) {
        double resolved = finite(value);
        return resolved * resolved;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    private static double nonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0D ? value : fallback;
    }
}
