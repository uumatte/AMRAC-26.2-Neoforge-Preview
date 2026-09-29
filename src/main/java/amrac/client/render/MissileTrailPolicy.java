package amrac.client.render;

/**
 * Linked by the BVR Replay mod: keep this class's name, package and public members, or replays
 * crash with NoClassDefFoundError.
 */
public final class MissileTrailPolicy {
    public static final int NODE_LIFETIME_TICKS = 40;

    public static final int MAX_NODES = NODE_LIFETIME_TICKS;

    public static final int MAX_TRAILS = 16;

    public static final double MAX_SEGMENT = 100.0D;

    public static final double MIN_VISIBLE_SPEED = 0.70D;
    public static final double FULL_SPEED = 24.0D;

    public static final double MIN_ANGULAR_WIDTH = 0.00085D;

    public static final float MAX_WIDTH = 0.55F;
    public static final float MIN_WIDTH = 0.09F;
    public static final int MAX_ALPHA = 190;

    private MissileTrailPolicy() {
    }

    public static float ageFraction(int ageTicks) {
        if (!(ageTicks > 0)) {
            return 0.0F;
        }
        return Math.min(1.0F, ageTicks / (float) NODE_LIFETIME_TICKS);
    }

    public static float speedStrength(double blocksPerTick) {
        if (!Double.isFinite(blocksPerTick) || blocksPerTick <= MIN_VISIBLE_SPEED) {
            return 0.0F;
        }
        double t = (blocksPerTick - MIN_VISIBLE_SPEED)
            / (FULL_SPEED - MIN_VISIBLE_SPEED);
        return (float) Math.min(1.0D, Math.max(0.0D, t));
    }

    public static float width(int ageTicks, float strength) {
        float age = ageFraction(ageTicks);
        float grown = MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * age;
        return grown * Math.max(0.0F, Math.min(1.0F, strength));
    }

    public static int alpha(int ageTicks, float strength) {
        float remaining = 1.0F - ageFraction(ageTicks);
        float faded = remaining * remaining;
        int a = Math.round(MAX_ALPHA * faded
            * Math.max(0.0F, Math.min(1.0F, strength)));
        return Math.max(0, Math.min(255, a));
    }

    public static float widthAt(int ageTicks, float strength,
                                double cameraDistance) {
        float natural = width(ageTicks, strength);
        if (natural <= 0.0F || !(cameraDistance > 0.0D)) {
            return natural;
        }
        return (float) Math.max(natural, cameraDistance * MIN_ANGULAR_WIDTH);
    }

    public static boolean breaksRibbon(double segmentLength) {
        return !Double.isFinite(segmentLength) || segmentLength > MAX_SEGMENT;
    }
}
