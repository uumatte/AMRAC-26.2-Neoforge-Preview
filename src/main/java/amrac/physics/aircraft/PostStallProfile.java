package amrac.physics.aircraft;

import java.util.Map;

public record PostStallProfile(double fadeBandDegrees,
                               double pitchAuthority,
                               double rollAuthority,
                               double yawAuthority,
                               double recoveryMoment,
                               double recoveryPressureFloor,
                               double rateCommandFade) {
    public static final PostStallProfile DEFAULT =
        new PostStallProfile(10.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.15D, 0.0D);

    public static PostStallProfile fromJson(Map<String, Object> root) {
        Map<String, Object> block = Json.object(root, "postStall");
        if (block == null) {
            return DEFAULT;
        }
        return new PostStallProfile(
            positive(Json.number(block, "fadeBandDegrees",
                DEFAULT.fadeBandDegrees()), DEFAULT.fadeBandDegrees()),
            unit(Json.number(block, "pitchAuthority", DEFAULT.pitchAuthority()),
                DEFAULT.pitchAuthority()),
            unit(Json.number(block, "rollAuthority", DEFAULT.rollAuthority()),
                DEFAULT.rollAuthority()),
            unit(Json.number(block, "yawAuthority", DEFAULT.yawAuthority()),
                DEFAULT.yawAuthority()),
            nonNegative(Json.number(block, "recoveryMoment",
                DEFAULT.recoveryMoment()), DEFAULT.recoveryMoment()),
            unit(Json.number(block, "recoveryPressureFloor",
                DEFAULT.recoveryPressureFloor()),
                DEFAULT.recoveryPressureFloor()),
            unit(Json.number(block, "rateCommandFade",
                DEFAULT.rateCommandFade()), DEFAULT.rateCommandFade()));
    }

    public static double axisFloor(double stated, double inherited) {
        if (Double.isFinite(stated) && stated > 0.0D) {
            return Math.min(stated, 1.0D);
        }
        return Double.isFinite(inherited) ? Math.max(inherited, 0.0D) : 0.0D;
    }

    public double share(double angleOfAttackDegrees, double stallAngle) {
        if (!Double.isFinite(angleOfAttackDegrees) || !Double.isFinite(stallAngle)) {
            return 0.0D;
        }
        double past = Math.abs(angleOfAttackDegrees) - stallAngle;
        if (past <= 0.0D) {
            return 0.0D;
        }
        double band = Math.max(fadeBandDegrees, 0.1D);
        double progress = Math.min(past / band, 1.0D);
        return progress * progress * (3.0D - 2.0D * progress);
    }

    public double openLoopShare(double angleOfAttackDegrees, double stallAngle) {
        return share(angleOfAttackDegrees, stallAngle) * rateCommandFade;
    }

    public double recoveryMomentAt(double angleOfAttackDegrees, double stallAngle,
                                   double pressureRatio) {
        if (recoveryMoment <= 0.0D) {
            return 0.0D;
        }
        double share = share(angleOfAttackDegrees, stallAngle);
        if (share <= 0.0D) {
            return 0.0D;
        }
        double pressure = Double.isFinite(pressureRatio)
            ? Math.max(pressureRatio, recoveryPressureFloor) : recoveryPressureFloor;
        double lever = Math.abs(Math.sin(Math.toRadians(
            Double.isFinite(angleOfAttackDegrees) ? angleOfAttackDegrees : 0.0D)));
        return recoveryMoment * share * Math.min(pressure, 1.0D) * lever;
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
