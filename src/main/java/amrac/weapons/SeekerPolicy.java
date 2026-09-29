package amrac.weapons;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * All missile steering follows the Solution from here, the only place that reads the true target
 * position. Chaff/flare rolls are seeded by missile and decoy, so live, virtual and test results
 * agree.
 */
public final class SeekerPolicy {
    public static final double CHAFF_RANGE_GATE = 150.0D;

    public static final double CHAFF_MIN_SEPARATION = 40.0D;

    public static final double OVERSHOOT_RANGE = 3000.0D;

    public static final double OVERSHOOT_CLOSURE = 200.0D;

    public static final double FLARE_VIEW_HALF_ANGLE = Math.toRadians(5.0D);

    public enum Loss {
        NONE,
        NO_TARGET,
        OUT_OF_VIEW,
        NO_ILLUMINATION,
        VELOCITY_GATE,
        REACQUIRING,
        CONFIRMING,
        OVERSHOT
    }

    public enum Phase {
        MIDCOURSE,
        TERMINAL
    }

    public enum Link {
        TRACKING,
        LOST,
        PERFECT
    }

    public record Decoy(int id, Vec3 position, Vec3 velocity, boolean flare) {
        public Decoy(int id, Vec3 position, Vec3 velocity) {
            this(id, position, velocity, false);
        }
    }

    public record Solution(@Nullable Vec3 aimPosition, @Nullable Vec3 aimVelocity,
                           boolean tracking, boolean decoyed, Phase phase,
                           Loss loss) {
    }

    private SeekerPolicy() {
    }

    public static Solution solve(MissileProfile profile, SeekerState seeker,
                                 Vec3 position, Vec3 bodyAxis,
                                 @Nullable Vec3 targetPosition,
                                 @Nullable Vec3 targetVelocity,
                                 Link link, List<Decoy> chaff, long seed) {
        return solve(profile, seeker, position, bodyAxis, targetPosition,
            targetVelocity, link, chaff, seed, false);
    }

