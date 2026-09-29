package amrac.upgrades.shooter;

public final class MachineGunFirePolicy {
    public static final int FIRE_INTERVAL_TICKS = 1;
    public static final int SERVER_TICKS_PER_SECOND = amrac.physics.TickRate.TICKS_PER_SECOND;
    public static final int SERVER_TICKS_PER_MINUTE = SERVER_TICKS_PER_SECOND * 60;
    public static final int PROJECTILES_PER_MINUTE =
        SERVER_TICKS_PER_MINUTE / FIRE_INTERVAL_TICKS;
    public static final float DAMAGE_PER_PROJECTILE = 15.0F;

    public static final double BIPLANE_MUZZLE_VELOCITY_MPS = 745.0D;
    public static final double VULCAN_MUZZLE_VELOCITY_MPS = 1050.0D;

    public static final double BIPLANE_PROJECTILE_SPEED =
        blocksPerTick(BIPLANE_MUZZLE_VELOCITY_MPS);
    public static final double VULCAN_PROJECTILE_SPEED =
        blocksPerTick(VULCAN_MUZZLE_VELOCITY_MPS);

    private MachineGunFirePolicy() {
    }

    public static double blocksPerTick(double metresPerSecond) {
        if (!Double.isFinite(metresPerSecond) || metresPerSecond <= 0.0D) {
            return 0.0D;
        }
        return metresPerSecond / SERVER_TICKS_PER_SECOND;
    }

    public static double muzzleSpeed(double stated) {
        return sanitizeProjectileSpeed(stated, BIPLANE_PROJECTILE_SPEED)
            * amrac.physics.aircraft.SpeedScale.current();
    }

    public static double sanitizeProjectileSpeed(double speed, double fallback) {
        if (Double.isFinite(speed) && speed > 0.0D) {
            return speed;
        }
        return Double.isFinite(fallback) && fallback > 0.0D ? fallback : 0.0D;
    }

    public static boolean canFire(int cooldownTicks) {
        return cooldownTicks <= 0;
    }

    public static int resetCooldown() {
        return FIRE_INTERVAL_TICKS - 1;
    }

    public static boolean hasUsableAmmo(boolean creativeMode, int ammoCount) {
        return creativeMode || ammoCount > 0;
    }

    public static boolean shouldConsumeAmmo(boolean creativeMode) {
        return !creativeMode;
    }
}
