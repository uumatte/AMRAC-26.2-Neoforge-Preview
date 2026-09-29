package amrac.entities.ai;

public final class AiTeamPolicy {
    private AiTeamPolicy() {
    }

    public static boolean hostile(String pilotTeam, String playerTeam) {
        return pilotTeam != null && !pilotTeam.isBlank()
            && playerTeam != null && !playerTeam.isBlank()
            && !pilotTeam.equals(playerTeam);
    }
}
