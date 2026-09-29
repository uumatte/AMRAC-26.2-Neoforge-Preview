package amrac.physics.aircraft;

import java.util.Map;

public final class AircraftPhysicsProfile {
    public static final Map<String, String> LEGACY_LEAVES = Map.of(
        "angleOfAttackLimiter.limitAngleOfAttack", "limits.limiterAngleOfAttack",
        "angleOfAttackLimiter.softStartAngleOfAttack",
        "limits.limiterSoftStartAngleOfAttack",
        "limitAngleOfAttack", "limits.limiterAngleOfAttack",
        "softStartAngleOfAttack", "limits.limiterSoftStartAngleOfAttack");

    private final String id;
    private final RadarProfile radar;
    private final FuelProfile fuel;
    private final FlapProfile flaps;
    private final CountermeasureProfile countermeasures;
    private final InertiaProfile inertia;
    private final FlightControlProfile flightControl;
    private final PostStallProfile postStall;

    private final double mass;
    private final double wingArea;

    private final CurveInterpolator thrustAltitudeCurve;
    private final CurveInterpolator thrustMachCurve;
    private final double afterburnerThrustMultiplier;

    private final CurveInterpolator liftCoefficientCurve;
    private final CurveInterpolator dragCoefficientCurve;
    private final CurveInterpolator waveDragMachCurve;
    private final CurveInterpolator liftAltitudeMultiplierCurve;
    private final double sideForceCoefficient;
    private final double sideSlipDragCoefficient;
    private final double gearDragCoefficient;
    private final double speedBrakeDragCoefficient;

    private final double maxPositiveG;
    private final double maxNegativeG;
    private final CurveInterpolator structuralSpeedLimitCurve;

    private final double maxPitchRate;
    private final double maxRollRate;
    private final double maxYawRate;
    private final double pitchRateResponse;
    private final double rollRateResponse;
    private final double yawRateResponse;
    private final double controlAuthorityReferenceSpeed;
    private final double minimumControlAuthority;
    private final double postStallControlAuthority;

    private final double pitchStability;
    private final double yawStability;
    private final double maxPitchStabilityRate;
    private final double maxYawStabilityRate;

    private final double limiterAngleOfAttack;
    private final double limiterSoftStartAngleOfAttack;

    private final double rollingResistance;
    private final double wheelBrakeFriction;
    private final double groundLateralGrip;
    private final double groundThrustFactor;

    private final double militaryLevelSpeed;
    private final double afterburnerLevelSpeed;

    private AircraftPhysicsProfile(Builder builder) {
        this.id = builder.id;
        this.radar = builder.radar;
        this.fuel = builder.fuel;
        this.flaps = builder.flaps;
        this.countermeasures = builder.countermeasures;
        this.inertia = builder.resolvedInertia();
        this.flightControl = builder.flightControl;
        this.postStall = builder.postStall;
        this.mass = builder.mass;
        this.wingArea = builder.wingArea;
        this.thrustAltitudeCurve = builder.thrustAltitudeCurve;
        this.thrustMachCurve = builder.thrustMachCurve;
        this.afterburnerThrustMultiplier = builder.afterburnerThrustMultiplier;
        this.liftCoefficientCurve = builder.liftCoefficientCurve;
        this.dragCoefficientCurve = builder.dragCoefficientCurve;
        this.waveDragMachCurve = builder.waveDragMachCurve;
        this.liftAltitudeMultiplierCurve = builder.liftAltitudeMultiplierCurve;
        this.sideForceCoefficient = builder.sideForceCoefficient;
        this.sideSlipDragCoefficient = builder.sideSlipDragCoefficient;
        this.gearDragCoefficient = builder.gearDragCoefficient;
        this.speedBrakeDragCoefficient = builder.speedBrakeDragCoefficient;
        this.maxPositiveG = builder.maxPositiveG;
        this.maxNegativeG = builder.maxNegativeG;
        this.structuralSpeedLimitCurve = builder.structuralSpeedLimitCurve;
        this.maxPitchRate = builder.maxPitchRate;
        this.maxRollRate = builder.maxRollRate;
        this.maxYawRate = builder.maxYawRate;
        this.pitchRateResponse = builder.pitchRateResponse;
        this.rollRateResponse = builder.rollRateResponse;
        this.yawRateResponse = builder.yawRateResponse;
        this.controlAuthorityReferenceSpeed = builder.controlAuthorityReferenceSpeed;
        this.minimumControlAuthority = builder.minimumControlAuthority;
        this.postStallControlAuthority = builder.postStallControlAuthority;
        this.pitchStability = builder.pitchStability;
        this.yawStability = builder.yawStability;
        this.maxPitchStabilityRate = builder.maxPitchStabilityRate;
        this.maxYawStabilityRate = builder.maxYawStabilityRate;
        this.limiterAngleOfAttack = builder.limiterAngleOfAttack;
        this.limiterSoftStartAngleOfAttack = builder.limiterSoftStartAngleOfAttack;
        this.rollingResistance = builder.rollingResistance;
        this.wheelBrakeFriction = builder.wheelBrakeFriction;
        this.groundLateralGrip = builder.groundLateralGrip;
        this.groundThrustFactor = builder.groundThrustFactor;
        this.militaryLevelSpeed = builder.militaryLevelSpeed;
        this.afterburnerLevelSpeed = builder.afterburnerLevelSpeed;
    }

