package amrac.physics.missile;

import amrac.physics.aircraft.CurveInterpolator;
import amrac.physics.aircraft.Json;

import java.util.Map;

public final class MissilePhysicsProfile {
    private final String id;
    private final double mass;
    private final double referenceArea;
    private final double length;
    private final CurveInterpolator thrustCurve;
    private final CurveInterpolator dragCoefficientCurve;
    private final CurveInterpolator normalForceCoefficientCurve;
    private final CurveInterpolator controlAuthorityCurve;
    private final double maxG;
    private final double maxAoA;
    private final double maxGRate;
    private final double referenceAltitude;
    private final double referenceLaunchSpeed;
    private final double referenceTargetSpeed;

    private MissilePhysicsProfile(Builder builder) {
        this.id = builder.id;
        this.launchMaxRange = builder.launchMaxRange;
        this.launchMinRange = builder.launchMinRange;
        this.launchCooldownTicks = builder.launchCooldownTicks;
        this.warmupSeconds = builder.warmupSeconds;
        this.carriedPerAircraft = builder.carriedPerAircraft;
        this.rearmIntervalTicks = builder.rearmIntervalTicks;
        this.seekerGimbalDegrees = builder.seekerGimbalDegrees;
        this.navigationConstant = builder.navigationConstant;
        this.maxLeadTicks = builder.maxLeadTicks;
        this.activeHoming = builder.activeHoming;
        this.seekerType = builder.seekerType;
        this.seekerFovDegrees = builder.seekerFovDegrees;
        this.seekerActivationRange = builder.seekerActivationRange;
        this.velocityGate = builder.velocityGate;
        this.velocityGateLookDownOnly = builder.velocityGateLookDownOnly;
        this.burnThroughRange = builder.burnThroughRange;
        this.chaffSusceptibility = builder.chaffSusceptibility;
        this.flareSusceptibility = builder.flareSusceptibility;
        this.heatSourceCoefficient = builder.heatSourceCoefficient;
        this.twoWayDatalink = builder.twoWayDatalink;
        this.extrapolateOnLoss = builder.extrapolateOnLoss;
        this.extrapolateAfterDecoy = builder.extrapolateAfterDecoy;
        this.gimbalMemorySeconds = builder.gimbalMemorySeconds;
        this.gimbalConfirmSeconds = builder.gimbalConfirmSeconds;
        this.reacquireSeconds = builder.reacquireSeconds;
        this.scanRateDegreesPerSecond = builder.scanRateDegreesPerSecond;
        this.inertialDriftMetresPerSecond = builder.inertialDriftMetresPerSecond;
        this.datalinkUpdateSeconds = builder.datalinkUpdateSeconds;
        this.datalinkErrorDegrees = builder.datalinkErrorDegrees;
        this.confirmSeconds = builder.confirmSeconds;
        this.explosionPower = builder.explosionPower;
        this.proximityFuseRadius = builder.proximityFuseRadius;
        this.armingDistance = builder.armingDistance;
        this.massStated = builder.massStated;
        this.carriedDragArea = builder.carriedDragArea;
        this.lifetimeTicks = builder.lifetimeTicks;
        this.loftAngleDegrees = builder.loftAngleDegrees;
        this.loftFullRange = builder.loftFullRange;
        this.loftMinRange = builder.loftMinRange;
        this.loftCeiling = builder.loftCeiling;
        this.inducedDragFactor = builder.inducedDragFactor;
        this.inducedSpeedLossPerG2 = builder.inducedSpeedLossPerG2;
        this.inducedSpeedLossCap = builder.inducedSpeedLossCap;
        this.mass = builder.mass;
        this.referenceArea = builder.referenceArea;
        this.length = builder.length;
        this.thrustCurve = builder.thrustCurve;
        this.dragCoefficientCurve = builder.dragCoefficientCurve;
        this.normalForceCoefficientCurve = builder.normalForceCoefficientCurve;
        this.controlAuthorityCurve = builder.controlAuthorityCurve;
        this.maxG = builder.maxG;
        this.maxAoA = builder.maxAoA;
        this.maxGRate = builder.maxGRate;
        this.referenceAltitude = builder.referenceAltitude;
        this.referenceLaunchSpeed = builder.referenceLaunchSpeed;
        this.referenceTargetSpeed = builder.referenceTargetSpeed;
    }

