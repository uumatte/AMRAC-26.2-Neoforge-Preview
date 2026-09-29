package amrac.physics.aircraft;

public final class CurveInterpolator {
    private final double[] inputs;
    private final double[] outputs;

    private CurveInterpolator(double[] inputs, double[] outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public static CurveInterpolator of(double[][] points) {
        if (points == null || points.length == 0) {
            throw new IllegalArgumentException("a curve needs at least one point");
        }
        double[][] sorted = new double[points.length][];
        for (int i = 0; i < points.length; i++) {
            if (points[i] == null || points[i].length != 2 ||
                !Double.isFinite(points[i][0]) || !Double.isFinite(points[i][1])) {
                throw new IllegalArgumentException(
                    "curve point " + i + " is not a finite [x, y] pair");
            }
            sorted[i] = new double[] {points[i][0], points[i][1]};
        }
        java.util.Arrays.sort(sorted, (a, b) -> Double.compare(a[0], b[0]));

        int unique = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i][0] != sorted[i - 1][0]) {
                sorted[unique++] = sorted[i];
            }
        }
        double[] inputs = new double[unique];
        double[] outputs = new double[unique];
        for (int i = 0; i < unique; i++) {
            inputs[i] = sorted[i][0];
            outputs[i] = sorted[i][1];
        }
        return new CurveInterpolator(inputs, outputs);
    }

    public double interpolate(double input) {
        if (!Double.isFinite(input)) {
            return outputs[0];
        }
        if (input <= inputs[0]) {
            return outputs[0];
        }
        int last = inputs.length - 1;
        if (input >= inputs[last]) {
            return outputs[last];
        }

        int low = 0;
        int high = last;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (inputs[middle] <= input) {
                low = middle;
            } else {
                high = middle;
            }
        }
        double span = inputs[high] - inputs[low];
        if (span <= 0.0D) {
            return outputs[low];
        }
        double fraction = (input - inputs[low]) / span;
        return outputs[low] + (outputs[high] - outputs[low]) * fraction;
    }

    public double maximumOutput() {
        double maximum = outputs[0];
        for (double output : outputs) {
            maximum = Math.max(maximum, output);
        }
        return maximum;
    }

    public double inputOfMaximumOutput() {
        int best = 0;
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > outputs[best]) {
                best = i;
            }
        }
        return inputs[best];
    }

    public int size() {
        return inputs.length;
    }

    public double inputAt(int index) {
        return inputs[index];
    }

    public double outputAt(int index) {
        return outputs[index];
    }

    public double[][] points() {
        double[][] points = new double[inputs.length][];
        for (int i = 0; i < inputs.length; i++) {
            points[i] = new double[] {inputs[i], outputs[i]};
        }
        return points;
    }
}