    public static AircraftPhysicsProfile fromJson(Map<String, Object> root,
                                                  AircraftPhysicsProfile fallback) {
        Builder builder = fallback == null ? new Builder() : fallback.toBuilder();
        builder.id = Json.string(root, "id", builder.id);
        if (Json.object(root, "radar") != null) {
            builder.radar = RadarProfile.fromJson(root);
        }
        if (Json.object(root, "fuel") != null) {
            builder.fuel = FuelProfile.fromJson(root);
        }
        if (Json.object(root, "flaps") != null) {
            builder.flaps = FlapProfile.fromJson(root);
        }
        if (Json.object(root, "countermeasures") != null) {
            builder.countermeasures = CountermeasureProfile.fromJson(root);
        }
        builder.mass = positive(Json.number(root, "mass", builder.mass), builder.mass);
        builder.wingArea = positive(Json.number(root, "wingArea", builder.wingArea),
            builder.wingArea);

        Map<String, Object> engine = Json.object(root, "engine");
        double[][] thrust = Json.curve(engine != null ? engine : root,
            "thrustAltitudeCurve");
        if (thrust != null) {
            builder.thrustAltitudeCurve = CurveInterpolator.of(thrust);
        }
        double[][] ram = Json.curve(engine != null ? engine : root, "thrustMachCurve");
        if (ram != null) {
            builder.thrustMachCurve = CurveInterpolator.of(ram);
        }
        builder.afterburnerThrustMultiplier = positive(
            Json.number(engine != null ? engine : root, "afterburnerThrustMultiplier",
                builder.afterburnerThrustMultiplier),
            builder.afterburnerThrustMultiplier);

        Map<String, Object> aero = Json.object(root, "aerodynamics");
        Map<String, Object> aeroSource = aero != null ? aero : root;
        double[][] lift = Json.curve(aeroSource, "liftCoefficientCurve");
        if (lift != null) {
            builder.liftCoefficientCurve = CurveInterpolator.of(lift);
        }
        double[][] drag = Json.curve(aeroSource, "dragCoefficientCurve");
        if (drag != null) {
            builder.dragCoefficientCurve = CurveInterpolator.of(drag);
        }
        double[][] wave = Json.curve(aeroSource, "waveDragMachCurve");
        if (wave != null) {
            builder.waveDragMachCurve = CurveInterpolator.of(wave);
        }
        double[][] ceiling = Json.curve(aeroSource, "liftAltitudeMultiplierCurve");
        if (ceiling != null) {
            builder.liftAltitudeMultiplierCurve = CurveInterpolator.of(ceiling);
        }
        builder.sideForceCoefficient = Json.number(aeroSource, "sideForceCoefficient",
            builder.sideForceCoefficient);
        builder.sideSlipDragCoefficient = Json.number(aeroSource,
            "sideSlipDragCoefficient", builder.sideSlipDragCoefficient);
        builder.gearDragCoefficient = Json.number(aeroSource, "gearDragCoefficient",
            builder.gearDragCoefficient);
        builder.speedBrakeDragCoefficient = Json.number(aeroSource,
            "speedBrakeDragCoefficient", builder.speedBrakeDragCoefficient);

        Map<String, Object> limits = Json.object(root, "limits");
        Map<String, Object> limitSource = limits != null ? limits : root;
        builder.maxPositiveG = positive(Json.number(limitSource, "maxPositiveG",
            builder.maxPositiveG), builder.maxPositiveG);
        double negative = Json.number(limitSource, "maxNegativeG", builder.maxNegativeG);
        builder.maxNegativeG = Double.isFinite(negative) && negative < 0.0D
            ? negative : builder.maxNegativeG;
        double[][] structural = Json.curve(limitSource, "structuralSpeedLimitCurve");
        if (structural != null) {
            builder.structuralSpeedLimitCurve = CurveInterpolator.of(structural);
        }
        Map<String, Object> legacyLimiter = Json.object(root, "angleOfAttackLimiter");
        Map<String, Object> legacyLimiterSource = legacyLimiter != null
            ? legacyLimiter : root;
        builder.limiterAngleOfAttack = positive(Json.number(limitSource,
            "limiterAngleOfAttack", Json.number(legacyLimiterSource,
                "limitAngleOfAttack", builder.limiterAngleOfAttack)),
            builder.limiterAngleOfAttack);
        builder.limiterSoftStartAngleOfAttack = positive(Json.number(limitSource,
            "limiterSoftStartAngleOfAttack", Json.number(legacyLimiterSource,
                "softStartAngleOfAttack", builder.limiterSoftStartAngleOfAttack)),
            builder.limiterSoftStartAngleOfAttack);

        Map<String, Object> response = Json.object(root, "response");
        Map<String, Object> responseSource = response != null ? response : root;
        builder.maxPitchRate = positive(Json.number(responseSource, "maxPitchRate",
            builder.maxPitchRate), builder.maxPitchRate);
        builder.maxRollRate = positive(Json.number(responseSource, "maxRollRate",
            builder.maxRollRate), builder.maxRollRate);
        builder.maxYawRate = positive(Json.number(responseSource, "maxYawRate",
            builder.maxYawRate), builder.maxYawRate);
        builder.pitchRateResponse = unit(Json.number(responseSource,
            "pitchRateResponse", builder.pitchRateResponse), builder.pitchRateResponse);
        builder.rollRateResponse = unit(Json.number(responseSource, "rollRateResponse",
            builder.rollRateResponse), builder.rollRateResponse);
        builder.yawRateResponse = unit(Json.number(responseSource, "yawRateResponse",
            builder.yawRateResponse), builder.yawRateResponse);
        builder.controlAuthorityReferenceSpeed = positive(Json.number(responseSource,
            "controlAuthorityReferenceSpeed", builder.controlAuthorityReferenceSpeed),
            builder.controlAuthorityReferenceSpeed);
        builder.minimumControlAuthority = unit(Json.number(responseSource,
            "minimumControlAuthority", builder.minimumControlAuthority),
            builder.minimumControlAuthority);
        builder.postStallControlAuthority = unit(Json.number(responseSource,
            "postStallControlAuthority", builder.postStallControlAuthority),
            builder.postStallControlAuthority);

        Map<String, Object> stability = Json.object(root, "stability");
        Map<String, Object> stabilitySource = stability != null ? stability : root;
        builder.pitchStability = Json.number(stabilitySource, "pitchStability",
            builder.pitchStability);
        builder.yawStability = Json.number(stabilitySource, "yawStability",
            builder.yawStability);
        builder.maxPitchStabilityRate = Json.number(stabilitySource,
            "maxPitchStabilityRate", builder.maxPitchStabilityRate);
        builder.maxYawStabilityRate = Json.number(stabilitySource,
            "maxYawStabilityRate", builder.maxYawStabilityRate);

        Map<String, Object> ground = Json.object(root, "ground");
        Map<String, Object> groundSource = ground != null ? ground : root;
        builder.rollingResistance = Json.number(groundSource, "rollingResistance",
            builder.rollingResistance);
        builder.wheelBrakeFriction = Json.number(groundSource, "wheelBrakeFriction",
            builder.wheelBrakeFriction);
        builder.groundLateralGrip = Json.number(groundSource, "lateralGrip",
            builder.groundLateralGrip);
        builder.groundThrustFactor = Json.number(groundSource, "thrustFactor",
            builder.groundThrustFactor);

        Map<String, Object> published = Json.object(root, "publishedSpeeds");
        Map<String, Object> publishedSource = published != null ? published : root;
        builder.militaryLevelSpeed = positive(Json.number(publishedSource,
            "militaryLevelSpeed", builder.militaryLevelSpeed),
            builder.militaryLevelSpeed);
        builder.afterburnerLevelSpeed = positive(Json.number(publishedSource,
            "afterburnerLevelSpeed", builder.afterburnerLevelSpeed),
            builder.afterburnerLevelSpeed);

        if (Json.object(root, "flightControl") != null) {
            builder.flightControl = FlightControlProfile.fromJson(root);
        }
        if (Json.object(root, "postStall") != null) {
            builder.postStall = PostStallProfile.fromJson(root);
        }
        if (Json.object(root, "inertia") != null) {
            builder.inertia = InertiaProfile.fromJson(root,
                builder.resolvedInertia());
        }

        return builder.build();
    }

