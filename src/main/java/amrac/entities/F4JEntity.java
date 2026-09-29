package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class F4JEntity extends F16Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.F4J;

    @Override
    public String flightModelId() {
        return FLIGHT_MODEL_ID;
    }

    public static final float DRY_MAX_SPEED = mach(0.668);
    public static final float WET_MAX_SPEED = mach(1.0588);

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 1.95F;
    private static final float PITCH_RATE_SCALE = 1.25F;

    private static final int SECOND_ENGINE_NUMERATOR = 9;
    private static final int SECOND_ENGINE_DENOMINATOR = 5;

    public F4JEntity(EntityType<? extends F4JEntity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(DRY_MAX_SPEED);
    }

    public F4JEntity(EntityType<? extends F4JEntity> entityTypeIn, Level worldIn,
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
    public boolean hasMachineGun() {
        return false;
    }

    @Override
    public boolean hasBombRack() {
        return true;
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
    protected TempMotionVars getMotionVars() {
        TempMotionVars vars = super.getMotionVars();
        vars.stallSpeed = 1.40D * AIRSPEED_SCALE;
        vars.takeOffSpeed = 1.75D * AIRSPEED_SCALE;
        return vars;
    }

    @Override
    public int getFuelCost() {
        return super.getFuelCost() * SECOND_ENGINE_NUMERATOR
            / SECOND_ENGINE_DENOMINATOR;
    }

    @Override
    public double getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.28D;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return 4.6D;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.F4J;
    }

    @Override
    public boolean semiActiveOnly() {
        return true;
    }

    @Override
    public double[][] railPositions() {
        return RAILS;
    }

    private static final double[][] RAILS = {
        {-0.517500D, 0.170625D, 0.230625D},
        { 0.517500D, 0.170625D, 0.230625D},
        {-0.842500D, 0.226250D, -0.310000D},
        { 0.842500D, 0.226250D, -0.310000D}
    };

    @Override
    public java.util.Set<String> barredMissiles() {
        return java.util.Set.of("MICA", "METEOR");
    }
}
