package amrac.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class F15EEntity extends F15Entity {
    public static final String FLIGHT_MODEL_ID =
        amrac.physics.aircraft.AircraftFlightModelIds.F15E;

    @Override
    public String flightModelId() {
        return FLIGHT_MODEL_ID;
    }

    @Override
    public int pylonCount() {
        return 8;
    }

    @Override
    public java.util.Set<String> barredMissiles() {
        return java.util.Set.of("MICA", "METEOR");
    }

    @Override
    public double[][] railPositions() {
        return RAILS;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return amrac.AmracItems.F15E;
    }

    private static final double[][] RAILS = {
        {-0.825000D, 0.477500D, -0.546250D},
        { 0.825000D, 0.477500D, -0.546250D},
        {-0.475000D, 0.460000D, -0.146250D},
        { 0.475000D, 0.460000D, -0.146250D},
        {-0.520000D, 0.435000D, -0.921250D},
        { 0.520000D, 0.435000D, -0.921250D},
        {-0.712500D, 0.547500D, -0.546250D},
        { 0.712500D, 0.547500D, -0.546250D}
    };

    public F15EEntity(EntityType<? extends F15EEntity> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
    }

    public F15EEntity(EntityType<? extends F15EEntity> entityTypeIn, Level worldIn,
                      double x, double y, double z) {
        this(entityTypeIn, worldIn);
        setPos(x, y, z);
    }
}
