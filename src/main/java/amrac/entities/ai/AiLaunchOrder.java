package amrac.entities.ai;

import java.util.Locale;

public record AiLaunchOrder(String aircraft, AiPilotRank rank, String team,
                            int fuelPercent, String loadout, String position,
                            float heading) {
    public static final int MAX_TEXT = 128;

    public static final AiLaunchOrder DEFAULT = new AiLaunchOrder("f16",
        AiPilotRank.ADVANCED, "", 100, "AIM120*2 AIM7*2", "~ ~1 ~", 0.0F);

    public AiLaunchOrder {
        aircraft = aircraft == null ? "" : aircraft.trim()
            .toLowerCase(Locale.ROOT);
        rank = rank == null ? AiPilotRank.TRAINEE : rank;
        team = team == null ? "" : team.trim();
        loadout = clip(loadout);
        position = clip(position);
        heading = Float.isFinite(heading) ? wrap(heading) : 0.0F;
    }

    private static String clip(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() > MAX_TEXT ? trimmed.substring(0, MAX_TEXT)
            : trimmed;
    }

    static float wrap(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public static String compass(float heading) {
        float h = wrap(heading);
        if (h >= -45.0F && h < 45.0F) {
            return "south";
        }
        if (h >= 45.0F && h < 135.0F) {
            return "west";
        }
        if (h >= -135.0F && h < -45.0F) {
            return "east";
        }
        return "north";
    }
}
