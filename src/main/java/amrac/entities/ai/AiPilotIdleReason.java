package amrac.entities.ai;

public enum AiPilotIdleReason {
    NONE(""),

    NOT_STARTED("Not started"),

    NO_AIRCRAFT("No empty aircraft within "
        + (int) AiPilotBrain.BOARDING_SEARCH_RADIUS + "m"),

    WRONG_AIRFRAME("Wrong airframe for this rank"),

    NO_MISSILES("No usable missiles for this aircraft"),

    NO_FUEL("Out of aviation fuel");

    private final String label;

    AiPilotIdleReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean shown() {
        return this != NONE;
    }

    public static AiPilotIdleReason onFoot(boolean aircraftInRange,
                                           boolean flyableInRange,
                                           boolean armableInRange) {
        if (!aircraftInRange) {
            return NO_AIRCRAFT;
        }
        if (!flyableInRange) {
            return WRONG_AIRFRAME;
        }
        return armableInRange ? NONE : NO_MISSILES;
    }

    public static AiPilotIdleReason byOrdinal(int ordinal) {
        AiPilotIdleReason[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
