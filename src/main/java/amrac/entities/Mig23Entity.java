package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import amrac.weapons.MissileFaction;

public class Mig23Entity extends F16Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.MIG23;

    @Override
    public String flightModelId() {
        return FLIGHT_MODEL_ID;
    }

    @Override
    public MissileFaction missileFaction() {
        return MissileFaction.SOVIET;
    }

    @Override
    public int pylonCount() {
        return 4;
    }

    @Override
    public double[][] railPositions() {
        return RAILS;
    }

    private static final double[][] RAILS = {
        {-0.287500D, 0.353750D, -0.750000D},
        { 0.287500D, 0.353750D, -0.750000D},
        {-0.475000D, 0.365625D, -0.912500D},
        { 0.475000D, 0.365625D, -0.912500D}
    };

    public static final float DRY_MAX_SPEED = mach(0.744);
    public static final float WET_MAX_SPEED = mach(1.2648);

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 1.80F;
    private static final float PITCH_RATE_SCALE = 1.15F;

    public Mig23Entity(EntityType<? extends Mig23Entity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(DRY_MAX_SPEED);
    }

    public Mig23Entity(EntityType<? extends Mig23Entity> entityTypeIn, Level worldIn,
                       double x, double y, double z) {
        this(entityTypeIn, worldIn);
        setPos(x, y, z);
    }

    @Override
    protected float getDryMaxSpeed() {
        return DRY_MAX_SPEED;
    }

    @Override
    protected float getWetMaxSpeed() {
        return WET_MAX_SPEED;
    }

    @Override
    protected float getRollRateScale() {
        return ROLL_RATE_SCALE;
    }

    @Override
    protected float getPitchRateScale() {
        return PITCH_RATE_SCALE;
    }

    @Override
    protected TempMotionVars getMotionVars() {
        TempMotionVars vars = super.getMotionVars();
        vars.stallSpeed = 1.41D * AIRSPEED_SCALE;
        vars.takeOffSpeed = 1.76D * AIRSPEED_SCALE;
        return vars;
    }

    @Override
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return 0.0D;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.3100D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 1.2500D * MODEL_SCALE;
    }

    @Override
    public double getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.72D;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return 4.3D;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.MIG23;
    }

    @Override
    public boolean hasRadar() {
        return true;
    }

    @Override
    public boolean hasMissiles() {
        return true;
    }

    @Override
    public java.util.Set<String> barredMissiles() {
        return java.util.Set.of("R77", "R771");
    }
}
