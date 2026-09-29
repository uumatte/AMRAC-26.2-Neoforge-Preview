package amrac.entities.ai;

/**
 * The AI outputs control inputs only: live through PlaneEntity.setControlInputs (the same entry as
 * the player's packet), virtual through AircraftControlInput. Change what an input means, or its
 * range, in both.
 */
public record AiCommand(
        int throttle,
        float pitch,
        float yaw,
        float roll,
        boolean gearDown,
        boolean flapsDown,
        boolean fireGun,
        boolean launchMissile,
        AiFlightPhase phase) {
    public static AiCommand idle(AiFlightPhase phase) {
        return new AiCommand(0, 0.0F, 0.0F, 0.0F, true, false, false, false,
            phase);
    }

    public boolean afterburner(int maxThrottle) {
        return throttle > maxThrottle;
    }
}
