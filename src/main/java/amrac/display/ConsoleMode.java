package amrac.display;

import net.minecraft.util.StringRepresentable;

public enum ConsoleMode implements StringRepresentable {
    MAP("map", "Map"),
    CONFIG_PLANE("config_plane", "Config: Plane"),
    CONFIG_MISSILE("config_missile", "Config: Missile");

    private final String name;
    private final String label;

    ConsoleMode(String name, String label) {
        this.name = name;
        this.label = label;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String label() {
        return label;
    }

    public String translationKey() {
        return "amrac.console.mode." + name;
    }

    public ConsoleMode next() {
        ConsoleMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ConsoleMode byName(String name) {
        for (ConsoleMode mode : values()) {
            if (mode.name.equals(name)) {
                return mode;
            }
        }
        return MAP;
    }
}