    public String id() {
        return id;
    }

    public RadarProfile radar() {
        return radar;
    }

    public FlapProfile flaps() {
        return flaps;
    }

    public FuelProfile fuel() {
        return fuel;
    }

    public CountermeasureProfile countermeasures() {
        return countermeasures;
    }

    public double mass() {
        return mass;
    }

    public double wingArea() {
        return wingArea;
    }

    public CurveInterpolator thrustAltitudeCurve() {
        return thrustAltitudeCurve;
    }

    public CurveInterpolator thrustMachCurve() {
        return thrustMachCurve;
    }

    public double afterburnerThrustMultiplier() {
        return afterburnerThrustMultiplier;
    }

    public CurveInterpolator liftCoefficientCurve() {
        return liftCoefficientCurve;
    }

    public CurveInterpolator dragCoefficientCurve() {
        return dragCoefficientCurve;
    }

    public CurveInterpolator waveDragMachCurve() {
        return waveDragMachCurve;
    }

    public CurveInterpolator liftAltitudeMultiplierCurve() {
        return liftAltitudeMultiplierCurve;
    }

    public double liftAltitudeMultiplier(double atmosphericAltitude) {
        double multiplier =
            liftAltitudeMultiplierCurve.interpolate(atmosphericAltitude);
        if (!Double.isFinite(multiplier)) {
            return 1.0D;
        }
        return multiplier < 0.0D ? 0.0D : Math.min(multiplier, 1.0D);
    }