    public static Solution solve(MissileProfile profile, SeekerState seeker,
                                 Vec3 position, Vec3 bodyAxis,
                                 @Nullable Vec3 targetPosition,
                                 @Nullable Vec3 targetVelocity,
                                 Link link, List<Decoy> decoys, long seed,
                                 boolean targetAfterburner) {
        List<Decoy> chaff = decoys;
        if (seeker.spent) {
            return spent(seeker);
        }
        seeker.flightTicks++;
        if (seeker.inertialAxis == null) {
            seeker.inertialAxis = inertialAxis(seed);
        }
        if (seeker.reacquireTimer > 0) {
            seeker.reacquireTimer--;
        }
        if (seeker.gimbalMemoryTicks > 0) {
            if (seeker.gimbalMemoryTicks == 1 && seeker.confirmTicks == 0) {
                dropLock(profile, seeker);
            } else if (seeker.gimbalMemoryTicks > 1) {
                seeker.gimbalMemoryTicks--;
            }
        }
        boolean seen = targetPosition != null && targetVelocity != null;
        boolean linked = seen && link != Link.LOST;
        boolean past = seen
            && overshot(profile, seeker, position, bodyAxis, targetPosition);
        if (past && !(profile.twoWayDatalink && linked)
                && seeker.gimbalMemoryTicks == 0
                && !canRememberGimbal(profile, seeker)) {
            return spent(seeker);
        }
        seeker.linkAge = linked ? 0 : seeker.linkAge + 1;

        if (linked && !seeker.active) {
            if (seeker.linkPosition == null || seeker.linkVelocity == null
                    || seeker.flightTicks <= 1
                    || seeker.midcourseAge + 1 >= profile.datalinkUpdateTicks) {
                seeker.midcourseAge = 0;
                seeker.linkPosition = targetPosition;
                seeker.linkVelocity = targetVelocity;
            } else {
                seeker.midcourseAge++;
                seeker.linkPosition = seeker.linkPosition.add(seeker.linkVelocity);
            }
        } else if ((!seeker.tracking || seeker.capturedDecoy >= 0)
                && seeker.linkPosition != null && seeker.linkVelocity != null) {
            seeker.linkPosition = seeker.linkPosition.add(seeker.linkVelocity);
            if (!seeker.active) {
                seeker.midcourseAge++;
            }
        }
        if (linked && seeker.active && profile.twoWayDatalink) {
            if (seeker.cuePosition == null || seeker.cueVelocity == null
                    || seeker.cueAge + 1 >= profile.datalinkUpdateTicks) {
                seeker.cueUpdates++;
                seeker.cueAge = 0;
                seeker.cuePosition = targetPosition.add(
                    cueError(profile, seeker, seed));
                seeker.cueVelocity = targetVelocity;
            } else {
                seeker.cueAge++;
                seeker.cuePosition = seeker.cuePosition.add(seeker.cueVelocity);
            }
        } else if (!seeker.tracking && seeker.cuePosition != null
                && seeker.cueVelocity != null) {
            seeker.linkPosition = seeker.cuePosition.add(seeker.cueVelocity);
            seeker.linkVelocity = seeker.cueVelocity;
            if (!seeker.relockingAfterDecoy) {
                seeker.holdPosition = seeker.linkPosition;
            }
            seeker.cuePosition = null;
            seeker.cueVelocity = null;
        }

        switch (profile.seekerType) {
            case IR -> {
                launchLock(seeker);
                return terminal(profile, seeker, position, bodyAxis,
                    seen ? targetPosition : null, targetVelocity, false,
                    decoys, seed, false, targetAfterburner);
            }
            case SARH -> {
                launchLock(seeker);
                if (!linked) {
                    return lost(profile, seeker, position, bodyAxis,
                        seen ? Loss.NO_ILLUMINATION : Loss.NO_TARGET);
                }
                return terminal(profile, seeker, position, bodyAxis,
                    targetPosition, targetVelocity, true, chaff, seed, false,
                    false);
            }
            default -> {
                if (!seeker.active) {
                    Vec3 predicted = seeker.linkPosition;
                    if (predicted != null
                            && (profile.seekerActivationRange <= 0.0D
                                || predicted.distanceTo(position)
                                    <= profile.seekerActivationRange)) {
                        seeker.active = true;
                        seeker.activatedAt = predicted.distanceTo(position);
                    } else {
                        seeker.tracking = false;
                        predicted = drifted(profile, seeker, predicted);
                        seeker.look = predicted == null ? null
                            : clampToGimbal(profile, bodyAxis,
                                predicted.subtract(position));
                        seeker.loss = predicted == null ? Loss.NO_TARGET
                            : Loss.NONE;
                        return new Solution(predicted, seeker.linkVelocity,
                            false, false, Phase.MIDCOURSE, seeker.loss);
                    }
                }
                return terminal(profile, seeker, position, bodyAxis,
                    seen ? targetPosition : null, targetVelocity, true, chaff,
                    seed, profile.seekerFov > 0.0D, false);
            }
        }
    }