    public static MissilePhysicsProfile fromJson(Map<String, Object> root,
                                                 MissilePhysicsProfile fallback) {
        Builder builder = fallback == null ? new Builder() : fallback.toBuilder();
        builder.id = Json.string(root, "id", builder.id);
        double statedMass = Json.number(root, "mass", Double.NaN);
        if (Double.isFinite(statedMass) && statedMass > 0.0D) {
            builder.mass = statedMass;
            builder.massStated = true;
        }
        builder.referenceArea = positive(
            Json.number(root, "referenceArea", builder.referenceArea),
            builder.referenceArea);
        builder.length = positive(Json.number(root, "length", builder.length),
            builder.length);

        Map<String, Object> motor = Json.object(root, "motor");
        double[][] thrust = Json.curve(motor != null ? motor : root, "thrustCurve");
        if (thrust != null) {
            builder.thrustCurve = CurveInterpolator.of(thrust);
        }

        Map<String, Object> aero = Json.object(root, "aerodynamics");
        Map<String, Object> aeroSource = aero != null ? aero : root;
        double[][] drag = Json.curve(aeroSource, "dragCoefficientCurve");
        if (drag != null) {
            builder.dragCoefficientCurve = CurveInterpolator.of(drag);
        }
        double[][] normal = Json.curve(aeroSource, "normalForceCoefficientCurve");
        if (normal != null) {
            builder.normalForceCoefficientCurve = CurveInterpolator.of(normal);
        }
        double[][] authority = Json.curve(aeroSource, "controlAuthorityCurve");
        if (authority != null) {
            builder.controlAuthorityCurve = CurveInterpolator.of(authority);
        }

        Map<String, Object> limits = Json.object(root, "limits");
        Map<String, Object> limitSource = limits != null ? limits : root;
        builder.maxG = positive(Json.number(limitSource, "maxG", builder.maxG),
            builder.maxG);
        builder.maxAoA = positive(Json.number(limitSource, "maxAoA", builder.maxAoA),
            builder.maxAoA);
        builder.maxGRate = Json.number(limitSource, "maxGRate", builder.maxGRate);

        Map<String, Object> launch = Json.object(root, "launch");
        if (launch != null) {
            builder.launchMaxRange = Json.number(launch, "maxRange", builder.launchMaxRange);
            builder.launchMinRange = Json.number(launch, "minRange", builder.launchMinRange);
            builder.launchCooldownTicks = (int) Math.round(Json.number(launch, "cooldownTicks", builder.launchCooldownTicks));
            builder.warmupSeconds = Json.number(launch, "warmupSeconds", builder.warmupSeconds);
            builder.carriedPerAircraft = (int) Math.round(Json.number(launch, "carried", builder.carriedPerAircraft));
            builder.rearmIntervalTicks = (int) Math.round(Json.number(launch, "rearmTicks", builder.rearmIntervalTicks));
        }

        Map<String, Object> seeker = Json.object(root, "seeker");
        if (seeker != null) {
            builder.seekerGimbalDegrees = Json.number(seeker, "gimbalDegrees", builder.seekerGimbalDegrees);
            builder.navigationConstant = Json.number(seeker, "navigationConstant", builder.navigationConstant);
            builder.maxLeadTicks = (int) Math.round(Json.number(seeker, "maxLeadTicks", builder.maxLeadTicks));
            if (seeker.containsKey("activeHoming")) {
                builder.activeHoming = Json.bool(seeker, "activeHoming", true);
            }
            builder.seekerType = amrac.weapons.SeekerType.parse(
                Json.string(seeker, "type", null));
            builder.seekerFovDegrees = Json.number(seeker, "fovDegrees", builder.seekerFovDegrees);
            builder.seekerActivationRange = Json.number(seeker, "activationRange", builder.seekerActivationRange);
            builder.velocityGate = Json.number(seeker, "velocityGate", builder.velocityGate);
            if (seeker.containsKey("velocityGateLookDownOnly")) {
                builder.velocityGateLookDownOnly =
                    Json.bool(seeker, "velocityGateLookDownOnly", false);
            }
            builder.chaffSusceptibility = Json.number(seeker, "chaffSusceptibility", builder.chaffSusceptibility);
            builder.flareSusceptibility = Json.number(seeker, "flareSusceptibility", builder.flareSusceptibility);
            builder.heatSourceCoefficient = Json.number(seeker, "heatSourceCoefficient", builder.heatSourceCoefficient);
            builder.burnThroughRange = Json.number(seeker, "burnThroughRange", builder.burnThroughRange);
            if (seeker.containsKey("extrapolateOnLoss")) {
                builder.extrapolateOnLoss = Json.bool(seeker, "extrapolateOnLoss", true);
            }
            if (seeker.containsKey("extrapolateAfterDecoy")) {
                builder.extrapolateAfterDecoy = Json.bool(seeker, "extrapolateAfterDecoy", false);
            } else if (seeker.containsKey("extrapolateOnLoss")) {
                builder.extrapolateAfterDecoy = builder.extrapolateOnLoss;
            }
            builder.gimbalMemorySeconds = Json.number(seeker, "gimbalMemorySeconds", builder.gimbalMemorySeconds);
            builder.gimbalConfirmSeconds = Json.number(seeker, "gimbalConfirmSeconds", builder.gimbalConfirmSeconds);
            if (seeker.containsKey("twoWayDatalink")) {
                builder.twoWayDatalink = Json.bool(seeker, "twoWayDatalink", false);
            }
            builder.reacquireSeconds = Json.number(seeker, "reacquireSeconds", builder.reacquireSeconds);
            builder.scanRateDegreesPerSecond = Json.number(seeker, "scanRateDegreesPerSecond", builder.scanRateDegreesPerSecond);
            builder.confirmSeconds = Json.number(seeker, "confirmSeconds", builder.confirmSeconds);
            builder.inertialDriftMetresPerSecond = Json.number(seeker, "inertialDriftMetresPerSecond", builder.inertialDriftMetresPerSecond);
            builder.datalinkUpdateSeconds = Json.number(seeker, "datalinkUpdateSeconds", builder.datalinkUpdateSeconds);
            builder.datalinkErrorDegrees = Json.number(seeker, "datalinkErrorDegrees", builder.datalinkErrorDegrees);
        }

        Map<String, Object> loft = Json.object(root, "loft");
        if (loft != null) {
            builder.loftAngleDegrees = Json.number(loft, "angleDegrees", builder.loftAngleDegrees);
            builder.loftFullRange = Json.number(loft, "fullRange", builder.loftFullRange);
            builder.loftMinRange = Json.number(loft, "minRange", builder.loftMinRange);
            builder.loftCeiling = Json.number(loft, "ceiling", builder.loftCeiling);
        }

        Map<String, Object> induced = Json.object(root, "inducedDrag");
        if (induced != null) {
            builder.inducedDragFactor = Json.number(induced, "factor", builder.inducedDragFactor);
            builder.inducedSpeedLossPerG2 = Json.number(induced, "speedLossPerG2", builder.inducedSpeedLossPerG2);
            builder.inducedSpeedLossCap = Json.number(induced, "speedLossCap", builder.inducedSpeedLossCap);
        }

        Map<String, Object> warhead = Json.object(root, "warhead");
        if (warhead != null) {
            builder.explosionPower = Json.number(warhead, "explosionPower", builder.explosionPower);
            builder.proximityFuseRadius = Json.number(warhead, "proximityFuseRadius", builder.proximityFuseRadius);
            builder.armingDistance = Json.number(warhead, "armingDistance", builder.armingDistance);
        }

        Map<String, Object> carriage = Json.object(root, "carriage");
        if (carriage != null) {
            builder.carriedDragArea = Json.number(carriage, "dragArea", builder.carriedDragArea);
        }

        builder.lifetimeTicks = (int) Math.round(Json.number(root, "lifetimeTicks", builder.lifetimeTicks));

        Map<String, Object> reference = Json.object(root, "referenceShot");
        Map<String, Object> referenceSource = reference != null ? reference : root;
        builder.referenceAltitude = Json.number(referenceSource, "altitude",
            builder.referenceAltitude);
        builder.referenceLaunchSpeed = positive(
            Json.number(referenceSource, "launchSpeed", builder.referenceLaunchSpeed),
            builder.referenceLaunchSpeed);
        builder.referenceTargetSpeed = positive(
            Json.number(referenceSource, "targetSpeed", builder.referenceTargetSpeed),
            builder.referenceTargetSpeed);
        return builder.build();
    }

