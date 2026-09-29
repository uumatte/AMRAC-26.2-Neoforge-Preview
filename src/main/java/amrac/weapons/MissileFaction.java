package amrac.weapons;

public enum MissileFaction {
    NATO("amrac.faction.nato"),

    SOVIET("amrac.faction.soviet"),

    CHINA("amrac.faction.china");

    private final String displayKey;

    MissileFaction(String displayKey) {
        this.displayKey = displayKey;
    }

    public String displayKey() {
        return displayKey;
    }

    public boolean accepts(MissileFaction round) {
        return this == round;
    }
}
