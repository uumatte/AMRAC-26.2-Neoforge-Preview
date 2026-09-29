package amrac.gps;

public record GpsContact(Kind kind, String name, double x, double y, double z,
                         double speed, boolean live, boolean seekerActive) {
    public GpsContact(Kind kind, String name, double x, double y, double z,
                      double speed, boolean live) {
        this(kind, name, x, y, z, speed, live, false);
    }

    public GpsContact(Kind kind, String name, double x, double y, double z,
                      double speed) {
        this(kind, name, x, y, z, speed, true);
    }

    public enum Kind {
        PLAYER,
        AI_AIRCRAFT,
        EMPTY_AIRCRAFT,
        OWN_MISSILE,
        MISSILE;

        public static Kind byOrdinal(int ordinal) {
            Kind[] values = values();
            return ordinal >= 0 && ordinal < values.length
                ? values[ordinal] : EMPTY_AIRCRAFT;
        }
    }

    public double speedBlocksPerSecond() {
        return speed * 20.0D;
    }
}
