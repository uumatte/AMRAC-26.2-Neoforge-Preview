package amrac.entities;

public final class TransonicPolicy {
    public static final double CLOUD_START_MACH = 0.92D;
    public static final double CLOUD_PEAK_MACH = 1.00D;
    public static final double CLOUD_END_MACH = 1.12D;

    public static final double BOOM_MACH = 1.00D;
    public static final double BOOM_RESET_MACH = 0.94D;

    public static final double SILENCE_MACH = 1.00D;
    public static final double SILENCE_RESET_MACH = 0.98D;

    private TransonicPolicy() {
    }

    public static double cloudIntensity(double mach) {
        if (!Double.isFinite(mach) || mach <= CLOUD_START_MACH ||
            mach >= CLOUD_END_MACH) {
            return 0.0D;
        }
        if (mach <= CLOUD_PEAK_MACH) {
            return (mach - CLOUD_START_MACH) / (CLOUD_PEAK_MACH - CLOUD_START_MACH);
        }
        return (CLOUD_END_MACH - mach) / (CLOUD_END_MACH - CLOUD_PEAK_MACH);
    }

    public static boolean shouldBoom(double mach, boolean armed) {
        return armed && Double.isFinite(mach) && mach >= BOOM_MACH;
    }

    public static boolean rearmsBoom(double mach) {
        return Double.isFinite(mach) && mach < BOOM_RESET_MACH;
    }

    public static boolean isSilent(double mach, boolean wasSilent) {
        if (!Double.isFinite(mach)) {
            return false;
        }
        return wasSilent ? mach > SILENCE_RESET_MACH : mach >= SILENCE_MACH;
    }
}
