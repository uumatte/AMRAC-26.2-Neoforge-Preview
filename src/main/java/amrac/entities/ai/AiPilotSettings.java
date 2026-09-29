package amrac.entities.ai;

import amrac.entities.AircraftLifecyclePolicy;
import amrac.physics.aircraft.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DEFAULTS is built from the policy constants and the bundled pilot.json repeats the same values
 * (AiPilotSettingsRegression checks they agree; not in this repository). Change a default in all
 * three.
 */
public final class AiPilotSettings {
    public record Rank(double cruiseAltitude, boolean attacks,
                       boolean afterburner, double engagementRange,
                       boolean egressAfterLaunch, boolean evades,
                       double notchErrorDegrees, Set<String> airframes) {
    }

    public static final AiPilotSettings DEFAULTS = defaults();

    private static volatile AiPilotSettings current = DEFAULTS;

    private final Map<AiPilotRank, Rank> ranks;

    public final int launchIntervalTicks;
    public final double gunRange;
    public final double gunTrackingCosine;
    public final double missileWarningReach;
    public final double lastDitchSeconds;

    public final double courseGoalDistance;
    public final double patrolRadius;
    public final double patrolLookahead;
    public final double patrolCapture;

    public final double departureAltitude;
    public final double minimumAltitude;
    public final double fuelStarvedCrashAltitude;

    public final double borderWarningDistance;
    public final double borderLookaheadDistance;
    public final double borderInwardGoalDistance;
    public final double borderTurnBackSpeed;
    public final double borderTurnBackLoad;
    public final double borderTurnBackPull;
    public final double borderTurnBackPullMinSpeed;

    public final double maxBank;
    public final double hardTurnLoad;
    public final double hardTurnPull;
    public final double hardTurnError;
    public final double turnBackLoadFraction;
    public final double glideBank;
    public final double maxClimbAngle;
    public final double cruiseSpeed;
    public final double rotateSpeed;
    public final double overspeedGuardMargin;

    public final boolean areaEnabled;
    public final double areaMinX;
    public final double areaMaxX;
    public final double areaMinZ;
    public final double areaMaxZ;

    private AiPilotSettings(Draft d) {
        this.ranks = Collections.unmodifiableMap(new EnumMap<>(d.ranks));
        this.launchIntervalTicks = d.launchIntervalTicks;
        this.gunRange = d.gunRange;
        this.gunTrackingCosine = d.gunTrackingCosine;
        this.missileWarningReach = d.missileWarningReach;
        this.lastDitchSeconds = d.lastDitchSeconds;
        this.courseGoalDistance = d.courseGoalDistance;
        this.patrolRadius = d.patrolRadius;
        this.patrolLookahead = d.patrolLookahead;
        this.patrolCapture = d.patrolCapture;
        this.departureAltitude = d.departureAltitude;
        this.minimumAltitude = d.minimumAltitude;
        this.fuelStarvedCrashAltitude = d.fuelStarvedCrashAltitude;
        this.borderWarningDistance = d.borderWarningDistance;
        this.borderLookaheadDistance = d.borderLookaheadDistance;
        this.borderInwardGoalDistance = d.borderInwardGoalDistance;
        this.borderTurnBackSpeed = d.borderTurnBackSpeed;
        this.borderTurnBackLoad = d.borderTurnBackLoad;
        this.borderTurnBackPull = d.borderTurnBackPull;
        this.borderTurnBackPullMinSpeed = d.borderTurnBackPullMinSpeed;
        this.maxBank = d.maxBank;
        this.hardTurnLoad = d.hardTurnLoad;
        this.hardTurnPull = d.hardTurnPull;
        this.hardTurnError = d.hardTurnError;
        this.turnBackLoadFraction = d.turnBackLoadFraction;
        this.glideBank = d.glideBank;
        this.maxClimbAngle = d.maxClimbAngle;
        this.cruiseSpeed = d.cruiseSpeed;
        this.rotateSpeed = d.rotateSpeed;
        this.overspeedGuardMargin = d.overspeedGuardMargin;
        this.areaEnabled = d.areaEnabled;
        this.areaMinX = d.areaMinX;
        this.areaMaxX = d.areaMaxX;
        this.areaMinZ = d.areaMinZ;
        this.areaMaxZ = d.areaMaxZ;
    }

