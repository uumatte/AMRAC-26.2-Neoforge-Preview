package amrac.weapons;

public final class MissileProfile {
    public final String id;

    public final int missilesCarried;
    public final int launchCooldownTicks;

    public final int warmupTicks;
    public final int rearmIntervalTicks;

    public final int boostTicks;
    public final double boostAcceleration;

    public final double maxSpeed;
    public final double coastDrag;
    public final double gravity;

    public final double maxLoadG;
    public final double maxLoadRate;
    public final double cornerSpeed;
    public final double minLoadG;
    public final double inducedDragPerG2;
    public final double maxInducedLoss;
    public final double maxTurnRate;

    public final double navigationConstant;
    public final int maxLeadTicks;
    public final double seekerGimbalLimit;

    public final double loftAngle;

    public final double loftFullRange;

    public final double loftMinRange;

    public final double loftCeiling;
    public final double inducedDragFactor;

    public final int maxLifetimeTicks;
    public final double proximityFuseRadius;
    public final double armingDistance;
    public final float explosionPower;

    public final double carriedMass;

    public final double carriedDragArea;

    public final boolean activeHoming;

    public final SeekerType seekerType;

    public final double seekerFov;

    public final double seekerActivationRange;

    public final double velocityGate;

    public final boolean velocityGateLookDownOnly;

    public final double burnThroughRange;

    public final double chaffSusceptibility;

    public final double flareSusceptibility;

    public final double heatSourceCoefficient;

    public final boolean twoWayDatalink;
    public final boolean extrapolateOnLoss;
    public final boolean extrapolateAfterDecoy;
    public final int gimbalMemoryTicks;
    public final int gimbalConfirmTicks;

    public final int reacquireTicks;
    public final double seekerScanRate;

    public final double inertialDrift;

    public final int datalinkUpdateTicks;

    public final double datalinkError;

    public final int lockConfirmTicks;

    public final MissileFaction faction;

    public final double maxLaunchRange;
    public final double minLaunchRange;

    private MissileProfile(Builder b) {
        this.id = b.id;
        this.missilesCarried = b.missilesCarried;
        this.launchCooldownTicks = b.launchCooldownTicks;
        this.warmupTicks = b.warmupTicks;
        this.rearmIntervalTicks = b.rearmIntervalTicks;
        this.boostTicks = b.boostTicks;
        this.boostAcceleration = b.boostAcceleration;
        this.maxSpeed = b.maxSpeed;
        this.coastDrag = b.coastDrag;
        this.gravity = b.gravity;
        this.maxLoadG = b.maxLoadG;
        this.maxLoadRate = b.maxLoadRate;
        this.cornerSpeed = b.cornerSpeed;
        this.minLoadG = b.minLoadG;
        this.inducedDragPerG2 = b.inducedDragPerG2;
        this.maxInducedLoss = b.maxInducedLoss;
        this.maxTurnRate = b.maxTurnRate;
        this.navigationConstant = b.navigationConstant;
        this.maxLeadTicks = b.maxLeadTicks;
        this.seekerGimbalLimit = b.seekerGimbalLimit;
        this.loftAngle = b.loftAngle;
        this.loftFullRange = b.loftFullRange;
        this.loftMinRange = b.loftMinRange;
        this.loftCeiling = b.loftCeiling;
        this.inducedDragFactor = b.inducedDragFactor;
        this.maxLifetimeTicks = b.maxLifetimeTicks;
        this.proximityFuseRadius = b.proximityFuseRadius;
        this.armingDistance = b.armingDistance;
        this.explosionPower = b.explosionPower;
        this.carriedMass = b.carriedMass;
        this.carriedDragArea = b.carriedDragArea;
        this.activeHoming = b.activeHoming;
        this.seekerType = b.seekerType == SeekerType.IR ? SeekerType.IR
            : b.activeHoming ? SeekerType.ARH : SeekerType.SARH;
        this.seekerFov = b.seekerFov;
        this.seekerActivationRange = b.seekerActivationRange;
        this.velocityGate = b.velocityGate;
        this.velocityGateLookDownOnly = b.velocityGateLookDownOnly;
        this.burnThroughRange = b.burnThroughRange;
        this.chaffSusceptibility = b.chaffSusceptibility;
        this.flareSusceptibility = b.flareSusceptibility;
        this.heatSourceCoefficient = b.heatSourceCoefficient;
        this.twoWayDatalink = b.twoWayDatalink;
        this.extrapolateOnLoss = b.extrapolateOnLoss;
        this.extrapolateAfterDecoy = b.extrapolateAfterDecoy == null
            ? b.extrapolateOnLoss : b.extrapolateAfterDecoy;
        this.gimbalMemoryTicks = b.gimbalMemoryTicks;
        this.gimbalConfirmTicks = b.gimbalConfirmTicks;
        this.reacquireTicks = b.reacquireTicks;
        this.seekerScanRate = b.seekerScanRate;
        this.inertialDrift = b.inertialDrift;
        this.datalinkUpdateTicks = b.datalinkUpdateTicks;
        this.datalinkError = b.datalinkError;
        this.lockConfirmTicks = b.lockConfirmTicks;
        this.faction = b.faction;
        this.maxLaunchRange = b.maxLaunchRange;
        this.minLaunchRange = b.minLaunchRange;
    }

