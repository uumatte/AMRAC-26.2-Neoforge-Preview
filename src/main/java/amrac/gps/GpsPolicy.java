package amrac.gps;

public final class GpsPolicy {
    public static final double RANGE = 40000.0D;

    public static final double[] AIRBORNE_SCALES =
        {5000.0D, 10000.0D, 20000.0D};

    public static final double[] TACTICAL_SCALES =
        {5000.0D, 10000.0D, 25000.0D, 40000.0D};

    public static double[] ladderFor(boolean tactical) {
        return tactical ? TACTICAL_SCALES : AIRBORNE_SCALES;
    }

    public static int defaultScaleIndex(double[] ladder) {
        return ladder.length - 1;
    }

    public static int nextScaleIndex(int index, int stops) {
        return stops <= 0 ? 0 : Math.floorMod(index + 1, stops);
    }

    public static double scaleAt(double[] ladder, int index) {
        return ladder.length == 0 ? RANGE
            : ladder[Math.floorMod(index, ladder.length)];
    }

    private GpsPolicy() {
    }

    public static double distance(double deltaX, double deltaZ) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaZ)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.hypot(deltaX, deltaZ);
    }

    public static boolean onDial(double deltaX, double deltaZ, double range) {
        return distance(deltaX, deltaZ) <= range;
    }

    /**
     * Heading is atan2(fx, -fz) (north is -Z), shared by the cockpit and handheld GPS; work it out
     * anywhere else and north and south can mirror.
     */
    public static double headingRadians(double forwardX, double forwardZ) {
        if (!Double.isFinite(forwardX) || !Double.isFinite(forwardZ)) {
            return 0.0D;
        }
        return Math.atan2(forwardX, -forwardZ);
    }

    public static void project(double deltaX, double deltaZ, double range,
                               double dialRadiusPixels, double[] out) {
        projectRotated(deltaX, deltaZ, range, dialRadiusPixels, 0.0D, out);
    }

    public static void projectRotated(double deltaX, double deltaZ,
                                      double range, double dialRadiusPixels,
                                      double headingRadians, double[] out) {
        if (!(range > 0.0D) || !Double.isFinite(deltaX)
                || !Double.isFinite(deltaZ) || !Double.isFinite(headingRadians)) {
            out[0] = 0.0D;
            out[1] = 0.0D;
            return;
        }
        double cos = Math.cos(headingRadians);
        double sin = Math.sin(headingRadians);
        double rotatedX = deltaX * cos + deltaZ * sin;
        double rotatedZ = deltaZ * cos - deltaX * sin;
        double scale = dialRadiusPixels / range;
        out[0] = rotatedX * scale;
        out[1] = rotatedZ * scale;
    }

    public static void unproject(double pixelX, double pixelY, double range,
                                 double dialRadiusPixels, double headingRadians,
                                 double[] out) {
        if (!(dialRadiusPixels > 0.0D) || !Double.isFinite(pixelX)
                || !Double.isFinite(pixelY) || !Double.isFinite(headingRadians)) {
            out[0] = 0.0D;
            out[1] = 0.0D;
            return;
        }
        double scale = range / dialRadiusPixels;
        double x = pixelX * scale;
        double z = pixelY * scale;
        double cos = Math.cos(headingRadians);
        double sin = Math.sin(headingRadians);
        out[0] = x * cos - z * sin;
        out[1] = x * sin + z * cos;
    }

    public static double autoRange(double furthest) {
        return autoRange(furthest, AIRBORNE_SCALES);
    }

    public static double autoRange(double furthest, double[] ladder) {
        if (ladder == null || ladder.length == 0) {
            return 0.0D;
        }
        if (!Double.isFinite(furthest) || furthest <= 0.0D) {
            return ladder[0];
        }
        for (double scale : ladder) {
            if (furthest <= scale) {
                return scale;
            }
        }
        return ladder[ladder.length - 1];
    }

    public static int dialRadius(int height, int margin, int base, int minimum) {
        int room = (height / 2 - margin - 5 - 52) / 2;
        return Math.max(minimum, Math.min(base, room));
    }
}