    public double launchMaxRange() {
        return launchMaxRange;
    }

    public double launchMinRange() {
        return launchMinRange;
    }

    public int launchCooldownTicks() {
        return launchCooldownTicks;
    }

    public double warmupSeconds() {
        return warmupSeconds;
    }

    public int carriedPerAircraft() {
        return carriedPerAircraft;
    }

    public int rearmIntervalTicks() {
        return rearmIntervalTicks;
    }

    public double seekerGimbalDegrees() {
        return seekerGimbalDegrees;
    }

    public double navigationConstant() {
        return navigationConstant;
    }

    public int maxLeadTicks() {
        return maxLeadTicks;
    }

    public Boolean activeHoming() {
        return activeHoming;
    }

    public amrac.weapons.SeekerType seekerType() {
        return seekerType;
    }

    public double seekerFovDegrees() {
        return seekerFovDegrees;
    }

    public double seekerActivationRange() {
        return seekerActivationRange;
    }

    public double velocityGate() {
        return velocityGate;
    }

    public Boolean velocityGateLookDownOnly() {
        return velocityGateLookDownOnly;
    }

    public double burnThroughRange() {
        return burnThroughRange;
    }

    public double chaffSusceptibility() {
        return chaffSusceptibility;
    }

    public double flareSusceptibility() {
        return flareSusceptibility;
    }

