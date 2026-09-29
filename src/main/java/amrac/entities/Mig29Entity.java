package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import amrac.weapons.MissileFaction;

public class Mig29Entity extends F16Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.MIG29;

    @Override
    public String flightModelId() {
        return FLIGHT_MODEL_ID;
    }

    @Override
    public MissileFaction missileFaction() {
        return MissileFaction.SOVIET;
    }

    @Override
    public java.util.Set<String> barredMissiles() {
        return java.util.Set.of("R771", "R73");
    }

    public static final float DRY_MAX_SPEED = mach(0.736);
    public static final float WET_MAX_SPEED = mach(1.0);

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 2.06F;
    private static final float PITCH_RATE_SCALE = 1.70F;

    private static final int SECOND_ENGINE_NUMERATOR = 8;
    private static final int SECOND_ENGINE_DENOMINATOR = 5;

    public Mig29Entity(EntityType<? extends Mig29Entity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(DRY_MAX_SPEED);
    }

    public Mig29Entity(EntityType<? extends Mig29Entity> entityTypeIn, Level worldIn,
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
        vars.stallSpeed = 1.14D * AIRSPEED_SCALE;
        vars.takeOffSpeed = 1.42D * AIRSPEED_SCALE;
        return vars;
    }

    @Override
    public int getFuelCost() {
        return super.getFuelCost() * SECOND_ENGINE_NUMERATOR
            / SECOND_ENGINE_DENOMINATOR;
    }

    @Override
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return 0.2000D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.406875D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 0.8125D * MODEL_SCALE;
    }

    @Override
    public double getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.43D;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return 5.5D;
    }
    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.MIG29;
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
    public double[][] railPositions() {
        return RAILS;
    }

    private static final double[][] RAILS = {
        {-0.5938D, 0.2906D, -0.5856D},
        { 0.5938D, 0.2906D, -0.5856D},
        {-0.9688D, 0.2756D, -0.8775D},
        { 0.9688D, 0.2756D, -0.8775D}
    };
}
