package amrac.weapons;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class LoadoutFit {
    public enum Reason {
        NO_MISSILES,
        TOO_MANY,
        NOT_CARRIED,
        COUNTERMEASURES_FULL
    }

    public record Refusal(Reason reason, String store, int capacity, int given) {
    }

    private LoadoutFit() {
    }

    public static Refusal check(SummonLoadout.Parsed loadout,
                                boolean hasMissiles, int pylons,
                                Predicate<String> carries,
                                int countermeasureCapacity) {
        List<String> stations = loadout.stations();
        boolean anyRound = stations.stream().anyMatch(Objects::nonNull);
        if (anyRound && !hasMissiles) {
            return new Refusal(Reason.NO_MISSILES, null, 0, 0);
        }
        if (stations.size() > pylons) {
            return new Refusal(Reason.TOO_MANY, null, pylons, stations.size());
        }
        for (String id : stations) {
            if (id != null && !carries.test(id)) {
                return new Refusal(Reason.NOT_CARRIED, id, 0, 0);
            }
        }
        int dispensed = loadout.chaff() + loadout.flare();
        if (dispensed > countermeasureCapacity) {
            return new Refusal(Reason.COUNTERMEASURES_FULL, null,
                countermeasureCapacity, dispensed);
        }
        return null;
    }
}
