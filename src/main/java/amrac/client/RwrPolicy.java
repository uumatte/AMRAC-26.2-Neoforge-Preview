package amrac.client;

public final class RwrPolicy {
    public static final double MAX_RANGE =
        amrac.weapons.MissileWarningService.WARNING_RANGE;

    public static final double CLOSE_RANGE = 300.0D;

    public static final double CRITICAL_RANGE = 1200.0D;

    public static final int MAX_SHOWN = 6;

    private RwrPolicy() {
    }

    public static double relativeBearing(double noseX, double noseZ,
                                         double toThreatX, double toThreatZ) {
        if (!finite(noseX) || !finite(noseZ) || !finite(toThreatX) || !finite(toThreatZ)) {
            return 0.0D;
        }
        double noseLen = Math.hypot(noseX, noseZ);
        double threatLen = Math.hypot(toThreatX, toThreatZ);
        if (noseLen < 1.0E-6D || threatLen < 1.0E-6D) {
            return 0.0D;
        }
        double cross = noseX * toThreatZ - noseZ * toThreatX;
        double dot = noseX * toThreatX + noseZ * toThreatZ;
        return Math.atan2(cross, dot);
    }

    public static double ringFraction(double distance) {
        double fullSize = fullSize(distance);
        if (!finite(fullSize) || fullSize <= CLOSE_RANGE) {
            return 0.0D;
        }
        if (fullSize >= MAX_RANGE) {
            return 1.0D;
        }
        double span = Math.log(MAX_RANGE / CLOSE_RANGE);
        return clamp01(Math.log(fullSize / CLOSE_RANGE) / span);
    }

    public static boolean isCritical(double distance) {
        double fullSize = fullSize(distance);
        return finite(fullSize) && fullSize <= CRITICAL_RANGE;
    }

    private static double fullSize(double distance) {
        return distance / amrac.physics.aircraft.SpeedScale.current();
    }

    public static boolean blinkOn(double distance, long timeMillis) {
        if (!isCritical(distance)) {
            return true;
        }
        double urgency = 1.0D - clamp01(fullSize(distance) / CRITICAL_RANGE);
        long period = (long) (400.0D - 250.0D * urgency);
        return ((timeMillis / Math.max(period, 80L)) & 1L) == 0L;
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }
}
