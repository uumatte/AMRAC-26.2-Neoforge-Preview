package amrac.weapons;

public final class MissileLifecyclePolicy {
    public static final double REALISE_DISTANCE = 1000.0D;

    public static final double VIRTUALISE_DISTANCE = 1400.0D;

    private MissileLifecyclePolicy() {
    }

    public static boolean shouldVirtualise(double relevantDistance,
                                           boolean terrainLoaded) {
        if (!Double.isFinite(relevantDistance)) {
            return true;
        }
        return !terrainLoaded || relevantDistance > VIRTUALISE_DISTANCE;
    }

    public static boolean shouldRealise(double relevantDistance,
                                        boolean terrainLoaded) {
        return terrainLoaded && Double.isFinite(relevantDistance) &&
            relevantDistance < REALISE_DISTANCE;
    }

    public static double relevantDistance(double nearestPlayerDistance,
                                          double targetDistance) {
        double nearest = Double.isFinite(nearestPlayerDistance)
            ? nearestPlayerDistance : Double.MAX_VALUE;
        double target = Double.isFinite(targetDistance)
            ? targetDistance : Double.MAX_VALUE;
        return Math.min(nearest, target);
    }
}
