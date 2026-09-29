package amrac.entities.ai;

import java.util.List;

public final class AiLaunchPolicy {
    public static final double BOARDING_RANGE = 32.0D;

    public record Candidate(java.util.UUID id, String flightModelId,
                            double distance, boolean crewed, boolean armed) {
    }

    private AiLaunchPolicy() {
    }

    public record Choice(java.util.UUID aircraftId, AiPilotIdleReason refusal) {
        public boolean launched() {
            return aircraftId != null;
        }
    }

    public static Choice choose(AiPilotRank rank, List<Candidate> candidates,
                                boolean alreadyFlying) {
        if (alreadyFlying) {
            return new Choice(null, AiPilotIdleReason.NONE);
        }
        if (rank == null || candidates == null || candidates.isEmpty()) {
            return new Choice(null, AiPilotIdleReason.NO_AIRCRAFT);
        }
        List<Candidate> inRange = new java.util.ArrayList<>();
        for (Candidate candidate : candidates) {
            if (Double.isFinite(candidate.distance())
                    && candidate.distance() <= BOARDING_RANGE
                    && !candidate.crewed()) {
                inRange.add(candidate);
            }
        }
        if (inRange.isEmpty()) {
            return new Choice(null, AiPilotIdleReason.NO_AIRCRAFT);
        }
        inRange.sort(java.util.Comparator.comparingDouble(Candidate::distance));

        boolean sawCleared = false;
        for (Candidate candidate : inRange) {
            if (!rank.canFly(candidate.flightModelId())) {
                continue;
            }
            sawCleared = true;
            if (candidate.armed() || !rank.requiresMissiles()) {
                return new Choice(candidate.id(), AiPilotIdleReason.NONE);
            }
        }
        return new Choice(null, sawCleared ? AiPilotIdleReason.NO_MISSILES
            : AiPilotIdleReason.WRONG_AIRFRAME);
    }
}
