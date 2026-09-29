package amrac.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import amrac.MathUtil;
import amrac.items.MissileItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import amrac.physics.aircraft.AircraftPhysicsResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import amrac.AmracDataSerializers;
import amrac.network.PlaneSyncPolicy;
import amrac.upgrades.shooter.MachineGunFirePolicy;
import amrac.upgrades.shooter.MachineGunThermalPolicy;
import amrac.network.RemotePlaneSmoothingPolicy;
import amrac.weapons.BombingPolicy;
import amrac.weapons.BombingSystem;
import amrac.weapons.MissilePolicy;
import amrac.weapons.AirframeLoadoutPolicy;
import amrac.weapons.MissileFaction;
import amrac.weapons.MissileProfiles;
import amrac.weapons.MissileLoadout;
import amrac.weapons.MissileSystem;
import amrac.weapons.MachineGunSystem;
import amrac.AmracItems;
import amrac.AmracDamage;
import amrac.AmracConfig;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.util.Mth.wrapDegrees;
import static amrac.MathUtil.*;

import amrac.MathUtil.EulerAngles;

@SuppressWarnings("ConstantConditions")
public class PlaneEntity extends Entity {
    public static final int MAX_THROTTLE = 100;
    public static final int DEFAULT_MAX_HEALTH =
        PlaneDurabilityPolicy.DEFAULT_MAX_HEALTH;
    private static final byte HURT_EVENT_ID = 61;
    private static final int HURT_FLASH_DURATION_TICKS = 10;
    private static final double PASSENGER_HITBOX_ROTATION_PIVOT_HEIGHT = 0.875D;
    private static final double PASSENGER_PROJECTILE_HIT_MARGIN = 0.30D;
    private static final double PLAYER_ATTACK_RAY_LENGTH = 6.0D;
    public static final double SPEED_OF_SOUND = 17.0D;

    public static final double AIRSPEED_SCALE = 10.2D / 4.4D;

    private static final double ENTITY_COLLISION_DAMAGE_SPEED_BPS =
        5.0D * AIRSPEED_SCALE;
    private static final float ENTITY_COLLISION_BASE_DAMAGE = 3.0F;
    private static final float ENTITY_COLLISION_DAMAGE_PER_EXCESS_BPS = 1.5F;
    private static final double ENTITY_COLLISION_CONTACT_MARGIN = 0.10D;
    private static final double MAX_ENTITY_COLLISION_SWEEP_DISTANCE = 4.0D;
    private static final int ENTITY_COLLISION_COOLDOWN_TICKS = 10;
    private static final double MIN_ENTITY_COLLISION_KNOCKBACK = 0.25D;
    private static final double ENTITY_COLLISION_KNOCKBACK_PER_BPS = 0.025D;
    private static final double MAX_ENTITY_COLLISION_KNOCKBACK = 1.25D;

    public static float mach(double machNumber) {
        return (float) (SPEED_OF_SOUND * machNumber);
    }

    private static final float BASE_MAX_SPEED = (float) (1.5D * AIRSPEED_SCALE);

    private static final float CONTROL_INPUT_FADE_OUT_RESPONSE = 0.3276F;
    private static final float MAX_CONTROL_INPUT_CHANGE_PER_TICK = 0.60F;
    private static final float ROLL_INPUT_FADE_OUT_RESPONSE = 0.4224F;
    private static final float MAX_ROLL_INPUT_CHANGE_PER_TICK = 0.70F;
    private static final float CONTROL_INPUT_SNAP_EPSILON = 0.005F;
    private static final float PILOT_CONTROL_SENSITIVITY = 0.6375F;
    private static final float PITCH_RATE_RESPONSE = 0.62F;
    private static final float PITCH_RATE_RELEASE_RESPONSE = 0.36F;
    private static final float MAX_PITCH_RATE_CHANGE_PER_TICK =
        ManeuverRatePolicy.MAX_PITCH_RATE_CHANGE_PER_TICK;
    private static final float YAW_RATE_RELEASE_RESPONSE = 0.36F;
    private static final float GROUND_YAW_RATE_SCALE = 1.5F;
    private static final float MAX_YAW_RATE_CHANGE_PER_TICK = 0.45F;
    private static final float ROLL_RATE_RESPONSE = 0.65F;
    private static final float ROLL_RATE_RELEASE_RESPONSE = 0.4375F;
    private static final float MAX_ROLL_RATE_CHANGE_PER_TICK =
        ManeuverRatePolicy.MAX_ROLL_RATE_CHANGE_PER_TICK;
    private static final float MAX_PITCH_ANGULAR_RATE =
        ManeuverRatePolicy.MAX_PITCH_ANGULAR_RATE;
    private static final float MAX_YAW_ANGULAR_RATE = 1.5F;
    private static final float MAX_ROLL_ANGULAR_RATE =
        ManeuverRatePolicy.MAX_ROLL_ANGULAR_RATE;
    private static final float MAX_GROUND_WHEEL_STEERING_AUTHORITY = 0.75F;
    private static final double GROUND_STEERING_BUILD_SPEED_RATIO = 0.18D;
    private static final double GROUND_STEERING_FADE_START_SPEED_RATIO = 0.55D;
    private static final double GROUND_STEERING_FADE_END_SPEED_RATIO = 1.0D;
    private static final double COLLISION_EPSILON = 1.0E-5D;
    private static final double SAFE_HORIZONTAL_IMPACT_SPEED = 0.35D * AIRSPEED_SCALE;
    private static final double SAFE_VERTICAL_LANDING_SPEED = 0.40D * AIRSPEED_SCALE;
    private static final double SAFE_BAD_ATTITUDE_LANDING_SPEED = 0.20D * AIRSPEED_SCALE;
    private static final double SAFE_CEILING_IMPACT_SPEED = 0.30D * AIRSPEED_SCALE;
    private static final double TAKEOFF_SPEED = 80.0D / 20.0D;
    private static final double STALL_SPEED = 1.16D * AIRSPEED_SCALE;
    private static final double WATER_SAMPLE_HEIGHT = 0.35D;
    private static final double WATER_SAMPLE_RADIUS = 0.65D;

