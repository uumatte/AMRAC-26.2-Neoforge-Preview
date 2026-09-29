package amrac.entities.loader;

public final class LoaderPolicy {
    public static final double DETECT_RANGE = 10.0D;

    public static final double STOP_DISTANCE = 4.0D;

    public static final double SPEED = 0.14D;

    public static final double HOME_TOLERANCE = 0.4D;

    public static final int TICKS_PER_ROUND = 10;

    public static final int DRIVE_TIMEOUT_TICKS = 300;

    public static final int SCAN_INTERVAL_TICKS = 20;

    public static final double LEASH_RANGE = DETECT_RANGE * 2.0D;

    public static final int MAX_STATIONS = 8;

    public static final int ROUNDS_PER_STATION = 16;

    public static final double GRAVITY = 0.08D;

    private LoaderPolicy() {
    }

    public static boolean withinDetectRange(double distanceSquared) {
        return distanceSquared <= DETECT_RANGE * DETECT_RANGE;
    }

    public static boolean arrived(double distanceSquared) {
        return distanceSquared <= STOP_DISTANCE * STOP_DISTANCE;
    }

    public static boolean home(double distanceSquared) {
        return distanceSquared <= HOME_TOLERANCE * HOME_TOLERANCE;
    }

    public static boolean withinLeash(double distanceFromHomeSquared) {
        return distanceFromHomeSquared <= LEASH_RANGE * LEASH_RANGE;
    }
}
