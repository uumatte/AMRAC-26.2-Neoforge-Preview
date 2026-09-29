package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import amrac.weapons.MissileFaction;

public class RafaleEntity extends F16Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.RAFALE;

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
        {-0.587500D, 0.286250D, -0.616250D},
        { 0.587500D, 0.286250D, -0.616250D},
        {-0.837500D, 0.281250D, -0.778750D},
        { 0.837500D, 0.281250D, -0.778750D},
        {-1.025000D, 0.276250D, -0.916250D},
        { 1.025000D, 0.276250D, -0.916250D}
    };

    public static final float DRY_MAX_SPEED = mach(0.75);
    public static final float WET_MAX_SPEED = mach(1.1176);

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 2.30F;
    private static final float PITCH_RATE_SCALE = 1.95F;

    public RafaleEntity(EntityType<? extends RafaleEntity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(DRY_MAX_SPEED);
    }

    public RafaleEntity(EntityType<? extends RafaleEntity> entityTypeIn, Level worldIn,
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
        vars.stallSpeed = 0.98D * AIRSPEED_SCALE;
        vars.takeOffSpeed = 1.23D * AIRSPEED_SCALE;
        return vars;
    }

    @Override
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return -0.0850D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.1500D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 1.1000D * MODEL_SCALE;
    }

    @Override
    public double getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 2.00D;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return 3.9D;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.RAFALE;
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
        return java.util.Set.of("AIM7", "AIM120", "AIM120L", "AIM9", "METEOR");
    }
}
