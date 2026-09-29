package amrac.client.render;

public final class LodPolicy {
    public static final int FULL = 0;
    public static final int COARSEST = 3;

    public static final double LOD1_FROM = 80.0D;
    public static final double LOD2_FROM = 250.0D;
    public static final double LOD3_FROM = 700.0D;

    public static final int STORES_HIDDEN_FROM_LEVEL = COARSEST;

    private LodPolicy() {
    }

    public static int level(double distance, boolean shadowPass) {
        int level;
        if (!(distance >= LOD1_FROM)) {
            level = FULL;
        } else if (distance < LOD2_FROM) {
            level = 1;
        } else if (distance < LOD3_FROM) {
            level = 2;
        } else {
            level = COARSEST;
        }
        if (shadowPass) {
            level = Math.min(COARSEST, level + 1);
        }
        return level;
    }

    public static boolean drawsStores(int level) {
        return level < STORES_HIDDEN_FROM_LEVEL;
    }
}
