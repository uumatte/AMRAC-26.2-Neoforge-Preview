package amrac.runway;

import amrac.structure.StructureAxis;

public final class RunwayPolicy {
    public static final int LENGTH = 2000;

    public static final int WIDTH = 43;

    public static final int HALF_WIDTH = WIDTH / 2;

    public static final int CLEARANCE = 100;

    public static final int THRESHOLD_LENGTH = 48;

    public static final int CENTRELINE_PERIOD = 40;

    public static final int CENTRELINE_DASH = 24;

    public static final int CENTRELINE_HALF_WIDTH = 1;

    public static final int THRESHOLD_INNER = 4;
    public static final int THRESHOLD_OUTER = 16;
    public static final int THRESHOLD_PITCH = 4;

    private RunwayPolicy() {
    }

    public static void offset(int step, int lateral, int forwardX,
                              int forwardZ, int[] out) {
        StructureAxis.offset(step, lateral, forwardX, forwardZ, out);
    }

    public static boolean onCentreline(int step, int lateral) {
        return Math.abs(lateral) <= CENTRELINE_HALF_WIDTH
            && !onThreshold(step, lateral)
            && Math.floorMod(step, CENTRELINE_PERIOD) < CENTRELINE_DASH;
    }

    public static boolean onEdgeStripe(int lateral) {
        return Math.abs(lateral) == HALF_WIDTH;
    }

    public static boolean onThreshold(int step, int lateral) {
        if (step >= THRESHOLD_LENGTH && step < LENGTH - THRESHOLD_LENGTH) {
            return false;
        }
        int fromCentre = Math.abs(lateral);
        return fromCentre >= THRESHOLD_INNER && fromCentre <= THRESHOLD_OUTER
            && Math.floorMod(fromCentre - THRESHOLD_INNER, THRESHOLD_PITCH) == 0;
    }

    public static boolean painted(int step, int lateral) {
        return onCentreline(step, lateral) || onEdgeStripe(lateral)
            || onThreshold(step, lateral);
    }
}
