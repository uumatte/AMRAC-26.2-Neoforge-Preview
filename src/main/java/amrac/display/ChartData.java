package amrac.display;

public record ChartData(String title, String xLabel, String yLabel,
                        double[] xs, double[] ys, String note,
                        double xMin, double xMax, double yMin, double yMax) {
    public ChartData(String title, String xLabel, String yLabel,
                     double[] xs, double[] ys, String note) {
        this(title, xLabel, yLabel, xs, ys, note,
            Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public ChartData pinned(double xMin, double xMax,
                            double yMin, double yMax) {
        return new ChartData(title, xLabel, yLabel, xs, ys, note,
            xMin, xMax, yMin, yMax);
    }

    public static final ChartData EMPTY =
        new ChartData("", "", "", new double[0], new double[0], "");

    public int size() {
        return Math.min(xs.length, ys.length);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public void xAxis(double[] out) {
        bounds(xs, xMin, xMax, out);
    }

    public void yAxis(double[] out) {
        bounds(ys, yMin, yMax, out);
    }

    private void bounds(double[] values, double low, double high,
                        double[] out) {
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (int i = 0; i < size(); i++) {
            double value = values[i];
            if (!Double.isFinite(value)) {
                continue;
            }
            lowest = Math.min(lowest, value);
            highest = Math.max(highest, value);
        }
        if (lowest > highest) {
            ChartPolicy.axis(0.0D, 1.0D, low, high, out);
            return;
        }
        ChartPolicy.axis(lowest, highest, low, high, out);
    }
}
