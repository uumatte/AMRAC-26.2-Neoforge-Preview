package amrac.hangar;

import amrac.runway.RunwayPolicy;
import amrac.structure.StructureAxis;

public final class HangarPolicy {
    public static final int APRON_LENGTH = 20;
    public static final int HALL_LENGTH = 30;
    public static final int TOTAL_LENGTH = APRON_LENGTH + HALL_LENGTH + APRON_LENGTH;

    public static final int WIDTH = RunwayPolicy.WIDTH;
    public static final int HALF_WIDTH = WIDTH / 2;

    public static final int EAVES_HEIGHT = 10;

    public static final int VAULT_RISE = 6;

    public static final int RIDGE_HEIGHT = EAVES_HEIGHT + VAULT_RISE;

    public static final int TOTAL_HEIGHT = RIDGE_HEIGHT + 1;

    public static final int HALL_START = APRON_LENGTH;
    public static final int HALL_END = APRON_LENGTH + HALL_LENGTH - 1;

    public enum Piece {
        FLOOR,
        MARKING,
        WALL,
        ROOF,
        TRUSS,
        PLINTH,
        GLASS,
        FRAME,
        LIGHT,
        CLEAR
    }

    public static final int LIGHT_SPACING = 6;
    public static final int LIGHT_OFFSET = HALF_WIDTH / 2;

    public static final int TRUSS_SPACING = 6;

    public static final int PLINTH_HEIGHT = 2;

    public static final int GLAZING_HEIGHT = 3;

    private HangarPolicy() {
    }

    public static boolean insideHall(int step) {
        return step >= HALL_START && step <= HALL_END;
    }

    public static int roofHeight(int lateral) {
        if (HALF_WIDTH <= 0) {
            return EAVES_HEIGHT;
        }
        double across = (double) lateral / HALF_WIDTH;
        double rise = VAULT_RISE * Math.sqrt(Math.max(0.0D, 1.0D - across * across));
        return EAVES_HEIGHT + (int) Math.round(rise);
    }

    public static int roofBottom(int lateral) {
        int side = Math.abs(lateral);
        if (side >= HALF_WIDTH) {
            return roofHeight(lateral);
        }
        return Math.min(roofHeight(lateral), roofHeight(side + 1) + 1);
    }

    public static boolean trussAt(int step) {
        return insideHall(step)
            && Math.floorMod(step - HALL_START, TRUSS_SPACING) == 0;
    }

    public static boolean atMouth(int step) {
        return step == HALL_START || step == HALL_END;
    }

    public static Piece piece(int step, int lateral, int up) {
        boolean inside = insideHall(step);
        int side = Math.abs(lateral);

        if (up == 0) {
            if (!inside) {
                if (side == HALF_WIDTH && lampAt(step)) {
                    return Piece.LIGHT;
                }
                return side <= 1 ? Piece.MARKING : Piece.FLOOR;
            }
            return side == LIGHT_OFFSET && lampAt(step)
                ? Piece.LIGHT : Piece.FLOOR;
        }

        if (!inside) {
            return Piece.CLEAR;
        }

        int roof = roofHeight(lateral);
        int springing = roofBottom(lateral);
        boolean inVault = up >= springing && up <= roof;
        if (up > roof) {
            return Piece.CLEAR;
        }

        if (atMouth(step) && (inVault || side == HALF_WIDTH)) {
            return Piece.FRAME;
        }

        if (inVault) {
            if (trussAt(step)) {
                return Piece.TRUSS;
            }
            return side == LIGHT_OFFSET && lampAt(step)
                ? Piece.LIGHT : Piece.ROOF;
        }

        if (side == HALF_WIDTH) {
            if (trussAt(step)) {
                return Piece.TRUSS;
            }
            if (up <= PLINTH_HEIGHT) {
                return Piece.PLINTH;
            }
            if (up > EAVES_HEIGHT - GLAZING_HEIGHT && up <= EAVES_HEIGHT) {
                return Piece.GLASS;
            }
            return Piece.WALL;
        }
        return Piece.CLEAR;
    }

    public static boolean lampAt(int step) {
        return Math.floorMod(step, LIGHT_SPACING) == 0;
    }

    public static void offset(int step, int lateral, int forwardX,
                              int forwardZ, int[] out) {
        StructureAxis.offset(step, lateral, forwardX, forwardZ, out);
    }

    public static int interiorWidth() {
        return WIDTH - 2;
    }
}
