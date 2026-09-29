package amrac.entities;

public final class PassengerHitboxPolicy {
    private static final double PARALLEL_EPSILON = 1.0E-12D;

    private PassengerHitboxPolicy() {
    }

    public static double segmentAabbHitFraction(
        double startX, double startY, double startZ,
        double endX, double endY, double endZ,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ) {
        if (!allFinite(startX, startY, startZ, endX, endY, endZ,
            minX, minY, minZ, maxX, maxY, maxZ) ||
            minX > maxX || minY > maxY || minZ > maxZ) {
            return Double.NaN;
        }

        double[] interval = {0.0D, 1.0D};
        if (!clipAxis(startX, endX - startX, minX, maxX, interval) ||
            !clipAxis(startY, endY - startY, minY, maxY, interval) ||
            !clipAxis(startZ, endZ - startZ, minZ, maxZ, interval)) {
            return Double.NaN;
        }
        return interval[0];
    }

    private static boolean clipAxis(double start, double delta,
                                    double minimum, double maximum,
                                    double[] interval) {
        if (Math.abs(delta) <= PARALLEL_EPSILON) {
            return start >= minimum && start <= maximum;
        }

        double first = (minimum - start) / delta;
        double second = (maximum - start) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1];
    }

    private static boolean allFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
