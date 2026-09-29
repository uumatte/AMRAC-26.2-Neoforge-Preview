package amrac.weapons;

import amrac.platform.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import amrac.entities.MissileEntity;
import amrac.entities.AircraftRegistry;
import amrac.entities.AircraftTargetSnapshot;
import amrac.entities.AircraftVirtualService;
import amrac.entities.PlaneEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VirtualMissileService {
    private static final Map<UUID, VirtualMissileState> ACTIVE =
        new LinkedHashMap<>();

    private VirtualMissileService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(VirtualMissileService::tick);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    public static List<VirtualMissileState> chasing(java.util.UUID aircraftId) {
        if (aircraftId == null || ACTIVE.isEmpty()) {
            return List.of();
        }
        List<VirtualMissileState> out = new java.util.ArrayList<>();
        for (VirtualMissileState state : ACTIVE.values()) {
            if (aircraftId.equals(state.targetId)
                    && (state.seeker == null || !state.seeker.spent)) {
                out.add(state);
            }
        }
        return out;
    }

    public static void adopt(VirtualMissileState state) {
        ACTIVE.put(state.id, state);
    }

    public static List<VirtualMissileState> all() {
        return ACTIVE.isEmpty() ? List.of() : new ArrayList<>(ACTIVE.values());
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        List<VirtualMissileState> finished = new ArrayList<>();
        List<VirtualMissileState> arriving = new ArrayList<>();

        for (VirtualMissileState state : ACTIVE.values()) {
            ServerLevel level = server.getLevel(state.dimension);
            if (level == null) {
                finished.add(state);
                continue;
            }
            boolean carried = state.movePending;
            state.movePending = false;
            if (!carried && ++state.age > state.profile.maxLifetimeTicks) {
                MissileEndgameLog.spent(state.profile.id,
                    shooterName(level, state), state.age, state.closestMiss,
                    state.profile.proximityFuseRadius,
                    state.velocity.length() * 20.0D, state.lastTargetRange,
                    "virtual", state.seeker.summary(state.profile.seekerType));
                finished.add(state);
                continue;
            }

            AircraftTargetSnapshot target = resolveTarget(level, state);
            if (target != null) {
                state.targetSeen = true;
                state.lastTargetRange =
                    target.position().distanceTo(state.position);
            } else if (state.targetSeen) {
                state.targetSeen = false;
                MissileEndgameLog.targetLost(state.profile.id,
                    shooterName(level, state), state.age,
                    state.lastTargetRange, "virtual");
            }
            Vec3 truePosition = target == null ? null : target.position();
            Vec3 trueVelocity = target == null ? null : target.velocity();
            Vec3 targetPosition = null;
            Vec3 before = state.position;
            if (carried) {
                state.position = state.position.add(state.velocity);
                state.travelled += state.velocity.length();
                reportTrack(state);
            } else {
                SeekerPolicy.Link link = SeekerLinks.link(level,
                    state.sourcePlaneId, truePosition, trueVelocity);
                state.seeker.launcherRange = SeekerLinks.launcherRange(
                    state.sourcePlaneId, truePosition);
                SeekerPolicy.Solution solution = SeekerPolicy.solve(state.profile,
                    state.seeker, state.position, state.axis, truePosition,
                    trueVelocity, link,
                    state.profile.seekerType.radar()
                        ? CountermeasureService.chaffNear(level, state.position)
                        : CountermeasureService.flaresNear(level, state.position),
                    SeekerPolicy.seed(state.id),
                    target != null && target.afterburner());
                if (amrac.trace.BvrTraceRecorder.recording()) {
                    amrac.trace.BvrTraceRecorder.sampleMissile(
                        new amrac.trace.BvrTraceRecorder.MissileSample(state.id,
                            state.profile, state.position, state.velocity,
                            state.axis, state.age, state.travelled, state.ownerId,
                            state.targetId, state.sourcePlaneId, truePosition,
                            trueVelocity, link, state.seeker, solution,
                            state.closestMiss, true));
                }
                warn(state, target);
                targetPosition = solution.aimPosition();
                Vec3 targetVelocity = solution.aimVelocity();
                step(state, targetPosition, targetVelocity);
                reportTrack(state);
                reportSeeker(state, solution);
            }

            if (!isFinite(state.position) || !isFinite(state.velocity)) {
                finished.add(state);
                continue;
            }

            Vec3 relevantTarget = truePosition != null ? truePosition
                : targetPosition;
            double relevant = MissileLifecyclePolicy.relevantDistance(
                nearestPlayerDistance(level, state.position),
                relevantTarget == null ? Double.NaN
                    : relevantTarget.distanceTo(state.position));
            // A virtual missile becomes an entity again only where entities tick, not merely where
            // the chunk is loaded. Where it can't, the virtual layer resolves the hit itself
            // (destroyLive), and it is skipped on the tick it is restored, so it detonates once.
            boolean loaded = level.isPositionEntityTicking(
                BlockPos.containing(state.position));
            boolean realising =
                MissileLifecyclePolicy.shouldRealise(relevant, loaded);

            if (target != null && fusedOn(state, before, target)) {
                if (target.virtual()) {
                    AircraftVirtualService.announceShotDown(level.getServer(),
                        target.id(), shooterName(level, state),
                        amrac.AmracItems.missileName(
                            state.profile.id));
                    AircraftVirtualService.destroy(target.id());
                    MissileSeekerService.reportHit(state.id.hashCode(),
                        state.ownerId);
                    finished.add(state);
                    continue;
                }
                if (!realising && destroyLive(level, state, target)) {
                    MissileSeekerService.reportHit(state.id.hashCode(),
                        state.ownerId);
                    finished.add(state);
                    continue;
                }
            }

            if (realising) {
                arriving.add(state);
            }
        }

        for (VirtualMissileState state : finished) {
            ACTIVE.remove(state.id);
        }
        for (VirtualMissileState state : arriving) {
            ACTIVE.remove(state.id);
            ServerLevel level = server.getLevel(state.dimension);
            if (level != null) {
                MissileEntity.realise(level, state);
            }
        }
    }

    public static void step(VirtualMissileState state,
                            @Nullable Vec3 targetPosition,
                            @Nullable Vec3 targetVelocity) {
        double[] axis = {state.axis.x, state.axis.y, state.axis.z};
        double speed = state.velocity.length();

        if (targetPosition != null) {
            double[] toTarget = {
                targetPosition.x - state.position.x,
                targetPosition.y - state.position.y,
                targetPosition.z - state.position.z};
            double[] desired = new double[3];
            Vec3 lead = targetVelocity == null ? Vec3.ZERO : targetVelocity;
            if (MissilePolicy.steeringDirection(state.profile, toTarget,
                new double[] {lead.x, lead.y, lead.z}, speed,
                state.position.y, desired)) {
                double[] turned = new double[3];
                if (MissilePolicy.turnToward(axis, desired,
                    MissilePolicy.maxTurnRadians(state.profile, speed,
                        state.position.y, state.age), turned)) {
                    axis = turned;
                }
            }
            state.guidanceLost = false;
        } else {
            state.guidanceLost = true;
        }
        state.axis = new Vec3(axis[0], axis[1], axis[2]);

        Vec3 velocity = state.velocity;
        double[] advanced = new double[3];
        if (MissilePolicy.advanceAxial(state.profile, state.age, state.position.y,
            new double[] {velocity.x, velocity.y, velocity.z}, axis, advanced)) {
            velocity = new Vec3(advanced[0], advanced[1], advanced[2]);
        }
        {
            double[] command = null;
            if (targetPosition != null) {
                double[] toTarget = {
                    targetPosition.x - state.position.x,
                    targetPosition.y - state.position.y,
                    targetPosition.z - state.position.z};
                if (MissilePolicy.withinGimbal(state.profile, axis, toTarget)) {
                    Vec3 targetMotion = targetVelocity == null
                        ? Vec3.ZERO : targetVelocity;
                    double[] demand = new double[3];
                    if (MissilePolicy.guidance(state.profile, toTarget,
                        new double[] {targetMotion.x - velocity.x,
                            targetMotion.y - velocity.y,
                            targetMotion.z - velocity.z},
                        velocity.length(), state.position.y, demand)) {
                        command = demand;
                    }
                }
            }

            double[] aligned = new double[3];
            double[] load = {state.lastLoadG};
            if (MissilePolicy.alignAndCharge(state.profile,
                new double[] {velocity.x, velocity.y, velocity.z}, axis,
                command, state.position.y, load, state.age, aligned)) {
                velocity = new Vec3(aligned[0], aligned[1], aligned[2]);
                state.lastLoadG = load[0];
            }
        }

        double capped = velocity.length();
        double cap = MissilePolicy.speedCap(state.profile);
        if (cap > 0.0D && capped > cap) {
            velocity = velocity.scale(cap / capped);
        }
        state.velocity = velocity;
        state.position = state.position.add(velocity);
        state.travelled += velocity.length();
    }

    private static void warn(VirtualMissileState state,
                             @Nullable AircraftTargetSnapshot target) {
        if (target == null) {
            return;
        }
        int mode = MissileWarningService.warnMode(state.profile.seekerType,
            state.seeker);
        if (mode == MissileWarningService.WARN_NEVER) {
            return;
        }
        PlaneEntity live = AircraftRegistry.liveEntity(state.targetId);
        if (live != null) {
            MissileWarningService.report(live.getId(),
                -Math.abs(state.id.hashCode()) - 1, state.position,
                mode == MissileWarningService.WARN_IN_RANGE);
        }
    }

    @Nullable
    private static void reportTrack(VirtualMissileState state) {
        MissileTrackService.report(state.id.hashCode(), state.position,
            MissilePolicy.motorLit(state.profile, state.age));
    }

    private static void reportSeeker(VirtualMissileState state,
                                     SeekerPolicy.Solution solution) {
        boolean holding = solution.tracking();
        MissileSeekerService.report(state.id.hashCode(), state.ownerId,
            state.position, state.axis, state.velocity,
            holding ? solution.aimPosition() : null,
            holding ? solution.aimVelocity() : null, holding,
            MissilePolicy.motorLit(state.profile, state.age));
    }

    private static AircraftTargetSnapshot resolveTarget(ServerLevel level,
                                                        VirtualMissileState state) {
        AircraftTargetSnapshot target =
            AircraftTargetSnapshot.of(level, state.targetId);
        if (target == null) {
            return null;
        }
        state.lastTargetPosition = target.position();
        state.lastTargetVelocity = target.velocity();
        return target;
    }

    private static boolean destroyLive(ServerLevel level,
                                       VirtualMissileState state,
                                       AircraftTargetSnapshot target) {
        PlaneEntity live = AircraftRegistry.liveEntity(target.id());
        if (live == null || !live.isAlive()) {
            return false;
        }
        Entity shooter = state.ownerId == null ? null
            : level.getEntity(state.ownerId);
        String named = shooter != null ? null : shooterName(level, state);
        live.hurt(amrac.AmracDamage.weaponSource(
                level, amrac.AmracDamage.PLANE_SHOT_DOWN,
                null, shooter,
                amrac.AmracItems.missileName(
                    state.profile.id),
                named == null ? null
                    : net.minecraft.network.chat.Component.literal(named)),
            Float.MAX_VALUE);
        level.explode(null, state.position.x, state.position.y,
            state.position.z, state.profile.explosionPower,
            net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        return true;
    }

    @Nullable
    private static String shooterName(ServerLevel level, VirtualMissileState state) {
        String name = AircraftVirtualService.shooterName(level, state.ownerId);
        return name != null ? name : state.ownerName;
    }

    private static boolean fusedOn(VirtualMissileState state, Vec3 before,
                                   AircraftTargetSnapshot target) {
        double[] pass = new double[4];
        double miss = MissilePolicy.passDistanceSqr(
            new double[] {before.x, before.y, before.z},
            new double[] {state.position.x, state.position.y, state.position.z},
            new double[] {target.position().x, target.position().y,
                target.position().z},
            new double[] {target.velocity().x, target.velocity().y,
                target.velocity().z},
            MissileEntity.halfExtents(target),
            state.profile.proximityFuseRadius, pass);
        state.closestMiss = Math.min(state.closestMiss, pass[3]);
        return miss <= 0.0D
            && MissilePolicy.shouldDetonate(state.profile, 0.0D, state.travelled);
    }

    private static double nearestPlayerDistance(ServerLevel level, Vec3 position) {
        double nearest = Double.MAX_VALUE;
        Collection<ServerPlayer> players = level.players();
        for (ServerPlayer player : players) {
            nearest = Math.min(nearest, player.position().distanceTo(position));
        }
        return players.isEmpty() ? Double.NaN : nearest;
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) &&
            Double.isFinite(vector.z);
    }
}
