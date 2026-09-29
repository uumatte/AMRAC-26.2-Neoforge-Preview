package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import amrac.weapons.MissileFaction;

public class J10cEntity extends F16Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.J10C;

    @Override
    public String flightModelId() {
        return FLIGHT_MODEL_ID;
    }

    @Override
    public MissileFaction missileFaction() {
        return MissileFaction.CHINA;
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
        {-0.487500D, 0.150625D, -0.512500D},
        { 0.487500D, 0.150625D, -0.512500D},
        {-0.700000D, 0.150625D, -0.662500D},
        { 0.700000D, 0.150625D, -0.662500D},
        {-0.925000D, 0.158750D, -0.843750D},
        { 0.925000D, 0.158750D, -0.843750D}
    };

    public static final float DRY_MAX_SPEED = mach(0.736);
    public static final float WET_MAX_SPEED = mach(1.1764);

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 1.94F;
    private static final float PITCH_RATE_SCALE = 1.78F;

    private static final int SECOND_ENGINE_NUMERATOR = 1;
    private static final int SECOND_ENGINE_DENOMINATOR = 1;

    public J10cEntity(EntityType<? extends J10cEntity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(DRY_MAX_SPEED);
    }

    public J10cEntity(EntityType<? extends J10cEntity> entityTypeIn, Level worldIn,
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
        vars.stallSpeed = 1.02D * AIRSPEED_SCALE;
        vars.takeOffSpeed = 1.27D * AIRSPEED_SCALE;
        return vars;
    }

    @Override
    public int getFuelCost() {
        return super.getFuelCost() * SECOND_ENGINE_NUMERATOR
            / SECOND_ENGINE_DENOMINATOR;
    }

    @Override
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return 0.1625D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.154375D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 0.7500D * MODEL_SCALE;
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
        return amrac.AmracItems.J10C;
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
