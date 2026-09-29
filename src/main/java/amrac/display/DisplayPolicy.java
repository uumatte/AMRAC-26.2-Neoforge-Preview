package amrac.display;

public final class DisplayPolicy {
    public static final int SCREEN_SIZE = 5;
    public static final int SCREEN_HALF = SCREEN_SIZE / 2;

    public static final int CONSOLE_LENGTH = 3;
    public static final int CONSOLE_WIDTH = 2;
    public static final int CONSOLE_HEIGHT = 2;

    public static final int CONNECT_RANGE = 16;

    public static final double PANEL_PROUD = 0.02D;

    public static double panelFaceOffset(double thicknessPixels) {
        return -(0.5D - thicknessPixels / 16.0D) + PANEL_PROUD;
    }

    private DisplayPolicy() {
    }

    public static void screenBasis(int faceX, int faceY, int faceZ, int[] out) {
        if (faceY != 0) {
            out[0] = 1;
            out[1] = 0;
            out[2] = 0;
            out[3] = 0;
            out[4] = 0;
            out[5] = 1;
            return;
        }
        out[0] = -faceZ;
        out[1] = 0;
        out[2] = faceX;
        out[3] = 0;
        out[4] = 1;
        out[5] = 0;
    }

    public static void screenOffset(int[] basis, int across, int up, int[] out) {
        out[0] = across * basis[0] + up * basis[3];
        out[1] = across * basis[1] + up * basis[4];
        out[2] = across * basis[2] + up * basis[5];
    }

    public static void consoleOffset(int facingX, int facingZ, int along,
                                     int deep, int up, int[] out) {
        out[0] = along * -facingZ + deep * -facingX;
        out[1] = up;
        out[2] = along * facingX + deep * -facingZ;
    }
}
