package amrac.entities.ai;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record AiLaunchCatalog(List<Airframe> airframes,
                              Map<AiPilotRank, Set<String>> clearances) {
    public record Airframe(String name, String flightModelId,
                           boolean hasMissiles, int pylons,
                           Set<String> missiles, int chaff, int flare) {
        public boolean carries(String profileId) {
            return missiles.contains(profileId);
        }
    }

    public AiLaunchCatalog {
        airframes = List.copyOf(airframes);
        Map<AiPilotRank, Set<String>> copy = new EnumMap<>(AiPilotRank.class);
        for (Map.Entry<AiPilotRank, Set<String>> entry : clearances.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        clearances = copy;
    }

    public Airframe airframe(String name) {
        for (Airframe airframe : airframes) {
            if (airframe.name().equals(name)) {
                return airframe;
            }
        }
        return null;
    }

    public boolean cleared(AiPilotRank rank, Airframe airframe) {
        Set<String> ids = clearances.get(rank);
        return airframe != null && ids != null
            && ids.contains(airframe.flightModelId());
    }
}