    public static AiPilotSettings current() {
        return current;
    }

    public static void apply(AiPilotSettings settings) {
        current = settings == null ? DEFAULTS : settings;
    }

    public Rank rank(AiPilotRank rank) {
        Rank line = ranks.get(rank);
        return line != null ? line : DEFAULTS.ranks.get(rank);
    }

    private static AiPilotSettings defaults() {
        Draft d = new Draft();
        for (AiPilotRank rank : AiPilotRank.values()) {
            d.ranks.put(rank, rank.shippedDefaults());
        }
        d.launchIntervalTicks = AiPilotCombatPolicy.LAUNCH_INTERVAL_TICKS;
        d.gunRange = AiPilotDirector.GUN_RANGE;
        d.gunTrackingCosine = AiPilotDirector.GUN_TRACKING_COSINE;
        d.missileWarningReach = AiPilotBrain.MISSILE_WARNING_REACH;
        d.lastDitchSeconds = AiNotchPolicy.LAST_DITCH_SECONDS;
        d.courseGoalDistance = AiPilotDirector.COURSE_GOAL_DISTANCE;
        d.patrolRadius = AiPilotPolicy.PATROL_RADIUS;
        d.patrolLookahead = AiPilotPolicy.PATROL_LOOKAHEAD;
        d.patrolCapture = AiPilotPolicy.PATROL_CAPTURE;
        d.departureAltitude = AiPilotPolicy.DEPARTURE_ALTITUDE;
        d.minimumAltitude = AiAltitudePolicy.MINIMUM_ALTITUDE;
        d.fuelStarvedCrashAltitude = AiAltitudePolicy.FUEL_STARVED_CRASH_ALTITUDE;
        d.borderWarningDistance = WorldBoundaryPolicy.WARNING_DISTANCE;
        d.borderLookaheadDistance = WorldBoundaryPolicy.LOOKAHEAD_DISTANCE;
        d.borderInwardGoalDistance = WorldBoundaryPolicy.INWARD_GOAL_DISTANCE;
        d.borderTurnBackSpeed = WorldBoundaryPolicy.TURN_BACK_SPEED;
        d.borderTurnBackLoad = AiPilotPolicy.TURN_BACK_LOAD;
        d.borderTurnBackPull = AiPilotPolicy.TURN_BACK_PULL;
        d.borderTurnBackPullMinSpeed = AiPilotPolicy.TURN_BACK_PULL_MIN_SPEED;
        d.maxBank = AiPilotPolicy.MAX_BANK;
        d.hardTurnLoad = AiPilotPolicy.HARD_TURN_LOAD;
        d.hardTurnPull = AiPilotPolicy.HARD_TURN_PULL;
        d.hardTurnError = AiPilotPolicy.HARD_TURN_ERROR;
        d.turnBackLoadFraction = AiPilotPolicy.TURN_BACK_LOAD_FRACTION;
        d.glideBank = AiPilotDirector.GLIDE_BANK;
        d.maxClimbAngle = AiPilotPolicy.DEPARTURE_CLIMB_ANGLE;
        d.cruiseSpeed = AiPilotPolicy.CRUISE_SPEED;
        d.rotateSpeed = AiPilotPolicy.ROTATE_SPEED;
        d.overspeedGuardMargin = AiThrottlePolicy.GUARD_MARGIN_BLOCKS_PER_SECOND;
        d.areaEnabled = false;
        d.areaMinX = -DEFAULT_AREA_HALF_WIDTH;
        d.areaMaxX = DEFAULT_AREA_HALF_WIDTH;
        d.areaMinZ = -DEFAULT_AREA_HALF_WIDTH;
        d.areaMaxZ = DEFAULT_AREA_HALF_WIDTH;
        return new AiPilotSettings(d);
    }