    private static Solution terminal(MissileProfile profile, SeekerState seeker,
                                     Vec3 position, Vec3 bodyAxis,
                                     @Nullable Vec3 targetPosition,
                                     @Nullable Vec3 targetVelocity,
                                     boolean radar, List<Decoy> chaff,
                                     long seed, boolean fovLimited,
                                     boolean targetAfterburner) {
        if (seeker.capturedDecoy >= 0) {
            Decoy held = find(chaff, seeker.capturedDecoy);
            if (held != null) {
                seeker.holdPosition = held.position();
                return track(seeker, position, held.position(), held.velocity(),
                    true);
            }
            seeker.capturedDecoy = -1;
            seeker.relockingAfterDecoy = true;
            dropLock(profile, seeker);
        }
        boolean searching = fovLimited && !seeker.tracking;
        if (targetPosition == null) {
            if (searching) {
                scan(profile, seeker, position, bodyAxis, null);
            }
            return lost(profile, seeker, position, bodyAxis, Loss.NO_TARGET);
        }

        Vec3 los = targetPosition.subtract(position);
        boolean inGimbal = MissilePolicy.withinGimbal(profile, arr(bodyAxis),
            arr(los));
        if (!inGimbal && canRememberGimbal(profile, seeker)) {
            seeker.linkPosition = seeker.linkPosition.add(seeker.linkVelocity);
            seeker.tracking = false;
            seeker.gimbalMemoryTicks = profile.gimbalMemoryTicks;
            seeker.confirmTicks = 0;
            seeker.reacquireTimer = 0;
        }
        boolean remembering = seeker.gimbalMemoryTicks > 0;
        if (remembering) {
            Vec3 point = searchPoint(profile, seeker);
            Vec3 direction = clampToGimbal(profile, bodyAxis,
                point.subtract(position));
            seeker.look = seeker.look == null || profile.seekerScanRate <= 0.0D
                ? direction : swing(seeker.look, direction, profile.seekerScanRate);
        } else if (searching) {
            boolean visible = inGimbal && !(radar && velocityGated(
                burnThroughGate(profile.velocityGate, profile.burnThroughRange,
                    los.length()), profile.velocityGateLookDownOnly,
                targetVelocity, los, position.y, targetPosition.y));
            scan(profile, seeker, position, bodyAxis, visible ? los : null);
        }
        boolean inView = inGimbal && (!(searching || remembering) || !fovLimited
            || withinCone(seeker.look, los, profile.seekerFov * 0.5D));
        if (!inView) {
            if (remembering) {
                return remembered(seeker, Loss.OUT_OF_VIEW);
            }
            return lost(profile, seeker, position, bodyAxis, Loss.OUT_OF_VIEW);
        }

        if (!radar && seeker.reacquireTimer <= 0) {
            Decoy taken = offerFlare(profile, seeker, position, bodyAxis, los,
                chaff, seed, targetAfterburner);
            if (taken != null) {
                seeker.capturedDecoy = taken.id();
                seeker.everDecoyed = true;
                seeker.holdPosition = taken.position();
                return track(seeker, position, taken.position(),
                    taken.velocity(), true);
            }
        }

        if (radar && velocityGated(burnThroughGate(profile.velocityGate,
                    profile.burnThroughRange, los.length()),
                profile.velocityGateLookDownOnly, targetVelocity, los,
                position.y, targetPosition.y)) {
            Decoy taken = seeker.reacquireTimer > 0 ? null
                : offerChaff(profile, seeker, position, bodyAxis, los, chaff,
                    seed, fovLimited);
            if (taken != null) {
                seeker.capturedDecoy = taken.id();
                seeker.everDecoyed = true;
                seeker.holdPosition = taken.position();
                return track(seeker, position, taken.position(),
                    taken.velocity(), true);
            }
            seeker.ticksInGate++;
            return lost(profile, seeker, position, bodyAxis, Loss.VELOCITY_GATE);
        }
        if (!seeker.tracking) {
            if (seeker.reacquireTimer > 0) {
                return lost(profile, seeker, position, bodyAxis,
                    Loss.REACQUIRING);
            }
            int confirmation = remembering ? profile.gimbalConfirmTicks
                : profile.lockConfirmTicks;
            if (++seeker.confirmTicks < confirmation) {
                if (remembering) {
                    return remembered(seeker, Loss.CONFIRMING);
                }
                return lost(profile, seeker, position, bodyAxis,
                    Loss.CONFIRMING);
            }
            if (seeker.everTracked) {
                seeker.relocks++;
            }
        }
        seeker.ticksTracking++;
        seeker.everTracked = true;
        seeker.relockingAfterDecoy = false;
        return track(seeker, position, targetPosition, targetVelocity, false);
    }

