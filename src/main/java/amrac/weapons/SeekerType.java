package amrac.weapons;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum SeekerType {
    IR,
    ARH,
    SARH;

    public boolean radar() {
        return this != IR;
    }

    @Nullable
    public static SeekerType parse(@Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
