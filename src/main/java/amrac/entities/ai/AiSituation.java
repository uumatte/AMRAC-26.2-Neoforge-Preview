package amrac.entities.ai;

public record AiSituation(
        AiPilotRank rank,
        boolean missionActive,
        double x, double y, double z,
        double velocityX, double velocityY, double velocityZ,
        double forwardX, double forwardZ,
        double upY, double rightY,
        double rollRate,
        boolean onGround,
        boolean powered,
        int throttle,
        int maxThrottle,
        double structuralSpeedLimit,
        double maxLoadG,
        double serviceCeiling,
        double smoothedAcceleration,
        boolean departureComplete,
        boolean hasTarget,
        boolean targetIsAircraft,
        double targetX, double targetY, double targetZ,
        double targetVelocityX, double targetVelocityY, double targetVelocityZ,
        boolean underMissileThreat,
        double boundaryMinX, double boundaryMaxX,
        double boundaryMinZ, double boundaryMaxZ,
        double stationX, double stationZ,
        double loadG) {
    public AiSituation(
            AiPilotRank rank, boolean missionActive,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            double forwardX, double forwardZ, double upY, double rightY,
            double rollRate, boolean onGround, boolean powered,
            int throttle, int maxThrottle, double structuralSpeedLimit,
            double maxLoadG, double serviceCeiling,
            double smoothedAcceleration, boolean departureComplete,
            boolean hasTarget, boolean targetIsAircraft,
            double targetX, double targetY, double targetZ,
            double targetVelocityX, double targetVelocityY, double targetVelocityZ,
            boolean underMissileThreat,
            double boundaryMinX, double boundaryMaxX,
            double boundaryMinZ, double boundaryMaxZ,
            double stationX, double stationZ) {
        this(rank, missionActive, x, y, z, velocityX, velocityY, velocityZ,
            forwardX, forwardZ, upY, rightY, rollRate, onGround, powered,
            throttle, maxThrottle, structuralSpeedLimit, maxLoadG,
            serviceCeiling, smoothedAcceleration, departureComplete,
            hasTarget, targetIsAircraft, targetX, targetY, targetZ,
            targetVelocityX, targetVelocityY, targetVelocityZ,
            underMissileThreat, boundaryMinX, boundaryMaxX,
            boundaryMinZ, boundaryMaxZ, stationX, stationZ, Double.NaN);
    }

    public double speed() {
        return Math.sqrt(velocityX * velocityX + velocityY * velocityY
            + velocityZ * velocityZ);
    }

    public double speedBlocksPerSecond() {
        return speed() * 20.0D;
    }

    public double targetRange() {
        if (!hasTarget) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = targetX - x;
        double dy = targetY - y;
        double dz = targetZ - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean climbingOut() {
        return !onGround && y < AiPilotSettings.current().minimumAltitude;
    }

    public double assignedAltitude() {
        return AiAltitudePolicy.assignedAltitude(rank.cruiseAltitude(),
            serviceCeiling);
    }
}