    static boolean overshot(MissileProfile profile, SeekerState seeker,
                            Vec3 position, Vec3 bodyAxis, Vec3 targetPosition) {
        Vec3 los = targetPosition.subtract(position);
        double range = los.length();
        double previous = seeker.lastRange;
        seeker.lastRange = range;
        seeker.closestRange = Math.min(seeker.closestRange, range);
        seeker.furthestRange = Math.max(seeker.furthestRange, range);
        double scale = amrac.physics.aircraft.SpeedScale.current();
        return Double.isFinite(previous) && range > previous
            && seeker.closestRange <= OVERSHOOT_RANGE * scale
            && seeker.furthestRange - seeker.closestRange >= OVERSHOOT_CLOSURE * scale
            && !MissilePolicy.withinGimbal(profile, arr(bodyAxis), arr(los));
    }

    private static Solution spent(SeekerState seeker) {
        seeker.spent = true;
        seeker.tracking = false;
        seeker.capturedDecoy = -1;
        seeker.relockingAfterDecoy = false;
        seeker.cuePosition = null;
        seeker.cueVelocity = null;
        seeker.holdPosition = null;
        seeker.gimbalMemoryTicks = 0;
        seeker.loss = Loss.OVERSHOT;
        return new Solution(null, null, false, false, Phase.TERMINAL, Loss.OVERSHOT);
    }

    private static void launchLock(SeekerState seeker) {
        if (!seeker.active) {
            seeker.active = true;
            seeker.tracking = true;
            seeker.everTracked = true;
        }
    }

    private static void dropLock(MissileProfile profile, SeekerState seeker) {
        if (seeker.tracking || seeker.gimbalMemoryTicks > 0) {
            seeker.reacquireTimer = profile.reacquireTicks;
        }
        seeker.gimbalMemoryTicks = 0;
        seeker.tracking = false;
        seeker.confirmTicks = 0;
    }

    private static boolean canRememberGimbal(MissileProfile profile, SeekerState seeker) {
        return profile.seekerType == SeekerType.ARH && profile.gimbalMemoryTicks > 0
            && seeker.tracking && seeker.everTracked && seeker.capturedDecoy < 0
            && !seeker.relockingAfterDecoy
            && seeker.linkPosition != null && seeker.linkVelocity != null;
    }

    private static Solution remembered(SeekerState seeker, Loss loss) {
        seeker.tracking = false;
        seeker.loss = loss;
        if (loss != Loss.CONFIRMING) {
            seeker.confirmTicks = 0;
        }
        return seeker.cuePosition != null && seeker.cueVelocity != null
            ? new Solution(seeker.cuePosition, seeker.cueVelocity, false, false,
                Phase.TERMINAL, loss)
            : new Solution(seeker.linkPosition, seeker.linkVelocity, false, false,
                Phase.TERMINAL, loss);
    }

    private static Solution track(SeekerState seeker, Vec3 position,
                                  Vec3 aim, Vec3 aimVelocity, boolean decoy) {
        seeker.tracking = true;
        seeker.confirmTicks = 0;
        seeker.gimbalMemoryTicks = 0;
        seeker.scanArc = 0.0D;
        seeker.loss = Loss.NONE;
        seeker.cuePosition = null;
        seeker.cueVelocity = null;
        seeker.look = aim.subtract(position);
        if (!decoy) {
            seeker.linkPosition = aim;
            seeker.linkVelocity = aimVelocity;
            seeker.holdPosition = null;
        }
        return new Solution(aim, aimVelocity, true, decoy, Phase.TERMINAL,
            Loss.NONE);
    }

    private static Solution lost(MissileProfile profile, SeekerState seeker,
                                 Vec3 position, Vec3 bodyAxis, Loss loss) {
        if (loss != Loss.CONFIRMING) {
            dropLock(profile, seeker);
        }
        seeker.tracking = false;
        seeker.loss = loss;
        if (profile.seekerType == SeekerType.ARH && seeker.linkPosition != null) {
            if (seeker.holdPosition == null && seeker.everTracked) {
                seeker.holdPosition = seeker.linkPosition;
            }
            if (!(profile.seekerFov > 0.0D) || seeker.look == null) {
                Vec3 lookAt = searchPoint(profile, seeker);
                seeker.look = clampToGimbal(profile, bodyAxis,
                    lookAt.subtract(position));
            }
            if (seeker.cuePosition != null && seeker.cueVelocity != null) {
                return new Solution(drifted(profile, seeker, seeker.cuePosition),
                    seeker.cueVelocity, false, false, Phase.TERMINAL, loss);
            }
            if (!extrapolates(profile, seeker) && seeker.holdPosition != null) {
                return new Solution(drifted(profile, seeker, seeker.holdPosition),
                    Vec3.ZERO, false, false, Phase.TERMINAL, loss);
            }
            return new Solution(drifted(profile, seeker, seeker.linkPosition),
                seeker.linkVelocity, false, false, Phase.TERMINAL, loss);
        }
        return new Solution(null, null, false, false, Phase.TERMINAL, loss);
    }

