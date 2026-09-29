package amrac.client;

public final class StickLimitPolicy {
    static final double[][] SCHEDULE = {
        {2000.0D, 1.00D},
        {4000.0D, 0.70D},
        {5500.0D, 0.48D},
        {7000.0D, 0.30D},
        {9000.0D, 0.22D},
        {11000.0D, 0.17D},
        {13000.0D, 0.14D},
    };

    public static final double FULL_STICK_ALTITUDE = SCHEDULE[0][0];

    public static final double MINIMUM_SHARE = SCHEDULE[SCHEDULE.length - 1][1];

    private StickLimitPolicy() {
    }

    public static double pitchShare(double altitude) {
        if (!Double.isFinite(altitude) || altitude <= SCHEDULE[0][0]) {
            return SCHEDULE[0][1];
        }
        for (int i = 1; i < SCHEDULE.length; i++) {
            if (altitude <= SCHEDULE[i][0]) {
                double t = (altitude - SCHEDULE[i - 1][0])
                    / (SCHEDULE[i][0] - SCHEDULE[i - 1][0]);
                return SCHEDULE[i - 1][1]
                    + t * (SCHEDULE[i][1] - SCHEDULE[i - 1][1]);
            }
        }
        return MINIMUM_SHARE;
    }

    public static float limit(float pitchInput, double altitude) {
        return (float) (pitchInput * pitchShare(altitude));
    }
}
