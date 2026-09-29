package amrac.entities;

public final class ManeuverRatePolicy {
    public static final float PREVIOUS_RATE_SCALE = 1.10F;
    public static final float PITCH_RATE_SCALE =
        PREVIOUS_RATE_SCALE * 1.15F;
    public static final float ROLL_RATE_SCALE =
        PREVIOUS_RATE_SCALE * 1.20F;

    public static final float MAX_PITCH_RATE_CHANGE_PER_TICK =
        0.80F * PITCH_RATE_SCALE;
    public static final float MAX_ROLL_RATE_CHANGE_PER_TICK =
        1.60F * ROLL_RATE_SCALE;
    public static final float MAX_PITCH_ANGULAR_RATE =
        3.6F * PITCH_RATE_SCALE;
    public static final float MAX_ROLL_ANGULAR_RATE =
        6.0F * ROLL_RATE_SCALE;

    private ManeuverRatePolicy() {
    }
}