    @Nullable
    private static Decoy offerChaff(MissileProfile profile, SeekerState seeker,
                                    Vec3 position, Vec3 bodyAxis, Vec3 los,
                                    List<Decoy> chaff, long seed,
                                    boolean fovLimited) {
        if (!(profile.chaffSusceptibility > 0.0D) || chaff.isEmpty()) {
            return null;
        }
        if (!seeker.tracking) {
            return null;
        }
        double range = los.length();
        double scale = amrac.physics.aircraft.SpeedScale.current();
        double rangeGate = CHAFF_RANGE_GATE * scale;
        double bloom = CHAFF_MIN_SEPARATION * scale;
        List<Decoy> ordered = chaff.stream()
            .sorted(Comparator.comparingInt(Decoy::id)).toList();
        for (Decoy decoy : ordered) {
            if (decoy.flare() || seeker.rolledDecoys.contains(decoy.id())) {
                continue;
            }
            Vec3 toDecoy = decoy.position().subtract(position);
            if (!MissilePolicy.withinGimbal(profile, arr(bodyAxis), arr(toDecoy))) {
                continue;
            }
            if (fovLimited && !withinCone(los, toDecoy, profile.seekerFov * 0.5D)) {
                continue;
            }
            if (Math.abs(toDecoy.length() - range) > rangeGate) {
                continue;
            }
            if (decoy.position().distanceTo(position.add(los))
                    < bloom) {
                continue;
            }
            seeker.rolledDecoys.add(decoy.id());
            if (roll(seed, decoy.id()) < profile.chaffSusceptibility) {
                return decoy;
            }
        }
        return null;
    }

    public static double flareChance(MissileProfile profile, boolean afterburner) {
        double p = Math.max(0.0D, Math.min(1.0D, profile.flareSusceptibility));
        if (afterburner) {
            p *= 1.0D - Math.max(0.0D, Math.min(1.0D, profile.heatSourceCoefficient));
        }
        return p;
    }

    @Nullable
    private static Decoy offerFlare(MissileProfile profile, SeekerState seeker,
                                    Vec3 position, Vec3 bodyAxis, Vec3 los,
                                    List<Decoy> decoys, long seed,
                                    boolean afterburner) {
        if (!(profile.flareSusceptibility > 0.0D) || decoys.isEmpty()) {
            return null;
        }
        double view = profile.seekerFov > 0.0D ? profile.seekerFov * 0.5D
            : FLARE_VIEW_HALF_ANGLE;
        double chance = flareChance(profile, afterburner);
        List<Decoy> ordered = decoys.stream()
            .sorted(Comparator.comparingInt(Decoy::id)).toList();
        for (Decoy decoy : ordered) {
            if (!decoy.flare() || seeker.rolledDecoys.contains(decoy.id())) {
                continue;
            }
            Vec3 toDecoy = decoy.position().subtract(position);
            if (!MissilePolicy.withinGimbal(profile, arr(bodyAxis), arr(toDecoy))
                    || !withinCone(los, toDecoy, view)) {
                continue;
            }
            seeker.rolledDecoys.add(decoy.id());
            if (roll(seed, decoy.id()) < chance) {
                return decoy;
            }
        }
        return null;
    }

