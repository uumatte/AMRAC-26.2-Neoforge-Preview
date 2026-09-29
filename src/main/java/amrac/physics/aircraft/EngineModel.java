package amrac.physics.aircraft;

public final class EngineModel {
    private final AircraftPhysicsProfile profile;

    public EngineModel(AircraftPhysicsProfile profile) {
        this.profile = profile;
    }

    public double staticThrust(double atmosphericAltitude) {
        return Math.max(0.0D,
            profile.thrustAltitudeCurve().interpolate(atmosphericAltitude));
    }

    public double maximumThrust(double atmosphericAltitude, double mach) {
        return staticThrust(atmosphericAltitude) * ramFactor(mach);
    }

    public double ramFactor(double mach) {
        double factor = profile.thrustMachCurve()
            .interpolate(Math.max(mach, 0.0D));
        return Double.isFinite(factor) && factor > 0.0D ? factor : 1.0D;
    }

    public double thrust(double atmosphericAltitude, double mach, double throttle,
                         double afterburnerSpool, boolean engineRunning) {
        if (!engineRunning) {
            return 0.0D;
        }
        double commanded = clampUnit(throttle);
        double spool = clampUnit(afterburnerSpool);
        double burner = 1.0D +
            (profile.afterburnerThrustMultiplier() - 1.0D) * spool;
        double lever = profile.fuel().thrustFraction(commanded);
        double thrust = maximumThrust(atmosphericAltitude, mach) * lever * burner;
        return Double.isFinite(thrust) && thrust > 0.0D ? thrust : 0.0D;
    }

    public double thrustToWeight(double atmosphericAltitude, double mach,
                                 double throttle, double afterburnerSpool,
                                 double gravity) {
        double weight = profile.weight(gravity);
        if (weight <= 1.0E-9D) {
            return 0.0D;
        }
        return thrust(atmosphericAltitude, mach, throttle, afterburnerSpool, true) /
            weight;
    }

    private static double clampUnit(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }
}