    public double sideForceCoefficient() {
        return sideForceCoefficient;
    }

    public double sideSlipDragCoefficient() {
        return sideSlipDragCoefficient;
    }

    public double gearDragCoefficient() {
        return gearDragCoefficient;
    }

    public double speedBrakeDragCoefficient() {
        return speedBrakeDragCoefficient;
    }

    public double maxPositiveG() {
        return maxPositiveG;
    }

    public double maxNegativeG() {
        return maxNegativeG;
    }

    public double structuralSpeedLimit(double atmosphericAltitude) {
        return Math.max(structuralSpeedLimitCurve.interpolate(atmosphericAltitude),
            1.0D);
    }

    public CurveInterpolator structuralSpeedLimitCurve() {
        return structuralSpeedLimitCurve;
    }

    public double maxPitchRate() {
        return maxPitchRate;
    }

    public double maxRollRate() {
        return maxRollRate;
    }

    public double maxYawRate() {
        return maxYawRate;
    }

    public double pitchRateResponse() {
        return pitchRateResponse;
    }

    public double rollRateResponse() {
        return rollRateResponse;
    }

    public double yawRateResponse() {
        return yawRateResponse;
    }

    public double controlAuthorityReferenceSpeed() {
        return controlAuthorityReferenceSpeed;
    }

    public double minimumControlAuthority() {
        return minimumControlAuthority;
    }

    public double postStallControlAuthority() {
        return postStallControlAuthority;
    }

    public double pitchStability() {
        return pitchStability;
    }

    public double yawStability() {
        return yawStability;
    }

    public double maxPitchStabilityRate() {
        return maxPitchStabilityRate;
    }

    public double maxYawStabilityRate() {
        return maxYawStabilityRate;
    }

    public double limiterAngleOfAttack() {
        return limiterAngleOfAttack;
    }

