package amrac.entities;

public final class AircraftLifecyclePolicy {
    public static final double REALISE_DISTANCE = 3000.0D;

    public static final double VIRTUALISE_DISTANCE = 4000.0D;

    public static final double VIRTUAL_ALTITUDE_FLOOR = 800.0D;

    public static final double VIRTUAL_LOST_ALTITUDE = 0.0D;

    private AircraftLifecyclePolicy() {
    }

    public static double virtualiseDistance(int viewDistanceChunks,
                                            int simulationDistanceChunks) {
        int loadedChunks = Math.max(3,
            Math.min(viewDistanceChunks, simulationDistanceChunks));
        return (loadedChunks - 1) * 16.0D;
    }

    public static double realiseDistance(int viewDistanceChunks,
                                         int simulationDistanceChunks) {
        int loadedChunks = Math.max(3,
            Math.min(viewDistanceChunks, simulationDistanceChunks));
        return (loadedChunks - 2) * 16.0D;
    }

    public static double horizontalLoadDistance(double deltaX, double deltaZ) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaZ)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(Math.abs(deltaX), Math.abs(deltaZ));
    }

    public static boolean shouldVirtualise(double nearestPlayerDistance,
                                           double altitude,
                                           boolean playerAboard,
                                           boolean needsGround,
                                           boolean climbingOut) {
        if (playerAboard) {
            return false;
        }
        if (needsGround && !climbingOut) {
            return false;
        }
        if (!climbingOut && !(altitude > VIRTUAL_ALTITUDE_FLOOR)) {
            return false;
        }
        if (Double.isNaN(nearestPlayerDistance)) {
            return true;
        }
        return nearestPlayerDistance > VIRTUALISE_DISTANCE;
    }

    public static boolean shouldVirtualise(double nearestPlayerDistance,
                                           double altitude,
                                           boolean playerAboard,
                                           boolean needsGround,
                                           boolean climbingOut,
                                           double virtualiseDistance) {
        if (playerAboard || (needsGround && !climbingOut)) return false;
        if (!climbingOut && !(altitude > VIRTUAL_ALTITUDE_FLOOR)) return false;
        if (Double.isNaN(nearestPlayerDistance)) return true;
        return nearestPlayerDistance > virtualiseDistance;
    }

    public static boolean shouldRealise(double nearestPlayerDistance,
                                        double altitude,
                                        boolean playerAboard,
                                        boolean needsGround,
                                        boolean climbingOut,
                                        boolean terrainLoaded) {
        if (!terrainLoaded) {
            return false;
        }
        if (playerAboard || needsGround) {
            return true;
        }
        if (!climbingOut && !(altitude > VIRTUAL_ALTITUDE_FLOOR)) {
            return true;
        }
        if (Double.isNaN(nearestPlayerDistance)) {
            return false;
        }
        return nearestPlayerDistance < REALISE_DISTANCE;
    }

    public static boolean shouldRealise(double nearestPlayerDistance,
                                        double altitude,
                                        boolean playerAboard,
                                        boolean needsGround,
                                        boolean climbingOut,
                                        boolean terrainLoaded,
                                        double realiseDistance) {
        if (!terrainLoaded) return false;
        if (playerAboard || needsGround) return true;
        if (!climbingOut && !(altitude > VIRTUAL_ALTITUDE_FLOOR)) return true;
        if (Double.isNaN(nearestPlayerDistance)) return false;
        return nearestPlayerDistance < realiseDistance;
    }

    public static boolean mustHoldAboveFloor(double altitude,
                                             double verticalSpeed) {
        if (!Double.isFinite(altitude) || !Double.isFinite(verticalSpeed)) {
            return false;
        }
        return altitude < VIRTUAL_ALTITUDE_FLOOR && verticalSpeed < 0.0D;
    }

    public static double heldAltitude(double altitude, double previousAltitude) {
        if (!Double.isFinite(previousAltitude)) {
            return altitude;
        }
        return Math.max(altitude,
            Math.min(VIRTUAL_ALTITUDE_FLOOR, previousAltitude));
    }

    public static boolean departing(boolean hasDestination, double altitude,
                                    boolean onGround) {
        if (!hasDestination || !Double.isFinite(altitude)) {
            return false;
        }
        return onGround || altitude <= VIRTUAL_ALTITUDE_FLOOR;
    }
}
