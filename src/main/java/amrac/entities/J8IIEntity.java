package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import amrac.weapons.MissileFaction;

public class J8IIEntity extends F16Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.J8II;

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
        return 4;
    }

    @Override
    public double[][] railPositions() {
        return RAILS;
    }

    private static final double[][] RAILS = {
        {-0.445000D, 0.279375D, -0.156875D},
        { 0.445000D, 0.279375D, -0.156875D},
        {-0.926250D, 0.284375D, -0.820625D},
        { 0.926250D, 0.284375D, -0.820625D}
    };

    public static final float DRY_MAX_SPEED = mach(0.78);
    public static final float WET_MAX_SPEED = mach(1.2942);

    public static final double MODEL_SCALE = 4.0D;

    private static final float ROLL_RATE_SCALE = 1.70F;
    private static final float PITCH_RATE_SCALE = 1.25F;

    public J8IIEntity(EntityType<? extends J8IIEntity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
        setMaxSpeed(DRY_MAX_SPEED);
    }

    public J8IIEntity(EntityType<? extends J8IIEntity> entityTypeIn, Level worldIn,
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
        vars.stallSpeed = 1.40D * AIRSPEED_SCALE;
        vars.takeOffSpeed = 1.75D * AIRSPEED_SCALE;
        return vars;
    }

    @Override
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return 0.0D;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.29875D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 1.190625D * MODEL_SCALE;
    }

    @Override
    public double getModelScale() {
        return MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.48D;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return 5.5D;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.J8II;
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
        return java.util.Set.of("PL10", "PL12A", "PL15");
    }
}