    public static final EntityDataAccessor<Integer> MAX_HEALTH = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> HEALTH = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Float> MAX_SPEED = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Boolean> DYING =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> PARKED = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> THROTTLE = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> ENGINE_STARTED = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> GEAR_DOWN = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> GEAR_BROKEN =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SPEED_BRAKE = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> CONTROL_POSE =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> WHEEL_BRAKE =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> FLAPS_DOWN =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> FLAPS_BROKEN =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);

    public static final EntityDataAccessor<Boolean> AOA_LIMITER =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Quaternionfc> Q = SynchedEntityData.defineId(PlaneEntity.class, AmracDataSerializers.QUATERNION);
    public static final EntityDataAccessor<Float> GUN_HEAT = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Boolean> GUN_OVERHEATED = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<String> LOADOUT =
        SynchedEntityData.defineId(PlaneEntity.class,
            EntityDataSerializers.STRING);

    public static final EntityDataAccessor<String> SELECTED_MISSILE =
        SynchedEntityData.defineId(PlaneEntity.class,
            EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.INT);
    public static final int FUEL_GAUGE_FULL = 20000;

    public static final EntityDataAccessor<Float> FUEL_LITRES =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);

    public static final EntityDataAccessor<Boolean> AFTERBURNER =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);

    public static final EntityDataAccessor<Integer> AMMO =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.INT);

    public static final int MAX_AMMO = 1280;

    public static final EntityDataAccessor<Integer> CHAFF =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> FLARE =
        SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.INT);

    public static final double FUEL_DUMP_LITRES_PER_SECOND = 250.0D;

    public static final int FUEL_ITEM_LITRES = 1000;
    private static final double TWIN_MUZZLE_SIDE_OFFSET = 0.75D;
    private static final double TWIN_MUZZLE_HEIGHT = 0.90D;
    private static final double TWIN_MUZZLE_FORWARD_OFFSET = 0.95D;
    private static final double TWIN_MUZZLE_CONVERGENCE_DISTANCE = 130.0D;
    public Quaternionf Q_Client = new Quaternionf();
    public Quaternionf Q_Prev = new Quaternionf();

    private int onGroundTicks;

    public float rotationRoll;
    public float prevRotationRoll;
    private float pitchInput;
    private float yawInput;

    public static final float YAW_AUTHORITY_SCALE = 0.5F;
    private float rollInput;
    private float targetPitchInput;
    private float targetYawInput;
    private float targetRollInput;
    private float pitchAngularRate;
    private float yawAngularRate;
    private float rollAngularRate;
    private boolean groundReverse;
    private float enginePower;
    private float deltaRotation;
    private float deltaRotationLeft;
    private int deltaRotationTicks;

    private int burnTime;
    private int burnTimeTotal;
    private double fuelUseAccumulator;

    private final MachineGunSystem machineGunSystem = new MachineGunSystem(this);
    private final BombingSystem bombingSystem = new BombingSystem(this);
    private final MissileSystem missileSystem = new MissileSystem(this);

    @FunctionalInterface
    public interface EngineSoundHook {
        void play(PlaneEntity plane);
    }

    @Nullable
    private static EngineSoundHook engineSoundHook;

    public static void setEngineSoundHook(EngineSoundHook hook) {
        engineSoundHook = hook;
    }

    public interface AirflowSoundHook {
        void play(PlaneEntity plane);
    }

    @Nullable
    private static AirflowSoundHook airflowSoundHook;

    public static void setAirflowSoundHook(AirflowSoundHook hook) {
        airflowSoundHook = hook;
    }

    /**
     * Engine, airflow and afterburner sounds are registered by the client through hook interfaces;
     * PlaneEntity is common code and must not reference client sound types.
     */
    public interface AfterburnerSoundHook {
        void play(PlaneEntity plane);
    }

    @Nullable
    private static AfterburnerSoundHook afterburnerSoundHook;

    public static void setAfterburnerSoundHook(AfterburnerSoundHook hook) {
        afterburnerSoundHook = hook;
    }

    public boolean mountMessage;
    private int damageTimeout;
    @Nullable
    private Vec3 clientPositionSeen;
    private long clientPositionSeenAt;
    private int hurtFlashTicks;
    public int notMovingTime;
    public int goldenHeartsTimeout = 0;

    private final TempMotionVars tempMotionVars = new TempMotionVars();
    private boolean movingUnderOwnPhysics;
    private boolean horizontalImpactContact;
    private boolean verticalImpactContact;
    private Vec3 previousEntityCollisionCenter;
    private final Map<Integer, Integer> entityCollisionDamageCooldowns = new HashMap<>();
    private int waterSubmersionSampleTick = Integer.MIN_VALUE;
    private double waterSubmersionSampleX;
    private double waterSubmersionSampleY;
    private double waterSubmersionSampleZ;
    private double cachedWaterSubmersion;

    public PlaneEntity(EntityType<? extends PlaneEntity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(BASE_MAX_SPEED);
    }

    @Override
    public float maxUpStep() {
        return 0.9999F;
    }

    public PlaneEntity(EntityType<? extends PlaneEntity> entityTypeIn, Level worldIn,
                       double x, double y, double z) {
        this(entityTypeIn, worldIn);
        setPos(x, y, z);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MAX_HEALTH, DEFAULT_MAX_HEALTH);
        builder.define(HEALTH, DEFAULT_MAX_HEALTH);
        builder.define(Q, new Quaternionf());
        builder.define(MAX_SPEED, BASE_MAX_SPEED);
        builder.define(PARKED, true);
        builder.define(DYING, false);
        builder.define(THROTTLE, 0);
        builder.define(ENGINE_STARTED, false);
        builder.define(GEAR_DOWN, true);
        builder.define(GEAR_BROKEN, false);
        builder.define(FLAPS_DOWN, false);
        builder.define(FLAPS_BROKEN, false);
        builder.define(SPEED_BRAKE, false);
        builder.define(CONTROL_POSE, 0);
        builder.define(WHEEL_BRAKE, false);
        builder.define(AOA_LIMITER, true);
        builder.define(GUN_HEAT, 0.0F);
        builder.define(GUN_OVERHEATED, false);
        builder.define(FUEL, 0);
        builder.define(FUEL_LITRES, 0.0F);
        builder.define(AFTERBURNER, false);
        builder.define(AMMO, 0);
        builder.define(CHAFF, 0);
        builder.define(FLARE, 0);
        builder.define(LOADOUT, MissileLoadout.EMPTY);
        builder.define(SELECTED_MISSILE, "");
    }

    public static final int GEAR_TRAVEL_TICKS = 30;

    private float gearPosition = 1.0F;
    private float gearPositionPrev = 1.0F;
    private float speedBrakePosition;
    private float speedBrakePositionPrev;
    private float flapPosition;
    private float flapPositionPrev;

    public boolean hasRetractableGear() {
        return false;
    }

    public boolean hasFlaps() {
        return false;
    }

    public amrac.physics.aircraft.FlapProfile getFlapProfile() {
        var profile = getFlightModelProfile();
        return profile == null
            ? amrac.physics.aircraft.FlapProfile.DEFAULT
            : profile.flaps();
    }

    public boolean areFlapsDown() {
        return hasFlaps() && !areFlapsBroken() && entityData.get(FLAPS_DOWN);
    }

    public boolean areFlapsBroken() {
        return hasFlaps() && entityData.get(FLAPS_BROKEN);
    }

    public float getFlapPosition() {
        return flapPosition;
    }

    public float getFlapPosition(float partialTicks) {
        return Mth.lerp(Mth.clamp(partialTicks, 0.0F, 1.0F),
            flapPositionPrev, flapPosition);
    }

    public boolean isTooFastForFlaps() {
        return getDeltaMovement().length() > getFlapProfile().placardSpeedBlocksPerTick();
    }

    public boolean setFlapsDown(boolean down) {
        if (!hasFlaps() || areFlapsBroken()) {
            return false;
        }
        if (down && isTooFastForFlaps()) {
            return false;
        }
        entityData.set(FLAPS_DOWN, down);
        return true;
    }

    private void tickFlaps() {
        flapPositionPrev = flapPosition;
        if (!hasFlaps()) {
            return;
        }
        var flaps = getFlapProfile();
        if (!level().isClientSide()
            && entityData.get(FLAPS_DOWN)
            && isTooFastForFlaps()) {
            entityData.set(FLAPS_DOWN, false);
            onFlapsAutoRetracted();
        }
        flapPosition = MathUtil.approach(flapPosition,
            areFlapsDown() ? 1.0F : 0.0F, 1.0F / flaps.travelTicks());
    }

    protected void onFlapsAutoRetracted() {
        if (getControllingPassenger() instanceof Player pilot) {
            amrac.AmracMod.sendOverlay(pilot,
                Component.translatable("amrac.message.flaps_auto_up"), true);
        }
    }

    public boolean isGearDown() {
        return !hasRetractableGear() || (!isGearBroken() && entityData.get(GEAR_DOWN));
    }

    public boolean isGearBroken() {
        return hasRetractableGear() && entityData.get(GEAR_BROKEN);
    }

    public boolean breakGear() {
        if (!hasRetractableGear() || isGearBroken() || level().isClientSide()) {
            return false;
        }
        entityData.set(GEAR_BROKEN, true);
        entityData.set(GEAR_DOWN, false);
        return true;
    }

    public static final double GEAR_DEPLOY_SPEED = 6.5D;

    public static final double GEAR_DESTRUCTION_SPEED = 10.0D;

    public static double gearDeploySpeed() {
        return GEAR_DEPLOY_SPEED * amrac.physics.aircraft.SpeedScale.current();
    }

    public boolean setGearDown(boolean down) {
        if (!hasRetractableGear() || isGearBroken()) {
            return false;
        }
        if (!down && (onGround() || getParked())) {
            return false;
        }
        if (down && !isGearDown() && !onGround() && !isOnWater() &&
            getDeltaMovement().length() > gearDeploySpeed()) {
            return false;
        }
        entityData.set(GEAR_DOWN, down);
        return true;
    }

    private static final float SPEED_BRAKE_RATE = 1.0F / 8.0F;

    public boolean isSpeedBrakeOut() {
        return entityData.get(SPEED_BRAKE);
    }

    public void setSpeedBrakeOut(boolean out) {
        entityData.set(SPEED_BRAKE, out);
    }

    public boolean isWheelBrakeOn() {
        return entityData.get(WHEEL_BRAKE);
    }

    public void setWheelBrakeOn(boolean on) {
        entityData.set(WHEEL_BRAKE, on);
    }

    public boolean hasAngleOfAttackLimiter() {
        return usesNewFlightModel();
    }

    public boolean isAngleOfAttackLimiterEnabled() {
        return hasAngleOfAttackLimiter() && entityData.get(AOA_LIMITER);
    }

    public void setAngleOfAttackLimiterEnabled(boolean enabled) {
        if (hasAngleOfAttackLimiter()) {
            entityData.set(AOA_LIMITER, enabled);
        }
    }

    private float surfacePitch;
    private float surfacePitchPrev;
    private float surfaceRoll;
    private float surfaceRollPrev;
    private float surfaceYaw;
    private float surfaceYawPrev;

    private static final float SURFACE_RESPONSE = 0.45F;

    private void tickControlSurfaces() {
        surfacePitchPrev = surfacePitch;
        surfaceRollPrev = surfaceRoll;
        surfaceYawPrev = surfaceYaw;
        float targetPitch;
        float targetRoll;
        float targetYaw;
        if (level().isClientSide() && !isLocalInstanceAuthoritative()) {
            int packed = entityData.get(CONTROL_POSE);
            targetPitch = unpackAxis(packed >> 16);
            targetRoll = unpackAxis(packed >> 8);
            targetYaw = unpackAxis(packed);
        } else {
            targetPitch = pitchInput;
            targetRoll = rollInput;
            targetYaw = yawInput;
            if (!level().isClientSide()) {
                entityData.set(CONTROL_POSE, (packAxis(pitchInput) << 16)
                    | (packAxis(rollInput) << 8) | packAxis(yawInput));
            }
        }
        surfacePitch += (targetPitch - surfacePitch) * SURFACE_RESPONSE;
        surfaceRoll += (targetRoll - surfaceRoll) * SURFACE_RESPONSE;
        surfaceYaw += (targetYaw - surfaceYaw) * SURFACE_RESPONSE;
    }

    private static int packAxis(float value) {
        float v = Float.isFinite(value) ? Mth.clamp(value, -1.0F, 1.0F) : 0.0F;
        return Math.round(v * 127.0F) & 0xFF;
    }

    private static float unpackAxis(int bits) {
        return ((byte) bits) / 127.0F;
    }

    /**
     * Pose {pitch up, right wing down, nose right} passes the inputs straight through: positive
     * roll/yaw input already is right wing down / nose right in the render frame (negating it once
     * reversed the F-15's surfaces).
     */
    public float[] getControlSurfacePose(float partialTicks) {
        return new float[] {
            Mth.lerp(partialTicks, surfacePitchPrev, surfacePitch),
            Mth.lerp(partialTicks, surfaceRollPrev, surfaceRoll),
            Mth.lerp(partialTicks, surfaceYawPrev, surfaceYaw)
        };
    }

    public float getSpeedBrakePosition(float partialTicks) {
        return Mth.lerp(partialTicks, speedBrakePositionPrev, speedBrakePosition);
    }

    public float getSpeedBrakePosition() {
        return speedBrakePosition;
    }

    protected void tickSpeedBrake() {
        speedBrakePositionPrev = speedBrakePosition;
        float target = isSpeedBrakeOut() ? 1.0F : 0.0F;
        speedBrakePosition += Mth.clamp(target - speedBrakePosition,
            -SPEED_BRAKE_RATE, SPEED_BRAKE_RATE);
        speedBrakePosition = Mth.clamp(speedBrakePosition, 0.0F, 1.0F);
    }

    protected void tickLandingGear() {
        gearPositionPrev = gearPosition;
        if (!hasRetractableGear()) {
            gearPosition = 1.0F;
            gearPositionPrev = 1.0F;
            return;
        }
        if (!level().isClientSide() && gearPosition > 0.0F
            && getDeltaMovement().length() > GEAR_DESTRUCTION_SPEED
                * amrac.physics.aircraft.SpeedScale.current()
            && breakGear()) {
            onGearTornOff();
        }
        gearPosition = MathUtil.approach(gearPosition,
            isGearDown() ? 1.0F : 0.0F, 1.0F / GEAR_TRAVEL_TICKS);
    }

    protected void onGearTornOff() {
        level().playSound(null, getX(), getY(), getZ(),
            SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.6F, 0.6F);
        if (getControllingPassenger() instanceof Player pilot) {
            amrac.AmracMod.sendOverlay(pilot,
                Component.translatable("amrac.message.gear_torn_off"), true);
        }
    }

    public float getGearPosition() {
        return gearPosition;
    }

    public float getGearPosition(float partialTicks) {
        return Mth.lerp(Mth.clamp(partialTicks, 0.0F, 1.0F),
            gearPositionPrev, gearPosition);
    }

    public float getMaxSpeed() {
        return entityData.get(MAX_SPEED);
    }

    public static final float ABSOLUTE_MAX_SPEED = mach(1.6D);

    public static final float DEFAULT_PLAYER_ROLL_INPUT_SCALE = 1.0F / 3.0F;

    public double getStructuralSpeedLimit() {
        var profile = getFlightModelProfile();
        if (profile == null) {
            return -1.0D;
        }
        double altitude = amrac.physics.aircraft
            .FlightModelRegistry.instance().atmosphere().atmosphericAltitude(getY());
        return profile.structuralSpeedLimit(altitude)
            / AircraftFlightModelBridge.TICKS_PER_SECOND;
    }

    /**
     * Player-flown physics runs on the client and the server skips tickFlightPhysics, so structural
     * overspeed is checked separately in the server tick; the anti-cheat speed cap
     * (newModelMaximumPlausibleSpeed) also follows the flight model's structural limit.
     */
    protected void tickStructuralLimit() {
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        if (GroundProximityPolicy.exceedsStructuralLimit(
            getDeltaMovement().length(), getStructuralSpeedLimit(),
            getOnGround() || isOnWater())) {
            breakUpFromStructuralOverspeed();
        }
    }

    private int ticksSinceAirborne = -1;

    public int getTicksSinceAirborne() {
        return ticksSinceAirborne;
    }

    protected void tickAirborneClock(boolean onGroundOrWater) {
        if (onGroundOrWater) {
            ticksSinceAirborne = -1;
        } else if (ticksSinceAirborne < Integer.MAX_VALUE) {
            ticksSinceAirborne++;
        }
    }

    private long groundSoundingTick = Long.MIN_VALUE;
    private double groundSoundingHeight = GroundProximityPolicy.NO_GROUND;

    public double getHeightAboveGround() {
        long now = level().getGameTime();
        if (groundSoundingTick == now) {
            return groundSoundingHeight;
        }
        groundSoundingTick = now;
        groundSoundingHeight = soundGround();
        return groundSoundingHeight;
    }

    private double soundGround() {
        Vec3 from = position();
        Vec3 to = from.subtract(0.0D, GroundProximityPolicy.MAX_SOUNDING_DEPTH, 0.0D);
        HitResult hit = level().clip(new ClipContext(from, to,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) {
            return GroundProximityPolicy.NO_GROUND;
        }
        return Math.max(0.0D, from.y - hit.getLocation().y);
    }

    @Nullable
    public String flightModelId() {
        return null;
    }

    protected float afterburnerSpoolForModel() {
        return 0.0F;
    }

    public float getAfterburnerSpool(float partialTicks) {
        return 0.0F;
    }

    public boolean usesNewFlightModel() {
        String id = flightModelId();
        return id != null && AircraftFlightModelBridge.hasProfile(id);
    }

    @Nullable
    public amrac.physics.aircraft.AircraftPhysicsProfile
            getFlightModelProfile() {
        String id = flightModelId();
        return id == null ? null
            : amrac.physics.aircraft.FlightModelRegistry
                .instance().profile(id);
    }

    @Nullable
    private amrac.physics.aircraft.FlightState lastFlightState;

    protected Quaternionf tickNewFlightModel(TempMotionVars tempMotionVars,
                                             boolean onGroundOrWater,
                                             Quaternionf attitude) {
        boolean applyPitchCommand = true;
        if (onGroundOrWater) {
            applyPitchCommand = tickOnGround(tempMotionVars);
        } else {
            decrementOnGroundTicks();
        }

        AircraftPhysicsResult result = AircraftFlightModelBridge.step(this,
            flightModelId(), tempMotionVars, onGroundOrWater, attitude,
            afterburnerSpoolForModel(), applyPitchCommand);
        if (result == null) {
            return attitude;
        }
        lastFlightState = result.state();

        if (onGroundOrWater) {
            tickRotation(tempMotionVars);
        }

        attitude = applyBodyAngularRates(attitude);

        return applyAerodynamicStability(attitude, tempMotionVars);
    }

    @Nullable
    public amrac.physics.aircraft.FlightState getFlightState() {
        return lastFlightState;
    }

    @Nullable
    public amrac.physics.aircraft.RadarProfile getRadarProfile() {
        var profile = getFlightModelProfile();
        return profile == null ? null : profile.radar();
    }

    public float getPlayerRollInputScale() {
        return usesNewFlightModel() ? 1.0F : DEFAULT_PLAYER_ROLL_INPUT_SCALE;
    }

    protected float newModelDryMaxSpeed(float fallback) {
        var profile = getFlightModelProfile();
        return profile == null ? fallback
            : (float) (profile.militaryLevelSpeed()
                / AircraftFlightModelBridge.TICKS_PER_SECOND);
    }

    protected float newModelWetMaxSpeed(float fallback) {
        var profile = getFlightModelProfile();
        return profile == null ? fallback
            : (float) (profile.afterburnerLevelSpeed()
                / AircraftFlightModelBridge.TICKS_PER_SECOND);
    }

    protected double newModelMaximumPlausibleSpeed(double fallback) {
        var profile = getFlightModelProfile();
        if (profile == null) {
            return fallback;
        }
        double altitude = amrac.physics.aircraft
            .FlightModelRegistry.instance().atmosphere().atmosphericAltitude(getY());
        return Math.max(fallback, profile.structuralSpeedLimit(altitude) * 1.15D
            / AircraftFlightModelBridge.TICKS_PER_SECOND);
    }

    public double getMaximumPlausibleSpeed() {
        return Math.max(0.5D, getMaxSpeed() * 1.5D + 0.5D);
    }

    public void setMaxSpeed(float maxSpeed) {
        float sanitized = Float.isFinite(maxSpeed) ? maxSpeed : BASE_MAX_SPEED;
        entityData.set(MAX_SPEED, Mth.clamp(sanitized, BASE_MAX_SPEED,
            ABSOLUTE_MAX_SPEED));
    }

    public int getThrottle() {
        return entityData.get(THROTTLE);
    }

    public boolean isAfterburnerEngaged() {
        return entityData.get(AFTERBURNER);
    }

    public boolean isAfterburnerLit() {
        return isAfterburnerEngaged() && isEngineRunning();
    }

    public void setAfterburnerEngaged(boolean engaged) {
        entityData.set(AFTERBURNER, engaged);
    }

    public void setThrottle(int throttle) {
        int clamped = Mth.clamp(throttle, 0, MAX_THROTTLE);
        if (clamped < MAX_THROTTLE) {
            entityData.set(AFTERBURNER, false);
        }
        entityData.set(THROTTLE, clamped);
    }

    public void setControlInputs(int throttle, float pitchInput, float yawInput, float rollInput, boolean groundReverse) {
        setControlInputs(throttle, pitchInput, yawInput, rollInput,
            groundReverse, 1.0F, 1.0F);
    }

    public void setControlInputs(int throttle, float pitchInput, float yawInput,
                                 float rollInput, boolean groundReverse,
                                 float pitchSensitivity,
                                 float rollSensitivity) {
        boolean detent = throttle > MAX_THROTTLE;
        setThrottle(throttle);
        if (detent) {
            setAfterburnerEngaged(true);
        }
        float sanitizedPitch = Float.isFinite(pitchInput) ? pitchInput : 0.0F;
        float sanitizedYaw = Float.isFinite(yawInput) ? yawInput : 0.0F;
        float sanitizedRoll = Float.isFinite(rollInput) ? rollInput : 0.0F;
        float pitchMultiplier = sanitizeControlSensitivity(pitchSensitivity);
        float rollMultiplier = sanitizeControlSensitivity(rollSensitivity);
        targetPitchInput = Mth.clamp(sanitizedPitch * pitchMultiplier,
            -1.0F, 1.0F);
        targetYawInput = Mth.clamp(sanitizedYaw, -1.0F, 1.0F);
        targetRollInput = Mth.clamp(sanitizedRoll * rollMultiplier,
            -1.0F, 1.0F);
        this.groundReverse = groundReverse && getThrottle() == 0;
    }

    private static float sanitizeControlSensitivity(float sensitivity) {
        return Float.isFinite(sensitivity)
            ? Mth.clamp(sensitivity, 0.0F, 1.0F) : 1.0F;
    }

    private void clearControlInputs() {
        pitchInput = 0.0F;
        yawInput = 0.0F;
        rollInput = 0.0F;
        targetPitchInput = 0.0F;
        targetYawInput = 0.0F;
        targetRollInput = 0.0F;
        groundReverse = false;
    }

    private void shutdownEngineAndControls() {
        clearControlInputs();
        setThrottle(0);
        setEngineStarted(false);
        enginePower = 0.0F;
    }

    private void tickControlInputs() {
        pitchInput = applyControlInputResponse(pitchInput, targetPitchInput);
        yawInput = applyControlInputResponse(yawInput, targetYawInput);
        rollInput = applyControlInputResponse(rollInput, targetRollInput,
            ROLL_INPUT_FADE_OUT_RESPONSE,
            MAX_ROLL_INPUT_CHANGE_PER_TICK);
    }

    private static float applyControlInputResponse(float current, float target) {
        return applyControlInputResponse(current, target,
            CONTROL_INPUT_FADE_OUT_RESPONSE, MAX_CONTROL_INPUT_CHANGE_PER_TICK);
    }

    private static float applyControlInputResponse(float current, float target,
                                                   float fadeOutResponse,
                                                   float maximumChangePerTick) {
        float difference = target - current;
        if (Math.abs(difference) <= CONTROL_INPUT_SNAP_EPSILON) {
            return target;
        }

        boolean immediateCommand = target != 0.0F &&
            (current == 0.0F || Math.signum(current) != Math.signum(target) ||
                Math.abs(target) > Math.abs(current));
        if (immediateCommand) {
            return target;
        }

        float change = Mth.clamp(difference * fadeOutResponse,
            -maximumChangePerTick, maximumChangePerTick);
        float smoothed = Mth.clamp(current + change, -1.0F, 1.0F);
        return Math.abs(target - smoothed) <= CONTROL_INPUT_SNAP_EPSILON ? target : smoothed;
    }

    public Quaternionf getQ() {
        return new Quaternionf(entityData.get(Q));
    }

    public void setQ(Quaternionf q) {
        entityData.set(Q, normalizeQuaternion(q));
    }

    public Quaternionf getQ_Client() {
        return new Quaternionf(Q_Client);
    }

    public void setQ_Client(Quaternionf q) {
        Q_Client = normalizeQuaternion(q);
    }

    public Quaternionf getQ_Prev() {
        return new Quaternionf(Q_Prev);
    }

    public void setQ_prev(Quaternionf q) {
        Q_Prev = normalizeQuaternion(q);
    }

    public void setPlacedYaw(float yaw) {
        float normalizedYaw = Mth.wrapDegrees(yaw);
        Quaternionf placedAttitude = normalizeQuaternion(
            toQuaternion(normalizedYaw, 0.0D, 0.0D));
        setYRot(normalizedYaw);
        yRotO = normalizedYaw;
        setXRot(0.0F);
        xRotO = 0.0F;
        rotationRoll = 0.0F;
        prevRotationRoll = 0.0F;
        pitchAngularRate = 0.0F;
        yawAngularRate = 0.0F;
        rollAngularRate = 0.0F;
        setQ(placedAttitude);
        setQ_Client(placedAttitude);
        setQ_prev(placedAttitude);
    }

    public void setHealth(int health) {
        entityData.set(HEALTH, Math.max(health, 0));
    }

    public int getHealth() {
        return entityData.get(HEALTH);
    }

    public int getMaxHealth() {
        return entityData.get(MAX_HEALTH);
    }

    public void setParked(Boolean val) {
        entityData.set(PARKED, val);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean getParked() {
        return entityData.get(PARKED);
    }

    public boolean isPowered() {
        if (!isAlive()) {
            return false;
        }
        if (AmracConfig.infiniteFuel()) {
            return true;
        }
        if (!isFuelMetered() && isCreative()) {
            return true;
        }
        return entityData.get(FUEL) > 0;
    }

    public int getCountermeasureCapacity() {
        var profile = getFlightModelProfile();
        return profile == null ? 0 : profile.countermeasures().capacity();
    }

    public int getChaffCount() {
        return entityData.get(CHAFF);
    }

    public int getFlareCount() {
        return entityData.get(FLARE);
    }

    public boolean setCountermeasures(int chaff, int flare) {
        if (chaff < 0 || flare < 0
                || chaff + flare > getCountermeasureCapacity()) {
            return false;
        }
        entityData.set(CHAFF, chaff);
        entityData.set(FLARE, flare);
        return true;
    }

    private int countermeasureCooldown;

    public boolean releaseCountermeasure(Player pilot,
                                         amrac.weapons.CountermeasureService.Kind kind) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)
                || getControllingPassenger() != pilot || isDying()
                || countermeasureCooldown > 0) {
            return false;
        }
        EntityDataAccessor<Integer> store =
            kind == amrac.weapons.CountermeasureService.Kind.CHAFF ? CHAFF : FLARE;
        int left = entityData.get(store);
        if (left <= 0) {
            return false;
        }
        entityData.set(store, left - 1);
        countermeasureCooldown =
            amrac.weapons.CountermeasureService.RELEASE_COOLDOWN_TICKS;
        amrac.weapons.CountermeasureService.release(serverLevel, this, pilot, kind);
        return true;
    }

    public long ticksSinceClientPosition() {
        if (level().isClientSide()
                || !(getControllingPassenger() instanceof ServerPlayer)) {
            clientPositionSeen = null;
            return 0L;
        }
        long now = level().getGameTime();
        Vec3 here = position();
        if (clientPositionSeen == null
                || here.distanceToSqr(clientPositionSeen) > 1.0E-8D) {
            clientPositionSeen = here;
            clientPositionSeenAt = now;
            return 0L;
        }
        return Math.max(0L, now - clientPositionSeenAt);
    }

    public Vec3 silentClientOffset() {
        int ticks = amrac.network.PlaneSyncPolicy.extrapolationTicks(
            ticksSinceClientPosition());
        return ticks == 0 ? Vec3.ZERO : getDeltaMovement().scale(ticks);
    }

    public amrac.physics.aircraft.FuelProfile getFuelProfile() {
        var profile = getFlightModelProfile();
        return profile == null
            ? amrac.physics.aircraft.FuelProfile.NONE
            : profile.fuel();
    }

    public boolean isFuelMetered() {
        return getFuelProfile().isMetered();
    }

    public double getFuelCapacityLitres() {
        return getFuelProfile().capacityLitres();
    }

    public double getFuelLitres() {
        return entityData.get(FUEL_LITRES);
    }

    public void setFuelLitres(double litres) {
        double capacity = getFuelCapacityLitres();
        double clamped = Double.isFinite(litres)
            ? Math.max(0.0D, Math.min(litres, capacity)) : 0.0D;
        entityData.set(FUEL_LITRES, (float) clamped);
        if (capacity > 0.0D) {
            entityData.set(FUEL, clamped > 0.0D ? FUEL_GAUGE_FULL : 0);
        }
    }

    public double addFuelLitres(double litres) {
        if (!isFuelMetered() || !Double.isFinite(litres) || litres <= 0.0D) {
            return 0.0D;
        }
        double before = getFuelLitres();
        double accepted = Math.min(litres, getFuelCapacityLitres() - before);
        if (accepted <= 0.0D) {
            return 0.0D;
        }
        setFuelLitres(before + accepted);
        return accepted;
    }

    public double getStoresMassKilograms() {
        return amrac.weapons.MissileLoadout
            .storesMass(getLoadout());
    }

    public double getStoresDragArea() {
        return amrac.weapons.MissileLoadout
            .storesDragArea(getLoadout());
    }

    public double getFuelMassOffsetKilograms() {
        return getFuelProfile().massOffsetKilograms(getFuelLitres());
    }

    public float getFuelLitresFraction() {
        double capacity = getFuelCapacityLitres();
        if (capacity <= 0.0D) {
            return 1.0F;
        }
        return (float) Mth.clamp(getFuelLitres() / capacity, 0.0D, 1.0D);
    }

    private boolean dumpingFuel;

    public void setDumpingFuel(boolean dumping) {
        this.dumpingFuel = dumping;
    }

    public boolean isDumpingFuel() {
        return dumpingFuel && getFuelLitres() > 0.0D;
    }

    private void tickMeteredFuel() {
        var fuel = getFuelProfile();
        if (AmracConfig.infiniteFuel()) {
            setFuelLitres(getFuelCapacityLitres());
            return;
        }

        double litres = getFuelLitres();
        if (dumpingFuel && litres > 0.0D) {
            litres = Math.max(0.0D, litres
                - FUEL_DUMP_LITRES_PER_SECOND / AircraftFlightModelBridge.TICKS_PER_SECOND);
        }

        if (isEngineRunning() && !getParked()) {
            double throttle = getThrottle() / (double) MAX_THROTTLE;
            double perSecond = fuel.flowLitresPerSecond(throttle,
                afterburnerSpoolForModel());
            litres = Math.max(0.0D,
                litres - perSecond / AircraftFlightModelBridge.TICKS_PER_SECOND);
        }

        setFuelLitres(litres);
    }

    private void tickFuel() {
        if (level().isClientSide()) {
            return;
        }
        if (isFuelMetered()) {
            tickMeteredFuel();
            return;
        }
        if (isCreative() || AmracConfig.infiniteFuel()) {
            entityData.set(FUEL, FUEL_GAUGE_FULL);
            return;
        }

        double demand = getEngineResourceDemandFactor();
        if (demand > 0.0D && burnTime > 0) {
            fuelUseAccumulator += Math.max(0, getFuelCost()) * demand;
            int consumed = (int) fuelUseAccumulator;
            if (consumed > 0) {
                fuelUseAccumulator -= consumed;
                burnTime = Math.max(0, burnTime - consumed);
            }
        }

        if (burnTime <= 0 && demand > 0.0D) {
            refuelFromPilot();
        }
        entityData.set(FUEL, burnTime);
    }

    private void refuelFromPilot() {
        Player pilot = getPlayer();
        if (pilot == null) {
            return;
        }
        Inventory inventory = pilot.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); ++slot) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int itemBurnTime = level().fuelValues().burnDuration(stack);
            if (itemBurnTime <= 0) {
                continue;
            }
            burnTime = itemBurnTime;
            burnTimeTotal = itemBurnTime;
            ItemStackTemplate remainderTemplate =
                stack.getItem().getCraftingRemainder();
            ItemStack remainder = remainderTemplate != null
                ? remainderTemplate.create() : ItemStack.EMPTY;
            stack.shrink(1);
            if (stack.isEmpty()) {
                inventory.setItem(slot, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
            } else if (!remainder.isEmpty() && !inventory.add(remainder)) {
                pilot.drop(remainder, false);
            }
            inventory.setChanged();
            return;
        }
    }

    public int getFuel() {
        return entityData.get(FUEL);
    }

    public float getFuelFraction() {
        if (isFuelMetered()) {
            return AmracConfig.infiniteFuel()
                ? 1.0F : getFuelLitresFraction();
        }
        if (isCreative() || AmracConfig.infiniteFuel()) {
            return 1.0F;
        }
        int total = burnTimeTotal;
        return total <= 0 ? 0.0F : Mth.clamp(getFuel() / (float) total, 0.0F, 1.0F);
    }

    public boolean isEngineStarted() {
        return entityData.get(ENGINE_STARTED);
    }

    private void setEngineStarted(boolean started) {
        entityData.set(ENGINE_STARTED, started);
    }

    public boolean isEngineRunning() {
        return EngineRunPolicy.shouldRun(isEngineStarted(),
            isPilot(getControllingPassenger())) && isPowered();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand,
                                      Vec3 hitVec) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof amrac.items.AircraftRemoverItem) {
            return amrac.items.AircraftRemoverItem.remove(this, player);
        }
        if (held.getItem() instanceof MissileItem missile && hasMissiles()
            && !player.isShiftKeyDown()) {
            if (level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (!canCarry(missile.profileId())) {
                boolean railFits = missileFaction().accepts(
                    MissileProfiles.byId(missile.profileId()).faction);
                amrac.AmracMod.sendOverlay(player,
                    railFits
                    ? Component.translatable(
                        "amrac.message.missile_not_cleared",
                        Component.translatable(missile.getDescriptionId()))
                    : Component.translatable(
                        "amrac.message.missile_wrong_rail",
                        Component.translatable(missile.getDescriptionId()),
                        Component.translatable(missileFaction().displayKey())),
                    true);
                return InteractionResult.CONSUME;
            }
            String loaded = loadMissileAt(position().add(hitVec),
                missile.profileId());
            if (loaded == null) {
                amrac.AmracMod.sendOverlay(player,
                    Component.translatable(
                    "amrac.message.pylons_full"), true);
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.7F, 1.4F);
            amrac.AmracMod.sendOverlay(player,
                Component.translatable(
                "amrac.message.missile_loaded",
                Component.translatable(missile.getDescriptionId()),
                getMissileCount(), pylonCount()), true);
            return InteractionResult.CONSUME;
        }
        if (!player.isShiftKeyDown()) {
            InteractionResult loaded = tryLoadStores(player, hand, held);
            if (loaded != null) {
                return loaded;
            }
        }
        return interactNormally(player, hand);
    }

    private InteractionResult tryLoadStores(Player player, InteractionHand hand,
                                            ItemStack held) {
        if (held.is(AmracItems.BULLET) && hasMachineGun()) {
            if (level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            int taken = loadMachineGunAmmo(held.getCount());
            if (taken <= 0) {
                amrac.AmracMod.sendOverlay(player,
                    Component.translatable("amrac.message.ammo_full",
                        MAX_AMMO), true);
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(taken);
            }
            level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.7F, 1.1F);
            amrac.AmracMod.sendOverlay(player,
                Component.translatable("amrac.message.ammo_loaded",
                    getMachineGunAmmoCount(), MAX_AMMO), true);
            return InteractionResult.CONSUME;
        }

        if (held.is(AmracItems.CHAFF) || held.is(AmracItems.FLARE)) {
            int capacity = getCountermeasureCapacity();
            if (capacity <= 0) {
                return null;
            }
            if (level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            boolean chaff = held.is(AmracItems.CHAFF);
            int room = capacity - getChaffCount() - getFlareCount();
            if (room <= 0) {
                amrac.AmracMod.sendOverlay(player,
                    Component.translatable("amrac.message.countermeasures_full",
                        capacity), true);
                return InteractionResult.CONSUME;
            }
            int taken = Math.min(room, held.getCount());
            EntityDataAccessor<Integer> store = chaff ? CHAFF : FLARE;
            entityData.set(store, entityData.get(store) + taken);
            if (!player.getAbilities().instabuild) {
                held.shrink(taken);
            }
            level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.7F, 1.3F);
            amrac.AmracMod.sendOverlay(player,
                Component.translatable("amrac.message.countermeasures_loaded",
                    getChaffCount(), getFlareCount(),
                    getChaffCount() + getFlareCount(), capacity), true);
            return InteractionResult.CONSUME;
        }

        if (held.is(AmracItems.AVIATION_FUEL)) {
            if (level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (!isFuelMetered()) {
                amrac.AmracMod.sendOverlay(player,
                    Component.translatable("amrac.message.fuel_unmetered"),
                    true);
                return InteractionResult.CONSUME;
            }
            double added = addFuelLitres(FUEL_ITEM_LITRES);
            if (added <= 0.0D) {
                amrac.AmracMod.sendOverlay(player,
                    Component.translatable("amrac.message.fuel_full"), true);
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8F, 0.9F);
            amrac.AmracMod.sendOverlay(player,
                Component.translatable("amrac.message.fuel_loaded",
                    Math.round(getFuelLitres()),
                    Math.round(getFuelCapacityLitres())), true);
            return InteractionResult.CONSUME;
        }
        return null;
    }

    private InteractionResult interactNormally(Player player,
                                               InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && itemStack.isEmpty()) {
            ejectPassengers();
            return InteractionResult.SUCCESS;
        }

        if (!level().isClientSide()) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        } else {
            return player.getRootVehicle() == getRootVehicle() ? InteractionResult.FAIL : InteractionResult.SUCCESS;
        }
    }

    public boolean isPickable() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (hurtFlashTicks > 0) {
            --hurtFlashTicks;
        }

        if (deathThroesTicks > 0) {
            tickDeathThroes();
            if (isRemoved()) {
                return;
            }
        }

        Vec3 currentMotion = getDeltaMovement();
        if (!Double.isFinite(currentMotion.x()) || !Double.isFinite(currentMotion.y()) ||
            !Double.isFinite(currentMotion.z())) {
            setDeltaMovement(Vec3.ZERO);
        }
        yRotO = getYRot();
        xRotO = getXRot();
        prevRotationRoll = rotationRoll;
        tickLandingGear();
        tickFlaps();
        tickSpeedBrake();
        tickControlSurfaces();

        if (level().isClientSide() && engineSoundHook != null && isEngineRunning()) {
            engineSoundHook.play(this);
        }

        if (level().isClientSide() && airflowSoundHook != null) {
            airflowSoundHook.play(this);
        }

        if (level().isClientSide() && afterburnerSoundHook != null) {
            afterburnerSoundHook.play(this);
        }

        if (level().isClientSide() && !isLocalInstanceAuthoritative()) {
            tickLerp();
            tickDeltaRotation(getQ_Client());
            return;
        }
        Entity controllingPassenger = getControllingPassenger();
        boolean receivesClientFlightState = !level().isClientSide() &&
            controllingPassenger instanceof ServerPlayer;
        if (isDying() || !isPilot(controllingPassenger)) {
            shutdownEngineAndControls();
        } else {
            setEngineStarted(EngineRunPolicy.nextStarted(isEngineStarted(),
                true, getThrottle()));
            tickControlInputs();
        }

        tickEnginePower();
        TempMotionVars tempMotionVars = getMotionVars();
        double speedScale = amrac.physics.aircraft.SpeedScale.current();
        tempMotionVars.takeOffSpeed *= speedScale;
        tempMotionVars.stallSpeed *= speedScale;
        tempMotionVars.pitchInput = pitchInput;
        tempMotionVars.yawInput = yawInput * YAW_AUTHORITY_SCALE;
        tempMotionVars.rollInput = rollInput;
        tempMotionVars.groundReverse = groundReverse;
        if (isNoGravity()) {
            tempMotionVars.gravity = 0;
        }
        Quaternionf q;
        if (level().isClientSide()) {
            q = getQ_Client();
        } else {
            q = getQ();
        }
        q = normalizeQuaternion(q);

        Vec3 oldMotion = getDeltaMovement();

        updateParkedState(tempMotionVars);

        if (receivesClientFlightState) {
            onGroundTicks = onGround() ? 5 : onGroundTicks - 1;
            ticksSinceClientPosition();
        }
        boolean onGroundOrWater = getOnGround() || isOnWater();
        if (!receivesClientFlightState) {
            q = tickFlightPhysics(tempMotionVars, onGroundOrWater, q);
        }
        tickStructuralLimit();

        Vec3 integratedMotion = getDeltaMovement();
        boolean idleGroundGravityOnly =
            PlaneSyncPolicy.shouldSuppressIdleGroundGravity(
                isVehicle(), onGround() && !isOnWater(),
                getThrottle() > 0 || tempMotionVars.groundReverse,
                oldMotion.lengthSqr(), integratedMotion.horizontalDistanceSqr(),
                integratedMotion.y(), tempMotionVars.gravity);

        if (!receivesClientFlightState && onGroundTicks > -50 &&
            ((oldMotion.length() < PlaneSyncPolicy.IDLE_GROUND_STOP_EPSILON &&
                integratedMotion.length() < PlaneSyncPolicy.IDLE_GROUND_STOP_EPSILON) ||
                idleGroundGravityOnly)) {
            setDeltaMovement(Vec3.ZERO);
        }
        reapplyPosition();

        if (!receivesClientFlightState &&
            (!onGround() || getDeltaMovement().horizontalDistanceSqr() > (double) 1.0E-5F ||
                (tickCount + getId()) % 4 == 0)) {
            boolean onGroundOld = onGround();
            Vec3 motion = getDeltaMovement();
            // Impact damage is checked only on the side actually moving the aircraft (for a player:
            // the client reports, the server rules). The second PLAYER move ServerPlayNetHandler
            // makes to correct a client vehicle is not an impact (see movingUnderOwnPhysics).
            tickStructuralCollisionDamage(motion);
            if ((onGroundOld || isOnWater()) &&
                (motion.lengthSqr() > 0.25 || getThrottle() > 0 || tempMotionVars.groundReverse)) {
                setOnGround(true);
            }
            movingUnderOwnPhysics = true;
            try {
                move(MoverType.SELF, motion);
            } finally {
                movingUnderOwnPhysics = false;
            }
            setOnGround(((motion.y()) == 0.0) ? onGroundOld : onGround());
        }

        if (isAlive() &&
            (!level().isClientSide() || isLocalInstanceAuthoritative())) {
            tickEntityCollisionImpacts();
        }

        tickAutomaticGear();

        setQ_prev(getQ_Client());
        if (!level().isClientSide()) {
            setQ(q);
        }
        tickDeltaRotation(q);

        if (level().isClientSide() && isLocalInstanceAuthoritative()) {
            setQ_Client(q);

            amrac.network.PlaneNetworking.sendRotation(q,
                getDeltaMovement());
        } else {
            ServerPlayer player = (ServerPlayer) getPlayer();
            if (player != null) {
                player.connection.aboveGroundVehicleTickCount = 0;
            }
        }
        if (damageTimeout > 0) {
            --damageTimeout;
        }
        if (countermeasureCooldown > 0) {
            --countermeasureCooldown;
        }
        if (!level().isClientSide() && getHealth() > getMaxHealth() & goldenHeartsTimeout > (getOnGround() ? 300 : 100)) {
            setHealth(getHealth() - 1);
            goldenHeartsTimeout = 0;
        }
        if (goldenHeartsTimeout < 1000 && isPowered()) {
            goldenHeartsTimeout++;
        }

        if (!level().isClientSide()) {
            tickFuel();
            missileSystem.tick();
            machineGunSystem.tick();
            bombingSystem.tick();
        }

        tickLerp();
    }

    private static boolean isFiniteVector(Vec3 vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) &&
            Double.isFinite(vector.z()) && Double.isFinite(vector.lengthSqr());
    }

    public int getFuelCost() {
        return AmracConfig.fuelCost();
    }

    public float getEnginePower() {
        return Float.isFinite(enginePower)
            ? Mth.clamp(enginePower, 0.0F, 1.0F) : 0.0F;
    }

    public double getEngineResourceDemandFactor() {
        if (getThrottle() <= 0 || getParked()) {
            return 0.0D;
        }
        return 0.15D + 0.85D * getEnginePower();
    }

    private boolean updateParkedState(TempMotionVars tempMotionVars) {
        Vec3 oldMotion = getDeltaMovement();
        final boolean parked = (isOnWater() || onGround()) &&
            (oldMotion.length() < 0.1) &&
            (notMovingTime > 20) &&
            (onGround() || isOnWater()) &&
            (getThrottle() == 0) &&
            (!tempMotionVars.groundReverse);
        setParked(parked);
        return parked;
    }

    private void tickEnginePower() {
        boolean engineRunning = isEngineRunning();
        float targetPower = engineRunning
            ? getThrottle() / (float) MAX_THROTTLE : 0.0F;
        if (!Float.isFinite(enginePower)) {
            enginePower = targetPower;
        }
        float response = targetPower > enginePower ? 0.035714286F : 0.04F;
        enginePower += Mth.clamp(targetPower - enginePower, -response, response);
        enginePower = Mth.clamp(enginePower, 0.0F, 1.0F);
    }

    protected TempMotionVars getMotionVars() {
        tempMotionVars.reset();
        return tempMotionVars;
    }

    protected float getMaxPitchAngularRate() {
        return MAX_PITCH_ANGULAR_RATE;
    }

    protected float getMaxPitchRateChangePerTick() {
        return MAX_PITCH_RATE_CHANGE_PER_TICK;
    }

    protected float getMaxRollAngularRate() {
        return MAX_ROLL_ANGULAR_RATE;
    }

    protected float getMaxRollRateChangePerTick() {
        return MAX_ROLL_RATE_CHANGE_PER_TICK;
    }

    private float getDynamicPressureControlAuthority(TempMotionVars tempMotionVars) {
        Vec3 motion = getDeltaMovement();
        double speed = Math.sqrt(motion.horizontalDistanceSqr());
        return (float) BattlefieldFlightModelPolicy.controlAuthority(speed,
            tempMotionVars.stallSpeed);
    }

    private float getGroundYawControlAuthority(TempMotionVars tempMotionVars,
                                                float aerodynamicAuthority) {
        double groundSpeed = Math.sqrt(getDeltaMovement().horizontalDistanceSqr());
        double takeOffSpeed = tempMotionVars.takeOffSpeed;
        if (!Double.isFinite(groundSpeed) || !Double.isFinite(takeOffSpeed) ||
            takeOffSpeed <= 1.0E-6D) {
            return Mth.clamp(aerodynamicAuthority, 0.0F, 1.0F);
        }

        double speedRatio = Math.max(groundSpeed / takeOffSpeed, 0.0D);
        double rollingBlend = smoothStep(speedRatio / GROUND_STEERING_BUILD_SPEED_RATIO);
        double steeringFadeRange = GROUND_STEERING_FADE_END_SPEED_RATIO -
            GROUND_STEERING_FADE_START_SPEED_RATIO;
        if (!Double.isFinite(speedRatio) || !Double.isFinite(steeringFadeRange) ||
            steeringFadeRange <= 1.0E-6D) {
            return Mth.clamp(aerodynamicAuthority, 0.0F, 1.0F);
        }

        double tireGrip = Mth.clamp(tempMotionVars.groundFrictionMultiplier,
            0.0D, 1.0D);
        double highSpeedFade = 1.0D - smoothStep((speedRatio -
            GROUND_STEERING_FADE_START_SPEED_RATIO) / steeringFadeRange);
        double wheelAuthority = MAX_GROUND_WHEEL_STEERING_AUTHORITY *
            rollingBlend * highSpeedFade * tireGrip;

        double clampedAerodynamicAuthority = Mth.clamp(
            aerodynamicAuthority, 0.0F, 1.0F);
        double combinedAuthority = 1.0D -
            (1.0D - clampedAerodynamicAuthority) *
            (1.0D - Mth.clamp(wheelAuthority, 0.0D, 1.0D));
        return Double.isFinite(combinedAuthority)
            ? (float) Mth.clamp(combinedAuthority, 0.0D, 1.0D)
            : (float) clampedAerodynamicAuthority;
    }

    protected void tickDeltaRotation(Quaternionf q) {
        EulerAngles angels1 = toEulerAngles(q);
        setXRot((float) angels1.pitch);
        setYRot((float) angels1.yaw);
        rotationRoll = (float) angels1.roll;

        float d = (float) wrapSubtractDegrees(yRotO, getYRot());
        if (rotationRoll >= 90 && prevRotationRoll <= 90) {
            d = 0;
        }
        int diff = 3;

        deltaRotationTicks = Math.min(10, Math.max((int) Math.abs(deltaRotationLeft) * 5, deltaRotationTicks));
        deltaRotationLeft *= 0.7;
        deltaRotationLeft += d;
        deltaRotationLeft = wrapDegrees(deltaRotationLeft);
        deltaRotation = Math.min(Math.abs(deltaRotationLeft), diff) * Math.signum(deltaRotationLeft);
        deltaRotationLeft -= deltaRotation;
        if (!(deltaRotation > 0)) {
            deltaRotationTicks--;
        }
    }

    protected void tickRotation(TempMotionVars tempMotionVars) {
        float yawAuthority = getDynamicPressureControlAuthority(tempMotionVars);
        if (getOnGround() && !isOnWater()) {
            yawAuthority = getGroundYawControlAuthority(tempMotionVars, yawAuthority);
        }
        float targetYawRate = -tempMotionVars.yawInput * GROUND_YAW_RATE_SCALE *
            yawAuthority;
        yawAngularRate = approachCommandedAngularRate(yawAngularRate,
            targetYawRate, YAW_RATE_RELEASE_RESPONSE,
            MAX_YAW_RATE_CHANGE_PER_TICK, MAX_YAW_ANGULAR_RATE);

        float maximumRollRate = getMaxRollAngularRate();
        float levelingRate = Mth.clamp(
            Mth.wrapDegrees(-rotationRoll) * 0.2F,
            -maximumRollRate, maximumRollRate);
        rollAngularRate = approachAngularRate(rollAngularRate, levelingRate,
            ROLL_RATE_RESPONSE, ROLL_RATE_RELEASE_RESPONSE,
            getMaxRollRateChangePerTick(), maximumRollRate);
    }

    private float approachCommandedAngularRate(float current, float target,
                                               float releaseResponse,
                                               float maximumReleaseChange,
                                               float maximumRate) {
        if (!Float.isFinite(current) || !Float.isFinite(target)) {
            return 0.0F;
        }
        float limitedTarget = Mth.clamp(target, -maximumRate, maximumRate);
        boolean immediateCommand = limitedTarget != 0.0F &&
            (current == 0.0F || Math.signum(current) != Math.signum(limitedTarget) ||
                Math.abs(limitedTarget) > Math.abs(current));
        if (immediateCommand) {
            float next = current + (limitedTarget - current) *
                PILOT_CONTROL_SENSITIVITY;
            return Math.abs(limitedTarget - next) <= 1.0E-4F
                ? limitedTarget
                : Mth.clamp(next, -maximumRate, maximumRate);
        }
        return approachAngularRate(current, limitedTarget, releaseResponse,
            releaseResponse, maximumReleaseChange, maximumRate);
    }

    private float approachAngularRate(float current, float target,
                                      float response, float releaseResponse,
                                      float maximumChange, float maximumRate) {
        if (!Float.isFinite(current) || !Float.isFinite(target)) {
            return 0.0F;
        }
        boolean sameDirection = current == 0.0F || target == 0.0F ||
            Math.signum(current) == Math.signum(target);
        boolean accelerating = target != 0.0F && sameDirection &&
            Math.abs(target) > Math.abs(current);
        float selectedResponse = accelerating ? response : releaseResponse;
        float change = Mth.clamp((target - current) * selectedResponse,
            -maximumChange, maximumChange);
        float next = Mth.clamp(current + change, -maximumRate, maximumRate);
        return Math.abs(next) < 1.0E-4F && target == 0.0F ? 0.0F : next;
    }

    protected Quaternionf tickFlightPhysics(TempMotionVars tempMotionVars,
                                            boolean onGroundOrWater,
                                            Quaternionf attitude) {
        tickAirborneClock(onGroundOrWater);
        return tickNewFlightModel(tempMotionVars, onGroundOrWater, attitude);
    }

    protected void decrementOnGroundTicks() {
        onGroundTicks--;
    }

    protected float getPitchAngularRate() {
        return pitchAngularRate;
    }

    protected float getYawAngularRate() {
        return yawAngularRate;
    }

    public float getRollAngularRate() {
        return rollAngularRate;
    }

    protected void setBodyAngularRates(float pitch, float yaw, float roll) {
        pitchAngularRate = Float.isFinite(pitch) ? pitch : 0.0F;
        yawAngularRate = Float.isFinite(yaw) ? yaw : 0.0F;
        rollAngularRate = Float.isFinite(roll) ? roll : 0.0F;
    }

    protected void breakUpFromStructuralOverspeed() {
        breakUpFromOverspeed();
    }

    protected Quaternionf applyBodyAngularRates(Quaternionf attitude) {
        Quaternionf result = new Quaternionf(attitude);
        result.mul(com.mojang.math.Axis.ZP.rotationDegrees(rollAngularRate));
        result.mul(com.mojang.math.Axis.XN.rotationDegrees(pitchAngularRate));
        result.mul(com.mojang.math.Axis.YP.rotationDegrees(yawAngularRate));

        result = normalizeQuaternion(result);

        EulerAngles displayAngles = toEulerAngles(result);
        setXRot((float) displayAngles.pitch);
        setYRot((float) displayAngles.yaw);
        rotationRoll = (float) displayAngles.roll;
        return result;
    }

    protected Quaternionf applyAerodynamicStability(Quaternionf attitude,
                                                  TempMotionVars tempMotionVars) {
        Quaternionf result = new Quaternionf(attitude);
        result.mul(com.mojang.math.Axis.XN.rotationDegrees(
            tempMotionVars.pitchStabilityRate));
        result.mul(com.mojang.math.Axis.YP.rotationDegrees(
            tempMotionVars.yawStabilityRate));
        result = normalizeQuaternion(result);
        EulerAngles displayAngles = toEulerAngles(result);
        setXRot((float) displayAngles.pitch);
        setYRot((float) displayAngles.yaw);
        rotationRoll = (float) displayAngles.roll;
        return result;
    }

    private double smoothStep(double value) {
        value = Mth.clamp(value, 0.0D, 1.0D);
        return value * value * (3.0D - 2.0D * value);
    }

    public Vec3 getBodyDirection(float x, float y, float z) {
        Quaternionf attitude = level().isClientSide() ? getQ_Client() : getQ();
        return getBodyDirection(attitude, x, y, z);
    }

    protected Vec3 getBodyDirection(Quaternionf attitude, float x, float y, float z) {
        return bodyDirection(attitude, x, y, z);
    }

    public static Vec3 bodyDirection(Quaternionf attitude, float x, float y,
                                     float z) {
        Vector3f direction = new Vector3f(x, y, z);
        direction.rotate(new Quaternionf(attitude.x(), -attitude.y(),
            -attitude.z(), attitude.w()));
        Vec3 result = new Vec3(direction);
        return result.lengthSqr() > 1.0E-8D ? result.normalize() : Vec3.ZERO;
    }

    protected boolean tickOnGround(TempMotionVars tempMotionVars) {
        if (getDeltaMovement().lengthSqr() < 0.01 && getOnGround()) {
            notMovingTime += 1;
        } else {
            notMovingTime = 0;
        }
        if (notMovingTime > 200 && getHealth() < getMaxHealth() && getPlayer() != null) {
            setHealth(getHealth() + 1);
            notMovingTime = 100;
        }

        boolean canTakeOff = true;
        if (onGroundTicks < 0) {
            onGroundTicks = 5;
        } else {
            onGroundTicks--;
        }
        double groundSpeed = Math.sqrt(getDeltaMovement().horizontalDistanceSqr());

        // At rotation the ground attitude controller yields pitch, and
        // AircraftFlightModelBridge.step passes no pitch input while the ground controller has it.
        // Change one and the nose won't lift, or the two fight.
        boolean rotating = groundSpeed >= tempMotionVars.takeOffSpeed
            && tempMotionVars.pitchInput > 0.0F;
        if (!rotating) {
            float maximumPitchRate = getMaxPitchAngularRate();
            float groundPitchRate = Mth.clamp(
                Mth.wrapDegrees(-getXRot()) * 0.25F,
                -maximumPitchRate, maximumPitchRate);
            pitchAngularRate = approachAngularRate(pitchAngularRate,
                groundPitchRate, PITCH_RATE_RESPONSE,
                PITCH_RATE_RELEASE_RESPONSE, getMaxPitchRateChangePerTick(),
                maximumPitchRate);
        }

        if (groundSpeed < tempMotionVars.takeOffSpeed) {
            canTakeOff = false;
        }
        BlockPos pos = BlockPos.containing(getX(), getY() - 1.0D, getZ());
        float slipperiness = level().getBlockState(pos).getBlock().getFriction();
        tempMotionVars.groundFrictionMultiplier = Mth.clamp(
            (1.2D - slipperiness) / 0.6D, 0.25D, 1.5D);
        return canTakeOff;
    }

    public Vector3f transformPos(Vector3f relPos) {
        Quaternionf attitude = level().isClientSide() ? getQ_Client() : getQ();
        relPos.rotate(new Quaternionf(attitude.x(), -attitude.y(),
            -attitude.z(), attitude.w()));
        return relPos;
    }

    public static boolean isPilot(@Nullable Entity passenger) {
        return passenger instanceof Player
            || passenger instanceof amrac.entities.ai.AiPilotEntity;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        List<Entity> list = getPassengers();
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0) instanceof LivingEntity living ? living : null;
    }

    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return rightSide ? TWIN_MUZZLE_SIDE_OFFSET : -TWIN_MUZZLE_SIDE_OFFSET;
    }

    public double getMachineGunMuzzleHeight() {
        return TWIN_MUZZLE_HEIGHT;
    }

    public double getMachineGunMuzzleForwardOffset() {
        return TWIN_MUZZLE_FORWARD_OFFSET;
    }

    public double getMachineGunConvergenceDistance() {
        return TWIN_MUZZLE_CONVERGENCE_DISTANCE;
    }

    public double getMachineGunMuzzleVelocity() {
        return MachineGunFirePolicy.BIPLANE_PROJECTILE_SPEED;
    }

    public boolean shouldRenderShooterModel() {
        return true;
    }

    public boolean shouldRenderPassengers() {
        return true;
    }

    public double getModelScale() {
        return 1.0D;
    }

    public double getAirframeRotationPivotHeight() {
        return PASSENGER_HITBOX_ROTATION_PIVOT_HEIGHT;
    }

    public boolean hasMachineGun() {
        return false;
    }

    public boolean hasBombRack() {
        return false;
    }

    public boolean hasMissiles() {
        return false;
    }

    public MissileSystem missiles() {
        return missileSystem;
    }

    public String[] getLoadout() {
        return MissileLoadout.decode(entityData.get(LOADOUT), pylonCount());
    }

    public void setLoadout(String[] slots) {
        if (!level().isClientSide()) {
            entityData.set(LOADOUT, MissileLoadout.encode(slots));
            String selected = getSelectedMissile();
            if (selected == null
                || MissileLoadout.firstOf(slots, selected) < 0) {
                List<String> aboard = MissileLoadout.typesAboard(slots);
                entityData.set(SELECTED_MISSILE,
                    aboard.isEmpty() ? "" : aboard.get(0));
            }
        }
    }

    public int getMissileCount() {
        return MissileLoadout.count(getLoadout());
    }

    @Nullable
    public String getSelectedMissile() {
        String id = entityData.get(SELECTED_MISSILE);
        return id == null || id.isEmpty() ? null : id;
    }

    public void setSelectedMissile(@Nullable String id) {
        if (!level().isClientSide()) {
            entityData.set(SELECTED_MISSILE, id == null ? "" : id);
        }
    }

    public void cycleSelectedMissile() {
        if (level().isClientSide()) {
            return;
        }
        setSelectedMissile(
            MissileLoadout.nextType(getLoadout(), getSelectedMissile()));
    }

    public int pylonCount() {
        return MissileLoadout.STATIONS;
    }

    public double[][] railPositions() {
        return MissileSystem.railPositions();
    }

    public MissileFaction missileFaction() {
        return MissileFaction.NATO;
    }

    public java.util.Set<String> barredMissiles() {
        return java.util.Set.of();
    }

    public boolean semiActiveOnly() {
        return false;
    }

    public boolean canCarry(String profileId) {
        return AirframeLoadoutPolicy.permits(missileFaction(), semiActiveOnly(),
            barredMissiles(), MissileProfiles.byId(profileId));
    }

    @Nullable
    public String loadMissileAt(Vec3 hit, String profileId) {
        if (level().isClientSide() || !hasMissiles()) {
            return null;
        }
        String[] slots = getLoadout();
        double[] distances = new double[pylonCount()];
        double[][] rails = railPositions();
        double scale = getModelScale();
        for (int i = 0; i < distances.length; i++) {
            Vec3 station = machineGuns().worldPoint(rails[i][0] * scale,
                rails[i][1] * scale, rails[i][2] * scale);
            distances[i] = station.distanceTo(hit);
        }
        int station = MissileLoadout.stationToLoad(slots, distances);
        if (station < 0) {
            return null;
        }
        slots[station] = profileId;
        setLoadout(slots);
        return profileId;
    }

    @Nullable
    public String loadMissile(String profileId) {
        if (level().isClientSide() || !hasMissiles() || !canCarry(profileId)) {
            return null;
        }
        String[] slots = getLoadout();
        for (int station = 0; station < slots.length; station++) {
            if (slots[station] == null || slots[station].isEmpty()) {
                slots[station] = profileId;
                setLoadout(slots);
                return profileId;
            }
        }
        return null;
    }

    public void tickUpkeepWhileUnticked() {
        if (level().isClientSide()) {
            return;
        }
        tickLandingGear();
        tickFlaps();
        tickSpeedBrake();
        tickControlSurfaces();
        if (damageTimeout > 0) {
            --damageTimeout;
        }
        if (goldenHeartsTimeout < 1000 && isPowered()) {
            goldenHeartsTimeout++;
        }
        tickFuel();
        machineGunSystem.tick();
        bombingSystem.tick();
    }

    public boolean launchMissile(LivingEntity pilot,
                                 AircraftTargetSnapshot target) {
        return missileSystem.launch(pilot, target);
    }

    public boolean hasRadar() {
        return false;
    }

    public MachineGunSystem machineGuns() {
        return machineGunSystem;
    }

    public void setMachineGunTrigger(boolean held, LivingEntity pilot) {
        machineGunSystem.setTriggerHeld(held, pilot);
    }

    public void tryDropBombs(Player player) {
        bombingSystem.tryDropBombs(player);
    }

    public void setGunThermalState(float heat, boolean overheated) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(GUN_HEAT, MachineGunThermalPolicy.sanitizeHeat(heat));
        entityData.set(GUN_OVERHEATED, overheated);
    }

    public float getGunHeat() {
        return MachineGunThermalPolicy.sanitizeHeat(entityData.get(GUN_HEAT));
    }

    public int getGunHeatPercentage() {
        return MachineGunThermalPolicy.heatPercentage(getGunHeat());
    }

    public boolean isGunOverheated() {
        return entityData.get(GUN_OVERHEATED);
    }

    public int getMachineGunAmmoCount() {
        return entityData.get(AMMO);
    }

    public static final int ROUNDS_PER_BULLET_ITEM = 1;

    public int loadMachineGunAmmo(int itemsAvailable) {
        int space = MAX_AMMO - getMachineGunAmmoCount();
        if (space <= 0 || itemsAvailable <= 0) {
            return 0;
        }
        int items = Math.min(itemsAvailable,
            (space + ROUNDS_PER_BULLET_ITEM - 1) / ROUNDS_PER_BULLET_ITEM);
        entityData.set(AMMO, Math.min(MAX_AMMO,
            getMachineGunAmmoCount() + items * ROUNDS_PER_BULLET_ITEM));
        return items;
    }

    public int getTntAmmoCount() {
        return countPilotItem(Items.TNT);
    }

    public boolean consumeMachineGunAmmo() {
        if (isCreative()) {
            return true;
        }
        int rounds = getMachineGunAmmoCount();
        if (rounds <= 0) {
            return false;
        }
        entityData.set(AMMO, rounds - 1);
        return true;
    }

    public boolean consumeTntAmmo(int count) {
        return count <= 0 || consumePilotItem(Items.TNT, count);
    }

    private int countPilotItem(Item item) {
        Player pilot = getPlayer();
        if (pilot == null) {
            return 0;
        }
        Inventory inventory = pilot.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); ++slot) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean consumePilotItem(Item item, int count) {
        Player pilot = getPlayer();
        if (pilot == null || count <= 0 || countPilotItem(item) < count) {
            return false;
        }
        Inventory inventory = pilot.getInventory();
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; ++slot) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= taken;
        }
        inventory.setChanged();
        return remaining == 0;
    }

    @Nullable
    public Player findPassengerHit(Vec3 worldStart, Vec3 worldEnd,
                                   double hitMargin) {
        if (!isFiniteVector(worldStart) || !isFiniteVector(worldEnd)) {
            return null;
        }

        double margin = Double.isFinite(hitMargin)
            ? Mth.clamp(hitMargin, 0.0D, 1.0D) : 0.0D;
        Quaternionf attitude = normalizeQuaternion(level().isClientSide()
            ? getQ_Client() : getQ());
        Quaternionf inverseWorldAttitude = new Quaternionf(-attitude.x(),
            attitude.y(), attitude.z(), attitude.w());
        Vec3 localStart = toPassengerHitboxFrame(worldStart, inverseWorldAttitude);
        Vec3 localEnd = toPassengerHitboxFrame(worldEnd, inverseWorldAttitude);

        Player closestPassenger = null;
        double closestFraction = Double.POSITIVE_INFINITY;
        for (Entity passenger : getPassengers()) {
            if (!(passenger instanceof Player player) || !passenger.isAlive() ||
                passenger.isSpectator()) {
                continue;
            }

            AABB body = passenger.getBoundingBox();
            double seatedBaseY = getPassengersRidingOffset() -
                passenger.getVehicleAttachmentPoint(this).y() -
                PASSENGER_HITBOX_ROTATION_PIVOT_HEIGHT;
            double hitFraction = PassengerHitboxPolicy.segmentAabbHitFraction(
                localStart.x(), localStart.y(), localStart.z(),
                localEnd.x(), localEnd.y(), localEnd.z(),
                body.minX - passenger.getX() - margin,
                seatedBaseY + body.minY - passenger.getY() - margin,
                body.minZ - passenger.getZ() - margin,
                body.maxX - passenger.getX() + margin,
                seatedBaseY + body.maxY - passenger.getY() + margin,
                body.maxZ - passenger.getZ() + margin);
            if (Double.isFinite(hitFraction) && hitFraction < closestFraction) {
                closestFraction = hitFraction;
                closestPassenger = player;
            }
        }
        return closestPassenger;
    }

    private Vec3 toPassengerHitboxFrame(Vec3 worldPoint,
                                        Quaternionf inverseWorldAttitude) {
        Vec3 relative = worldPoint.subtract(position().add(0.0D,
            PASSENGER_HITBOX_ROTATION_PIVOT_HEIGHT, 0.0D));
        Vector3f local = new Vector3f((float) relative.x(),
            (float) relative.y(), (float) relative.z());
        local.rotate(inverseWorldAttitude);
        return new Vec3(local.x(), local.y(), local.z());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        if (input.read("max_speed", com.mojang.serialization.Codec.FLOAT).isPresent()) {
            setMaxSpeed(input.getFloatOr("max_speed", BASE_MAX_SPEED));
        }

        int maxHealth = DEFAULT_MAX_HEALTH;
        int health = DEFAULT_MAX_HEALTH;
        if (input.getInt("max_health").isPresent()) {
            maxHealth = input.getIntOr("max_health", DEFAULT_MAX_HEALTH);
            if (maxHealth <= 0) {
                maxHealth = DEFAULT_MAX_HEALTH;
            }
        }

        if (input.getInt("health").isPresent()) {
            health = input.getIntOr("health", maxHealth);
            if (health <= 0) {
                health = 1;
            }
        } else {
            health = maxHealth;
        }

        if (PlaneDurabilityPolicy.usesHistoricalDefault(maxHealth)) {
            health = PlaneDurabilityPolicy.migrateHealthToCurrentDefault(
                health, maxHealth);
            maxHealth = DEFAULT_MAX_HEALTH;
        }
        entityData.set(MAX_HEALTH, maxHealth);
        entityData.set(HEALTH, health);

        if (input.getInt("throttle").isPresent()) {
            setThrottle(input.getIntOr("throttle", 0));
        }

        if (!level().isClientSide()) {
            entityData.set(LOADOUT, MissileLoadout.encode(
                MissileLoadout.decode(input.getStringOr("loadout", ""),
                    pylonCount())));
            entityData.set(SELECTED_MISSILE,
                input.getStringOr("selected_missile", ""));
        }
        burnTime = Math.max(0, input.getIntOr("burn_time", 0));
        burnTimeTotal = Math.max(0, input.getIntOr("burn_time_total", 0));
        entityData.set(FUEL_LITRES,
            Math.max(0.0F, input.getFloatOr("fuel_litres", 0.0F)));
        entityData.set(AFTERBURNER, input.getBooleanOr("afterburner", false));
        entityData.set(AMMO,
            Mth.clamp(input.getIntOr("ammo", 0), 0, MAX_AMMO));
        int dispensers = getCountermeasureCapacity();
        int chaffLoaded = Mth.clamp(input.getIntOr("chaff", 0), 0, dispensers);
        entityData.set(CHAFF, chaffLoaded);
        entityData.set(FLARE, Mth.clamp(input.getIntOr("flare", 0), 0,
            dispensers - chaffLoaded));
        entityData.set(FUEL, burnTime);
        machineGunSystem.load(input.getFloatOr("gun_heat", 0.0F),
            input.getBooleanOr("gun_overheated", false));
        entityData.set(AOA_LIMITER, input.getBooleanOr("aoa_limiter", true));
        boolean brokenGear = input.getBooleanOr("gear_broken", false);
        entityData.set(GEAR_BROKEN, brokenGear);
        if (brokenGear) {
            entityData.set(GEAR_DOWN, false);
            gearPosition = 0.0F;
            gearPositionPrev = 0.0F;
        }
        Quaternionf saved = new Quaternionf(
            input.getFloatOr("q_x", 0.0F), input.getFloatOr("q_y", 0.0F),
            input.getFloatOr("q_z", 0.0F), input.getFloatOr("q_w", 0.0F));
        if (saved.lengthSquared() > 1.0E-6F) {
            Quaternionf restored = normalizeQuaternion(saved);
            setQ(restored);
            setQ_Client(restored);
            setQ_prev(restored);
        }
        boolean brokenFlaps = input.getBooleanOr("flaps_broken", false);
        entityData.set(FLAPS_BROKEN, brokenFlaps);
        if (brokenFlaps) {
            entityData.set(FLAPS_DOWN, false);
        }
        flapPosition = 0.0F;
        flapPositionPrev = 0.0F;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        output.putInt("health", entityData.get(HEALTH));
        output.putInt("max_health", entityData.get(MAX_HEALTH));
        output.putFloat("max_speed", entityData.get(MAX_SPEED));
        output.putInt("throttle", getThrottle());
        output.putString("loadout", entityData.get(LOADOUT));
        output.putString("selected_missile",
            entityData.get(SELECTED_MISSILE));
        output.putInt("burn_time", burnTime);
        output.putInt("burn_time_total", burnTimeTotal);
        output.putFloat("fuel_litres", entityData.get(FUEL_LITRES));
        output.putBoolean("afterburner", entityData.get(AFTERBURNER));
        output.putInt("ammo", entityData.get(AMMO));
        output.putInt("chaff", entityData.get(CHAFF));
        output.putInt("flare", entityData.get(FLARE));
        output.putFloat("gun_heat", machineGunSystem.getHeat());
        output.putBoolean("gun_overheated", machineGunSystem.isOverheated());
        output.putBoolean("aoa_limiter", entityData.get(AOA_LIMITER));
        output.putBoolean("gear_broken", entityData.get(GEAR_BROKEN));
        output.putBoolean("flaps_broken", entityData.get(FLAPS_BROKEN));
        Quaternionf attitude = getQ();
        output.putFloat("q_x", attitude.x());
        output.putFloat("q_y", attitude.y());
        output.putFloat("q_z", attitude.z());
        output.putFloat("q_w", attitude.w());
    }

    @Override
    protected boolean canRide(Entity entityIn) {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public float getPickRadius() {
        return PlaneSyncPolicy.PROJECTILE_HIT_MARGIN;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (Q.equals(key) && level().isClientSide() && !isLocalInstanceAuthoritative()) {
            acceptRemoteAttitudeSnapshot(getQ());
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == HURT_EVENT_ID) {
            hurtFlashTicks = HURT_FLASH_DURATION_TICKS;
        } else {
            super.handleEntityEvent(id);
        }
    }

    public double getPassengersRidingOffset() {
        return 0.375;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger,
                                                EntityDimensions dimensions,
                                                float scale) {
        return new Vec3(0.0D, getPassengersRidingOffset() * scale, 0.0D);
    }

    private boolean isPlaneInvulnerableTo(DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        if (direct != null && PlaneDurabilityPolicy.isRiderAttackingOwnAircraft(
            direct == this, direct.isPassengerOfSameVehicle(this))) {
            return true;
        }
        return isInvulnerableToBase(source);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean dismountsUnderwater() {
        return true;
    }

    private void tickStructuralCollisionDamage(Vec3 requestedMotion) {
        if ((level().isClientSide() && !isLocalInstanceAuthoritative()) ||
            requestedMotion.lengthSqr() < COLLISION_EPSILON * COLLISION_EPSILON) {
            horizontalImpactContact = false;
            verticalImpactContact = false;
            return;
        }

        List<VoxelShape> planeCollisions = level().getEntityCollisions(this,
            getBoundingBox().expandTowards(requestedMotion));
        Vec3 allowedMotion = collideBoundingBox(this, requestedMotion,
            getBoundingBox(), level(), planeCollisions);
        applyStructuralCollisionDamage(requestedMotion,
            Math.abs(requestedMotion.x() - allowedMotion.x()) > COLLISION_EPSILON,
            Math.abs(requestedMotion.y() - allowedMotion.y()) > COLLISION_EPSILON,
            Math.abs(requestedMotion.z() - allowedMotion.z()) > COLLISION_EPSILON);
    }

    private void applyStructuralCollisionDamage(Vec3 requestedMotion,
                                                boolean blockedX,
                                                boolean blockedY,
                                                boolean blockedZ) {
        if (!AmracConfig.crashDamage()) {
            horizontalImpactContact = blockedX || blockedZ;
            verticalImpactContact = blockedY;
            return;
        }
        boolean horizontalBlocked = blockedX || blockedZ;
        boolean newHorizontalImpact = horizontalBlocked && !horizontalImpactContact;
        boolean newVerticalImpact = blockedY && !verticalImpactContact;

        horizontalImpactContact = horizontalBlocked;
        verticalImpactContact = blockedY;

        if ((!level().isClientSide() && damageTimeout > 0) ||
            (!newHorizontalImpact && !newVerticalImpact)) {
            return;
        }

        double impactScale = amrac.physics.aircraft.SpeedScale.current();
        double horizontalExcess = 0.0D;
        if (newHorizontalImpact) {
            double impactX = blockedX ? requestedMotion.x() : 0.0D;
            double impactZ = blockedZ ? requestedMotion.z() : 0.0D;
            double horizontalImpactSpeed = Math.sqrt(impactX * impactX + impactZ * impactZ);
            horizontalExcess = Math.max(0.0D,
                horizontalImpactSpeed - SAFE_HORIZONTAL_IMPACT_SPEED * impactScale);
        }

        double verticalExcess = 0.0D;
        if (newVerticalImpact) {
            double verticalImpactSpeed = Math.abs(requestedMotion.y());
            double safeVerticalSpeed = SAFE_CEILING_IMPACT_SPEED * impactScale;
            if (requestedMotion.y() < 0.0D) {
                double upY = Mth.clamp(transformPos(new Vector3f(0, 1, 0)).y(),
                    -1.0D, 1.0D);
                double minimumLandingUp = Math.cos(Math.toRadians(getLandingAngle()));
                double badAttitude = Mth.clamp(
                    (minimumLandingUp - upY) / minimumLandingUp, 0.0D, 1.0D);

                verticalImpactSpeed = Math.max(verticalImpactSpeed,
                    requestedMotion.length() * Math.sqrt(badAttitude));
                safeVerticalSpeed = (SAFE_VERTICAL_LANDING_SPEED -
                    (SAFE_VERTICAL_LANDING_SPEED - SAFE_BAD_ATTITUDE_LANDING_SPEED) *
                        badAttitude) * impactScale;
            }
            verticalExcess = Math.max(0.0D, verticalImpactSpeed - safeVerticalSpeed);
        }

        double damagingImpact = Math.sqrt(horizontalExcess * horizontalExcess +
            verticalExcess * verticalExcess);
        double descentRate = Math.max(0.0D, -requestedMotion.y()) * 20.0D;
        if (!PlaneDurabilityPolicy.isCrash(damagingImpact, newVerticalImpact,
            isGearDown(), COLLISION_EPSILON, descentRate)) {
            return;
        }
        if (level().isClientSide()) {
            amrac.network.PlaneNetworking.sendCrash();
        } else {
            crash(Float.MAX_VALUE);
        }
    }

    @Override
    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
        if (!movingUnderOwnPhysics) {
            fallDistance = 0.0F;
            return;
        }
        if ((onGroundIn || isOnWater()) && true) {
            final double y1 = transformPos(new Vector3f(0, 1, 0)).y();
            if (y1 < Math.cos(Math.toRadians(getLandingAngle()))) {
                state.getBlock().fallOn(level(), state, pos, this, (float) (getDeltaMovement().length() * 5));
            }
            fallDistance = 0.0F;
        }
    }

    private static final int AUTO_GEAR_UP_AIRBORNE_TICKS = 20;

    private void tickAutomaticGear() {
        if (level().isClientSide() || !hasRetractableGear() || !isGearDown() ||
            onGroundTicks > -AUTO_GEAR_UP_AIRBORNE_TICKS ||
            getDeltaMovement().y() <= 0.0D) {
            return;
        }
        setGearDown(false);
    }

    protected int getLandingAngle() {
        return 20;
    }

    @Override
    public boolean causeFallDamage(double distance, float damageMultiplier,
                                   net.minecraft.world.damagesource.DamageSource source) {
        if (isVehicle()) {
            crash((float) (distance * damageMultiplier));
        }
        return false;
    }

    public void crash(float damage) {
        if (level().isClientSide() || isRemoved() || damageTimeout > 0) {
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            hurtServer(serverLevel, AmracDamage.source(level(),
                AmracDamage.PLANE_CRASH_DEATH, this), damage);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source,
                              float amount) {
        if (isPlaneInvulnerableTo(source) || isRemoved()) {
            return false;
        }

        Entity directEntity = source.getDirectEntity();
        boolean cannonRound = directEntity instanceof MachineGunBulletEntity;
        boolean missileHit = AmracDamage.is(source, AmracDamage.PLANE_SHOT_DOWN)
            && (directEntity == null || directEntity instanceof MissileEntity);
        boolean planeImpact = directEntity instanceof PlaneEntity &&
            AmracDamage.is(source, AmracDamage.PLANE_COLLISION);

        Player passengerTarget = findPassengerDamageTarget(source, directEntity);
        if (passengerTarget != null) {
            return passengerTarget.hurtServer(level, source, amount);
        }

        if (directEntity instanceof Player player && source.getEntity() == directEntity
            && player.getMainHandItem().is(Items.GOLDEN_AXE)) {
            if (dropPlaneItem()) {
                discard();
                return true;
            }
            return false;
        }

        if (damageTimeout > 0 && !cannonRound && !planeImpact && !missileHit) {
            return false;
        }

        int health = getHealth();
        if (health <= 0) {
            return false;
        }

        if (amount > 0.0F) {
            level().broadcastEntityEvent(this, HURT_EVENT_ID);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_HURT,
                SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        health -= (int) Math.ceil(amount);
        setHealth(health);
        if (!cannonRound) {
            damageTimeout = 10;
        }
        if (health <= 0) {
            if (!beginDeathThroes(source)) {
                killPassengersOnDestruction(source);
                explode();
                discard();
            }
        }
        return true;
    }

    @Nullable
    private Player findPassengerDamageTarget(DamageSource source,
                                             @Nullable Entity directEntity) {
        if (directEntity == null || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return null;
        }

        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            Vec3 start = directEntity.position();
            Vec3 motion = directEntity.getDeltaMovement();
            Vec3 end;
            if (isFiniteVector(motion) && motion.lengthSqr() > 1.0E-12D) {
                end = start.add(motion);
            } else {
                start = new Vec3(directEntity.xOld, directEntity.yOld,
                    directEntity.zOld);
                end = directEntity.position();
            }
            return findPassengerHit(start, end, PASSENGER_PROJECTILE_HIT_MARGIN);
        }

        if (directEntity instanceof LivingEntity && source.getEntity() == directEntity) {
            Vec3 start = directEntity.getEyePosition(1.0F);
            Vec3 end = start.add(directEntity.getViewVector(1.0F)
                .scale(PLAYER_ATTACK_RAY_LENGTH));
            return findPassengerHit(start, end, 0.0D);
        }
        return null;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player player &&
            !attacker.isPassengerOfSameVehicle(this)) {
            Vec3 start = attacker.getEyePosition(1.0F);
            Vec3 end = start.add(attacker.getViewVector(1.0F)
                .scale(PLAYER_ATTACK_RAY_LENGTH));
            Player passengerTarget = findPassengerHit(start, end, 0.0D);
            if (passengerTarget != null && passengerTarget != attacker) {
                player.attack(passengerTarget);
                return true;
            }
        }
        return super.skipAttackInteraction(attacker);
    }

    private static final int DEATH_THROES_TICKS = 4;

    private int deathThroesTicks;

    @Nullable
    private DamageSource pendingPassengerDeathSource;

    public boolean isDying() {
        return entityData.get(DYING);
    }

    private boolean beginDeathThroes(DamageSource source) {
        if (isDying() || level().isClientSide() ||
            !(getControllingPassenger() instanceof Player pilot)) {
            return false;
        }
        deathThroesTicks = DEATH_THROES_TICKS;
        pendingPassengerDeathSource = createPassengerDeathSource(source);
        entityData.set(DYING, true);
        shutdownEngineAndControls();
        amrac.AmracMod.sendOverlay(pilot,
            Component.translatable("amrac.message.airframe_destroyed"), true);
        return true;
    }

    private void tickDeathThroes() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                getX(), getY(), getZ(), 6, 0.8D, 0.8D, 0.8D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                getX(), getY(), getZ(), 3, 0.5D, 0.5D, 0.5D, 0.04D);
        }
        if (--deathThroesTicks > 0) {
            return;
        }
        DamageSource deathSource = pendingPassengerDeathSource;
        pendingPassengerDeathSource = null;
        killPassengers(deathSource != null ? deathSource
            : AmracDamage.source(level(), AmracDamage.PLANE_CRASH_DEATH, this));
        explode();
        discard();
    }

    private void killPassengersOnDestruction(DamageSource planeDamageSource) {
        killPassengers(createPassengerDeathSource(planeDamageSource));
    }

    private void killPassengers(DamageSource passengerDeathSource) {
        List<Entity> passengerSnapshot = new ArrayList<>();
        getIndirectPassengers().forEach(passengerSnapshot::add);
        if (passengerSnapshot.isEmpty()) {
            return;
        }
        ejectPassengers();

        for (Entity passenger : passengerSnapshot) {
            if (!(passenger instanceof LivingEntity living) || passenger.isRemoved() ||
                living.isDeadOrDying()) {
                continue;
            }
            if (living.isPassenger()) {
                living.stopRiding();
            }
            living.hurt(passengerDeathSource, Float.MAX_VALUE);
            if (!living.isDeadOrDying()) {
                living.setHealth(0.0F);
                living.die(passengerDeathSource);
            }
        }
    }

    private DamageSource createPassengerDeathSource(DamageSource planeDamageSource) {
        Entity direct = planeDamageSource.getDirectEntity();
        if (direct instanceof MissileEntity missile) {
            Entity killer = planeDamageSource.getEntity();
            boolean anonymous = killer == null || killer == missile;
            return AmracDamage.weaponSource(level(),
                AmracDamage.PLANE_SHOT_DOWN, missile,
                anonymous ? missile : killer, missile.weaponName(),
                anonymous ? missile.ownerName() : null);
        }
        if (direct instanceof MachineGunBulletEntity) {
            Entity shooter = planeDamageSource.getEntity();
            if (shooter != null) {
                return AmracDamage.weaponSource(level(),
                    AmracDamage.PLANE_SHOT_DOWN, direct, shooter,
                    Component.translatable("amrac.weapon.cannon"));
            }
        }
        Component weapon = AmracDamage.weapon(planeDamageSource);
        if (weapon != null && AmracDamage.is(planeDamageSource,
                AmracDamage.PLANE_SHOT_DOWN)) {
            Component attackerName = AmracDamage.attackerName(planeDamageSource);
            if (planeDamageSource.getEntity() != null || attackerName != null) {
                return AmracDamage.weaponSource(level(),
                    AmracDamage.PLANE_SHOT_DOWN, direct,
                    planeDamageSource.getEntity(), weapon, attackerName);
            }
        }
        if (direct instanceof PlaneEntity otherPlane &&
            AmracDamage.is(planeDamageSource,
                AmracDamage.PLANE_COLLISION)) {
            Player otherPilot = findPlayerPilot(otherPlane);
            if (otherPilot != null) {
                return AmracDamage.source(level(),
                    AmracDamage.PLANE_MIDAIR_COLLISION, otherPilot);
            }
        }
        return AmracDamage.source(level(),
            AmracDamage.PLANE_CRASH_DEATH, this);
    }

    @Nullable
    private static Player findPlayerPilot(PlaneEntity plane) {
        if (plane.getControllingPassenger() instanceof Player pilot) {
            return pilot;
        }
        for (Entity passenger : plane.getIndirectPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void breakUpFromOverspeed() {
        setDeltaMovement(getDeltaMovement().scale(0.35D));
        for (Entity passenger : new java.util.ArrayList<>(getPassengers())) {
            passenger.stopRiding();
            passenger.setDeltaMovement(getDeltaMovement());
            if (passenger instanceof Player player) {
                amrac.AmracMod.sendOverlay(player,
                    Component.translatable(
                    "amrac.message.overspeed_breakup"), true);
            }
        }
        explode();
        discard();
    }

    private void explode() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(),
                5, 1, 1, 1, 2);
            serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(),
                10, 1, 1, 1, 1);
        }
        float power = (float) (PlaneDurabilityPolicy.blastPower(
            getFuelLitres(), getFuelCapacityLitres())
            * AmracConfig.crashBlast());
        level().explode(this, getX(), getY(), getZ(), power,
            power > 0.0F ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE);
    }

    protected boolean dropPlaneItem() {
        Item item = getDropItem();
        if (item == null) {
            return false;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ItemEntity dropped = spawnAtLocation(serverLevel, new ItemStack(item));
        if (dropped == null) {
            return false;
        }
        dropped.setInvulnerable(true);
        return true;
    }

    @Nullable
    protected Item getDropItem() {
        return null;
    }

    @Override
    public ItemStack getPickResult() {
        Item item = getDropItem();
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public boolean isCreative() {
        Player pilot = getPlayer();
        return pilot != null && pilot.isCreative();
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        if (isOwnImpactBomb(entity)) {
            return false;
        }
        if (!onGround() && !isOnWater() && !(entity instanceof PlaneEntity)) {
            return false;
        }
        return super.canCollideWith(entity);
    }

    @Override
    public void push(Entity entity) {
        if (!isOwnImpactBomb(entity)) {
            super.push(entity);
        }
    }

    private boolean isOwnImpactBomb(Entity entity) {
        return entity instanceof ImpactTntEntity bomb &&
            BombingPolicy.isSourceAircraftOrPassenger(bomb.getSourcePlaneId(),
                getId(), getId());
    }

    private void tickEntityCollisionImpacts() {
        AABB planeBox = getBoundingBox();
        Vec3 currentCenter = planeBox.getCenter();
        Vec3 previousCenter = previousEntityCollisionCenter == null
            ? currentCenter : previousEntityCollisionCenter;
        previousEntityCollisionCenter = currentCenter;

        Vec3 sweepDisplacement = currentCenter.subtract(previousCenter);
        if (!isFiniteVector(sweepDisplacement) ||
            sweepDisplacement.lengthSqr() > MAX_ENTITY_COLLISION_SWEEP_DISTANCE *
                MAX_ENTITY_COLLISION_SWEEP_DISTANCE) {
            previousCenter = currentCenter;
            sweepDisplacement = Vec3.ZERO;
        }

        entityCollisionDamageCooldowns.entrySet()
            .removeIf(entry -> entry.getValue() <= tickCount);

        AABB sweptBox = planeBox.expandTowards(sweepDisplacement.scale(-1.0D))
            .inflate(ENTITY_COLLISION_CONTACT_MARGIN);
        for (Entity target : level().getEntities(this, sweptBox, this::canImpactEntity)) {
            if (!intersectsEntityDuringSweep(target, previousCenter, currentCenter,
                planeBox)) {
                continue;
            }
            Integer nextDamageTick = entityCollisionDamageCooldowns.get(target.getId());
            boolean damageReady = !level().isClientSide() &&
                (nextDamageTick == null || nextDamageTick <= tickCount);
            if (applyEntityCollisionImpact(target, sweepDisplacement, planeBox,
                damageReady)) {
                entityCollisionDamageCooldowns.put(target.getId(),
                    tickCount + ENTITY_COLLISION_COOLDOWN_TICKS);
            }
        }
    }

    private boolean canImpactEntity(Entity entity) {
        return entity.isAlive() && !entity.isSpectator() &&
            !isOwnImpactBomb(entity) &&
            entity.getVehicle() == null &&
            !entity.isPassengerOfSameVehicle(this) &&
            (entity instanceof LivingEntity || entity instanceof PlaneEntity ||
                entity.isPushable());
    }

    private boolean intersectsEntityDuringSweep(Entity target, Vec3 previousCenter,
                                                Vec3 currentCenter, AABB planeBox) {
        AABB targetBox = target.getBoundingBox().inflate(
            planeBox.getXsize() * 0.5D + ENTITY_COLLISION_CONTACT_MARGIN,
            planeBox.getYsize() * 0.5D + ENTITY_COLLISION_CONTACT_MARGIN,
            planeBox.getZsize() * 0.5D + ENTITY_COLLISION_CONTACT_MARGIN);
        return targetBox.contains(previousCenter) || targetBox.contains(currentCenter) ||
            targetBox.clip(previousCenter, currentCenter).isPresent();
    }

    private boolean applyEntityCollisionImpact(Entity target, Vec3 sweepDisplacement,
                                               AABB planeBox, boolean damageReady) {
        Vec3 motion = getDeltaMovement();
        double speedPerTick = isFiniteVector(motion) ? motion.length() : 0.0D;
        double speedBlocksPerSecond = speedPerTick * 20.0D;

        Vec3 impactDirection;
        if (isFiniteVector(sweepDisplacement) &&
            sweepDisplacement.lengthSqr() > 1.0E-8D) {
            impactDirection = sweepDisplacement.normalize();
        } else if (speedPerTick > 1.0E-4D) {
            impactDirection = motion.scale(1.0D / speedPerTick);
        } else {
            Vec3 awayFromPlane = target.getBoundingBox().getCenter()
                .subtract(planeBox.getCenter());
            impactDirection = awayFromPlane.lengthSqr() > 1.0E-8D
                ? awayFromPlane.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
        }

        boolean damageCooldownConsumed = false;
        if (damageReady && speedBlocksPerSecond >= ENTITY_COLLISION_DAMAGE_SPEED_BPS) {
            float damage = ENTITY_COLLISION_BASE_DAMAGE +
                (float) ((speedBlocksPerSecond - ENTITY_COLLISION_DAMAGE_SPEED_BPS) *
                    ENTITY_COLLISION_DAMAGE_PER_EXCESS_BPS);
            damageCooldownConsumed = true;
            target.hurt(AmracDamage.source(level(),
                AmracDamage.PLANE_COLLISION, this, getPlayer()), damage);
        }

        if (target.isRemoved()) {
            return damageCooldownConsumed;
        }

        pushTargetOutsideAirframe(target, planeBox, impactDirection);

        double knockback = Mth.clamp(MIN_ENTITY_COLLISION_KNOCKBACK +
                speedBlocksPerSecond * ENTITY_COLLISION_KNOCKBACK_PER_BPS,
            MIN_ENTITY_COLLISION_KNOCKBACK, MAX_ENTITY_COLLISION_KNOCKBACK);
        double verticalKnockback = Mth.clamp(
            impactDirection.y() * knockback + 0.10D, -0.60D, 0.80D);
        Vec3 targetMotion = target.getDeltaMovement();
        double forwardMotion = targetMotion.dot(impactDirection);
        if (forwardMotion < knockback) {
            targetMotion = targetMotion.add(
                impactDirection.scale(knockback - forwardMotion));
        }
        if (targetMotion.y() < verticalKnockback) {
            targetMotion = new Vec3(targetMotion.x(), verticalKnockback,
                targetMotion.z());
        }
        target.setDeltaMovement(targetMotion);
        target.hurtMarked = true;
        return damageCooldownConsumed;
    }

    private void pushTargetOutsideAirframe(Entity target, AABB planeBox,
                                           Vec3 impactDirection) {
        AABB targetBox = target.getBoundingBox();
        Vec3 planeCenter = planeBox.getCenter();
        Vec3 targetCenter = targetBox.getCenter();
        double requiredTargetProjection = planeCenter.dot(impactDirection) +
            projectedBoxExtent(planeBox, impactDirection) +
            projectedBoxExtent(targetBox, impactDirection) +
            ENTITY_COLLISION_CONTACT_MARGIN;
        double separationDistance = requiredTargetProjection -
            targetCenter.dot(impactDirection);
        if (separationDistance <= 1.0E-4D) {
            return;
        }
        target.move(MoverType.SELF, impactDirection.scale(separationDistance));
        if (level().isClientSide()) {
            double targetX = target.getX();
            double targetY = target.getY();
            double targetZ = target.getZ();
            target.snapTo(targetX, targetY, targetZ, target.getYRot(),
                target.getXRot());
            target.xOld = targetX;
            target.yOld = targetY;
            target.zOld = targetZ;
        }
    }

    private static double projectedBoxExtent(AABB box, Vec3 direction) {
        return Math.abs(direction.x()) * box.getXsize() * 0.5D +
            Math.abs(direction.y()) * box.getYsize() * 0.5D +
            Math.abs(direction.z()) * box.getZsize() * 0.5D;
    }

    public boolean getOnGround() {
        return onGround() || onGroundTicks > 1;
    }

    public boolean isOnWater() {
        return getWaterSubmersion() > 0.0D;
    }

    public double getWaterSubmersion() {
        if (waterSubmersionSampleTick == tickCount &&
            Math.abs(waterSubmersionSampleX - getX()) < 1.0E-4D &&
            Math.abs(waterSubmersionSampleY - getY()) < 1.0E-4D &&
            Math.abs(waterSubmersionSampleZ - getZ()) < 1.0E-4D) {
            return cachedWaterSubmersion;
        }

        final double[][] horizontalSamples = {
            {0.0D, 0.0D},
            {WATER_SAMPLE_RADIUS, 0.0D},
            {-WATER_SAMPLE_RADIUS, 0.0D},
            {0.0D, WATER_SAMPLE_RADIUS},
            {0.0D, -WATER_SAMPLE_RADIUS}
        };
        final double[] verticalSamples = {
            WATER_SAMPLE_HEIGHT - 0.25D,
            WATER_SAMPLE_HEIGHT + 0.25D
        };

        int wetSamples = 0;
        int sampleCount = horizontalSamples.length * verticalSamples.length;
        for (double verticalOffset : verticalSamples) {
            for (double[] sample : horizontalSamples) {
                BlockPos samplePos = BlockPos.containing(getX() + sample[0],
                    getY() + verticalOffset, getZ() + sample[1]);
                if (level().getFluidState(samplePos).is(FluidTags.WATER)) {
                    ++wetSamples;
                }
            }
        }
        cachedWaterSubmersion = sampleCount > 0
            ? wetSamples / (double) sampleCount : 0.0D;
        waterSubmersionSampleTick = tickCount;
        waterSubmersionSampleX = getX();
        waterSubmersionSampleY = getY();
        waterSubmersionSampleZ = getZ();
        return cachedWaterSubmersion;
    }

    @Override
    public void rideTick() {
        super.rideTick();
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        boolean b = (passenger instanceof Player) && ((Player) passenger).isLocalPlayer();

        if (hasPassenger(passenger) && !b) {
            applyYawToEntity(passenger);
        }
    }

    public void applyYawToEntity(Entity entityToUpdate) {
        entityToUpdate.setYHeadRot(entityToUpdate.getYHeadRot() + deltaRotation);

        entityToUpdate.yRotO += deltaRotation;

        entityToUpdate.setYBodyRot(getYRot());

        float f = wrapDegrees(entityToUpdate.yRotO - getYRot());
        float f1 = Mth.clamp(f, -105.0F, 105.0F);

        float perc = deltaRotationTicks > 0 ? 1f / deltaRotationTicks : 1f;
        float diff = (f1 - f) * perc;

        entityToUpdate.setYRot(entityToUpdate.getYRot() + diff);
        entityToUpdate.yRotO += diff;

        entityToUpdate.setYHeadRot(entityToUpdate.getYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity livingEntity) {
        return super.getDismountLocationForPassenger(livingEntity);
    }

    private boolean remotePositionInitialized;
    private boolean remotePositionSnapshotPending;
    private int remotePositionTicksWithoutSnapshot;
    private Vec3 remoteLastNetworkPosition = Vec3.ZERO;
    private Vec3 remoteTrajectoryPosition = Vec3.ZERO;
    private Vec3 remoteSmoothingVelocity = Vec3.ZERO;

    private boolean remoteAttitudeInitialized;
    private boolean remoteAttitudeSnapshotPending;
    private int remoteAttitudeTicksWithoutSnapshot;
    private Quaternionf remoteLastNetworkAttitude = new Quaternionf();
    private Quaternionf remoteTrajectoryAttitude = new Quaternionf();
    private Quaternionf remoteAngularStep = new Quaternionf();

    private void tickLerp() {
        if (!level().isClientSide()) {
            return;
        }
        if (isLocalInstanceAuthoritative()) {
            resetRemoteSmoothing();
            syncPacketPositionCodec(getX(), getY(), getZ());
            return;
        }

        tickRemotePositionSmoothing();
        tickRemoteAttitudeSmoothing();
    }

    private void tickRemotePositionSmoothing() {
        if (!remotePositionInitialized) {
            return;
        }

        if (remotePositionSnapshotPending) {
            remoteTrajectoryPosition = remoteLastNetworkPosition;
            remotePositionTicksWithoutSnapshot = 0;
            remotePositionSnapshotPending = false;
        } else {
            ++remotePositionTicksWithoutSnapshot;
            double retention = RemotePlaneSmoothingPolicy.staleMotionRetention(
                remotePositionTicksWithoutSnapshot);
            if (retention < 1.0D) {
                remoteSmoothingVelocity = remoteSmoothingVelocity.scale(retention);
            }
            remoteTrajectoryPosition = remoteTrajectoryPosition.add(
                remoteSmoothingVelocity);
        }

        Vec3 currentPosition = position();
        Vec3 predictedPosition = currentPosition.add(remoteSmoothingVelocity);
        Vec3 predictionError = remoteTrajectoryPosition.subtract(predictedPosition);
        double speed = remoteSmoothingVelocity.length();
        double targetDistance = remoteTrajectoryPosition.subtract(currentPosition).length();
        Vec3 nextPosition;
        if (RemotePlaneSmoothingPolicy.shouldSnapPosition(targetDistance, speed, false)) {
            nextPosition = remoteTrajectoryPosition;
        } else {
            double correctionScale = RemotePlaneSmoothingPolicy.correctionScale(
                predictionError.length(), speed);
            nextPosition = predictedPosition.add(predictionError.scale(correctionScale));
        }

        if (!isFiniteVector(nextPosition)) {
            nextPosition = remoteLastNetworkPosition;
            remoteSmoothingVelocity = Vec3.ZERO;
        }
        setPos(nextPosition.x(), nextPosition.y(), nextPosition.z());
        setDeltaMovement(remoteSmoothingVelocity);
    }

    private void tickRemoteAttitudeSmoothing() {
        if (!remoteAttitudeInitialized) {
            return;
        }

        if (remoteAttitudeSnapshotPending) {
            remoteTrajectoryAttitude = new Quaternionf(remoteLastNetworkAttitude);
            remoteAttitudeTicksWithoutSnapshot = 0;
            remoteAttitudeSnapshotPending = false;
        } else {
            ++remoteAttitudeTicksWithoutSnapshot;
            if (remoteAttitudeTicksWithoutSnapshot >
                RemotePlaneSmoothingPolicy.MAX_EXTRAPOLATION_TICKS) {
                remoteAngularStep = lerpQ((float) (1.0D -
                    RemotePlaneSmoothingPolicy.STALE_MOTION_RETENTION),
                    remoteAngularStep, new Quaternionf());
            }
            remoteTrajectoryAttitude = applyQuaternionStep(
                remoteTrajectoryAttitude, remoteAngularStep);
        }

        Quaternionf currentAttitude = getQ_Client();
        Quaternionf predictedAttitude = applyQuaternionStep(currentAttitude,
            remoteAngularStep);
        double angularError = quaternionAngularDistance(predictedAttitude,
            remoteTrajectoryAttitude);
        Quaternionf nextAttitude = RemotePlaneSmoothingPolicy.shouldSnapAttitude(
            angularError)
            ? new Quaternionf(remoteTrajectoryAttitude)
            : lerpQ(RemotePlaneSmoothingPolicy.ATTITUDE_CORRECTION_BLEND,
                predictedAttitude, remoteTrajectoryAttitude);
        setQ_prev(currentAttitude);
        setQ_Client(nextAttitude);
    }

    private void acceptRemoteAttitudeSnapshot(Quaternionf snapshot) {
        Quaternionf normalizedSnapshot = normalizeQuaternion(snapshot);
        if (!remoteAttitudeInitialized || firstTick) {
            remoteAttitudeInitialized = true;
            remoteAttitudeSnapshotPending = false;
            remoteAttitudeTicksWithoutSnapshot = 0;
            remoteLastNetworkAttitude = new Quaternionf(normalizedSnapshot);
            remoteTrajectoryAttitude = new Quaternionf(normalizedSnapshot);
            remoteAngularStep = new Quaternionf();
            setQ_Client(normalizedSnapshot);
            setQ_prev(normalizedSnapshot);
            return;
        }

        Quaternionf inversePrevious = new Quaternionf(
            -remoteLastNetworkAttitude.x(), -remoteLastNetworkAttitude.y(),
            -remoteLastNetworkAttitude.z(), remoteLastNetworkAttitude.w());
        inversePrevious.mul(normalizedSnapshot);
        Quaternionf sampledStep = normalizeQuaternion(inversePrevious);
        if (sampledStep.w() < 0.0F) {
            sampledStep = new Quaternionf(-sampledStep.x(), -sampledStep.y(),
                -sampledStep.z(), -sampledStep.w());
        }
        double sampledAngle = quaternionAngularDistance(
            new Quaternionf(), sampledStep);
        remoteAngularStep = lerpQ((float)
            RemotePlaneSmoothingPolicy.angularStepFraction(sampledAngle),
            new Quaternionf(), sampledStep);
        remoteLastNetworkAttitude = new Quaternionf(normalizedSnapshot);
        remoteAttitudeSnapshotPending = true;
    }

    private static Quaternionf applyQuaternionStep(Quaternionf attitude,
                                                  Quaternionf step) {
        Quaternionf result = new Quaternionf(attitude);
        result.mul(step);
        return normalizeQuaternion(result);
    }

    private static double quaternionAngularDistance(Quaternionf first,
                                                    Quaternionf second) {
        Quaternionf normalizedFirst = normalizeQuaternion(first);
        Quaternionf normalizedSecond = normalizeQuaternion(second);
        double dot = Math.abs(normalizedFirst.x() * normalizedSecond.x() +
            normalizedFirst.y() * normalizedSecond.y() +
            normalizedFirst.z() * normalizedSecond.z() +
            normalizedFirst.w() * normalizedSecond.w());
        return 2.0D * Math.acos(Mth.clamp(dot, 0.0D, 1.0D));
    }

    private static Vec3 blendMotion(Vec3 current, Vec3 sample,
                                        double amount) {
        return new Vec3(
            RemotePlaneSmoothingPolicy.blend(current.x(), sample.x(), amount),
            RemotePlaneSmoothingPolicy.blend(current.y(), sample.y(), amount),
            RemotePlaneSmoothingPolicy.blend(current.z(), sample.z(), amount));
    }

    private void resetRemoteSmoothing() {
        remotePositionInitialized = false;
        remotePositionSnapshotPending = false;
        remotePositionTicksWithoutSnapshot = 0;
        remoteLastNetworkPosition = position();
        remoteTrajectoryPosition = position();
        remoteSmoothingVelocity = Vec3.ZERO;
        remoteAttitudeInitialized = false;
        remoteAttitudeSnapshotPending = false;
        remoteAttitudeTicksWithoutSnapshot = 0;
        remoteLastNetworkAttitude = getQ_Client();
        remoteTrajectoryAttitude = getQ_Client();
        remoteAngularStep = new Quaternionf();
    }

    @Override
    public void lerpMotion(Vec3 movement) {
        if (level().isClientSide() && isLocalInstanceAuthoritative()) {
            return;
        }
        if (level().isClientSide()) {
            Vec3 sample = movement;
            if (!isFiniteVector(sample)) {
                sample = Vec3.ZERO;
            }
            remoteSmoothingVelocity = remoteSmoothingVelocity.lengthSqr() <= 1.0E-12D
                ? sample
                : blendMotion(remoteSmoothingVelocity, sample,
                    RemotePlaneSmoothingPolicy.MOTION_PACKET_BLEND);
            setDeltaMovement(remoteSmoothingVelocity);
            return;
        }
        super.lerpMotion(movement);
    }

    public void lerpTo(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        if (isLocalInstanceAuthoritative() || !Double.isFinite(x) ||
            !Double.isFinite(y) || !Double.isFinite(z)) {
            return;
        }
        Vec3 snapshot = new Vec3(x, y, z);
        if (!remotePositionInitialized || teleport) {
            remotePositionInitialized = true;
            remotePositionSnapshotPending = false;
            remotePositionTicksWithoutSnapshot = 0;
            remoteLastNetworkPosition = snapshot;
            remoteTrajectoryPosition = snapshot;
            absMoveTo(x, y, z, yaw, pitch);
            return;
        }

        Vec3 sampledVelocity = snapshot.subtract(remoteLastNetworkPosition);
        double plausibleSampleSpeed = Math.max(1.0D, getMaxSpeed() * 2.0D + 1.0D);
        if (isFiniteVector(sampledVelocity) &&
            sampledVelocity.length() <= plausibleSampleSpeed) {
            remoteSmoothingVelocity = remoteSmoothingVelocity.lengthSqr() <= 1.0E-12D
                ? sampledVelocity
                : blendMotion(remoteSmoothingVelocity, sampledVelocity,
                    RemotePlaneSmoothingPolicy.POSITION_SAMPLE_BLEND);
        }
        remoteLastNetworkPosition = snapshot;
        remotePositionSnapshotPending = true;
    }

    public void absMoveTo(double x, double y, double z, float yaw, float pitch) {
        double d0 = Mth.clamp(x, -3.0E7D, 3.0E7D);
        double d1 = Mth.clamp(z, -3.0E7D, 3.0E7D);
        xOld = d0;
        yOld = y;
        zOld = d1;
        setPos(d0, y, d1);
        setYRot(yaw % 360.0F);
        setXRot(pitch % 360.0F);

        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (passenger == getControllingPassenger()) {
            shutdownEngineAndControls();
        }
        if (isLocalInstanceAuthoritative()) {
            mountMessage = true;

            if (remotePositionInitialized) {
                absMoveTo(remoteLastNetworkPosition.x(),
                    remoteLastNetworkPosition.y(),
                    remoteLastNetworkPosition.z(), getYRot(), getXRot());
            }
            Quaternionf authoritativeAttitude = getQ();
            setQ_Client(authoritativeAttitude);
            setQ_prev(authoritativeAttitude);
            resetRemoteSmoothing();
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        boolean wasControllingPassenger =
            passenger == getControllingPassenger();
        super.removePassenger(passenger);
        if (wasControllingPassenger) {
            shutdownEngineAndControls();
        }
    }

    public Player getPlayer() {
        if (getControllingPassenger() instanceof Player) {
            return (Player) getControllingPassenger();
        }
        return null;
    }

    public double getCameraDistanceMultiplayer() {
        return 1;
    }

    protected static class TempMotionVars {
        public float pitchInput;
        public float yawInput;
        public float rollInput;
        public boolean groundReverse;
        double takeOffSpeed;
        double stallSpeed;
        double gravity;
        float pitchStabilityRate;
        float yawStabilityRate;
        double groundFrictionMultiplier;

        public TempMotionVars() {
            reset();
        }

        public void reset() {
            pitchInput = 0;
            yawInput = 0;
            rollInput = 0;
            groundReverse = false;
            takeOffSpeed = TAKEOFF_SPEED;
            stallSpeed = STALL_SPEED;
            gravity = -amrac.physics.aircraft.SpeedScale.gravityBlocksPerTickSquared();
            pitchStabilityRate = 0.0F;
            yawStabilityRate = 0.0F;
            groundFrictionMultiplier = 1.0D;
        }
    }
}