    public record Parsed(AiPilotSettings settings, List<String> problems) {
    }

    public static Parsed parse(String text) {
        Map<String, Object> root = Json.parseObject(text);
        List<String> problems = new ArrayList<>();
        Draft d = Draft.of(DEFAULTS);

        Map<String, Object> ranks = Json.object(root, "ranks");
        if (ranks != null) {
            for (Map.Entry<String, Object> entry : ranks.entrySet()) {
                AiPilotRank rank = rankNamed(entry.getKey());
                if (rank == null) {
                    problems.add("ranks." + entry.getKey()
                        + ": no such rank (TRAINEE, ELEMENTARY, ADVANCED,"
                        + " VETERAN, ACE)");
                    continue;
                }
                if (!(entry.getValue() instanceof Map<?, ?>)) {
                    problems.add("ranks." + entry.getKey() + ": not an object");
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> line = (Map<String, Object>) entry.getValue();
                d.ranks.put(rank, readRank(line, d.ranks.get(rank),
                    "ranks." + entry.getKey(), problems));
            }
        }

        Reader r = new Reader(problems);
        Map<String, Object> combat = Json.object(root, "combat");
        d.launchIntervalTicks = (int) Math.round(r.number(combat,
            "combat.launchIntervalTicks", d.launchIntervalTicks, 1.0D, 72000.0D));
        d.gunRange = r.number(combat, "combat.gunRange", d.gunRange, 0.0D, 4000.0D);
        d.gunTrackingCosine = r.number(combat, "combat.gunTrackingCosine",
            d.gunTrackingCosine, 0.0D, 1.0D);
        d.missileWarningReach = r.number(combat, "combat.missileWarningReach",
            d.missileWarningReach, 0.0D, 4096.0D);
        d.lastDitchSeconds = r.number(combat, "combat.lastDitchSeconds",
            d.lastDitchSeconds, 0.0D, 10.0D);

        Map<String, Object> navigation = Json.object(root, "navigation");
        d.courseGoalDistance = r.number(navigation,
            "navigation.courseGoalDistance", d.courseGoalDistance, 100.0D, 1.0E6D);
        d.patrolRadius = r.number(navigation, "navigation.patrolRadius",
            d.patrolRadius, 100.0D, 1.0E6D);
        d.patrolLookahead = r.number(navigation, "navigation.patrolLookahead",
            d.patrolLookahead, 50.0D, 1.0E6D);
        d.patrolCapture = r.number(navigation, "navigation.patrolCapture",
            d.patrolCapture, 100.0D, 1.0E6D);

        Map<String, Object> altitude = Json.object(root, "altitude");
        double lowestFloor = AircraftLifecyclePolicy.VIRTUAL_ALTITUDE_FLOOR
            + MINIMUM_ALTITUDE_MARGIN;
        d.minimumAltitude = r.number(altitude, "altitude.minimumAltitude",
            d.minimumAltitude, lowestFloor, 1.0E5D);
        d.departureAltitude = r.number(altitude, "altitude.departureAltitude",
            d.departureAltitude, d.minimumAltitude, 1.0E5D);
        d.fuelStarvedCrashAltitude = r.number(altitude,
            "altitude.fuelStarvedCrashAltitude", d.fuelStarvedCrashAltitude,
            -64.0D, AircraftLifecyclePolicy.VIRTUAL_ALTITUDE_FLOOR - 1.0D);

        Map<String, Object> border = Json.object(root, "border");
        d.borderWarningDistance = r.number(border, "border.warningDistance",
            d.borderWarningDistance, 100.0D, 1.0E6D);
        d.borderLookaheadDistance = r.number(border, "border.lookaheadDistance",
            d.borderLookaheadDistance, 100.0D, 1.0E6D);
        d.borderInwardGoalDistance = r.number(border, "border.inwardGoalDistance",
            d.borderInwardGoalDistance, 100.0D, 1.0E6D);
        d.borderTurnBackSpeed = r.number(border, "border.turnBackSpeed",
            d.borderTurnBackSpeed, 20.0D, 2000.0D);
        d.borderTurnBackLoad = r.number(border, "border.turnBackLoad",
            d.borderTurnBackLoad, 1.0D, 15.0D);
        d.borderTurnBackPull = r.number(border, "border.turnBackPull",
            d.borderTurnBackPull, 0.0D, 1.0D);
        d.borderTurnBackPullMinSpeed = r.number(border,
            "border.turnBackPullMinSpeed", d.borderTurnBackPullMinSpeed,
            0.0D, 2000.0D);

        Map<String, Object> handling = Json.object(root, "handling");
        d.maxBank = r.degrees(handling, "handling.maxBankDegrees",
            d.maxBank, 10.0D, 80.0D);
        d.hardTurnLoad = r.number(handling, "handling.hardTurnLoad",
            d.hardTurnLoad, 1.0D, 15.0D);
        d.hardTurnPull = r.number(handling, "handling.hardTurnPull",
            d.hardTurnPull, 0.0D, 1.0D);
        d.hardTurnError = r.degrees(handling, "handling.hardTurnErrorDegrees",
            d.hardTurnError, 10.0D, 180.0D);
        d.turnBackLoadFraction = r.number(handling,
            "handling.turnBackLoadFraction", d.turnBackLoadFraction, 0.1D, 1.0D);
        d.glideBank = r.degrees(handling, "handling.glideBankDegrees",
            d.glideBank, 5.0D, 80.0D);
        d.maxClimbAngle = r.degrees(handling, "handling.maxClimbDegrees",
            d.maxClimbAngle, 5.0D, 60.0D);
        d.cruiseSpeed = r.number(handling, "handling.cruiseSpeed",
            d.cruiseSpeed * 20.0D, 60.0D, 1200.0D) / 20.0D;
        d.rotateSpeed = r.number(handling, "handling.rotateSpeed",
            d.rotateSpeed * 20.0D, 20.0D, 200.0D) / 20.0D;
        d.overspeedGuardMargin = r.number(handling,
            "handling.overspeedGuardMargin", d.overspeedGuardMargin, 0.0D, 400.0D);

        Map<String, Object> area = Json.object(root, "area");
        if (area != null) {
            d.areaEnabled = r.bool(area, "area.enabled", d.areaEnabled);
            double[] first = r.corner(area, "area.corner1");
            double[] second = r.corner(area, "area.corner2");
            if (first != null && second != null) {
                double minX = Math.min(first[0], second[0]);
                double maxX = Math.max(first[0], second[0]);
                double minZ = Math.min(first[1], second[1]);
                double maxZ = Math.max(first[1], second[1]);
                if (maxX - minX < 1.0D || maxZ - minZ < 1.0D) {
                    problems.add("area: the two corners do not enclose anything;"
                        + " the area is switched off");
                    d.areaEnabled = false;
                } else {
                    d.areaMinX = minX;
                    d.areaMaxX = maxX;
                    d.areaMinZ = minZ;
                    d.areaMaxZ = maxZ;
                    double band = 2.0D * d.borderWarningDistance;
                    if (d.areaEnabled && (maxX - minX < band || maxZ - minZ < band)) {
                        problems.add("area: " + (long) (maxX - minX) + " x "
                            + (long) (maxZ - minZ) + " is narrower than twice"
                            + " border.warningDistance (" + (long) band
                            + "); pilots will spend the whole flight turning"
                            + " back");
                    }
                }
            } else if (first != null || second != null) {
                problems.add("area: needs both corner1 and corner2;"
                    + " the area is switched off");
                d.areaEnabled = false;
            }
        }

        return new Parsed(new AiPilotSettings(d), List.copyOf(problems));
    }

    static final double DEFAULT_AREA_HALF_WIDTH = 25000.0D;

    static final double MINIMUM_ALTITUDE_MARGIN = 100.0D;

    private static Rank readRank(Map<String, Object> line, Rank base, String path,
                                 List<String> problems) {
        Reader r = new Reader(problems);
        double cruise = r.number(line, path + ".cruiseAltitude",
            base.cruiseAltitude(), 0.0D, 1.0E5D);
        boolean attacks = r.bool(line, path + ".attacks", base.attacks());
        boolean afterburner = r.bool(line, path + ".afterburner",
            base.afterburner());
        double range = r.number(line, path + ".engagementRange",
            base.engagementRange(), 0.0D, 1.0E6D);
        boolean egress = r.bool(line, path + ".egressAfterLaunch",
            base.egressAfterLaunch());
        boolean evades = r.bool(line, path + ".evades", base.evades());
        double notchError = r.number(line, path + ".notchErrorDegrees",
            base.notchErrorDegrees(), 0.0D, 90.0D);
        Set<String> airframes = base.airframes();
        Object listed = line.get("airframes");
        if (listed != null) {
            if (listed instanceof List<?> list) {
                Set<String> read = new LinkedHashSet<>();
                for (Object item : list) {
                    if (item instanceof String name && !name.isBlank()) {
                        read.add(name.trim().toLowerCase(Locale.ROOT));
                    } else {
                        problems.add(path + ".airframes: " + item
                            + " is not an airframe name");
                    }
                }
                airframes = Collections.unmodifiableSet(read);
            } else {
                problems.add(path + ".airframes: not a list");
            }
        }
        return new Rank(cruise, attacks, afterburner, range, egress, evades,
            notchError, airframes);
    }

    private static AiPilotRank rankNamed(String name) {
        try {
            return AiPilotRank.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private record Reader(List<String> problems) {
        double number(Map<String, Object> section, String path, double fallback,
                      double min, double max) {
            Object value = value(section, path);
            if (value == null) {
                return fallback;
            }
            if (!(value instanceof Number number)
                    || !Double.isFinite(number.doubleValue())) {
                problems.add(path + ": " + value + " is not a number");
                return fallback;
            }
            double v = number.doubleValue();
            if (v < min || v > max) {
                problems.add(path + ": " + plain(v) + " is outside " + plain(min)
                    + " to " + plain(max) + "; kept " + plain(fallback));
                return fallback;
            }
            return v;
        }

        double degrees(Map<String, Object> section, String path,
                       double fallbackRadians, double min, double max) {
            double v = number(section, path, Double.NaN, min, max);
            if (Double.isNaN(v)) {
                return fallbackRadians;
            }
            return Math.toRadians(v);
        }

        private static String plain(double v) {
            if (Double.isNaN(v)) {
                return "the shipped value";
            }
            return new java.math.BigDecimal(v)
                .round(new java.math.MathContext(6))
                .stripTrailingZeros().toPlainString();
        }

        boolean bool(Map<String, Object> section, String path, boolean fallback) {
            Object value = value(section, path);
            if (value == null) {
                return fallback;
            }
            if (value instanceof Boolean b) {
                return b;
            }
            problems.add(path + ": " + value + " is not true or false");
            return fallback;
        }

        double[] corner(Map<String, Object> section, String path) {
            Object value = value(section, path);
            if (value == null) {
                return null;
            }
            if (value instanceof List<?> list && list.size() == 2
                    && list.get(0) instanceof Number x
                    && list.get(1) instanceof Number z
                    && Double.isFinite(x.doubleValue())
                    && Double.isFinite(z.doubleValue())
                    && Math.abs(x.doubleValue()) <= 3.0E7D
                    && Math.abs(z.doubleValue()) <= 3.0E7D) {
                return new double[] {x.doubleValue(), z.doubleValue()};
            }
            problems.add(path + ": " + value + " is not an [x, z] pair");
            return null;
        }

        private static Object value(Map<String, Object> section, String path) {
            if (section == null) {
                return null;
            }
            return section.get(path.substring(path.lastIndexOf('.') + 1));
        }
    }

    private static final class Draft {
        final Map<AiPilotRank, Rank> ranks = new EnumMap<>(AiPilotRank.class);
        int launchIntervalTicks;
        double gunRange;
        double gunTrackingCosine;
        double missileWarningReach;
        double lastDitchSeconds;
        double courseGoalDistance;
        double patrolRadius;
        double patrolLookahead;
        double patrolCapture;
        double departureAltitude;
        double minimumAltitude;
        double fuelStarvedCrashAltitude;
        double borderWarningDistance;
        double borderLookaheadDistance;
        double borderInwardGoalDistance;
        double borderTurnBackSpeed;
        double borderTurnBackLoad;
        double borderTurnBackPull;
        double borderTurnBackPullMinSpeed;
        double maxBank;
        double hardTurnLoad;
        double hardTurnPull;
        double hardTurnError;
        double turnBackLoadFraction;
        double glideBank;
        double maxClimbAngle;
        double cruiseSpeed;
        double rotateSpeed;
        double overspeedGuardMargin;
        boolean areaEnabled;
        double areaMinX;
        double areaMaxX;
        double areaMinZ;
        double areaMaxZ;

        static Draft of(AiPilotSettings s) {
            Draft d = new Draft();
            d.ranks.putAll(s.ranks);
            d.launchIntervalTicks = s.launchIntervalTicks;
            d.gunRange = s.gunRange;
            d.gunTrackingCosine = s.gunTrackingCosine;
            d.missileWarningReach = s.missileWarningReach;
            d.lastDitchSeconds = s.lastDitchSeconds;
            d.courseGoalDistance = s.courseGoalDistance;
            d.patrolRadius = s.patrolRadius;
            d.patrolLookahead = s.patrolLookahead;
            d.patrolCapture = s.patrolCapture;
            d.departureAltitude = s.departureAltitude;
            d.minimumAltitude = s.minimumAltitude;
            d.fuelStarvedCrashAltitude = s.fuelStarvedCrashAltitude;
            d.borderWarningDistance = s.borderWarningDistance;
            d.borderLookaheadDistance = s.borderLookaheadDistance;
            d.borderInwardGoalDistance = s.borderInwardGoalDistance;
            d.borderTurnBackSpeed = s.borderTurnBackSpeed;
            d.borderTurnBackLoad = s.borderTurnBackLoad;
            d.borderTurnBackPull = s.borderTurnBackPull;
            d.borderTurnBackPullMinSpeed = s.borderTurnBackPullMinSpeed;
            d.maxBank = s.maxBank;
            d.hardTurnLoad = s.hardTurnLoad;
            d.hardTurnPull = s.hardTurnPull;
            d.hardTurnError = s.hardTurnError;
            d.turnBackLoadFraction = s.turnBackLoadFraction;
            d.glideBank = s.glideBank;
            d.maxClimbAngle = s.maxClimbAngle;
            d.cruiseSpeed = s.cruiseSpeed;
            d.rotateSpeed = s.rotateSpeed;
            d.overspeedGuardMargin = s.overspeedGuardMargin;
            d.areaEnabled = s.areaEnabled;
            d.areaMinX = s.areaMinX;
            d.areaMaxX = s.areaMaxX;
            d.areaMinZ = s.areaMinZ;
            d.areaMaxZ = s.areaMaxZ;
            return d;
        }
    }
}
