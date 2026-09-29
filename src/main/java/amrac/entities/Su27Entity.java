package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import amrac.weapons.MissileFaction;

public class Su27Entity extends F16Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.SU27;

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
        return 6;
    }

    @Override
    public double[][] railPositions() {
        return RAILS;
    }

    private static final double[][] RAILS = {
        {-0.531250D, 0.515625D, -0.500000D},
        { 0.531250D, 0.515625D, -0.500000D},
        {-0.750000D, 0.513750D, -0.750000D},
        { 0.750000D, 0.513750D, -0.750000D},
        {-1.125000D, 0.510625D, -1.062500D},
        { 1.125000D, 0.510625D, -1.062500D}
    };

    public static final float DRY_MAX_SPEED = mach(0.736);
    public static final float WET_MAX_SPEED = mach(1.1176);

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 1.86F;
    private static final float PITCH_RATE_SCALE = 1.62F;

    private static final int SECOND_ENGINE_NUMERATOR = 8;
    private static final int SECOND_ENGINE_DENOMINATOR = 5;

    public Su27Entity(EntityType<? extends Su27Entity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(DRY_MAX_SPEED);
    }

    public Su27Entity(EntityType<? extends Su27Entity> entityTypeIn, Level worldIn,
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
        vars.stallSpeed = 1.12D * AIRSPEED_SCALE;
        vars.takeOffSpeed = 1.40D * AIRSPEED_SCALE;
        return vars;
    }

    @Override
    public int getFuelCost() {
        return super.getFuelCost() * SECOND_ENGINE_NUMERATOR
            / SECOND_ENGINE_DENOMINATOR;
    }

    @Override
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return -0.15625D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.692438D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 1.5625D * MODEL_SCALE;
    }

    @Override
    public double getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 2.08D;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return 5.5D;
    }
    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.SU27;
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
        return java.util.Set.of();
    }
}
