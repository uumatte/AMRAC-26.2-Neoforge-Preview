package amrac.entities.loader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import amrac.AmracMod;
import amrac.entities.PlaneEntity;

public enum LoaderAirframe {
    F16("f16", 4, "F-16C"),
    F15("f15", 6, "F-15C"),
    F15E("f15e", 8, "F-15E"),
    SU27("su27", 6, "Su-27"),
    F4J("f4j", 4, "F-4J"),
    MIG29("mig29", 4, "MiG-29"),
    J10C("j10c", 6, "J-10C"),
    TYPHOON("typhoon", 6, "Typhoon"),
    MIG21("mig21", 4, "MiG-21"),
    J8II("j8ii", 4, "J-8II"),
    F18("f18", 6, "F/A-18C"),
    MIG23("mig23", 4, "MiG-23ML"),
    RAFALE("rafale", 6, "Rafale C"),
    SU30("su30", 8, "Su-30");

    private final String path;
    private final int stations;
    private final String label;

    LoaderAirframe(String path, int stations, String label) {
        this.path = path;
        this.stations = stations;
        this.label = label;
    }

    public String path() {
        return path;
    }

    public int stations() {
        return stations;
    }

    public String label() {
        return label;
    }

    public boolean matches(Entity entity) {
        return entity instanceof PlaneEntity
            && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                .equals(AmracMod.id(path));
    }

    public static LoaderAirframe byOrdinal(int ordinal) {
        LoaderAirframe[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }

    public LoaderAirframe cycle(int direction) {
        return byOrdinal(ordinal() + direction);
    }
}
