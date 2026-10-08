package amrac.display;

public enum ChartKind {
    TURN_RATE_VS_SPEED("turn_rate_vs_speed", "Turn Rate vs Speed",
        ConsoleMode.CONFIG_PLANE, "Speed (m/s)", "Turn rate (deg/s)", false),
    SEP_VS_SPEED("sep_vs_speed", "SEP vs Speed",
        ConsoleMode.CONFIG_PLANE, "Speed (m/s)", "SEP (m/s)", true),
    MAX_SPEED_VS_ALTITUDE("max_speed_vs_altitude", "Max Speed vs Altitude",
        ConsoleMode.CONFIG_PLANE, "Altitude (m)", "Max speed (m/s)", false),

    SPEED_VS_TIME("speed_vs_time", "Speed vs Time",
        ConsoleMode.CONFIG_MISSILE, "Time (s)", "Speed (m/s)", true),
    AVAILABLE_G_VS_SPEED("available_g_vs_speed", "Available G vs Speed",
        ConsoleMode.CONFIG_MISSILE, "Speed (m/s)", "Available load (g)", true),
    NEZ_VS_SPEED("nez_vs_speed", "NEZ vs Speed",
        ConsoleMode.CONFIG_MISSILE, "Launch speed (m/s)", "NEZ (m)", true);

    private final String name;
    private final String label;
    private final ConsoleMode mode;
    private final String xLabel;
    private final String yLabel;
    private final boolean altitude;

    ChartKind(String name, String label, ConsoleMode mode, String xLabel,
              String yLabel, boolean altitude) {
        this.name = name;
        this.label = label;
        this.mode = mode;
        this.xLabel = xLabel;
        this.yLabel = yLabel;
        this.altitude = altitude;
    }

    public String getSerializedName() {
        return name;
    }

    public String label() {
        return label;
    }

    public ConsoleMode mode() {
        return mode;
    }

    public String xLabel() {
        return xLabel;
    }

    public String yLabel() {
        return yLabel;
    }

    // label()/xLabel()/yLabel() stay English (tests read them); panels get the keys below.
    public String translationKey() {
        return "amrac.chart." + name;
    }

    public String titleText() {
        return SyncedText.of(translationKey());
    }

    public String xLabelText() {
        return SyncedText.of(translationKey() + ".x");
    }

    public String yLabelText() {
        return SyncedText.of(translationKey() + ".y");
    }

    public boolean takesAltitude() {
        return altitude;
    }

    public boolean takesTarget() {
        return this == NEZ_VS_SPEED;
    }

    public boolean takesLaunchSpeed() {
        return this == SPEED_VS_TIME;
    }

    public static ChartKind[] forMode(ConsoleMode mode) {
        return java.util.Arrays.stream(values())
            .filter(kind -> kind.mode == mode)
            .toArray(ChartKind[]::new);
    }

    public static ChartKind byName(String name, ConsoleMode mode) {
        for (ChartKind kind : values()) {
            if (kind.name.equals(name)) {
                return kind;
            }
        }
        ChartKind[] offered = forMode(mode);
        return offered.length > 0 ? offered[0] : TURN_RATE_VS_SPEED;
    }
}
