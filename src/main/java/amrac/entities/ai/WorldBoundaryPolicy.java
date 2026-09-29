package amrac.entities.ai;

public final class WorldBoundaryPolicy {
    public static final double WARNING_DISTANCE = 4000.0D;

    public static final double PLAYER_WARNING_DISTANCE = 3000.0D;

    public static double playerWarningDistance() {
        return PLAYER_WARNING_DISTANCE * amrac.physics.aircraft.SpeedScale.current();
    }

    public static final double TURN_BACK_SPEED = 250.0D;
    public static final double LOOKAHEAD_DISTANCE = 4000.0D;
    static final double INWARD_GOAL_DISTANCE = 8000.0D;

    private WorldBoundaryPolicy() {
    }

    public static double distanceToBoundary(double minX, double maxX,
                                            double minZ, double maxZ,
                                            double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return 0.0D;
        return Math.max(0.0D, Math.min(Math.min(x - minX, maxX - x),
            Math.min(z - minZ, maxZ - z)));
    }

    /**
     * The AI turn-back band and the player warning distance are separate constants. The turn-back
     * goal overrides every flight phase, so the band must not grow with speed, or the AI never
     * engages.
     */
    public static boolean avoidanceGoal(double minX, double maxX,
                                        double minZ, double maxZ,
                                        double x, double z,
                                        double forwardX, double forwardZ,
                                        double[] out) {
        double band = AiPilotSettings.current().borderWarningDistance;
        double length = Math.hypot(forwardX, forwardZ);
        double fx = length > 1.0E-6D ? forwardX / length : 0.0D;
        double fz = length > 1.0E-6D ? forwardZ / length : 0.0D;
        double lookahead = AiPilotSettings.current().borderLookaheadDistance;
        double projectedX = x + fx * lookahead;
        double projectedZ = z + fz * lookahead;

        boolean west = x - minX <= band || projectedX <= minX;
        boolean east = maxX - x <= band || projectedX >= maxX;
        boolean north = z - minZ <= band || projectedZ <= minZ;
        boolean south = maxZ - z <= band || projectedZ >= maxZ;
        if (!west && !east && !north && !south) return false;

        double inwardX = (west ? 1.0D : 0.0D) - (east ? 1.0D : 0.0D);
        double inwardZ = (north ? 1.0D : 0.0D) - (south ? 1.0D : 0.0D);
        if (inwardX == 0.0D && inwardZ == 0.0D) {
            inwardX = (minX + maxX) * 0.5D - x;
            inwardZ = (minZ + maxZ) * 0.5D - z;
        }
        double inwardLength = Math.hypot(inwardX, inwardZ);
        if (!(inwardLength > 1.0E-6D)) return false;
        double inward = AiPilotSettings.current().borderInwardGoalDistance;
        out[0] = x + inwardX / inwardLength * inward;
        out[1] = z + inwardZ / inwardLength * inward;
        return true;
    }
}
