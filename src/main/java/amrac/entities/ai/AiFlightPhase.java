package amrac.entities.ai;

/**
 * Derived in one place (AiPilotDirector.phase) and shared by both layers; don't re-derive the phase
 * elsewhere from booleans.
 */
public enum AiFlightPhase {
    IDLE("Idle"),

    TAKEOFF("Takeoff"),

    CLIMB("Climb"),

    CRUISE("Cruise"),

    ENGAGE("Engage"),

    EGRESS("Egress"),

    RECOVER("Recover"),

    EMERGENCY("Emergency");

    private final String label;

    AiFlightPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean airborne() {
        return this != IDLE && this != TAKEOFF;
    }
}
