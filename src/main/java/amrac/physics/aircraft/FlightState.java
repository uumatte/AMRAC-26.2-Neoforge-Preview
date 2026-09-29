package amrac.physics.aircraft;

public final class FlightState {
    private final double worldY;
    private final double atmosphericAltitude;
    private final double density;
    private final double speedOfSound;
    private final double airspeed;
    private final double mach;
    private final double angleOfAttack;
    private final double sideSlip;
    private final double dynamicPressure;
    private final double liftCoefficient;
    private final double dragCoefficient;
    private final double lift;
    private final double drag;
    private final double sideForce;
    private final double thrust;
    private final double weight;
    private final double loadFactor;
    private final double controlAuthority;
    private final double rollAuthority;
    private final double yawAuthority;
    private final double loadAvailable;
    private final double targetAngleOfAttack;
    private final double rollInertia;
    private final double pitchInertia;
    private final double authorityMargin;
    private final boolean controlSaturated;
    private final boolean stalled;
    private final boolean limiterActive;
    private final boolean groundContact;

    FlightState(Builder builder) {
        this.worldY = builder.worldY;
        this.atmosphericAltitude = builder.atmosphericAltitude;
        this.density = builder.density;
        this.speedOfSound = builder.speedOfSound;
        this.airspeed = builder.airspeed;
        this.mach = builder.mach;
        this.angleOfAttack = builder.angleOfAttack;
        this.sideSlip = builder.sideSlip;
        this.dynamicPressure = builder.dynamicPressure;
        this.liftCoefficient = builder.liftCoefficient;
        this.dragCoefficient = builder.dragCoefficient;
        this.lift = builder.lift;
        this.drag = builder.drag;
        this.sideForce = builder.sideForce;
        this.thrust = builder.thrust;
        this.weight = builder.weight;
        this.loadFactor = builder.loadFactor;
        this.controlAuthority = builder.controlAuthority;
        this.rollAuthority = builder.rollAuthority;
        this.yawAuthority = builder.yawAuthority;
        this.loadAvailable = builder.loadAvailable;
        this.targetAngleOfAttack = builder.targetAngleOfAttack;
        this.rollInertia = builder.rollInertia;
        this.pitchInertia = builder.pitchInertia;
        this.authorityMargin = builder.authorityMargin;
        this.controlSaturated = builder.controlSaturated;
        this.stalled = builder.stalled;
        this.limiterActive = builder.limiterActive;
        this.groundContact = builder.groundContact;
    }

    static Builder builder() {
        return new Builder();
    }

    public double worldY() {
        return worldY;
    }

    public double atmosphericAltitude() {
        return atmosphericAltitude;
    }

    public double density() {
        return density;
    }

    public double speedOfSound() {
        return speedOfSound;
    }

    public double airspeed() {
        return airspeed;
    }

    public double mach() {
        return mach;
    }

    public double angleOfAttack() {
        return angleOfAttack;
    }

    public double sideSlip() {
        return sideSlip;
    }

    public double dynamicPressure() {
        return dynamicPressure;
    }

    public double liftCoefficient() {
        return liftCoefficient;
    }

    public double dragCoefficient() {
        return dragCoefficient;
    }

    public double lift() {
        return lift;
    }

    public double drag() {
        return drag;
    }

    public double sideForce() {
        return sideForce;
    }

    public double thrust() {
        return thrust;
    }

    public double weight() {
        return weight;
    }

    public double loadFactor() {
        return loadFactor;
    }

    public double controlAuthority() {
        return controlAuthority;
    }

    public double rollAuthority() {
        return rollAuthority;
    }

    public double yawAuthority() {
        return yawAuthority;
    }

    public double loadAvailable() {
        return loadAvailable;
    }

    public double targetAngleOfAttack() {
        return targetAngleOfAttack;
    }

    public double rollInertia() {
        return rollInertia;
    }

    public double pitchInertia() {
        return pitchInertia;
    }

    public double authorityMargin() {
        return authorityMargin;
    }

    public boolean controlSaturated() {
        return controlSaturated;
    }

    public boolean stalled() {
        return stalled;
    }

    public boolean limiterActive() {
        return limiterActive;
    }

    public boolean groundContact() {
        return groundContact;
    }

    public double thrustToWeight() {
        return weight > 1.0E-9D ? thrust / weight : 0.0D;
    }

    public String summary() {
        return String.format(
            "y=%.0f alt=%.0fm v=%.1fm/s M=%.2f aoa=%.1f beta=%.1f " +
                "CL=%.3f Cd=%.3f L=%.0fN D=%.0fN T=%.0fN G=%.2f%s%s",
            worldY, atmosphericAltitude, airspeed, mach, angleOfAttack, sideSlip,
            liftCoefficient, dragCoefficient, lift, drag, thrust, loadFactor,
            stalled ? " STALL" : "", limiterActive ? " LIM" : "")
            + String.format(" nAvail=%.1f aTgt=%.1f auth=%.2f/%.2f/%.2f Ix=%.0f%s",
                loadAvailable, targetAngleOfAttack, controlAuthority,
                rollAuthority, yawAuthority, rollInertia,
                controlSaturated ? " SAT" : "");
    }

    @Override
    public String toString() {
        return summary();
    }

    static final class Builder {
        double worldY;
        double atmosphericAltitude;
        double density;
        double speedOfSound;
        double airspeed;
        double mach;
        double angleOfAttack;
        double sideSlip;
        double dynamicPressure;
        double liftCoefficient;
        double dragCoefficient;
        double lift;
        double drag;
        double sideForce;
        double thrust;
        double weight;
        double loadFactor;
        double controlAuthority;
        double rollAuthority;
        double yawAuthority;
        double loadAvailable;
        double targetAngleOfAttack;
        double rollInertia;
        double pitchInertia;
        double authorityMargin = 1.0D;
        boolean controlSaturated;
        boolean stalled;
        boolean limiterActive;
        boolean groundContact;

        FlightState build() {
            return new FlightState(this);
        }
    }
}
