package amrac.weapons;

public final class BombingPolicy {
    public static final int SERVER_TICKS_PER_SECOND = amrac.physics.TickRate.TICKS_PER_SECOND;
    public static final int BOMBS_PER_SALVO = 2;
    public static final int INTER_BOMB_DELAY_TICKS = 3;
    public static final int COOLDOWN_SECONDS = 10;
    public static final int COOLDOWN_TICKS =
        COOLDOWN_SECONDS * SERVER_TICKS_PER_SECOND;
    public static final double INHERITED_AIRCRAFT_VELOCITY_MULTIPLIER =
        1.15D;
    public static final double INITIAL_DROP_SPEED_BLOCKS_PER_SECOND = 5.0D;
    public static final double INITIAL_DROP_SPEED_BLOCKS_PER_TICK =
        INITIAL_DROP_SPEED_BLOCKS_PER_SECOND / SERVER_TICKS_PER_SECOND;
    public static final double GRAVITY_PER_TICK = 0.04D;
    public static final float EXPLOSION_POWER = 4.0F;
    public static final double AIRFRAME_ROTATION_PIVOT_HEIGHT = 0.875D;
    public static final double RELEASE_LOCAL_Y = -0.35D;

    private BombingPolicy() {
    }

    public static boolean isWeaponSystemAvailable(boolean hasBombRack) {
        return hasBombRack;
    }

    public static int requiredTnt(boolean creativeMode) {
        return creativeMode ? 0 : BOMBS_PER_SALVO;
    }

    public static boolean hasRequiredTnt(boolean creativeMode, int tntCount) {
        return tntCount >= requiredTnt(creativeMode);
    }

    public static boolean isSourceAircraftOrPassenger(int sourcePlaneId,
                                                       int entityId,
                                                       int rootVehicleId) {
        return sourcePlaneId >= 0 &&
            (entityId == sourcePlaneId || rootVehicleId == sourcePlaneId);
    }

    public static int remainingCooldownTicks(long gameTime,
                                             long cooldownEndGameTime) {
        long remaining = cooldownEndGameTime - gameTime;
        if (remaining <= 0L) {
            return 0;
        }
        return (int) Math.min(remaining, Integer.MAX_VALUE);
    }
}
