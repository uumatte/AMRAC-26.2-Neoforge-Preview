package amrac.refuel;

public final class RefuelPolicy {
    public static final double REACH = 10.0D;

    public static final int TRANSFER_TICKS = 60;

    public static final int EXTEND_TICKS = 10;

    public static final int BARREL_LITRES = 1000;

    public static final int MIN_BARRELS = 1;
    public static final int MAX_BARRELS = 64;
    public static final int DEFAULT_BARRELS = 4;

    public static final double PARKED_SPEED = 0.02D;

    public static boolean parked(double dx, double dz) {
        return dx * dx + dz * dz <= PARKED_SPEED * PARKED_SPEED;
    }

    public static final double MIN_USEFUL_FRACTION = 0.5D;

    public static final int SCAN_INTERVAL_TICKS = 10;

    public static final int COOLDOWN_TICKS = 20 * 120;

    private RefuelPolicy() {
    }

    public static int clampBarrels(int barrels) {
        return Math.max(MIN_BARRELS, Math.min(MAX_BARRELS, barrels));
    }

    public static int barrelsDueBy(int elapsed, int planned) {
        if (planned <= 0 || elapsed <= 0) {
            return 0;
        }
        if (elapsed >= TRANSFER_TICKS) {
            return planned;
        }
        return Math.min(planned, (int) ((long) elapsed * planned / TRANSFER_TICKS));
    }

    public static int plannedBarrels(int requested, int stocked,
                                     double litresToCapacity) {
        if (requested <= 0 || stocked <= 0 || !worthPumping(litresToCapacity)) {
            return 0;
        }
        int roomFor = (int) Math.ceil(litresToCapacity / BARREL_LITRES);
        return Math.max(0, Math.min(Math.min(requested, stocked), roomFor));
    }

    public static boolean worthPumping(double litresToCapacity) {
        return litresToCapacity >= BARREL_LITRES * MIN_USEFUL_FRACTION;
    }

    public static boolean inReach(double distanceSquared) {
        return distanceSquared <= REACH * REACH;
    }

    public static float extension(float age) {
        if (age <= 0.0F) {
            return 0.0F;
        }
        float t = Math.min(1.0F, age / EXTEND_TICKS);
        return t * t * (3.0F - 2.0F * t);
    }
}