    public double limiterSoftStartAngleOfAttack() {
        return limiterSoftStartAngleOfAttack;
    }

    public double rollingResistance() {
        return rollingResistance;
    }

    public double wheelBrakeFriction() {
        return wheelBrakeFriction;
    }

    public double groundLateralGrip() {
        return groundLateralGrip;
    }

    public double groundThrustFactor() {
        return groundThrustFactor;
    }

    public double militaryLevelSpeed() {
        return militaryLevelSpeed;
    }

    public double afterburnerLevelSpeed() {
        return afterburnerLevelSpeed;
    }

    public InertiaProfile inertia() {
        return inertia;
    }

    public FlightControlProfile flightControl() {
        return flightControl;
    }

    public PostStallProfile postStall() {
        return postStall;
    }

    public double postStallPitchAuthority() {
        return PostStallProfile.axisFloor(postStall.pitchAuthority(),
            postStallControlAuthority);
    }

    public double postStallRollAuthority() {
        return PostStallProfile.axisFloor(postStall.rollAuthority(),
            postStallControlAuthority);
    }

    public double postStallYawAuthority() {
        return PostStallProfile.axisFloor(postStall.yawAuthority(),
            postStallControlAuthority);
    }

    public double referenceDynamicPressure() {
        return ControlAuthorityPolicy.referenceDynamicPressure(
            fullAuthoritySpeed());
    }

    public double fullAuthoritySpeed() {
        double stated = flightControl.fullAuthoritySpeed();
        return stated > 0.0D ? stated : controlAuthorityReferenceSpeed;
    }

    public double rollTaperOnsetRatio() {
        double reference = referenceDynamicPressure();
        if (reference <= 1.0E-9D) {
            return 0.0D;
        }
        return ControlAuthorityPolicy.referenceDynamicPressure(
            flightControl.rollTaperOnsetSpeed(fullAuthoritySpeed())) / reference;
    }

    public double weight(double gravity) {
        return mass * gravity;
    }

    public double stallAngleOfAttack() {
        return liftCoefficientCurve.inputOfMaximumOutput();
    }

    public double maximumLiftCoefficient() {
        return liftCoefficientCurve.maximumOutput();
    }

