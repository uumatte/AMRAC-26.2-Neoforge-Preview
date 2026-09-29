package amrac.client;

/**
 * Mouse X to roll/yaw is not sign-flipped: the PlaneClientControls accumulator is negative to the
 * right, and so are body roll and yaw. Change the sign in one place and the other two must follow.
 */
public final class HorizontalStickPolicy {
    public enum Axis {
        ROLL("roll"),
        YAW("yaw");

        private final String configName;

        Axis(String configName) {
            this.configName = configName;
        }

        public String configName() {
            return configName;
        }

        public String translationKey() {
            return "amrac.controls.horizontal_mouse_stick." + configName;
        }

        public Axis next() {
            return this == ROLL ? YAW : ROLL;
        }

        public static Axis fromConfigName(String name) {
            for (Axis axis : values()) {
                if (axis.configName.equalsIgnoreCase(name)) {
                    return axis;
                }
            }
            return null;
        }
    }

    public static final Axis DEFAULT_AXIS = Axis.ROLL;

    private HorizontalStickPolicy() {
    }

    public static float roll(Axis axis, float keyboard, float mouse) {
        if (axis == Axis.YAW) {
            return sane(keyboard);
        }
        return pick(keyboard, mouse);
    }

    public static float yaw(Axis axis, float keyboard, float mouse) {
        if (axis == Axis.ROLL) {
            return sane(keyboard);
        }
        return pick(keyboard, mouse);
    }

    private static float pick(float keyboard, float mouse) {
        float held = sane(keyboard);
        return held != 0.0F ? held : sane(mouse);
    }

    private static float sane(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }
}
