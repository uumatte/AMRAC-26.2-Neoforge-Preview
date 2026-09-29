package amrac.physics.aircraft;

public final class AircraftPhysicsResult {
    private final Vec3d velocity;
    private final double pitchRate;
    private final double yawRate;
    private final double rollRate;
    private final double pitchStabilityRate;
    private final double yawStabilityRate;
    private final FlightState state;
    private final boolean structuralFailure;

    AircraftPhysicsResult(Vec3d velocity, double pitchRate, double yawRate,
                          double rollRate, double pitchStabilityRate,
                          double yawStabilityRate, FlightState state,
                          boolean structuralFailure) {
        this.velocity = velocity;
        this.pitchRate = pitchRate;
        this.yawRate = yawRate;
        this.rollRate = rollRate;
        this.pitchStabilityRate = pitchStabilityRate;
        this.yawStabilityRate = yawStabilityRate;
        this.state = state;
        this.structuralFailure = structuralFailure;
    }

    public Vec3d velocity() {
        return velocity;
    }

    public double pitchRate() {
        return pitchRate;
    }

    public double yawRate() {
        return yawRate;
    }

    public double rollRate() {
        return rollRate;
    }

    public double pitchStabilityRate() {
        return pitchStabilityRate;
    }

    public double yawStabilityRate() {
        return yawStabilityRate;
    }

    public FlightState state() {
        return state;
    }

    public boolean structuralFailure() {
        return structuralFailure;
    }
}
