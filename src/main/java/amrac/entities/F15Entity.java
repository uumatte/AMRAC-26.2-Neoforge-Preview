package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class F15Entity extends F16Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.F15;

    @Override
    public String flightModelId() {
        return FLIGHT_MODEL_ID;
    }

    public static final float DRY_MAX_SPEED = mach(0.75);
    public static final float WET_MAX_SPEED = mach(1.2);

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 1.55F;
    private static final float PITCH_RATE_SCALE = 1.45F;

    private static final int SECOND_ENGINE_NUMERATOR = 3;
    private static final int SECOND_ENGINE_DENOMINATOR = 2;

    public F15Entity(EntityType<? extends F15Entity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(DRY_MAX_SPEED);
    }

    public F15Entity(EntityType<? extends F15Entity> entityTypeIn, Level worldIn,
                     double x, double y, double z) {
        this(entityTypeIn, worldIn);
        setPos(x, y, z);
    }

    @Override
    public int pylonCount() {
        return 6;
    }

    @Override
    public java.util.Set<String> barredMissiles() {
        return java.util.Set.of("MICA", "METEOR");
    }

    @Override
    public double[][] railPositions() {
        return RAILS;
    }

    private static final double[][] RAILS = {
        {-0.825000D, 0.477500D, -0.546250D},
        { 0.825000D, 0.477500D, -0.546250D},
        {-0.475000D, 0.460000D, -0.146250D},
        { 0.475000D, 0.460000D, -0.146250D},
        {-0.520000D, 0.435000D, -0.921250D},
        { 0.520000D, 0.435000D, -0.921250D}
    };

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
        vars.stallSpeed = 1.16D * AIRSPEED_SCALE;
        vars.takeOffSpeed = 1.45D * AIRSPEED_SCALE;
        return vars;
    }

    @Override
    public int getFuelCost() {
        return super.getFuelCost() * SECOND_ENGINE_NUMERATOR
            / SECOND_ENGINE_DENOMINATOR;
    }

    @Override
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return -0.4375D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.703438D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 0.09375D * MODEL_SCALE;
    }

    @Override
    public double getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.85D;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return 5.2D;
    }
    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.F15;
    }
    @Override
    public boolean hasRadar() {
        return true;
    }
    @Override
    public boolean hasMissiles() {
        return true;
    }
}
