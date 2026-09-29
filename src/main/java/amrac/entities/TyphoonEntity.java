package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import amrac.AmracItems;
import amrac.physics.aircraft.AircraftFlightModelIds;
import amrac.physics.aircraft.AircraftPhysicsProfile;
import amrac.physics.aircraft.AircraftPhysicsResult;
import amrac.physics.aircraft.FlightModelRegistry;
import amrac.weapons.MissileFaction;

public class TyphoonEntity extends J10cEntity {
    public static final String FLIGHT_MODEL_ID = AircraftFlightModelIds.TYPHOON;

    private static final double[][] RAILS = {
        {-0.412500D, 0.270625D, -0.280000D},
        { 0.412500D, 0.270625D, -0.280000D},
        {-0.687500D, 0.250000D, -0.580000D},
        { 0.687500D, 0.250000D, -0.580000D},
        {-0.937500D, 0.255000D, -0.792500D},
        { 0.937500D, 0.255000D, -0.792500D}
    };

    public TyphoonEntity(EntityType<? extends TyphoonEntity> entityType,
                         Level level) {
        super(entityType, level);
        setMaxSpeed(getDryMaxSpeed());
    }

    public TyphoonEntity(EntityType<? extends TyphoonEntity> entityType,
                         Level level, double x, double y, double z) {
        this(entityType, level);
        setPos(x, y, z);
    }

    @Override
    public String flightModelId() {
        return FLIGHT_MODEL_ID;
    }

    @Override
    public MissileFaction missileFaction() {
        return MissileFaction.NATO;
    }

    @Override
    public java.util.Set<String> barredMissiles() {
        return java.util.Set.of("MICA");
    }

    @Override
    public double[][] railPositions() {
        return RAILS;
    }

    @Override
    public double getMachineGunMuzzleSideOffset(boolean rightSide) {
        return -0.23125D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleHeight() {
        return 0.3675D * MODEL_SCALE;
    }

    @Override
    public double getMachineGunMuzzleForwardOffset() {
        return 0.53125D * MODEL_SCALE;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.20D;
    }

    @Override
    public double getCameraDistanceMultiplayer() {
        return 4.05D;
    }

    @Override
    protected Item getDropItem() {
        return AmracItems.TYPHOON;
    }
}
