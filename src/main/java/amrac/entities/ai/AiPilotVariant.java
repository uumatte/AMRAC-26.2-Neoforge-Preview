package amrac.entities.ai;

import java.util.List;

public enum AiPilotVariant {
    SKELETON("Skeleton Pilot",
        List.of(AiPilotRank.TRAINEE, AiPilotRank.ELEMENTARY)),

    WITHER_SKELETON("Wither Skeleton Pilot",
        List.of(AiPilotRank.ADVANCED, AiPilotRank.VETERAN, AiPilotRank.ACE));

    private final String label;
    private final List<AiPilotRank> ranks;

    AiPilotVariant(String label, List<AiPilotRank> ranks) {
        this.label = label;
        this.ranks = ranks;
    }

    public String label() {
        return label;
    }

    public List<AiPilotRank> ranks() {
        return ranks;
    }

    public AiPilotRank defaultRank() {
        return ranks.getFirst();
    }

    public boolean allows(AiPilotRank rank) {
        return ranks.contains(rank);
    }

    public static AiPilotVariant of(AiPilotRank rank) {
        for (AiPilotVariant variant : values()) {
            if (variant.ranks.contains(rank)) {
                return variant;
            }
        }
        return SKELETON;
    }

    public static AiPilotVariant byOrdinal(int ordinal) {
        AiPilotVariant[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SKELETON;
    }
}
