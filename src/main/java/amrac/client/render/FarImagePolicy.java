package amrac.client.render;

public final class FarImagePolicy {
    public static final double DRAW_FRACTION = 0.75D;

    public static final double MIN_DRAW_RADIUS = 16.0D;

    private FarImagePolicy() {
    }

    public static double drawRadius(int renderDistanceChunks) {
        return Math.max(MIN_DRAW_RADIUS,
            renderDistanceChunks * 16.0D * DRAW_FRACTION);
    }

    public static double pull(double distance, double radius) {
        if (!(distance > radius) || !Double.isFinite(distance)
            || !(radius > 0.0D)) {
            return 1.0D;
        }
        return radius / distance;
    }
}
