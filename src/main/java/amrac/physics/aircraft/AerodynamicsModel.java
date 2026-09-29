package amrac.physics.aircraft;

public final class AerodynamicsModel {
    public static final double MINIMUM_FLOW_SPEED = 1.0E-4D;

    private final AircraftPhysicsProfile profile;

    public AerodynamicsModel(AircraftPhysicsProfile profile) {
        this.profile = profile;
    }

    public static double angleOfAttackDegrees(Vec3d velocity, Vec3d forward, Vec3d up) {
        double forwardSpeed = velocity.dot(forward);
        double normalSpeed = velocity.dot(up);
        if (Math.hypot(forwardSpeed, normalSpeed) < MINIMUM_FLOW_SPEED) {
            return 0.0D;
        }
        return Math.toDegrees(Math.atan2(-normalSpeed, forwardSpeed));
    }

    public static double sideSlipDegrees(Vec3d velocity, Vec3d right) {
        double speed = velocity.length();
        if (speed < MINIMUM_FLOW_SPEED) {
            return 0.0D;
        }
        double ratio = velocity.dot(right) / speed;
        return Math.toDegrees(Math.asin(clamp(ratio, -1.0D, 1.0D)));
    }

    public static double dynamicPressure(double density, double airspeed) {
        return 0.5D * density * airspeed * airspeed;
    }

    public double liftCoefficient(double angleOfAttackDegrees) {
        return profile.liftCoefficientCurve().interpolate(angleOfAttackDegrees);
    }

    public double dragCoefficient(double angleOfAttackDegrees) {
        return Math.max(0.0D,
            profile.dragCoefficientCurve().interpolate(angleOfAttackDegrees));
    }

    public double waveDragCoefficient(double mach) {
        return Math.max(0.0D,
            profile.waveDragMachCurve().interpolate(Math.max(mach, 0.0D)));
    }

    public double totalDragCoefficient(double angleOfAttackDegrees, double mach,
                                       double gearPosition,
                                       double speedBrakePosition) {
        return dragCoefficient(angleOfAttackDegrees) +
            waveDragCoefficient(mach) +
            profile.gearDragCoefficient() * clamp(gearPosition, 0.0D, 1.0D) +
            profile.speedBrakeDragCoefficient() *
                clamp(speedBrakePosition, 0.0D, 1.0D);
    }

    public double lift(double density, double wingFlowSpeed,
                       double angleOfAttackDegrees) {
        return dynamicPressure(density, wingFlowSpeed) * profile.wingArea() *
            liftCoefficient(angleOfAttackDegrees);
    }

    public double drag(double density, double airspeed, double angleOfAttackDegrees,
                       double mach, double gearPosition, double speedBrakePosition) {
        return dynamicPressure(density, airspeed) * profile.wingArea() *
            totalDragCoefficient(angleOfAttackDegrees, mach, gearPosition,
                speedBrakePosition);
    }

    public double sideForce(double density, double airspeed, double sideSlipDegrees) {
        return -dynamicPressure(density, airspeed) * profile.wingArea() *
            profile.sideForceCoefficient() * Math.sin(Math.toRadians(sideSlipDegrees));
    }

    public double sideSlipDragCoefficient(double sideSlipDegrees) {
        double sine = Math.sin(Math.toRadians(sideSlipDegrees));
        return profile.sideSlipDragCoefficient() * sine * sine;
    }

    public double postStallAuthority(double angleOfAttackDegrees, double floor) {
        double share = profile.postStall().share(angleOfAttackDegrees,
            profile.stallAngleOfAttack());
        if (share <= 0.0D) {
            return 1.0D;
        }
        return 1.0D + (clamp(floor, 0.0D, 1.0D) - 1.0D) * share;
    }

    public boolean isStalled(double angleOfAttackDegrees) {
        return Math.abs(angleOfAttackDegrees) > profile.stallAngleOfAttack();
    }

    public AircraftPhysicsProfile profile() {
        return profile;
    }

    static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return value < minimum ? minimum : Math.min(value, maximum);
    }
}
