package amrac.display;

public record AxisBounds(double xMin, double xMax, double yMin, double yMax) {
    public static final AxisBounds AUTO =
        new AxisBounds(Double.NaN, Double.NaN, Double.NaN, Double.NaN);

    public boolean any() {
        return Double.isFinite(xMin) || Double.isFinite(xMax)
            || Double.isFinite(yMin) || Double.isFinite(yMax);
    }
}