    public double stallSpeed(double density, double gravity) {
        double denominator = density * wingArea * maximumLiftCoefficient();
        if (denominator <= 1.0E-9D) {
            return 0.0D;
        }
        return Math.sqrt(2.0D * weight(gravity) / denominator);
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.radar = radar;
        builder.fuel = fuel;
        builder.flaps = flaps;
        builder.countermeasures = countermeasures;
        builder.inertia = inertia;
        builder.flightControl = flightControl;
        builder.postStall = postStall;
        builder.id = id;
        builder.mass = mass;
        builder.wingArea = wingArea;
        builder.thrustAltitudeCurve = thrustAltitudeCurve;
        builder.thrustMachCurve = thrustMachCurve;
        builder.afterburnerThrustMultiplier = afterburnerThrustMultiplier;
        builder.liftCoefficientCurve = liftCoefficientCurve;
        builder.dragCoefficientCurve = dragCoefficientCurve;
        builder.waveDragMachCurve = waveDragMachCurve;
        builder.liftAltitudeMultiplierCurve = liftAltitudeMultiplierCurve;
        builder.sideForceCoefficient = sideForceCoefficient;
        builder.sideSlipDragCoefficient = sideSlipDragCoefficient;
        builder.gearDragCoefficient = gearDragCoefficient;
        builder.speedBrakeDragCoefficient = speedBrakeDragCoefficient;
        builder.maxPositiveG = maxPositiveG;
        builder.maxNegativeG = maxNegativeG;
        builder.structuralSpeedLimitCurve = structuralSpeedLimitCurve;
        builder.maxPitchRate = maxPitchRate;
        builder.maxRollRate = maxRollRate;
        builder.maxYawRate = maxYawRate;
        builder.pitchRateResponse = pitchRateResponse;
        builder.rollRateResponse = rollRateResponse;
        builder.yawRateResponse = yawRateResponse;
        builder.controlAuthorityReferenceSpeed = controlAuthorityReferenceSpeed;
        builder.minimumControlAuthority = minimumControlAuthority;
        builder.postStallControlAuthority = postStallControlAuthority;
        builder.pitchStability = pitchStability;
        builder.yawStability = yawStability;
        builder.maxPitchStabilityRate = maxPitchStabilityRate;
        builder.maxYawStabilityRate = maxYawStabilityRate;
        builder.limiterAngleOfAttack = limiterAngleOfAttack;
        builder.limiterSoftStartAngleOfAttack = limiterSoftStartAngleOfAttack;
        builder.rollingResistance = rollingResistance;
        builder.wheelBrakeFriction = wheelBrakeFriction;
        builder.groundLateralGrip = groundLateralGrip;
        builder.groundThrustFactor = groundThrustFactor;
        builder.militaryLevelSpeed = militaryLevelSpeed;
        builder.afterburnerLevelSpeed = afterburnerLevelSpeed;
        return builder;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static double unit(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0D && value <= 1.0D
            ? value : fallback;
    }

    public static final class Builder {
        private String id = "unnamed";
        private RadarProfile radar = RadarProfile.fromJson(java.util.Map.of());
        private FuelProfile fuel = FuelProfile.NONE;
        private FlapProfile flaps = FlapProfile.DEFAULT;
        private CountermeasureProfile countermeasures = CountermeasureProfile.NONE;
        private InertiaProfile inertia;
        private FlightControlProfile flightControl = FlightControlProfile.DEFAULT;
        private PostStallProfile postStall = PostStallProfile.DEFAULT;
        private double mass = 12000.0D;
        private double wingArea = 100.0D;
        private CurveInterpolator thrustAltitudeCurve =
            CurveInterpolator.of(new double[][] {{0.0D, 160000.0D}});
        private double afterburnerThrustMultiplier = 1.0D;
        private CurveInterpolator liftCoefficientCurve =
            CurveInterpolator.of(new double[][] {{-90.0D, 0.0D}, {0.0D, 0.0D},
                {20.0D, 1.2D}, {90.0D, 0.0D}});
        private CurveInterpolator thrustMachCurve =
            CurveInterpolator.of(new double[][] {{0.0D, 1.0D}});
        private CurveInterpolator waveDragMachCurve =
            CurveInterpolator.of(new double[][] {{0.0D, 0.0D}});
        private CurveInterpolator liftAltitudeMultiplierCurve =
            CurveInterpolator.of(new double[][] {{0.0D, 1.0D}});
        private CurveInterpolator dragCoefficientCurve =
            CurveInterpolator.of(new double[][] {{-90.0D, 1.8D}, {0.0D, 0.1D},
                {90.0D, 1.8D}});
        private double sideForceCoefficient = 1.5D;
        private double sideSlipDragCoefficient = 1.2D;
        private double gearDragCoefficient = 0.02D;
        private double speedBrakeDragCoefficient = 0.10D;
        private double maxPositiveG = 9.0D;
        private double maxNegativeG = -3.0D;
        private CurveInterpolator structuralSpeedLimitCurve =
            CurveInterpolator.of(new double[][] {{0.0D, 500.0D}});
        private double maxPitchRate = 90.0D;
        private double maxRollRate = 270.0D;
        private double maxYawRate = 25.0D;
        private double pitchRateResponse = 0.35D;
        private double rollRateResponse = 0.40D;
        private double yawRateResponse = 0.30D;
        private double controlAuthorityReferenceSpeed = 180.0D;
        private double minimumControlAuthority = 0.02D;
        private double postStallControlAuthority = 0.35D;
        private double pitchStability = 0.45D;
        private double yawStability = 0.90D;
        private double maxPitchStabilityRate = 25.0D;
        private double maxYawStabilityRate = 18.0D;
        private double limiterAngleOfAttack = 24.0D;
        private double limiterSoftStartAngleOfAttack = 18.0D;
        private double rollingResistance = 0.020D;
        private double wheelBrakeFriction = 0.40D;
        private double groundLateralGrip = 6.0D;
        private double groundThrustFactor = 1.0D;
        private double militaryLevelSpeed = 300.0D;
        private double afterburnerLevelSpeed = 360.0D;

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder mass(double value) {
            this.mass = value;
            return this;
        }

        public Builder wingArea(double value) {
            this.wingArea = value;
            return this;
        }

        public Builder thrustAltitudeCurve(double[][] value) {
            this.thrustAltitudeCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder thrustMachCurve(double[][] value) {
            this.thrustMachCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder afterburnerThrustMultiplier(double value) {
            this.afterburnerThrustMultiplier = value;
            return this;
        }

        public Builder liftCoefficientCurve(double[][] value) {
            this.liftCoefficientCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder dragCoefficientCurve(double[][] value) {
            this.dragCoefficientCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder waveDragMachCurve(double[][] value) {
            this.waveDragMachCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder liftAltitudeMultiplierCurve(double[][] value) {
            this.liftAltitudeMultiplierCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder sideForceCoefficient(double value) {
            this.sideForceCoefficient = value;
            return this;
        }

        public Builder sideSlipDragCoefficient(double value) {
            this.sideSlipDragCoefficient = value;
            return this;
        }

        public Builder gearDragCoefficient(double value) {
            this.gearDragCoefficient = value;
            return this;
        }

        public Builder speedBrakeDragCoefficient(double value) {
            this.speedBrakeDragCoefficient = value;
            return this;
        }

        public Builder maxPositiveG(double value) {
            this.maxPositiveG = value;
            return this;
        }

        public Builder maxNegativeG(double value) {
            this.maxNegativeG = value;
            return this;
        }

        public Builder structuralSpeedLimitCurve(double[][] value) {
            this.structuralSpeedLimitCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder maxPitchRate(double value) {
            this.maxPitchRate = value;
            return this;
        }

        public Builder maxRollRate(double value) {
            this.maxRollRate = value;
            return this;
        }

        public Builder maxYawRate(double value) {
            this.maxYawRate = value;
            return this;
        }

        public Builder pitchRateResponse(double value) {
            this.pitchRateResponse = value;
            return this;
        }

        public Builder rollRateResponse(double value) {
            this.rollRateResponse = value;
            return this;
        }

        public Builder yawRateResponse(double value) {
            this.yawRateResponse = value;
            return this;
        }

        public Builder controlAuthorityReferenceSpeed(double value) {
            this.controlAuthorityReferenceSpeed = value;
            return this;
        }

        public Builder minimumControlAuthority(double value) {
            this.minimumControlAuthority = value;
            return this;
        }

        public Builder postStallControlAuthority(double value) {
            this.postStallControlAuthority = value;
            return this;
        }

        public Builder pitchStability(double value) {
            this.pitchStability = value;
            return this;
        }

        public Builder yawStability(double value) {
            this.yawStability = value;
            return this;
        }

        public Builder maxPitchStabilityRate(double value) {
            this.maxPitchStabilityRate = value;
            return this;
        }

        public Builder maxYawStabilityRate(double value) {
            this.maxYawStabilityRate = value;
            return this;
        }

        public Builder limiterAngleOfAttack(double value) {
            this.limiterAngleOfAttack = value;
            return this;
        }

        public Builder limiterSoftStartAngleOfAttack(double value) {
            this.limiterSoftStartAngleOfAttack = value;
            return this;
        }

        public Builder rollingResistance(double value) {
            this.rollingResistance = value;
            return this;
        }

        public Builder wheelBrakeFriction(double value) {
            this.wheelBrakeFriction = value;
            return this;
        }

        public Builder groundLateralGrip(double value) {
            this.groundLateralGrip = value;
            return this;
        }

        public Builder groundThrustFactor(double value) {
            this.groundThrustFactor = value;
            return this;
        }

        public Builder militaryLevelSpeed(double value) {
            this.militaryLevelSpeed = value;
            return this;
        }

        public Builder afterburnerLevelSpeed(double value) {
            this.afterburnerLevelSpeed = value;
            return this;
        }

        public Builder inertia(InertiaProfile value) {
            this.inertia = value;
            return this;
        }

        public Builder flightControl(FlightControlProfile value) {
            this.flightControl = value == null ? FlightControlProfile.DEFAULT : value;
            return this;
        }

        public Builder postStall(PostStallProfile value) {
            this.postStall = value == null ? PostStallProfile.DEFAULT : value;
            return this;
        }

        InertiaProfile resolvedInertia() {
            return inertia != null ? inertia
                : InertiaProfile.forAirframe(mass, wingArea, maxRollRate,
                    maxPitchRate, maxYawRate);
        }

        public AircraftPhysicsProfile build() {
            return new AircraftPhysicsProfile(this);
        }
    }
}
