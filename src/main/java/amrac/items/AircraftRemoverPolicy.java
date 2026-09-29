package amrac.items;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AircraftRemoverPolicy {
    public enum Refusal {
        NONE,
        AIRBORNE,
        OCCUPIED,
        WRECKED
    }

    private AircraftRemoverPolicy() {
    }

    public static Refusal refusal(boolean alive, boolean dying,
                                  boolean onSurface, boolean playerAboard) {
        if (!alive || dying) {
            return Refusal.WRECKED;
        }
        if (!onSurface) {
            return Refusal.AIRBORNE;
        }
        return playerAboard ? Refusal.OCCUPIED : Refusal.NONE;
    }

    public static int fuelDrums(double litres, int litresPerDrum) {
        if (!(litres > 0.0D) || !Double.isFinite(litres) || litresPerDrum <= 0) {
            return 0;
        }
        return (int) Math.floor(litres / litresPerDrum + 1.0E-6D);
    }

    public static int bulletItems(int rounds, int roundsPerItem) {
        if (rounds <= 0 || roundsPerItem <= 0) {
            return 0;
        }
        return rounds / roundsPerItem;
    }

    public static Map<String, Integer> missiles(String[] loadout) {
        Map<String, Integer> counted = new LinkedHashMap<>();
        if (loadout == null) {
            return counted;
        }
        for (String id : loadout) {
            if (id != null && !id.isBlank()) {
                counted.merge(id, 1, Integer::sum);
            }
        }
        return counted;
    }

    public static int[] stacks(int count, int maxStack) {
        if (count <= 0) {
            return new int[0];
        }
        int size = Math.max(1, maxStack);
        int full = count / size;
        int rest = count % size;
        int[] out = new int[full + (rest > 0 ? 1 : 0)];
        for (int i = 0; i < full; i++) {
            out[i] = size;
        }
        if (rest > 0) {
            out[full] = rest;
        }
        return out;
    }
}