    public static Builder from(MissileProfile base) {
        return new Builder(base);
    }

    static Builder scratch() {
        return new Builder(null);
    }

    public double maxSpeedBps() {
        return maxSpeed * 20.0D;
    }

    public int burnoutTick() {
        return boostTicks;
    }

    @Override
    public String toString() {
        return id;
    }

    public static final class Builder {
        private String id;
        private int missilesCarried;
        private int launchCooldownTicks;
        private int warmupTicks;
        private int rearmIntervalTicks;
        private int boostTicks;
        private double boostAcceleration;
        private double maxSpeed;
        private double coastDrag;
        private double gravity;
        private double maxLoadG;
        private double maxLoadRate;
        private double cornerSpeed;
        private double minLoadG;
        private double inducedDragPerG2;
        private double maxInducedLoss;
        private double maxTurnRate;
        private double navigationConstant;
        private int maxLeadTicks;
        private double seekerGimbalLimit;
        private double loftAngle;
        private double loftFullRange;
        private double loftMinRange;
        private double loftCeiling = Double.POSITIVE_INFINITY;
        private double inducedDragFactor = Double.NaN;
        private int maxLifetimeTicks;
        private double proximityFuseRadius;
        private double armingDistance;
        private float explosionPower;
        private double carriedMass = 150.0D;
        private double carriedDragArea = 0.020D;
        private boolean activeHoming;
        private SeekerType seekerType;
        private double seekerFov;
        private double seekerActivationRange;
        private double velocityGate;
        private boolean velocityGateLookDownOnly;
        private double burnThroughRange;
        private double chaffSusceptibility;
        private double flareSusceptibility;
        private double heatSourceCoefficient;
        private boolean twoWayDatalink;
        private boolean extrapolateOnLoss = true;
        private Boolean extrapolateAfterDecoy;
        private int gimbalMemoryTicks;
        private int gimbalConfirmTicks = 2;
        private int reacquireTicks;
        private double seekerScanRate;
        private double inertialDrift;
        private int datalinkUpdateTicks;
        private double datalinkError;
        private int lockConfirmTicks;
        private MissileFaction faction = MissileFaction.NATO;
        private double maxLaunchRange;
        private double minLaunchRange;

