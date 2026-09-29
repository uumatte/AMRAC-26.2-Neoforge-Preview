package amrac.weapons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SummonLoadout {
    public static final int MAX_COUNT = 999;

    public static final String CHAFF = "CHAFF";
    public static final String FLARE = "FLARE";
    public static final String EMPTY_STATION = "-";

    public record Parsed(List<String> stations, int chaff, int flare) {
    }

    public static final class Invalid extends Exception {
        private final String reason;
        private final String token;

        Invalid(String reason, String token) {
            super(reason + ": " + token);
            this.reason = reason;
            this.token = token;
        }

        public String reason() {
            return reason;
        }

        public String token() {
            return token;
        }
    }

    private SummonLoadout() {
    }

    public static Parsed parse(String text) throws Invalid {
        List<String> stations = new ArrayList<>();
        int chaff = 0;
        int flare = 0;
        if (text == null) {
            return new Parsed(stations, 0, 0);
        }
        for (String token : text.trim().split("[\\s,]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int star = token.indexOf('*');
            String name = star < 0 ? token : token.substring(0, star);
            int count = 1;
            if (star >= 0) {
                try {
                    count = Integer.parseInt(token.substring(star + 1));
                } catch (NumberFormatException e) {
                    throw new Invalid("bad_count", token);
                }
                if (count < 1 || count > MAX_COUNT) {
                    throw new Invalid("bad_count", token);
                }
            }
            String id = canonical(name);
            if (CHAFF.equals(id)) {
                chaff += count;
            } else if (FLARE.equals(id)) {
                flare += count;
            } else if (EMPTY_STATION.equals(name)) {
                for (int i = 0; i < count; i++) {
                    stations.add(null);
                }
            } else if (id != null && MissileProfiles.isKnown(id)) {
                for (int i = 0; i < count; i++) {
                    stations.add(id);
                }
            } else {
                throw new Invalid("unknown_store", name);
            }
        }
        return new Parsed(stations, chaff, flare);
    }

    public static String write(List<String> stations) {
        int end = stations == null ? 0 : stations.size();
        while (end > 0 && token(stations.get(end - 1)).equals(EMPTY_STATION)) {
            end--;
        }
        StringBuilder line = new StringBuilder();
        int i = 0;
        while (i < end) {
            String store = token(stations.get(i));
            int run = 1;
            while (i + run < end && token(stations.get(i + run)).equals(store)) {
                run++;
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(store);
            if (run > 1) {
                line.append('*').append(run);
            }
            i += run;
        }
        return line.toString();
    }

    private static String token(String station) {
        return station == null || station.isBlank() ? EMPTY_STATION : station;
    }

    public static String canonical(String name) {
        if (name == null) {
            return null;
        }
        String id = name.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        return id.isEmpty() ? null : id;
    }

    public static List<String> storeNames() {
        List<String> names = new ArrayList<>();
        for (MissileProfile profile : MissileProfiles.CARRIABLE) {
            names.add(profile.id);
        }
        names.add(CHAFF);
        names.add(FLARE);
        names.add(EMPTY_STATION);
        return names;
    }
}
