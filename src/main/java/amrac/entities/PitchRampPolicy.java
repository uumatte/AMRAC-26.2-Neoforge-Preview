package amrac.entities;

public final class PitchRampPolicy {
    private PitchRampPolicy() {
    }

    public static final int RAMP_START_AUTHORITY = 25;

    public static final int RAMP_TICKS = 10;

    public static int rampedAuthority(int targetAuthority, int ticksHeld) {
        if (targetAuthority <= RAMP_START_AUTHORITY) {
            return targetAuthority;
        }
        if (ticksHeld >= RAMP_TICKS) {
            return targetAuthority;
        }
        int held = Math.max(0, ticksHeld);
        int span = targetAuthority - RAMP_START_AUTHORITY;
        return RAMP_START_AUTHORITY + span * held / RAMP_TICKS;
    }
}
