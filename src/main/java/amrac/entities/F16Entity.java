package amrac.entities;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import amrac.upgrades.shooter.MachineGunFirePolicy;

public class F16Entity extends PlaneEntity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.F16;

    @Override
    public String flightModelId() {
        return FLIGHT_MODEL_ID;
    }
    public static final float DRY_MAX_SPEED = mach(1.47);
    public static final float WET_MAX_SPEED = mach(2.352);

    /**
     * Every jet extends F16Entity. The speed envelope is overridable, but the base constructor
     * calls it before subclass fields exist, so overrides may only return constants.
     */
    protected float getDryMaxSpeed() {
        return newModelDryMaxSpeed(DRY_MAX_SPEED);
    }

    protected float getWetMaxSpeed() {
        return newModelWetMaxSpeed(WET_MAX_SPEED);
    }

    @Override
    public double getMaximumPlausibleSpeed() {
        return newModelMaximumPlausibleSpeed(super.getMaximumPlausibleSpeed());
    }

    @Override
    protected float afterburnerSpoolForModel() {
        return getAfterburnerSpool(1.0F);
    }

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 2.45F;
    private static final float PITCH_RATE_SCALE = 1.35F;

    private static final int JET_FUEL_COST_MULTIPLIER = 4;

    public static final EntityDataAccessor<Float> AFTERBURNER_SPOOL =
        SynchedEntityData.defineId(F16Entity.class, EntityDataSerializers.FLOAT);

    private float afterburnerSpool;
    private boolean afterburnerEngaged;

    public F16Entity(EntityType<? extends F16Entity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(getDryMaxSpeed());
    }

    public F16Entity(EntityType<? extends F16Entity> entityTypeIn, Level worldIn,
                     double x, double y, double z) {
        this(entityTypeIn, worldIn);
        setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AFTERBURNER_SPOOL, 0.0F);
    }

    @Override
    public boolean hasRetractableGear() {
        return true;
    }

    @Override
    public boolean hasFlaps() {
        return true;
    }

    @Override
    public boolean hasMachineGun() {
        return true;
    }

    @Override
    public boolean hasBombRack() {
        return true;
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        afterburnerSpool = AfterburnerPolicy.sanitizeSpool(
            input.getFloatOr("afterburner_spool", 0.0F));
        afterburnerEngaged = input.getBooleanOr("afterburner_engaged", false);
        syncAfterburnerSpool();
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat("afterburner_spool", afterburnerSpool);
        output.putBoolean("afterburner_engaged", afterburnerEngaged);
    }

    @Override
    public void tick() {
        tickAfterburner();
        super.tick();
    }

    private void tickAfterburner() {
        boolean running = isEngineRunning();
        afterburnerEngaged = AfterburnerPolicy.commandedFromDetent(running,
            isAfterburnerEngaged());
        float previousSpool = afterburnerSpool;
        afterburnerSpool = AfterburnerPolicy.approachSpool(afterburnerSpool,
            afterburnerEngaged);
        setMaxSpeed((float) AfterburnerPolicy.maxSpeed(getDryMaxSpeed(),
            getWetMaxSpeed(), afterburnerSpool));
        if (!level().isClientSide() && previousSpool != afterburnerSpool) {
            syncAfterburnerSpool();
        }
    }

    private void syncAfterburnerSpool() {
        if (!level().isClientSide()) {
            entityData.set(AFTERBURNER_SPOOL, afterburnerSpool);
        }
    }

    @Override
    public float getAfterburnerSpool(float partialTicks) {
        if (level().isClientSide() && !isLocalInstanceAuthoritative()) {
            return AfterburnerPolicy.sanitizeSpool(entityData.get(AFTERBURNER_SPOOL));
        }
        return AfterburnerPolicy.sanitizeSpool(afterburnerSpool);
    }

    public boolean isAfterburnerLit() {
        return AfterburnerPolicy.isEffective(getAfterburnerSpool(1.0F));
    }

    @Override
    protected TempMotionVars getMotionVars() {
        TempMotionVars vars = super.getMotionVars();
        vars.stallSpeed = 1.21D * AIRSPEED_SCALE;
        vars.takeOffSpeed = 1.51D * AIRSPEED_SCALE;
        return vars;
    }

    protected float getRollRateScale() {
        return ROLL_RATE_SCALE;
    }

    protected float getPitchRateScale() {
        return PITCH_RATE_SCALE;
    }

    @Override
    protected float getMaxRollAngularRate() {
        return super.getMaxRollAngularRate() * getRollRateScale();
    }

    @Override
    protected float getMaxRollRateChangePerTick() {
        return super.getMaxRollRateChangePerTick() * getRollRateScale();
    }

    @Override
    protected float getMaxPitchAngularRate() {
        return super.getMaxPitchAngularRate() * getPitchRateScale();
    }

    @Override
    protected float getMaxPitchRateChangePerTick() {
        return super.getMaxPitchRateChangePerTick() * getPitchRateScale();
    }

    @Override
    public int getFuelCost() {
        return super.getFuelCost() * JET_FUEL_COST_MULTIPLIER;
    }

    @Override
    public double getEngineResourceDemandFactor() {
        return super.getEngineResourceDemandFactor() *
            AfterburnerPolicy.fuelMultiplier(afterburnerSpool);
    }

    @Override
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return 0.10D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.50925D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 0.9375D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunConvergenceDistance() {
        return 85.0D;
    }

    @Override
    public double getMachineGunMuzzleVelocity() {
        return MachineGunFirePolicy.VULCAN_PROJECTILE_SPEED;
    }

    @Override
    public boolean shouldRenderShooterModel() {
        return false;
    }

    @Override
    public double getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.25D;
    }

    @Override
    public double getAirframeRotationPivotHeight() {
        return super.getAirframeRotationPivotHeight() * MODEL_SCALE;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return MODEL_SCALE;
    }

    @Override
    public boolean shouldRenderPassengers() {
        return false;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.F16;
    }

    @Override
    public java.util.Set<String> barredMissiles() {
        return java.util.Set.of("AIM120L", "AIM9", "MICA", "METEOR");
    }

    @Override
    public boolean hasRadar() {
        return true;
    }

    /**
     * Configuration leaks down the inheritance chain: hasMissiles(), barredMissiles() and
     * missileFaction() inherit from F16Entity/PlaneEntity (the F-16 once lost hasMissiles; the
     * Typhoon, F-15 and MiG-21 once inherited the wrong barred list or faction). Check each when
     * adding or changing an aircraft.
     */
    @Override
    public boolean hasMissiles() {
        return true;
    }

    /**
     * Rails must match the renderer's PYLONS: mesh (x, y, z) is rail (x/16, 1.475 - y/16, -z/16),
     * or a round is drawn in one place and launched from another. The pylon count in LoaderAirframe
     * is a hand copy of pylonCount(); keep them in step.
     */
    @Override
    public double[][] railPositions() {
        return RAILS;
    }

    private static final double[][] RAILS = {
        {-0.7625D, 0.2844D, -0.5925D},
        { 0.7625D, 0.2844D, -0.5925D},
        {-0.9825D, 0.2800D, -0.7050D},
        { 0.9825D, 0.2800D, -0.7050D}
    };
}
