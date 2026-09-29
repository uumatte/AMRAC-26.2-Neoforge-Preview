package amrac.display;

public final class ChartPolicy {
    public static final int TARGET_TICKS = 5;

    public static final double PADDING = 0.05D;

    private ChartPolicy() {
    }

    public static double niceStep(double rawStep) {
        if (!(rawStep > 0.0D) || !Double.isFinite(rawStep)) {
            return 1.0D;
        }
        double magnitude = Math.pow(10.0D, Math.floor(Math.log10(rawStep)));
        double normalised = rawStep / magnitude;
        double nice = normalised <= 1.0D ? 1.0D
            : normalised <= 2.0D ? 2.0D
            : normalised <= 5.0D ? 5.0D : 10.0D;
        return nice * magnitude;
    }

    public static boolean clipSegment(double x0, double y0, double x1, double y1,
                                      double left, double top,
                                      double right, double bottom,
                                      double[] out) {
        if (!Double.isFinite(x0) || !Double.isFinite(y0)
                || !Double.isFinite(x1) || !Double.isFinite(y1)) {
            return false;
        }
        double dx = x1 - x0;
        double dy = y1 - y0;
        double enter = 0.0D;
        double leave = 1.0D;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0 - left, right - x0, y0 - top, bottom - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0D) {
                if (q[i] < 0.0D) {
                    return false;
                }
                continue;
            }
            double at = q[i] / p[i];
            if (p[i] < 0.0D) {
                enter = Math.max(enter, at);
            } else {
                leave = Math.min(leave, at);
            }
        }
        if (enter > leave) {
            return false;
        }
        out[0] = x0 + enter * dx;
        out[1] = y0 + enter * dy;
        out[2] = x0 + leave * dx;
        out[3] = y0 + leave * dy;
        return true;
    }

    public static void axis(double lowest, double highest,
                            double overrideLow, double overrideHigh,
                            double[] out) {
        double low = Double.isFinite(overrideLow) ? overrideLow : lowest;
        double high = Double.isFinite(overrideHigh) ? overrideHigh : highest;
        if (low > high) {
            double swap = low;
            low = high;
            high = swap;
        }
        axis(low, high, out);
        if (Double.isFinite(overrideLow)) {
            out[0] = low;
        }
        if (Double.isFinite(overrideHigh)) {
            out[1] = high;
        }
    }

    public static void axis(double lowest, double highest, double[] out) {
        if (!Double.isFinite(lowest) || !Double.isFinite(highest)) {
            out[0] = 0.0D;
            out[1] = 1.0D;
            out[2] = 1.0D;
            return;
        }
        if (highest < lowest) {
            double swap = lowest;
            lowest = highest;
            highest = swap;
        }
        boolean neverNegative = lowest >= 0.0D;
        boolean neverPositive = highest <= 0.0D;

        double span = highest - lowest;
        if (span <= 0.0D) {
            double size = Math.abs(highest) > 1.0E-9D
                ? Math.abs(highest) * 0.1D : 1.0D;
            lowest = highest - size;
            highest = highest + size;
        } else {
            lowest -= span * PADDING;
            highest += span * PADDING;

            if (neverNegative) {
                lowest = Math.max(lowest, 0.0D);
            }
            if (neverPositive) {
                highest = Math.min(highest, 0.0D);
            }
        }
        span = highest - lowest;

        double step = niceStep(span / TARGET_TICKS);
        out[0] = Math.floor(lowest / step) * step;
        out[1] = Math.ceil(highest / step) * step;
        out[2] = step;
    }

    public static double fraction(double value, double min, double max) {
        double span = max - min;
        return span > 0.0D ? (value - min) / span : 0.5D;
    }

    public static int labelDecimals(double step) {
        if (!(step > 0.0D) || !Double.isFinite(step)) {
            return 0;
        }
        int decimals = (int) Math.ceil(-Math.log10(step));
        return Math.max(0, Math.min(4, decimals));
    }
}