        private Builder(MissileProfile base) {
            if (base == null) {
                return;
            }
            this.id = base.id;
            this.missilesCarried = base.missilesCarried;
            this.launchCooldownTicks = base.launchCooldownTicks;
            this.warmupTicks = base.warmupTicks;
            this.rearmIntervalTicks = base.rearmIntervalTicks;
            this.boostTicks = base.boostTicks;
            this.boostAcceleration = base.boostAcceleration;
            this.maxSpeed = base.maxSpeed;
            this.coastDrag = base.coastDrag;
            this.gravity = base.gravity;
            this.maxLoadG = base.maxLoadG;
            this.maxLoadRate = base.maxLoadRate;
            this.cornerSpeed = base.cornerSpeed;
            this.minLoadG = base.minLoadG;
            this.inducedDragPerG2 = base.inducedDragPerG2;
            this.maxInducedLoss = base.maxInducedLoss;
            this.maxTurnRate = base.maxTurnRate;
            this.navigationConstant = base.navigationConstant;
            this.maxLeadTicks = base.maxLeadTicks;
            this.seekerGimbalLimit = base.seekerGimbalLimit;
            this.loftAngle = base.loftAngle;
            this.loftFullRange = base.loftFullRange;
            this.loftMinRange = base.loftMinRange;
            this.loftCeiling = base.loftCeiling;
            this.inducedDragFactor = base.inducedDragFactor;
            this.maxLifetimeTicks = base.maxLifetimeTicks;
            this.proximityFuseRadius = base.proximityFuseRadius;
            this.armingDistance = base.armingDistance;
            this.explosionPower = base.explosionPower;
            this.carriedMass = base.carriedMass;
            this.carriedDragArea = base.carriedDragArea;
            this.activeHoming = base.activeHoming;
            this.seekerType = base.seekerType;
            this.seekerFov = base.seekerFov;
            this.seekerActivationRange = base.seekerActivationRange;
            this.velocityGate = base.velocityGate;
            this.velocityGateLookDownOnly = base.velocityGateLookDownOnly;
            this.burnThroughRange = base.burnThroughRange;
            this.chaffSusceptibility = base.chaffSusceptibility;
            this.flareSusceptibility = base.flareSusceptibility;
            this.heatSourceCoefficient = base.heatSourceCoefficient;
            this.twoWayDatalink = base.twoWayDatalink;
            this.extrapolateOnLoss = base.extrapolateOnLoss;
            this.extrapolateAfterDecoy = base.extrapolateAfterDecoy;
            this.gimbalMemoryTicks = base.gimbalMemoryTicks;
            this.gimbalConfirmTicks = base.gimbalConfirmTicks;
            this.reacquireTicks = base.reacquireTicks;
            this.seekerScanRate = base.seekerScanRate;
            this.inertialDrift = base.inertialDrift;
            this.datalinkUpdateTicks = base.datalinkUpdateTicks;
            this.datalinkError = base.datalinkError;
            this.lockConfirmTicks = base.lockConfirmTicks;
            this.faction = base.faction;
            this.maxLaunchRange = base.maxLaunchRange;
            this.minLaunchRange = base.minLaunchRange;
        }