    public double heatSourceCoefficient() {
        return heatSourceCoefficient;
    }

    public Boolean extrapolateOnLoss() {
        return extrapolateOnLoss;
    }

    public Boolean extrapolateAfterDecoy() {
        return extrapolateAfterDecoy;
    }

    public double gimbalMemorySeconds() {
        return gimbalMemorySeconds;
    }

    public double gimbalConfirmSeconds() {
        return gimbalConfirmSeconds;
    }

    public Boolean twoWayDatalink() {
        return twoWayDatalink;
    }

    public double scanRateDegreesPerSecond() {
        return scanRateDegreesPerSecond;
    }

    public double inertialDriftMetresPerSecond() {
        return inertialDriftMetresPerSecond;
    }

    public double datalinkUpdateSeconds() {
        return datalinkUpdateSeconds;
    }

    public double datalinkErrorDegrees() {
        return datalinkErrorDegrees;
    }

    public double reacquireSeconds() {
        return reacquireSeconds;
    }

    public double confirmSeconds() {
        return confirmSeconds;
    }

    public double loftAngleDegrees() {
        return loftAngleDegrees;
    }

    public double loftFullRange() {
        return loftFullRange;
    }

    public double loftMinRange() {
        return loftMinRange;
    }

    public double loftCeiling() {
        return loftCeiling;
    }

    public double inducedDragFactor() {
        return inducedDragFactor;
    }

    public double inducedSpeedLossPerG2() {
        return inducedSpeedLossPerG2;
    }

    public double inducedSpeedLossCap() {
        return inducedSpeedLossCap;
    }

