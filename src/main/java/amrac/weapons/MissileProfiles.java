package amrac.weapons;

import amrac.physics.missile.MissilePhysicsProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MissileProfiles {
    public static final int NEW_MODEL_LIFETIME_TICKS = 480;

    public static final MissileProfile AIM7 = MissileProfile.scratch()
        .id("AIM7")
        .carriedMass(231.0D)
        .carriedDragArea(0.026D)
        .missilesCarried(MissilePolicy.MISSILES_CARRIED)
        .launchCooldownTicks(MissilePolicy.LAUNCH_COOLDOWN_TICKS)
        .rearmIntervalTicks(MissilePolicy.REARM_INTERVAL_TICKS)
        .boostTicks(MissilePolicy.BOOST_TICKS)
        .boostAcceleration(MissilePolicy.BOOST_ACCELERATION)
        .maxSpeed(MissilePolicy.MAX_SPEED)
        .coastDrag(MissilePolicy.COAST_DRAG)
        .gravity(MissilePolicy.GRAVITY)
        .maxLoadG(MissilePolicy.MAX_LOAD_G)
        .cornerSpeed(MissilePolicy.CORNER_SPEED)
        .minLoadG(MissilePolicy.MIN_LOAD_G)
        .inducedDragPerG2(MissilePolicy.INDUCED_DRAG_PER_G2)
        .maxInducedLoss(MissilePolicy.MAX_INDUCED_LOSS)
        .maxTurnRate(MissilePolicy.MAX_TURN_RATE)
        .navigationConstant(MissilePolicy.NAVIGATION_CONSTANT)
        .maxLeadTicks(MissilePolicy.MAX_LEAD_TICKS)
        .seekerGimbalLimit(MissilePolicy.SEEKER_GIMBAL_LIMIT)
        .maxLifetimeTicks(NEW_MODEL_LIFETIME_TICKS)
        .proximityFuseRadius(MissilePolicy.PROXIMITY_FUSE_RADIUS)
        .armingDistance(MissilePolicy.ARMING_DISTANCE)
        .explosionPower(MissilePolicy.EXPLOSION_POWER)
        .maxLaunchRange(MissilePolicy.MAX_LAUNCH_RANGE)
        .minLaunchRange(MissilePolicy.MIN_LAUNCH_RANGE)
        .build();

    public static final List<MissileProfile> MEDIUM_RANGE_EXPERIMENTS =
        buildMediumRange();

    private static List<MissileProfile> buildMediumRange() {
        List<MissileProfile> out = new ArrayList<>();
        for (int bps : new int[] {1280, 1360, 1400}) {
            for (int extraSeconds : new int[] {0, 1, 2}) {
                out.add(MissileProfile.from(AIM7)
                    .id("MRM_" + bps + "_B" + extraSeconds)
                    .maxSpeedBps(bps)
                    .extraBoostSeconds(extraSeconds)
                    .build());
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static final MissileProfile DOGFIGHT_50G = dogfight("DOGFIGHT_50G", 50.0D);
    public static final MissileProfile DOGFIGHT_60G = dogfight("DOGFIGHT_60G", 60.0D);

    private static MissileProfile dogfight(String id, double loadG) {
        return MissileProfile.from(AIM7)
            .id(id)
            .maxLoadG(loadG)
            .cornerSpeedBps(300.0D)
            .maxInducedLoss(0.10D)
            .maxSpeedBps(450.0D)
            .boostTicks(25)
            .boostAcceleration(0.85D)
            .maxLifetimeTicks(60)
            .armingDistance(8.0D)
            .seekerGimbalLimit(Math.toRadians(85.0D))
            .maxLaunchRange(1000.0D)
            .build();
    }

    public static final int ACTIVE_RADAR_LIFETIME_TICKS = 600;

    public static final amrac.physics.missile.MissilePerformance
        AIM120_MEASURED = measureAmraam();

    private static amrac.physics.missile.MissilePerformance
            measureAmraam() {
        var registry = amrac.physics.aircraft
            .FlightModelRegistry.instance();
        var model = registry.missileFlightModel(
            amrac.physics.missile.MissileFlightModelIds.AIM120);
        return model == null ? null
            : amrac.physics.missile.MissilePerformance
                .measure(model, registry.atmosphere(),
                    NEW_MODEL_LIFETIME_TICKS / 20.0D);
    }

    public static final MissileProfile AIM120 = MissileProfile.from(AIM7)
        .carriedMass(152.0D)
        .carriedDragArea(0.020D)
        .id("AIM120")
        .maxLoadG(AIM120_MEASURED == null ? 30.0D : AIM120_MEASURED.maxLoadG())
        .maxSpeedBps(AIM120_MEASURED == null ? 476.0D
            : AIM120_MEASURED.peakSpeedBlocksPerSecond())
        .boostTicks(AIM120_MEASURED == null ? 100
            : (int) Math.round(AIM120_MEASURED.timeToPeakSeconds() * 20.0D))
        .boostAcceleration(0.0D)
        .coastDrag(0.0D)
        .activeHoming(true)
        .maxLaunchRange(12000.0D)
        .maxLifetimeTicks(NEW_MODEL_LIFETIME_TICKS)
        .build();

    public static final MissileProfile AIM9 = MissileProfile.from(AIM7)
        .carriedMass(85.0D)
        .carriedDragArea(0.010D)
        .id("AIM9")
        .seekerType(SeekerType.IR)
        .maxLifetimeTicks(MissilePolicy.MAX_LIFETIME_TICKS)
        .maxLoadG(50.0D)
        .maxSpeedBps(544.0D)
        .maxLaunchRange(600.0D)
        .activeHoming(true)
        .build();

    public static final double BLOCKS_PER_SECOND_PER_MACH = 340.0D;

    public static final MissileProfile R77 = MissileProfile.from(AIM120)
        .carriedMass(175.0D)
        .carriedDragArea(0.030D)
        .id("R77")
        .faction(MissileFaction.SOVIET)
        .maxLifetimeTicks(420)
        .boostTicks(60)
        .boostAcceleration(350.0D / 400.0D)
        .maxSpeedBps(3.0D * BLOCKS_PER_SECOND_PER_MACH)
        .coastDrag(0.008D)
        .maxLoadG(40.0D)
        .build();

    public static final MissileProfile R27 = MissileProfile.from(AIM7)
        .carriedMass(253.0D)
        .carriedDragArea(0.033D)
        .id("R27")
        .faction(MissileFaction.SOVIET)
        .boostTicks(AIM7.boostTicks + 20)
        .build();

    public static final MissileProfile R73 = MissileProfile.from(AIM9)
        .carriedMass(105.0D)
        .carriedDragArea(0.018D)
        .id("R73")
        .faction(MissileFaction.SOVIET)
        .maxLoadG(60.0D)
        .activeHoming(true)
        .build();

    public static final MissileProfile MICA = MissileProfile.from(AIM120)
        .carriedMass(112.0D)
        .carriedDragArea(0.014D)
        .id("MICA")
        .faction(MissileFaction.NATO)
        .maxLifetimeTicks(860)
        .boostTicks(104)
        .boostAcceleration(168.0D / 400.0D)
        .maxSpeedBps(2.5D * BLOCKS_PER_SECOND_PER_MACH)
        .coastDrag(0.0048D)
        .maxLoadG(50.0D)
        .inducedDragPerG2(AIM120.inducedDragPerG2 * 0.60D)
        .build();

    public static final MissileProfile METEOR = MissileProfile.from(AIM120)
        .id("METEOR")
        .faction(MissileFaction.NATO)
        .carriedMass(190.0D)
        .carriedDragArea(0.026D)
        .maxLifetimeTicks(1000)
        .maxLoadG(40.0D)
        .build();

    public static final MissileProfile PL12 = MissileProfile.from(AIM120)
        .carriedMass(180.0D)
        .carriedDragArea(0.026D)
        .id("PL12")
        .faction(MissileFaction.CHINA)
        .maxLifetimeTicks(520)
        .boostTicks(120)
        .boostAcceleration(192.0D / 400.0D)
        .maxSpeedBps(2.5D * BLOCKS_PER_SECOND_PER_MACH)
        .coastDrag(0.006D)
        .maxLoadG(38.0D)
        .inducedDragPerG2(AIM120.inducedDragPerG2 * 0.85D)
        .build();

    public static final MissileProfile PL8 = MissileProfile.from(AIM9)
        .carriedMass(115.0D)
        .carriedDragArea(0.015D)
        .id("PL8")
        .faction(MissileFaction.CHINA)
        .maxLaunchRange(1000.0D)
        .boostTicks(AIM9.boostTicks + 20)
        .activeHoming(true)
        .build();

    public static final MissileProfile PL10 = MissileProfile.from(AIM9)
        .carriedMass(105.0D)
        .carriedDragArea(0.018D)
        .id("PL10")
        .faction(MissileFaction.CHINA)
        .maxLoadG(70.0D)
        .maxLaunchRange(1200.0D)
        .boostTicks(AIM9.boostTicks + 20)
        .activeHoming(true)
        .build();

    public static final int IMPROVED_LIFETIME_TICKS = 800;

    public static final int LONG_RANGE_LIFETIME_TICKS = 1300;

    public static final MissileProfile AIM120L = MissileProfile.from(AIM120)
        .carriedMass(161.0D)
        .carriedDragArea(0.020D)
        .id("AIM120L")
        .maxLaunchRange(20000.0D)
        .maxLifetimeTicks(IMPROVED_LIFETIME_TICKS)
        .build();

    public static final MissileProfile R771 = MissileProfile.from(AIM120)
        .carriedMass(190.0D)
        .carriedDragArea(0.026D)
        .id("R771")
        .faction(MissileFaction.SOVIET)
        .maxLaunchRange(20000.0D)
        .maxLifetimeTicks(680)
        .build();

    public static final MissileProfile PL12A = MissileProfile.from(AIM120)
        .carriedMass(195.0D)
        .carriedDragArea(0.026D)
        .id("PL12A")
        .faction(MissileFaction.CHINA)
        .maxLaunchRange(20000.0D)
        .maxLifetimeTicks(740)
        .build();

    public static final MissileProfile PL15 = MissileProfile.from(AIM120)
        .carriedMass(210.0D)
        .carriedDragArea(0.028D)
        .id("PL15")
        .faction(MissileFaction.CHINA)
        .maxLifetimeTicks(LONG_RANGE_LIFETIME_TICKS)
        .maxLaunchRange(35000.0D)
        .build();

    private static final Map<String, MissileProfile> BY_ID = index();

    private static Map<String, MissileProfile> index() {
        Map<String, MissileProfile> map = new LinkedHashMap<>();
        map.put(AIM7.id, AIM7);
        for (MissileProfile profile : MEDIUM_RANGE_EXPERIMENTS) {
            map.put(profile.id, profile);
        }
        map.put(AIM120.id, AIM120);
        map.put(METEOR.id, METEOR);
        map.put(AIM120L.id, AIM120L);
        map.put(R771.id, R771);
        map.put(PL12A.id, PL12A);
        map.put(PL15.id, PL15);
        map.put(AIM9.id, AIM9);
        map.put(R77.id, R77);
        map.put(R27.id, R27);
        map.put(R73.id, R73);
        map.put(PL12.id, PL12);
        map.put(PL8.id, PL8);
        map.put(PL10.id, PL10);
        map.put(MICA.id, MICA);
        map.put(DOGFIGHT_50G.id, DOGFIGHT_50G);
        map.put(DOGFIGHT_60G.id, DOGFIGHT_60G);
        return Collections.unmodifiableMap(map);
    }

    private MissileProfiles() {
    }

    public static MissileProfile defaultProfile() {
        return AIM7;
    }

    public static boolean isKnown(String id) {
        return id != null && BY_ID.containsKey(id);
    }

    private static final java.util.Map<String, MissilePhysicsProfile> SEEN =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, MissileProfile> DOCUMENTED =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final Map<String, String> ALIASES = Map.of(
        "CURRENT", "AIM7",
        "LEGACY", "AIM7");

    public static MissileProfile byId(String id) {
        if (id == null || id.isEmpty()) {
            return defaultProfile();
        }
        String canonical = ALIASES.getOrDefault(id, id);
        MissileProfile base = BY_ID.get(canonical);
        if (base == null) {
            return defaultProfile();
        }
        MissilePhysicsProfile document = amrac.physics
            .aircraft.FlightModelRegistry.instance().missileProfile(canonical);
        if (document == null) {
            return base;
        }
        if (SEEN.get(canonical) != document) {
            SEEN.put(canonical, document);
            DOCUMENTED.put(canonical, documented(base, document));
        }
        MissileProfile applied = DOCUMENTED.get(canonical);
        return applied == null ? base : applied;
    }

    private static MissileProfile documented(MissileProfile base,
                                             MissilePhysicsProfile document) {
        MissileProfile.Builder b = MissileProfile.from(base);
        if (given(document.launchMaxRange())) {
            b.maxLaunchRange(document.launchMaxRange());
        }
        if (given(document.launchMinRange())) {
            b.minLaunchRange(document.launchMinRange());
        }
        if (given(document.warmupSeconds())) {
            b.warmupTicks((int) Math.round(document.warmupSeconds() * 20.0D));
        }
        if (document.launchCooldownTicks() > 0) {
            b.launchCooldownTicks(document.launchCooldownTicks());
        }
        if (document.carriedPerAircraft() > 0) {
            b.missilesCarried(document.carriedPerAircraft());
        }
        if (document.rearmIntervalTicks() > 0) {
            b.rearmIntervalTicks(document.rearmIntervalTicks());
        }
        if (given(document.seekerGimbalDegrees())) {
            b.seekerGimbalLimit(Math.toRadians(document.seekerGimbalDegrees()));
        }
        if (given(document.navigationConstant())) {
            b.navigationConstant(document.navigationConstant());
        }
        if (document.maxLeadTicks() > 0) {
            b.maxLeadTicks(document.maxLeadTicks());
        }
        if (document.activeHoming() != null) {
            b.activeHoming(document.activeHoming());
        }
        if (document.seekerType() != null) {
            b.seekerType(document.seekerType());
            b.activeHoming(document.seekerType() != SeekerType.SARH);
        }
        if (given(document.seekerFovDegrees())) {
            b.seekerFov(Math.toRadians(document.seekerFovDegrees()));
        }
        if (given(document.seekerActivationRange())) {
            b.seekerActivationRange(document.seekerActivationRange());
        }
        if (given(document.velocityGate())) {
            b.velocityGate(document.velocityGate() / 20.0D);
        }
        if (document.velocityGateLookDownOnly() != null) {
            b.velocityGateLookDownOnly(document.velocityGateLookDownOnly());
        }
        if (given(document.burnThroughRange())) {
            b.burnThroughRange(document.burnThroughRange());
        }
        if (given(document.chaffSusceptibility())) {
            b.chaffSusceptibility(Math.min(1.0D, document.chaffSusceptibility()));
        }
        if (given(document.flareSusceptibility())) {
            b.flareSusceptibility(Math.min(1.0D, document.flareSusceptibility()));
        }
        if (given(document.heatSourceCoefficient())) {
            b.heatSourceCoefficient(Math.min(1.0D, document.heatSourceCoefficient()));
        }
        if (given(document.scanRateDegreesPerSecond())) {
            b.seekerScanRate(Math.toRadians(document.scanRateDegreesPerSecond()) / 20.0D);
        }
        if (given(document.datalinkUpdateSeconds())) {
            b.datalinkUpdateTicks((int) Math.round(document.datalinkUpdateSeconds() * 20.0D));
        }
        if (given(document.datalinkErrorDegrees())) {
            b.datalinkError(Math.toRadians(document.datalinkErrorDegrees()));
        }
        if (given(document.inertialDriftMetresPerSecond())) {
            b.inertialDrift(document.inertialDriftMetresPerSecond() / 20.0D);
        }
        if (document.extrapolateOnLoss() != null) {
            b.extrapolateOnLoss(document.extrapolateOnLoss());
        }
        if (document.extrapolateAfterDecoy() != null) {
            b.extrapolateAfterDecoy(document.extrapolateAfterDecoy());
        } else if (document.extrapolateOnLoss() != null) {
            b.extrapolateAfterDecoy(document.extrapolateOnLoss());
        }
        if (document.twoWayDatalink() != null) {
            b.twoWayDatalink(document.twoWayDatalink());
        }
        if (given(document.reacquireSeconds())) {
            b.reacquireTicks((int) Math.round(document.reacquireSeconds() * 20.0D));
        }
        if (given(document.confirmSeconds())) {
            b.lockConfirmTicks((int) Math.round(document.confirmSeconds() * 20.0D));
        }
        if (given(document.gimbalMemorySeconds())) {
            b.gimbalMemoryTicks((int) Math.round(document.gimbalMemorySeconds() * 20.0D));
        }
        if (given(document.gimbalConfirmSeconds())) {
            b.gimbalConfirmTicks((int) Math.round(document.gimbalConfirmSeconds() * 20.0D));
        }
        if (given(document.loftAngleDegrees())) {
            b.loftAngle(Math.toRadians(document.loftAngleDegrees()));
            double reach = given(document.launchMaxRange())
                ? document.launchMaxRange() : base.maxLaunchRange;
            b.loftFullRange(given(document.loftFullRange())
                ? document.loftFullRange() : reach * 0.45D);
            b.loftMinRange(given(document.loftMinRange())
                ? document.loftMinRange() : reach * 0.15D);
            b.loftCeiling(given(document.loftCeiling())
                ? document.loftCeiling() : Double.POSITIVE_INFINITY);
        }
        if (given(document.maxGRate())) {
            b.maxLoadRate(document.maxGRate());
        }
        if (given(document.inducedDragFactor())) {
            b.inducedDragFactor(document.inducedDragFactor());
        }
        if (given(document.inducedSpeedLossPerG2())) {
            b.inducedDragPerG2(document.inducedSpeedLossPerG2());
        }
        if (given(document.inducedSpeedLossCap())) {
            b.maxInducedLoss(Math.min(1.0D, document.inducedSpeedLossCap()));
        }
        if (given(document.explosionPower())) {
            b.explosionPower((float) document.explosionPower());
        }
        if (given(document.proximityFuseRadius())) {
            b.proximityFuseRadius(document.proximityFuseRadius());
        }
        if (given(document.armingDistance())) {
            b.armingDistance(document.armingDistance());
        }
        if (given(document.carriedMass())) {
            b.carriedMass(document.carriedMass());
        }
        if (given(document.carriedDragArea())) {
            b.carriedDragArea(document.carriedDragArea());
        }
        if (document.lifetimeTicks() > 0) {
            b.maxLifetimeTicks(document.lifetimeTicks());
        }
        return b.build();
    }

    private static boolean given(double value) {
        return Double.isFinite(value) && value >= 0.0D;
    }

    public static final List<MissileProfile> CARRIABLE =
        List.of(AIM7, AIM120, AIM9, R27, R77, R73, PL12, PL8, PL10,
            AIM120L, R771, PL12A, PL15, MICA, METEOR);

    public static List<MissileProfile> all() {
        List<MissileProfile> out = new ArrayList<>();
        out.add(AIM7);
        out.addAll(MEDIUM_RANGE_EXPERIMENTS);
        out.add(AIM120);
        out.add(AIM120L);
        out.add(R771);
        out.add(PL12A);
        out.add(PL15);
        out.add(AIM9);
        out.add(R77);
        out.add(R27);
        out.add(R73);
        out.add(PL12);
        out.add(PL8);
        out.add(PL10);
        out.add(METEOR);
        out.add(DOGFIGHT_50G);
        out.add(DOGFIGHT_60G);
        return Collections.unmodifiableList(out);
    }
}
