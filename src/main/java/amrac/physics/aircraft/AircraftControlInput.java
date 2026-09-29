package amrac.physics.aircraft;

public final class AircraftControlInput {
    private final double throttle;
    private final double pitch;
    private final double yaw;
    private final double roll;
    private final double gearPosition;
    private final double flapPosition;
    private final double speedBrakePosition;
    private final double afterburnerSpool;
    private final boolean engineRunning;
    private final boolean angleOfAttackLimiterEnabled;
    private final boolean groundContact;
    private final boolean groundReverse;
    private final double groundFrictionMultiplier;
    private final double groundLateralGripMultiplier;
    private final double wheelBrake;
    private final double fuelMassOffsetKilograms;
    private final double storesMassKilograms;
    private final double storesDragArea;
    private final double storesRollInertia;
    private final double storesPitchInertia;
    private final double storesRollMoment;

    private AircraftControlInput(Builder builder) {
        this.throttle = clamp(builder.throttle, 0.0D, 1.0D);
        this.pitch = clamp(builder.pitch, -1.0D, 1.0D);
        this.yaw = clamp(builder.yaw, -1.0D, 1.0D);
        this.roll = clamp(builder.roll, -1.0D, 1.0D);
        this.gearPosition = clamp(builder.gearPosition, 0.0D, 1.0D);
        this.flapPosition = clamp(builder.flapPosition, 0.0D, 1.0D);
        this.speedBrakePosition = clamp(builder.speedBrakePosition, 0.0D, 1.0D);
        this.afterburnerSpool = clamp(builder.afterburnerSpool, 0.0D, 1.0D);
        this.engineRunning = builder.engineRunning;
        this.angleOfAttackLimiterEnabled = builder.angleOfAttackLimiterEnabled;
        this.groundContact = builder.groundContact;
        this.groundReverse = builder.groundReverse;
        this.groundFrictionMultiplier = nonNegative(builder.groundFrictionMultiplier);
        this.groundLateralGripMultiplier =
            nonNegative(builder.groundLateralGripMultiplier);
        this.wheelBrake = clamp(builder.wheelBrake, 0.0D, 1.0D);
        this.fuelMassOffsetKilograms =
            Double.isFinite(builder.fuelMassOffsetKilograms)
                ? builder.fuelMassOffsetKilograms : 0.0D;
        this.storesMassKilograms = nonNegative(builder.storesMassKilograms);
        this.storesDragArea = nonNegative(builder.storesDragArea);
        this.storesRollInertia = atLeastZero(builder.storesRollInertia);
        this.storesPitchInertia = atLeastZero(builder.storesPitchInertia);
        this.storesRollMoment = Double.isFinite(builder.storesRollMoment)
            ? builder.storesRollMoment : 0.0D;
    }

    public static Builder builder() {
        return new Builder();
    }

    public double throttle() {
        return throttle;
    }

    public double pitch() {
        return pitch;
    }

    public double yaw() {
        return yaw;
    }

    public double roll() {
        return roll;
    }

    public double flapPosition() {
        return flapPosition;
    }

    public double gearPosition() {
        return gearPosition;
    }

    public double speedBrakePosition() {
        return speedBrakePosition;
    }

    public double wheelBrake() {
        return wheelBrake;
    }

    public double afterburnerSpool() {
        return afterburnerSpool;
    }

    public boolean engineRunning() {
        return engineRunning;
    }

    public boolean angleOfAttackLimiterEnabled() {
        return angleOfAttackLimiterEnabled;
    }

    public boolean groundContact() {
        return groundContact;
    }

    public boolean groundReverse() {
        return groundReverse;
    }

    public double groundFrictionMultiplier() {
        return groundFrictionMultiplier;
    }

    public double fuelMassOffsetKilograms() {
        return fuelMassOffsetKilograms;
    }

    public double storesMassKilograms() {
        return storesMassKilograms;
    }

    public double storesDragArea() {
        return storesDragArea;
    }

    public double storesRollInertia() {
        return storesRollInertia;
    }

    public double storesPitchInertia() {
        return storesPitchInertia;
    }

    public double storesRollMoment() {
        return storesRollMoment;
    }

    public double groundLateralGripMultiplier() {
        return groundLateralGripMultiplier;
    }

    private static double atLeastZero(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0D ? value : 1.0D;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return value < minimum ? minimum : Math.min(value, maximum);
    }

    public static final class Builder {
        private double throttle;
        private double pitch;
        private double yaw;
        private double roll;
        private double gearPosition;
        private double flapPosition;
        private double speedBrakePosition;
        private double afterburnerSpool;
        private boolean engineRunning = true;
        private boolean angleOfAttackLimiterEnabled;
        private boolean groundContact;
        private boolean groundReverse;
        private double groundFrictionMultiplier = 1.0D;
        private double groundLateralGripMultiplier = 1.0D;
        private double wheelBrake;
        private double fuelMassOffsetKilograms;
        private double storesMassKilograms;
        private double storesDragArea;
        private double storesRollInertia;
        private double storesPitchInertia;
        private double storesRollMoment;

        public Builder throttle(double value) {
            this.throttle = value;
            return this;
        }

        public Builder pitch(double value) {
            this.pitch = value;
            return this;
        }

        public Builder yaw(double value) {
            this.yaw = value;
            return this;
        }

        public Builder roll(double value) {
            this.roll = value;
            return this;
        }

        public Builder flapPosition(double value) {
            this.flapPosition = value;
            return this;
        }

        public Builder gearPosition(double value) {
            this.gearPosition = value;
            return this;
        }

        public Builder speedBrakePosition(double value) {
            this.speedBrakePosition = value;
            return this;
        }

        public Builder wheelBrake(double value) {
            this.wheelBrake = value;
            return this;
        }

        public Builder afterburnerSpool(double value) {
            this.afterburnerSpool = value;
            return this;
        }

        public Builder engineRunning(boolean value) {
            this.engineRunning = value;
            return this;
        }

        public Builder angleOfAttackLimiterEnabled(boolean value) {
            this.angleOfAttackLimiterEnabled = value;
            return this;
        }

        public Builder groundContact(boolean value) {
            this.groundContact = value;
            return this;
        }

        public Builder groundReverse(boolean value) {
            this.groundReverse = value;
            return this;
        }

        public Builder groundFrictionMultiplier(double value) {
            this.groundFrictionMultiplier = value;
            return this;
        }

        public Builder fuelMassOffsetKilograms(double value) {
            this.fuelMassOffsetKilograms = value;
            return this;
        }

        public Builder storesMassKilograms(double value) {
            this.storesMassKilograms = value;
            return this;
        }

        public Builder storesDragArea(double value) {
            this.storesDragArea = value;
            return this;
        }

        public Builder storesRollInertia(double value) {
            this.storesRollInertia = value;
            return this;
        }

        public Builder storesPitchInertia(double value) {
            this.storesPitchInertia = value;
            return this;
        }

        public Builder storesRollMoment(double value) {
            this.storesRollMoment = value;
            return this;
        }

        public Builder groundLateralGripMultiplier(double value) {
            this.groundLateralGripMultiplier = value;
            return this;
        }

        public AircraftControlInput build() {
            return new AircraftControlInput(this);
        }
    }
}