    public double angleOfAttackFor(double normalForceCoefficient) {
        if (!Double.isFinite(normalForceCoefficient)
            || normalForceCoefficient <= 0.0D) {
            return 0.0D;
        }
        double low = 0.0D;
        double high = maxAoA;
        if (normalForceCoefficient(high) <= normalForceCoefficient) {
            return high;
        }
        for (int i = 0; i < 32; i++) {
            double middle = 0.5D * (low + high);
            if (normalForceCoefficient(middle) < normalForceCoefficient) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return 0.5D * (low + high);
    }

    public double explosionPower() {
        return explosionPower;
    }

    public double proximityFuseRadius() {
        return proximityFuseRadius;
    }

    public double armingDistance() {
        return armingDistance;
    }

    public double carriedMass() {
        return massStated ? mass : Double.NaN;
    }

    public double carriedDragArea() {
        return carriedDragArea;
    }

    public int lifetimeTicks() {
        return lifetimeTicks;
    }

    public String id() {
        return id;
    }

    public double mass() {
        return mass;
    }

    public double referenceArea() {
        return referenceArea;
    }

    public double length() {
        return length;
    }

    public CurveInterpolator thrustCurve() {
        return thrustCurve;
    }

    public CurveInterpolator dragCoefficientCurve() {
        return dragCoefficientCurve;
    }

    public CurveInterpolator normalForceCoefficientCurve() {
        return normalForceCoefficientCurve;
    }

    public CurveInterpolator controlAuthorityCurve() {
        return controlAuthorityCurve;
    }

    public double maxG() {
        return maxG;
    }

    public double maxAoA() {
        return maxAoA;
    }

    public double maxGRate() {
        return maxGRate;
    }

    public double referenceAltitude() {
        return referenceAltitude;
    }

    public double referenceLaunchSpeed() {
        return referenceLaunchSpeed;
    }

    public double referenceTargetSpeed() {
        return referenceTargetSpeed;
    }

    public double thrust(double secondsSinceLaunch) {
        double thrust = thrustCurve.interpolate(Math.max(secondsSinceLaunch, 0.0D));
        return Double.isFinite(thrust) && thrust > 0.0D ? thrust : 0.0D;
    }

    public double dragCoefficient(double mach) {
        double coefficient = dragCoefficientCurve.interpolate(Math.max(mach, 0.0D));
        return Double.isFinite(coefficient) && coefficient > 0.0D ? coefficient : 0.0D;
    }

    public double normalForceCoefficient(double angleOfAttackDegrees) {
        double coefficient = normalForceCoefficientCurve.interpolate(
            Math.abs(angleOfAttackDegrees));
        return Double.isFinite(coefficient) && coefficient > 0.0D ? coefficient : 0.0D;
    }

    public double controlAuthority(double mach) {
        double authority = controlAuthorityCurve.interpolate(Math.max(mach, 0.0D));
        if (!Double.isFinite(authority) || authority <= 0.0D) {
            return 0.0D;
        }
        return Math.min(authority, 1.0D);
    }

    public double burnoutSeconds() {
        for (int i = thrustCurve.size() - 1; i >= 0; i--) {
            if (thrustCurve.outputAt(i) > 0.0D) {
                return i + 1 < thrustCurve.size()
                    ? thrustCurve.inputAt(i + 1) : thrustCurve.inputAt(i);
            }
        }
        return 0.0D;
    }

    public double totalImpulse() {
        double impulse = 0.0D;
        for (int i = 1; i < thrustCurve.size(); i++) {
            double width = thrustCurve.inputAt(i) - thrustCurve.inputAt(i - 1);
            impulse += 0.5D * width *
                (thrustCurve.outputAt(i) + thrustCurve.outputAt(i - 1));
        }
        return impulse;
    }

    private final double launchMaxRange;
    private final double launchMinRange;
    private final int launchCooldownTicks;
    private final double warmupSeconds;
    private final int carriedPerAircraft;
    private final int rearmIntervalTicks;
    private final double seekerGimbalDegrees;
    private final double navigationConstant;
    private final int maxLeadTicks;
    private final Boolean activeHoming;
    private final amrac.weapons.SeekerType seekerType;
    private final double seekerFovDegrees;
    private final double seekerActivationRange;
    private final double velocityGate;
    private final Boolean velocityGateLookDownOnly;
    private final double burnThroughRange;
    private final double chaffSusceptibility;
    private final double flareSusceptibility;
    private final double heatSourceCoefficient;
    private final Boolean twoWayDatalink;
    private final Boolean extrapolateOnLoss;
    private final Boolean extrapolateAfterDecoy;
    private final double gimbalMemorySeconds;
    private final double gimbalConfirmSeconds;
    private final double reacquireSeconds;
    private final double scanRateDegreesPerSecond;
    private final double inertialDriftMetresPerSecond;
    private final double datalinkUpdateSeconds;
    private final double datalinkErrorDegrees;
    private final double confirmSeconds;
    private final double explosionPower;
    private final double proximityFuseRadius;
    private final double armingDistance;
    private final boolean massStated;
    private final double carriedDragArea;
    private final int lifetimeTicks;
    private final double loftAngleDegrees;
    private final double loftFullRange;
    private final double loftMinRange;
    private final double loftCeiling;
    private final double inducedDragFactor;
    private final double inducedSpeedLossPerG2;
    private final double inducedSpeedLossCap;

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.id = id;
        builder.mass = mass;
        builder.referenceArea = referenceArea;
        builder.length = length;
        builder.thrustCurve = thrustCurve;
        builder.dragCoefficientCurve = dragCoefficientCurve;
        builder.normalForceCoefficientCurve = normalForceCoefficientCurve;
        builder.controlAuthorityCurve = controlAuthorityCurve;
        builder.maxG = maxG;
        builder.maxAoA = maxAoA;
        builder.maxGRate = maxGRate;
        builder.referenceAltitude = referenceAltitude;
        builder.referenceLaunchSpeed = referenceLaunchSpeed;
        builder.referenceTargetSpeed = referenceTargetSpeed;
        builder.launchMaxRange = launchMaxRange;
        builder.launchMinRange = launchMinRange;
        builder.launchCooldownTicks = launchCooldownTicks;
        builder.warmupSeconds = warmupSeconds;
        builder.carriedPerAircraft = carriedPerAircraft;
        builder.rearmIntervalTicks = rearmIntervalTicks;
        builder.seekerGimbalDegrees = seekerGimbalDegrees;
        builder.navigationConstant = navigationConstant;
        builder.maxLeadTicks = maxLeadTicks;
        builder.activeHoming = activeHoming;
        builder.seekerType = seekerType;
        builder.seekerFovDegrees = seekerFovDegrees;
        builder.seekerActivationRange = seekerActivationRange;
        builder.velocityGate = velocityGate;
        builder.velocityGateLookDownOnly = velocityGateLookDownOnly;
        builder.burnThroughRange = burnThroughRange;
        builder.chaffSusceptibility = chaffSusceptibility;
        builder.flareSusceptibility = flareSusceptibility;
        builder.heatSourceCoefficient = heatSourceCoefficient;
        builder.twoWayDatalink = twoWayDatalink;
        builder.extrapolateOnLoss = extrapolateOnLoss;
        builder.extrapolateAfterDecoy = extrapolateAfterDecoy;
        builder.gimbalMemorySeconds = gimbalMemorySeconds;
        builder.gimbalConfirmSeconds = gimbalConfirmSeconds;
        builder.reacquireSeconds = reacquireSeconds;
        builder.scanRateDegreesPerSecond = scanRateDegreesPerSecond;
        builder.inertialDriftMetresPerSecond = inertialDriftMetresPerSecond;
        builder.datalinkUpdateSeconds = datalinkUpdateSeconds;
        builder.datalinkErrorDegrees = datalinkErrorDegrees;
        builder.confirmSeconds = confirmSeconds;
        builder.explosionPower = explosionPower;
        builder.proximityFuseRadius = proximityFuseRadius;
        builder.armingDistance = armingDistance;
        builder.massStated = massStated;
        builder.carriedDragArea = carriedDragArea;
        builder.lifetimeTicks = lifetimeTicks;
        builder.loftAngleDegrees = loftAngleDegrees;
        builder.loftFullRange = loftFullRange;
        builder.loftMinRange = loftMinRange;
        builder.loftCeiling = loftCeiling;
        builder.inducedDragFactor = inducedDragFactor;
        builder.inducedSpeedLossPerG2 = inducedSpeedLossPerG2;
        builder.inducedSpeedLossCap = inducedSpeedLossCap;
        return builder;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    public static final class Builder {
        private String id = "unnamed";
        private double mass = 150.0D;
        private double referenceArea = 0.05D;
        private double length = 3.6D;
        private CurveInterpolator thrustCurve =
            CurveInterpolator.of(new double[][] {{0.0D, 0.0D}});
        private CurveInterpolator dragCoefficientCurve =
            CurveInterpolator.of(new double[][] {{0.0D, 0.3D}});
        private CurveInterpolator normalForceCoefficientCurve =
            CurveInterpolator.of(new double[][] {{0.0D, 0.0D}, {25.0D, 12.0D}});
        private CurveInterpolator controlAuthorityCurve =
            CurveInterpolator.of(new double[][] {{0.0D, 1.0D}});
        private double launchMaxRange = Double.NaN;
        private double launchMinRange = Double.NaN;
        private int launchCooldownTicks = -1;
        private double warmupSeconds = Double.NaN;
        private int carriedPerAircraft = -1;
        private int rearmIntervalTicks = -1;
        private double seekerGimbalDegrees = Double.NaN;
        private double navigationConstant = Double.NaN;
        private int maxLeadTicks = -1;
        private Boolean activeHoming = null;
        private amrac.weapons.SeekerType seekerType = null;
        private double seekerFovDegrees = Double.NaN;
        private double seekerActivationRange = Double.NaN;
        private double velocityGate = Double.NaN;
        private Boolean velocityGateLookDownOnly = null;
        private double burnThroughRange = Double.NaN;
        private double chaffSusceptibility = Double.NaN;
        private double flareSusceptibility = Double.NaN;
        private double heatSourceCoefficient = Double.NaN;
        private Boolean twoWayDatalink = null;
        private Boolean extrapolateOnLoss = null;
        private Boolean extrapolateAfterDecoy = null;
        private double gimbalMemorySeconds = Double.NaN;
        private double gimbalConfirmSeconds = Double.NaN;
        private double reacquireSeconds = Double.NaN;
        private double scanRateDegreesPerSecond = Double.NaN;
        private double inertialDriftMetresPerSecond = Double.NaN;
        private double datalinkUpdateSeconds = Double.NaN;
        private double datalinkErrorDegrees = Double.NaN;
        private double confirmSeconds = Double.NaN;
        private double explosionPower = Double.NaN;
        private double proximityFuseRadius = Double.NaN;
        private double armingDistance = Double.NaN;
        private boolean massStated = false;
        private double carriedDragArea = Double.NaN;
        private int lifetimeTicks = -1;
        private double loftAngleDegrees = Double.NaN;
        private double loftFullRange = Double.NaN;
        private double loftMinRange = Double.NaN;
        private double loftCeiling = Double.NaN;
        private double inducedDragFactor = Double.NaN;
        private double inducedSpeedLossPerG2 = Double.NaN;
        private double inducedSpeedLossCap = Double.NaN;
        private double maxG = 30.0D;
        private double maxAoA = 25.0D;
        private double maxGRate = Double.NaN;
        private double referenceAltitude = 10000.0D;
        private double referenceLaunchSpeed = 680.0D;
        private double referenceTargetSpeed = 680.0D;

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder mass(double value) {
            this.mass = value;
            this.massStated = true;
            return this;
        }

        public Builder referenceArea(double value) {
            this.referenceArea = value;
            return this;
        }

        public Builder length(double value) {
            this.length = value;
            return this;
        }

        public Builder thrustCurve(double[][] value) {
            this.thrustCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder dragCoefficientCurve(double[][] value) {
            this.dragCoefficientCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder normalForceCoefficientCurve(double[][] value) {
            this.normalForceCoefficientCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder controlAuthorityCurve(double[][] value) {
            this.controlAuthorityCurve = CurveInterpolator.of(value);
            return this;
        }

        public Builder maxG(double value) {
            this.maxG = value;
            return this;
        }

        public Builder maxAoA(double value) {
            this.maxAoA = value;
            return this;
        }

        public MissilePhysicsProfile build() {
            return new MissilePhysicsProfile(this);
        }
    }
}
