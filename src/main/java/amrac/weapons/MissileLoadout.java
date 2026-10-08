package amrac.weapons;

import java.util.ArrayList;
import java.util.List;

public final class MissileLoadout {
    public static final int STATIONS = 4;

    public static final String EMPTY = "";

    private MissileLoadout() {
    }

    public static String[] decode(String encoded) {
        return decode(encoded, STATIONS);
    }

    public static String[] decode(String encoded, int stations) {
        int count = Math.max(0, stations);
        String[] slots = new String[count];
        if (encoded == null || encoded.isEmpty()) {
            return slots;
        }
        String[] parts = encoded.split(",", -1);
        for (int i = 0; i < count && i < parts.length; i++) {
            String id = parts[i].trim();
            if (!id.isEmpty() && MissileProfiles.isKnown(id)) {
                slots[i] = id;
            }
        }
        return slots;
    }

    public static double storesMass(String[] loadout) {
        return sum(loadout, true);
    }

    public static double storesDragArea(String[] loadout) {
        return sum(loadout, false);
    }

    public record StoresInertia(double rollInertia, double pitchInertia,
                                double rollMoment) {
        public static final StoresInertia NONE =
            new StoresInertia(0.0D, 0.0D, 0.0D);

        public double yawInertia() {
            return rollInertia + pitchInertia;
        }
    }

    public static StoresInertia storesInertia(String[] loadout,
                                              double[] stationSpanMetres,
                                              double storesArmMetres) {
        if (loadout == null || loadout.length == 0) {
            return StoresInertia.NONE;
        }
        double arm = Double.isFinite(storesArmMetres) ? storesArmMetres : 0.0D;
        double roll = 0.0D;
        double pitch = 0.0D;
        double moment = 0.0D;
        for (int station = 0; station < loadout.length; station++) {
            String id = loadout[station];
            if (id == null || id.isEmpty()) {
                continue;
            }
            MissileProfile profile = MissileProfiles.byId(id);
            if (profile == null) {
                continue;
            }
            double mass = Math.max(0.0D, profile.carriedMass);
            if (mass <= 0.0D) {
                continue;
            }
            double span = stationSpanMetres == null
                || station >= stationSpanMetres.length
                || !Double.isFinite(stationSpanMetres[station])
                ? 0.0D : stationSpanMetres[station];
            roll += mass * span * span;
            pitch += mass * arm * arm;
            moment += mass * span;
        }
        return new StoresInertia(roll, pitch, moment);
    }

    private static double sum(String[] loadout, boolean mass) {
        if (loadout == null) {
            return 0.0D;
        }
        double total = 0.0D;
        for (String id : loadout) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            MissileProfile profile = MissileProfiles.byId(id);
            if (profile != null) {
                total += Math.max(0.0D,
                    mass ? profile.carriedMass : profile.carriedDragArea);
            }
        }
        return total;
    }

    public static String encode(String[] slots) {
        StringBuilder out = new StringBuilder();
        int stations = slots == null ? 0 : slots.length;
        for (int i = 0; i < stations; i++) {
            if (i > 0) {
                out.append(',');
            }
            if (slots != null && i < slots.length && slots[i] != null) {
                out.append(slots[i]);
            }
        }
        String encoded = out.toString();
        return encoded.chars().allMatch(c -> c == ',') ? EMPTY : encoded;
    }

    public static int count(String[] slots) {
        int n = 0;
        for (String slot : slots) {
            if (slot != null) {
                n++;
            }
        }
        return n;
    }

    public static int firstOf(String[] slots, String id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < slots.length; i++) {
            if (id.equals(slots[i])) {
                return i;
            }
        }
        return -1;
    }

    public static int firstLoaded(String[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                return i;
            }
        }
        return -1;
    }

    public static int stationToLoad(String[] slots, double[] distances) {
        return stationToLoad(slots, distances, null);
    }

    // Nearest empty station on the lighter wing (sign of lateral); nearest
    // alone hung 4 clicked rounds 3/1 on six pylons and 4/0 on eight.
    public static int stationToLoad(String[] slots, double[] distances,
                                    double[] lateral) {
        int heavy = 0;
        if (lateral != null) {
            int balance = 0;
            for (int i = 0; i < slots.length && i < lateral.length; i++) {
                if (slots[i] != null) {
                    balance += side(lateral[i]);
                }
            }
            heavy = Integer.signum(balance);
        }
        int best = nearestEmpty(slots, distances, lateral, heavy);
        return best < 0 && heavy != 0
            ? nearestEmpty(slots, distances, null, 0) : best;
    }

    private static int nearestEmpty(String[] slots, double[] distances,
                                    double[] lateral, int heavy) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < slots.length && i < distances.length; i++) {
            if (slots[i] != null || !Double.isFinite(distances[i])) {
                continue;
            }
            if (heavy != 0 && lateral != null && i < lateral.length
                && side(lateral[i]) == heavy) {
                continue;
            }
            if (distances[i] < bestDistance) {
                bestDistance = distances[i];
                best = i;
            }
        }
        return best;
    }

    private static int side(double lateral) {
        return Double.isFinite(lateral) ? (int) Math.signum(lateral) : 0;
    }

    public static List<String> typesAboard(String[] slots) {
        List<String> types = new ArrayList<>();
        for (String slot : slots) {
            if (slot != null && !types.contains(slot)) {
                types.add(slot);
            }
        }
        return types;
    }

    public static String nextType(String[] slots, String current) {
        List<String> types = typesAboard(slots);
        if (types.isEmpty()) {
            return null;
        }
        int at = types.indexOf(current);
        return types.get((at + 1) % types.size());
    }
}