        public Builder id(String v) { this.id = v; return this; }
        public Builder carriedMass(double v) { this.carriedMass = v; return this; }
        public Builder carriedDragArea(double v) {
            this.carriedDragArea = v;
            return this;
        }
        public Builder missilesCarried(int v) { this.missilesCarried = v; return this; }
        public Builder launchCooldownTicks(int v) { this.launchCooldownTicks = v; return this; }
        public Builder warmupTicks(int v) { this.warmupTicks = Math.max(0, v); return this; }
        public Builder rearmIntervalTicks(int v) { this.rearmIntervalTicks = v; return this; }
        public Builder boostTicks(int v) { this.boostTicks = v; return this; }
        public Builder boostAcceleration(double v) { this.boostAcceleration = v; return this; }
        public Builder maxSpeed(double v) { this.maxSpeed = v; return this; }
        public Builder maxSpeedBps(double v) { this.maxSpeed = v / 20.0D; return this; }
        public Builder coastDrag(double v) { this.coastDrag = v; return this; }
        public Builder gravity(double v) { this.gravity = v; return this; }
        public Builder maxLoadG(double v) { this.maxLoadG = v; return this; }
        public Builder maxLoadRate(double v) { this.maxLoadRate = v; return this; }
        public Builder cornerSpeed(double v) { this.cornerSpeed = v; return this; }
        public Builder cornerSpeedBps(double v) { this.cornerSpeed = v / 20.0D; return this; }
        public Builder minLoadG(double v) { this.minLoadG = v; return this; }
        public Builder inducedDragPerG2(double v) { this.inducedDragPerG2 = v; return this; }
        public Builder maxInducedLoss(double v) { this.maxInducedLoss = v; return this; }
        public Builder maxTurnRate(double v) { this.maxTurnRate = v; return this; }
        public Builder navigationConstant(double v) { this.navigationConstant = v; return this; }
        public Builder maxLeadTicks(int v) { this.maxLeadTicks = v; return this; }
        public Builder seekerGimbalLimit(double v) { this.seekerGimbalLimit = v; return this; }
        public Builder loftAngle(double v) { this.loftAngle = v; return this; }
        public Builder loftFullRange(double v) { this.loftFullRange = v; return this; }
        public Builder loftMinRange(double v) { this.loftMinRange = v; return this; }
        public Builder loftCeiling(double v) { this.loftCeiling = v; return this; }
        public Builder inducedDragFactor(double v) { this.inducedDragFactor = v; return this; }
        public Builder maxLifetimeTicks(int v) { this.maxLifetimeTicks = v; return this; }
        public Builder proximityFuseRadius(double v) { this.proximityFuseRadius = v; return this; }
        public Builder armingDistance(double v) { this.armingDistance = v; return this; }
        public Builder explosionPower(float v) { this.explosionPower = v; return this; }
        public Builder activeHoming(boolean v) { this.activeHoming = v; return this; }
        public Builder seekerType(SeekerType v) { this.seekerType = v; return this; }
        public Builder seekerFov(double v) { this.seekerFov = v; return this; }
        public Builder seekerActivationRange(double v) { this.seekerActivationRange = v; return this; }
        public Builder velocityGate(double v) { this.velocityGate = v; return this; }
        public Builder velocityGateLookDownOnly(boolean v) { this.velocityGateLookDownOnly = v; return this; }
        public Builder burnThroughRange(double v) { this.burnThroughRange = Math.max(0.0D, v); return this; }
        public Builder chaffSusceptibility(double v) { this.chaffSusceptibility = v; return this; }
        public Builder flareSusceptibility(double v) { this.flareSusceptibility = v; return this; }
        public Builder heatSourceCoefficient(double v) { this.heatSourceCoefficient = v; return this; }
        public Builder twoWayDatalink(boolean v) { this.twoWayDatalink = v; return this; }
        public Builder extrapolateOnLoss(boolean v) { this.extrapolateOnLoss = v; return this; }
        public Builder extrapolateAfterDecoy(boolean v) { this.extrapolateAfterDecoy = v; return this; }
        public Builder gimbalMemoryTicks(int v) { this.gimbalMemoryTicks = Math.max(0, v); return this; }
        public Builder gimbalConfirmTicks(int v) { this.gimbalConfirmTicks = Math.max(1, v); return this; }
        public Builder reacquireTicks(int v) { this.reacquireTicks = Math.max(0, v); return this; }
        public Builder seekerScanRate(double v) { this.seekerScanRate = Math.max(0.0D, v); return this; }
        public Builder datalinkUpdateTicks(int v) { this.datalinkUpdateTicks = Math.max(0, v); return this; }
        public Builder datalinkError(double v) { this.datalinkError = Double.isFinite(v) ? Math.max(0.0D, v) : 0.0D; return this; }
        public Builder inertialDrift(double v) { this.inertialDrift = Double.isFinite(v) ? Math.max(0.0D, v) : 0.0D; return this; }
        public Builder lockConfirmTicks(int v) { this.lockConfirmTicks = Math.max(0, v); return this; }
        public Builder faction(MissileFaction v) { this.faction = v; return this; }
        public Builder maxLaunchRange(double v) { this.maxLaunchRange = v; return this; }
        public Builder minLaunchRange(double v) { this.minLaunchRange = v; return this; }

        public Builder extraBoostSeconds(double seconds) {
            this.boostTicks += (int) Math.round(seconds * 20.0D);
            return this;
        }

        public MissileProfile build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalStateException("a profile needs an id");
            }
            return new MissileProfile(this);
        }
    }
}
