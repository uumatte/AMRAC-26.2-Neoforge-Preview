package amrac.entities;

/**
 * Afterburner has no state of its own: it is derived from the throttle (MAX_THROTTLE + 1 is the
 * gate, see PlaneEntity.setThrottle), on client and server alike from the synced throttle. Change
 * the throttle encoding and update both.
 */
public final class AfterburnerPolicy {
    public static final int ENGAGE_THROTTLE = 95;
    public static final int DISENGAGE_THROTTLE = 88;
    public static final float SPOOL_UP_RATE = 1.0F / 25.0F;
    public static final float SPOOL_DOWN_RATE = 1.0F / 16.0F;
    public static final double FUEL_MULTIPLIER = 3.0D;
    public static final float MINIMUM_EFFECTIVE_SPOOL = 1.0E-3F;

    private AfterburnerPolicy() {
    }

    public static boolean commanded(boolean engineRunning, int throttle,
                                    boolean wasEngaged) {
        if (!engineRunning) {
            return false;
        }
        if (wasEngaged) {
            return throttle >= DISENGAGE_THROTTLE;
        }
        return throttle >= ENGAGE_THROTTLE;
    }

    public static boolean commandedFromDetent(boolean engineRunning,
                                              boolean detentSelected) {
        return engineRunning && detentSelected;
    }

    public static float approachSpool(float currentSpool, boolean commanded) {
        float spool = Float.isFinite(currentSpool)
            ? clamp(currentSpool, 0.0F, 1.0F) : 0.0F;
        float target = commanded ? 1.0F : 0.0F;
        float rate = commanded ? SPOOL_UP_RATE : SPOOL_DOWN_RATE;
        if (target > spool) {
            spool = Math.min(target, spool + rate);
        } else {
            spool = Math.max(target, spool - rate);
        }
        return clamp(spool, 0.0F, 1.0F);
    }

    public static double fuelMultiplier(float spool) {
        return 1.0D + (FUEL_MULTIPLIER - 1.0D) * sanitizeSpool(spool);
    }

    public static double maxSpeed(double drySpeed, double wetSpeed, float spool) {
        double dry = Double.isFinite(drySpeed) ? drySpeed : 0.0D;
        double wet = Double.isFinite(wetSpeed) ? wetSpeed : dry;
        return dry + (wet - dry) * sanitizeSpool(spool);
    }

    public static boolean isEffective(float spool) {
        return sanitizeSpool(spool) > MINIMUM_EFFECTIVE_SPOOL;
    }

    public static float sanitizeSpool(float spool) {
        return Float.isFinite(spool) ? clamp(spool, 0.0F, 1.0F) : 0.0F;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
