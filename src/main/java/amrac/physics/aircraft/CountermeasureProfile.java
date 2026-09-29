package amrac.physics.aircraft;

import java.util.Map;

public record CountermeasureProfile(int capacity, int chaff, int flare) {
    public static final CountermeasureProfile NONE = new CountermeasureProfile(0, 0, 0);

    public static final int MAX_CAPACITY = 1000;

    public static CountermeasureProfile fromJson(Map<String, Object> root) {
        Map<String, Object> block = Json.object(root, "countermeasures");
        if (block == null) {
            return NONE;
        }
        double stated = Json.number(block, "capacity", 0.0D);
        if (!Double.isFinite(stated) || stated <= 0.0D) {
            return NONE;
        }
        int capacity = (int) Math.min(MAX_CAPACITY, Math.round(stated));
        return of(capacity, count(block, "chaff"), count(block, "flare"));
    }

    public static CountermeasureProfile of(int capacity, int chaff, int flare) {
        int slots = Math.max(0, Math.min(MAX_CAPACITY, capacity));
        if (chaff < 0 && flare < 0) {
            chaff = slots / 2;
            flare = slots - chaff;
        } else if (chaff < 0) {
            flare = Math.min(flare, slots);
            chaff = slots - flare;
        } else if (flare < 0) {
            chaff = Math.min(chaff, slots);
            flare = slots - chaff;
        }
        chaff = Math.min(chaff, slots);
        flare = Math.min(flare, slots - chaff);
        return new CountermeasureProfile(slots, chaff, flare);
    }

    private static int count(Map<String, Object> block, String key) {
        double value = Json.number(block, key, -1.0D);
        if (!Double.isFinite(value) || value < 0.0D) {
            return -1;
        }
        return (int) Math.min(MAX_CAPACITY, Math.round(value));
    }
}
