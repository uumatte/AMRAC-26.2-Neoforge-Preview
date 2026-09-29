package amrac.entities;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import amrac.AmracEntities;

public final class AircraftTypes {
    private static final Map<String, EntityType<? extends PlaneEntity>> BY_NAME =
        new LinkedHashMap<>();

    static {
        BY_NAME.put("f16", AmracEntities.F16);
        BY_NAME.put("f15", AmracEntities.F15);
        BY_NAME.put("su27", AmracEntities.SU27);
        BY_NAME.put("mig29", AmracEntities.MIG29);
        BY_NAME.put("j10c", AmracEntities.J10C);
        BY_NAME.put("typhoon", AmracEntities.TYPHOON);
        BY_NAME.put("mig21", AmracEntities.MIG21);
        BY_NAME.put("f4j", AmracEntities.F4J);
        BY_NAME.put("j8ii", AmracEntities.J8II);
        BY_NAME.put("f15e", AmracEntities.F15E);
        BY_NAME.put("f18", AmracEntities.F18);
        BY_NAME.put("mig23", AmracEntities.MIG23);
        BY_NAME.put("rafale", AmracEntities.RAFALE);
        BY_NAME.put("su30", AmracEntities.SU30);
    }

    private AircraftTypes() {
    }

    public static Map<String, EntityType<? extends PlaneEntity>> byName() {
        return Collections.unmodifiableMap(BY_NAME);
    }

    @Nullable
    public static EntityType<? extends PlaneEntity> byName(String name) {
        return name == null ? null : BY_NAME.get(name);
    }
}
