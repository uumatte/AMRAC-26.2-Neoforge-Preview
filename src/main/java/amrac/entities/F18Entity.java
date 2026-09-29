package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import amrac.weapons.MissileFaction;

public class F18Entity extends F16Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.F18;

    @Override
    public String flightModelId() {
        return FLIGHT_MODEL_ID;
    }

    @Override
    public MissileFaction missileFaction() {
        return MissileFaction.NATO;
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
        {-0.581250D, 0.362500D, -0.468750D},
        { 0.581250D, 0.362500D, -0.468750D},
        {-0.875000D, 0.365625D, -0.593750D},
        { 0.875000D, 0.365625D, -0.593750D},
        {-1.187500D, 0.368750D, -0.718750D},
        { 1.187500D, 0.368750D, -0.718750D}
    };

    public static final float DRY_MAX_SPEED = mach(0.716);
    public static final float WET_MAX_SPEED = mach(1.0294);

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 2.20F;
    private static final float PITCH_RATE_SCALE = 1.85F;

    public F18Entity(EntityType<? extends F18Entity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(DRY_MAX_SPEED);
    }

    public F18Entity(EntityType<? extends F18Entity> entityTypeIn, Level worldIn,
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
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return 0.0D;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.0450D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 1.5500D * MODEL_SCALE;
    }

    @Override
    public double getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 2.05D;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return 4.3D;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.F18;
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
        return java.util.Set.of("MICA", "METEOR");
    }
}