    public static boolean velocityGated(double gate, boolean lookDownOnly,
                                        @Nullable Vec3 targetVelocity,
                                        Vec3 lineOfSight, double observerY,
                                        double targetY) {
        if (!(gate > 0.0D) || targetVelocity == null) {
            return false;
        }
        if (lookDownOnly && targetY >= observerY) {
            return false;
        }
        double length = lineOfSight.length();
        if (length < 1.0E-6D) {
            return false;
        }
        double radial = targetVelocity.dot(lineOfSight) / length;
        return Math.abs(radial) < gate;
    }

    public static double burnThroughGate(double gate, double range,
                                         double distance) {
        if (!(range > 0.0D) || !(distance < range)) {
            return gate;
        }
        double inside = 1.0D - Math.max(0.0D, distance) / range;
        return gate * (1.0D - inside * inside);
    }

    public static double roll(long seed, int decoyId) {
        long z = seed + 0x9E3779B97F4A7C15L * (decoyId + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    public static long seed(java.util.UUID id) {
        return id.getMostSignificantBits() * 31L ^ id.getLeastSignificantBits();
    }

    @Nullable
    static Vec3 searchPoint(MissileProfile profile, SeekerState seeker) {
        if (seeker.cuePosition != null) {
            return drifted(profile, seeker, seeker.cuePosition);
        }
        if (!extrapolates(profile, seeker) && seeker.holdPosition != null) {
            return drifted(profile, seeker, seeker.holdPosition);
        }
        return drifted(profile, seeker, seeker.linkPosition);
    }

    @Nullable
    static Vec3 drifted(MissileProfile profile, SeekerState seeker,
                        @Nullable Vec3 point) {
        if (point == null) {
            return null;
        }
        Vec3 error = inertialError(profile, seeker);
        return error == null ? point : point.add(error);
    }

    static Vec3 cueError(MissileProfile profile, SeekerState seeker, long seed) {
        if (!(profile.datalinkError > 0.0D) || !Double.isFinite(seeker.launcherRange)
                || !(seeker.launcherRange > 0.0D)) {
            return Vec3.ZERO;
        }
        long h = (seed ^ (seeker.cueUpdates * 0xD1B54A32D192ED03L))
            * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        double bearing = ((h >>> 11) & 0xFFFFF) / (double) 0x100000 * Math.PI * 2.0D;
        double rise = ((h >>> 33) & 0xFFFF) / (double) 0x10000 * 2.0D - 1.0D;
        double size = 0.5D + ((h >>> 49) & 0x7FFF) / (double) 0x8000;
        double flat = Math.sqrt(Math.max(0.0D, 1.0D - rise * rise));
        double metres = Math.tan(profile.datalinkError) * seeker.launcherRange * size;
        return new Vec3(Math.cos(bearing) * flat * metres, rise * metres,
            Math.sin(bearing) * flat * metres);
    }

    @Nullable
    static Vec3 inertialError(MissileProfile profile, SeekerState seeker) {
        if (profile.seekerType != SeekerType.ARH || !(profile.inertialDrift > 0.0D)
                || seeker.inertialAxis == null || seeker.flightTicks <= 0) {
            return null;
        }
        return seeker.inertialAxis.scale(profile.inertialDrift * seeker.flightTicks);
    }

    static Vec3 inertialAxis(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        double bearing = ((h >>> 11) & 0xFFFFF) / (double) 0x100000 * Math.PI * 2.0D;
        double rise = (((h >>> 33) & 0xFFFF) / (double) 0x10000 - 0.5D) * 0.5D;
        double flat = Math.sqrt(1.0D - rise * rise);
        return new Vec3(Math.cos(bearing) * flat, rise, Math.sin(bearing) * flat);
    }

    private static boolean extrapolates(MissileProfile profile, SeekerState seeker) {
        if (seeker.gimbalMemoryTicks > 0) {
            return true;
        }
        return seeker.capturedDecoy >= 0 || seeker.relockingAfterDecoy
            ? profile.extrapolateAfterDecoy : profile.extrapolateOnLoss;
    }

    static void scan(MissileProfile profile, SeekerState seeker, Vec3 position,
                     Vec3 bodyAxis, @Nullable Vec3 visibleLos) {
        double half = profile.seekerFov * 0.5D;
        Vec3 centrePoint = searchPoint(profile, seeker);
        Vec3 centre = centrePoint == null ? bodyAxis
            : clampToGimbal(profile, bodyAxis, centrePoint.subtract(position));
        if (seeker.look == null || seeker.look.length() < 1.0E-9D) {
            seeker.look = centre;
        }
        double rate = profile.seekerScanRate;
        if (seeker.reacquireTimer > 0) {
            seeker.look = rate > 0.0D ? swing(seeker.look, centre, rate) : centre;
            return;
        }
        if (visibleLos != null && withinCone(seeker.look, visibleLos, half)) {
            seeker.look = clampToGimbal(profile, bodyAxis, visibleLos);
            return;
        }
        if (!(rate > 0.0D)) {
            seeker.look = centre;
            return;
        }
        double pitch = Math.max(profile.seekerFov * 0.8D, 1.0E-3D);
        double reach = profile.seekerGimbalLimit + angle(centre, bodyAxis) + half;
        double stride = Math.max(half, 1.0E-3D);
        double remaining = rate;
        for (int guard = 0; guard < 512 && remaining > 1.0E-9D; guard++) {
            double off = Math.sqrt(pitch * seeker.scanArc / Math.PI);
            if (off > reach) {
                seeker.scanArc = 0.0D;
                off = 0.0D;
            }
            Vec3 wanted = clampToGimbal(profile, bodyAxis,
                spiral(centre, off, 2.0D * Math.PI * off / pitch));
            double gap = angle(seeker.look, wanted);
            if (gap < 1.0E-6D) {
                seeker.scanArc += Math.min(stride, remaining);
                continue;
            }
            double move = Math.min(Math.min(stride, remaining), gap);
            seeker.look = swing(seeker.look, wanted, move);
            remaining -= move;
            if (visibleLos != null && withinCone(seeker.look, visibleLos, half)) {
                seeker.look = clampToGimbal(profile, bodyAxis, visibleLos);
                return;
            }
        }
    }

    static Vec3 spiral(Vec3 centre, double off, double around) {
        Vec3 c = centre.normalize();
        Vec3 reference = Math.abs(c.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D)
            : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 u = c.cross(reference).normalize();
        Vec3 v = c.cross(u);
        return c.scale(Math.cos(off)).add(u.scale(Math.cos(around) * Math.sin(off)))
            .add(v.scale(Math.sin(around) * Math.sin(off)));
    }

    private static Vec3 swing(Vec3 from, Vec3 to, double maxRadians) {
        double[] out = new double[3];
        if (!MissilePolicy.turnToward(arr(from), arr(to), maxRadians, out)) {
            return to;
        }
        return new Vec3(out[0], out[1], out[2]);
    }

    private static double angle(Vec3 a, Vec3 b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1.0E-9D || lb < 1.0E-9D) {
            return 0.0D;
        }
        return Math.acos(Math.max(-1.0D, Math.min(1.0D, a.dot(b) / (la * lb))));
    }

    static Vec3 clampToGimbal(MissileProfile profile, Vec3 bodyAxis, Vec3 wanted) {
        double[] out = new double[3];
        if (!MissilePolicy.turnToward(arr(bodyAxis), arr(wanted),
                profile.seekerGimbalLimit, out)) {
            return bodyAxis;
        }
        return new Vec3(out[0], out[1], out[2]);
    }

    static boolean withinCone(Vec3 axis, Vec3 direction, double halfAngle) {
        double a = axis.length();
        double d = direction.length();
        if (a < 1.0E-9D || d < 1.0E-9D) {
            return false;
        }
        return axis.dot(direction) / (a * d) >= Math.cos(halfAngle);
    }

    @Nullable
    private static Decoy find(List<Decoy> decoys, int id) {
        for (Decoy decoy : decoys) {
            if (decoy.id() == id) {
                return decoy;
            }
        }
        return null;
    }

    private static double[] arr(Vec3 v) {
        return new double[] {v.x, v.y, v.z};
    }
}
