package amrac.upgrades.shooter;

public final class MachineGunThermalPolicy {
    public static final int SERVER_TICKS_PER_SECOND = amrac.physics.TickRate.TICKS_PER_SECOND;
    public static final int CONTINUOUS_FIRE_SECONDS = 5;
    public static final int OVERHEAT_COOLDOWN_SECONDS = 5;
    public static final float HEAT_PER_PROJECTILE = 1.0F;
    public static final int PROJECTILES_TO_OVERHEAT =
        CONTINUOUS_FIRE_SECONDS * SERVER_TICKS_PER_SECOND /
            MachineGunFirePolicy.FIRE_INTERVAL_TICKS;
    public static final float MAX_HEAT =
        PROJECTILES_TO_OVERHEAT * HEAT_PER_PROJECTILE;
    public static final float OVERHEATED_COOLING_PER_TICK = MAX_HEAT /
        (OVERHEAT_COOLDOWN_SECONDS * SERVER_TICKS_PER_SECOND);
    public static final float RELEASED_COOLING_MULTIPLIER = 1.35F;
    public static final float RELEASED_COOLING_PER_TICK =
        OVERHEATED_COOLING_PER_TICK * RELEASED_COOLING_MULTIPLIER;
    private static final float COOLING_ZERO_EPSILON = 1.0E-3F;

    private MachineGunThermalPolicy() {
    }

    public static float addProjectileHeat(float heat) {
        return Math.min(MAX_HEAT, sanitizeHeat(heat) + HEAT_PER_PROJECTILE);
    }

    public static float coolWhileOverheated(float heat) {
        return subtractCooling(heat, OVERHEATED_COOLING_PER_TICK);
    }

    public static float coolWhileReleased(float heat) {
        return subtractCooling(heat, RELEASED_COOLING_PER_TICK);
    }

    public static boolean reachedOverheatLimit(float heat) {
        return sanitizeHeat(heat) >= MAX_HEAT;
    }

    public static int heatPercentage(float heat) {
        return Math.round(sanitizeHeat(heat) * 100.0F / MAX_HEAT);
    }

    public static float sanitizeHeat(float heat) {
        if (!Float.isFinite(heat)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(MAX_HEAT, heat));
    }

    private static float subtractCooling(float heat, float amount) {
        float cooled = sanitizeHeat(heat) - amount;
        return cooled <= COOLING_ZERO_EPSILON ? 0.0F : cooled;
    }
}
