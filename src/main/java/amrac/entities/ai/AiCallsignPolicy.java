package amrac.entities.ai;

public final class AiCallsignPolicy {
    public static final int MIN_NUMBER = 0;
    public static final int NUMBER_BOUND = 10000;

    public static String displayName(String callsign, String team) {
        String name = callsign == null || callsign.isBlank() ? "AI" : callsign;
        return team == null || team.isBlank() ? name : name + " [" + team + "]";
    }

    private AiCallsignPolicy() {
    }

    public static String format(AiPilotRank rank, int number) {
        AiPilotRank safe = rank == null ? AiPilotRank.TRAINEE : rank;
        return safe.label() + String.format("%04d", normalise(number));
    }

    public static int normalise(int number) {
        return Math.floorMod(number, NUMBER_BOUND);
    }

    public static boolean assigned(int number) {
        return number >= MIN_NUMBER && number < NUMBER_BOUND;
    }

    public static final int UNASSIGNED = -1;
}
