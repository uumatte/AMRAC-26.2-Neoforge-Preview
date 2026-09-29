package amrac.gps;

import java.util.UUID;

public record GpsPin(UUID id, UUID owner, String ownerName, String name,
                     double x, double z, String sharedTeam) {
    public boolean shared() {
        return sharedTeam != null && !sharedTeam.isBlank();
    }

    public GpsPin sharedWith(String team) {
        return new GpsPin(id, owner, ownerName, name, x, z, team);
    }
}
