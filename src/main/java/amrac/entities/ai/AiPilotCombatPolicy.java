package amrac.entities.ai;

import java.util.function.ToDoubleFunction;

public final class AiPilotCombatPolicy {
    public static final int LAUNCH_INTERVAL_TICKS = 20 * 60;

    /**
     * The only firing check: it decides both whether and why not, and
     * AiPilotDirector.missileSolution calls it; round choice (AiPilotBrain.chooseRound) is shared
     * by both layers too. Don't copy these conditions.
     */
    public static String whyNoLaunch(AiSituation situation,
                                     AiFlightPhase phase) {
        if (!situation.rank().attacks()) {
            return "this rank does not attack at all";
        }
        if (!situation.hasTarget()) {
            return "no target: nothing hostile has been found";
        }
        if (!situation.targetIsAircraft()) {
            return "the target is a player on foot, not an aircraft";
        }
        if (phase != AiFlightPhase.ENGAGE && phase != AiFlightPhase.EGRESS) {
            return "phase is " + phase + " rather than ENGAGE or EGRESS"
                + (phase == AiFlightPhase.CLIMB
                    ? " (still climbing to its assigned altitude)" : "");
        }
        if (situation.targetRange() > situation.rank().engagementRange()) {
            return String.format("target at %.0f is outside this rank's %.0f",
                situation.targetRange(), situation.rank().engagementRange());
        }
        return null;
    }

    private AiPilotCombatPolicy() {
    }

    public static String selectMissile(String[] loadout, double targetRange,
                                       ToDoubleFunction<String> maxRange) {
        if (loadout == null) {
            return null;
        }
        String inRange = null;
        double inRangeBest = Double.NEGATIVE_INFINITY;
        String longest = null;
        double longestRange = Double.NEGATIVE_INFINITY;
        for (String id : loadout) {
            if (id == null || id.isBlank()) {
                continue;
            }
            double range = maxRange.applyAsDouble(id);
            if (!Double.isFinite(range)) {
                continue;
            }
            if (range > longestRange) {
                longestRange = range;
                longest = id;
            }
            if (targetRange <= range && range > inRangeBest) {
                inRangeBest = range;
                inRange = id;
            }
        }
        return inRange != null ? inRange : longest;
    }

    public static boolean armed(String[] loadout) {
        if (loadout == null) {
            return false;
        }
        for (String id : loadout) {
            if (id != null && !id.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
