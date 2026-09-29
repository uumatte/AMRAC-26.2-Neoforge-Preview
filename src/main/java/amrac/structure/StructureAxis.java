package amrac.structure;

/**
 * All build kits (runway, hangar) share this forward/right-to-world conversion: the right of (fx,
 * fz) is (-fz, fx). Don't compute it in each kit.
 */
public final class StructureAxis {
    private StructureAxis() {
    }

    public static void offset(int step, int lateral, int forwardX,
                              int forwardZ, int[] out) {
        out[0] = step * forwardX + lateral * -forwardZ;
        out[1] = step * forwardZ + lateral * forwardX;
    }

    public static void bounds(int originX, int originY, int originZ,
                              int forwardX, int forwardZ, int length,
                              int halfWidth, int height, int[] out) {
        int[] corner = new int[2];
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int step : new int[] {0, length - 1}) {
            for (int lateral : new int[] {-halfWidth, halfWidth}) {
                offset(step, lateral, forwardX, forwardZ, corner);
                minX = Math.min(minX, originX + corner[0]);
                maxX = Math.max(maxX, originX + corner[0]);
                minZ = Math.min(minZ, originZ + corner[1]);
                maxZ = Math.max(maxZ, originZ + corner[1]);
            }
        }
        out[0] = minX;
        out[1] = originY;
        out[2] = minZ;
        out[3] = maxX;
        out[4] = originY + height - 1;
        out[5] = maxZ;
    }
}
