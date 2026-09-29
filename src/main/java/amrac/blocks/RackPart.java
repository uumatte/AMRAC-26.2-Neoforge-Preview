package amrac.blocks;

import net.minecraft.util.StringRepresentable;

public enum RackPart implements StringRepresentable {
    LEFT("left", 0),
    MID_LEFT("mid_left", 1),
    MID_RIGHT("mid_right", 2),
    RIGHT("right", 3);

    public static final int LENGTH = 4;

    private final String name;
    private final int index;

    RackPart(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public int index() {
        return index;
    }

    public static RackPart at(int index) {
        return values()[index];
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
