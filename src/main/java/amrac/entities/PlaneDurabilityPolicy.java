package amrac.entities;

public final class PlaneDurabilityPolicy {
    public static final int DEFAULT_MAX_HEALTH = 100;

    /**
     * "A rider can't attack their own aircraft" must not be written as "same root vehicle": the
     * aircraft's root vehicle is itself, so crash damage would be refused (PlaneEntity's hurt check
     * uses the same rule).
     */
    public static boolean isRiderAttackingOwnAircraft(boolean directIsTheAircraft,
                                                      boolean sharesRootVehicle) {
        return !directIsTheAircraft && sharesRootVehicle;
    }

    public static final double MAX_TOUCHDOWN_DESCENT_RATE = 20.0D;

    public static boolean isCrash(double impactExcess, boolean verticalContact,
                                  boolean gearDown, double epsilon) {
        return isCrash(impactExcess, verticalContact, gearDown, epsilon, 0.0D);
    }

    public static boolean isCrash(double impactExcess, boolean verticalContact,
                                  boolean gearDown, double epsilon,
                                  double descentRate) {
        if (!Double.isFinite(impactExcess)) {
            return true;
        }
        if (verticalContact && descentRate > MAX_TOUCHDOWN_DESCENT_RATE) {
            return true;
        }
        return impactExcess > epsilon || (verticalContact && !gearDown);
    }

    public static final float DRY_BLAST_POWER = 2.0F;

    public static final float FULL_BLAST_POWER = 4.0F;

    public static float blastPower(double fuelLitres, double capacityLitres) {
        double fraction = 0.0D;
        if (Double.isFinite(fuelLitres) && Double.isFinite(capacityLitres)
                && capacityLitres > 0.0D && fuelLitres > 0.0D) {
            fraction = Math.min(1.0D, fuelLitres / capacityLitres);
        }
        return (float) (DRY_BLAST_POWER
            + (FULL_BLAST_POWER - DRY_BLAST_POWER) * fraction);
    }

    public static final int PREVIOUS_DEFAULT_MAX_HEALTH = 200;
    public static final int LEGACY_DEFAULT_MAX_HEALTH = 10;
    public static final float CRASH_DAMAGE_MULTIPLIER = 1.20F;

    private PlaneDurabilityPolicy() {
    }

    public static boolean usesHistoricalDefault(int storedMaxHealth) {
        return storedMaxHealth == PREVIOUS_DEFAULT_MAX_HEALTH ||
            storedMaxHealth == LEGACY_DEFAULT_MAX_HEALTH;
    }

    public static int migrateHealthToCurrentDefault(int storedHealth,
                                                     int storedMaxHealth) {
        if (!usesHistoricalDefault(storedMaxHealth)) {
            return storedHealth;
        }
        return Math.max(1, Math.round(storedHealth * DEFAULT_MAX_HEALTH /
            (float) storedMaxHealth));
    }

    public static float adjustedCrashDamage(float rawDamage, int maxHealth) {
        if (!Float.isFinite(rawDamage)) {
            return 0.0F;
        }
        float structuralHealthScale = Math.max(maxHealth, 1) /
            (float) LEGACY_DEFAULT_MAX_HEALTH;
        return Math.max(0.0F, rawDamage + 2.0F) * structuralHealthScale *
            CRASH_DAMAGE_MULTIPLIER;
    }
}
