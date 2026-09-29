package amrac.entities.ai;

import java.util.Set;
import amrac.physics.aircraft.AircraftFlightModelIds;

public enum AiPilotRank {
    TRAINEE("Trainee", 2000.0D, false, false, 0.0D, false,
        Set.of(AircraftFlightModelIds.F4J, AircraftFlightModelIds.MIG21)),

    ELEMENTARY("Elementary", 3000.0D, true, true, 6000.0D, false,
        Set.of(AircraftFlightModelIds.F4J, AircraftFlightModelIds.MIG21,
            AircraftFlightModelIds.MIG23)),

    ADVANCED("Advanced", 4500.0D, true, true, 14000.0D, true,
        Set.of(AircraftFlightModelIds.F16, AircraftFlightModelIds.MIG29,
            AircraftFlightModelIds.J8II)),

    VETERAN("Veteran", 5500.0D, true, true, 22000.0D, true,
        Set.of(AircraftFlightModelIds.F15, AircraftFlightModelIds.F15E,
            AircraftFlightModelIds.SU27, AircraftFlightModelIds.SU30,
            AircraftFlightModelIds.RAFALE, AircraftFlightModelIds.F18)),

    ACE("Ace", 7000.0D, true, true, 32000.0D, true,
        Set.of(AircraftFlightModelIds.J10C, AircraftFlightModelIds.TYPHOON));

    private final String label;
    private final double cruiseAltitude;
    private final boolean attacks;
    private final boolean afterburner;
    private final double engagementRange;
    private final boolean egressAfterLaunch;
    private final Set<String> airframes;

    AiPilotRank(String label, double cruiseAltitude, boolean attacks,
                boolean afterburner, double engagementRange,
                boolean egressAfterLaunch, Set<String> airframes) {
        this.label = label;
        this.cruiseAltitude = cruiseAltitude;
        this.attacks = attacks;
        this.afterburner = afterburner;
        this.engagementRange = engagementRange;
        this.egressAfterLaunch = egressAfterLaunch;
        this.airframes = airframes;
    }

    public String label() {
        return label;
    }

    public double cruiseAltitude() {
        return AiPilotSettings.current().rank(this).cruiseAltitude();
    }

    public boolean attacks() {
        return AiPilotSettings.current().rank(this).attacks();
    }

    public boolean usesAfterburner() {
        return AiPilotSettings.current().rank(this).afterburner();
    }

    public double engagementRange() {
        return AiPilotSettings.current().rank(this).engagementRange();
    }

    public boolean evades() {
        return AiPilotSettings.current().rank(this).evades();
    }

    public double notchError() {
        return Math.toRadians(AiPilotSettings.current().rank(this).notchErrorDegrees());
    }

    public Set<String> airframes() {
        return AiPilotSettings.current().rank(this).airframes();
    }

    AiPilotSettings.Rank shippedDefaults() {
        return new AiPilotSettings.Rank(cruiseAltitude, attacks, afterburner,
            engagementRange, egressAfterLaunch,
            this == ADVANCED || this == VETERAN || this == ACE,
            shippedNotchErrorDegrees(), airframes);
    }

    private double shippedNotchErrorDegrees() {
        return switch (this) {
            case ACE -> 1.0D;
            case VETERAN -> 3.0D;
            case ADVANCED -> 7.5D;
            default -> 10.0D;
        };
    }

    public boolean canFly(String flightModelId) {
        return flightModelId != null && airframes().contains(flightModelId);
    }

    public boolean requiresMissiles() {
        return attacks;
    }

    public AiPilotVariant variant() {
        return AiPilotVariant.of(this);
    }

    public static AiPilotRank byOrdinal(int ordinal) {
        AiPilotRank[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : TRAINEE;
    }

    public static AiPilotRank bySavedName(String name) {
        if (name == null || name.isBlank()) {
            return TRAINEE;
        }
        return switch (name) {
            case "TRAINING" -> TRAINEE;
            case "ROOKIE" -> ELEMENTARY;
            default -> {
                try {
                    yield valueOf(name);
                } catch (IllegalArgumentException ignored) {
                    yield TRAINEE;
                }
            }
        };
    }
}
