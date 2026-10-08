package amrac.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import amrac.client.SoundMixPolicy;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import amrac.AmracDamage;
import amrac.AmracEntities;
import amrac.AmracSounds;
import amrac.weapons.MissileLifecyclePolicy;
import amrac.weapons.MissilePolicy;
import amrac.weapons.MissileProfile;
import amrac.weapons.MissileProfiles;
import amrac.weapons.VirtualMissileService;
import amrac.weapons.VirtualMissileState;

import java.util.UUID;

public class MissileEntity extends Entity {
    private static final EntityDataAccessor<Integer> TARGET_ID =
        SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> MOTOR =
        SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> WARN_MODE =
        SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> SPENT =
        SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BOOLEAN);

    public static final byte MOTOR_COLD = 0;
    public static final byte MOTOR_BOOST = 1;
    public static final byte MOTOR_COAST = 2;

    private int sourcePlaneId = -1;
    @Nullable
    private UUID ownerUuid;
    @Nullable
    private String ownerNameAtLaunch;
    @Nullable
    private UUID targetUuid;

    @Nullable
    private UUID sourcePlaneUuid;

    private amrac.weapons.SeekerState seeker = new amrac.weapons.SeekerState();

    public amrac.weapons.SeekerState seekerState() {
        return seeker;
    }

    private double closestMiss = Double.POSITIVE_INFINITY;

    private double lastTargetRange = Double.NaN;

    private boolean targetSeen;
    private double travelled;
    private double lastLoadG;
    private boolean guidanceLost;

    private boolean pointed;
    private int restoredAge;

    private Vec3 axis = Vec3.ZERO;
    /** World-space radians/second; carried through virtualisation and saves. */
    private final double[] angularVelocity = new double[3];

    private MissileProfile profile = MissileProfiles.defaultProfile();

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public MissileEntity(Level level, @Nullable LivingEntity owner,
                         PlaneEntity launcher, AircraftTargetSnapshot target,
                         Vec3 position, Vec3 velocity, MissileProfile profile) {
        this(AmracEntities.MISSILE, level);
        this.profile = profile == null
            ? MissileProfiles.defaultProfile() : profile;
        if (owner != null) {
            ownerUuid = owner.getUUID();
            String pilot = level instanceof ServerLevel serverLevel
                ? amrac.entities.ai.AiPilotService.displayName(serverLevel,
                    ownerUuid)
                : null;
            ownerNameAtLaunch = pilot != null ? pilot
                : owner.getDisplayName().getString();
        }
        sourcePlaneId = launcher.getId();
        sourcePlaneUuid = launcher.getUUID();
        targetUuid = target.id();
        seeker = new amrac.weapons.SeekerState(target.position(), target.velocity());
        PlaneEntity live = AircraftRegistry.liveEntity(target.id());
        entityData.set(TARGET_ID, live == null ? 0 : live.getId() + 1);
        setPos(position.x, position.y, position.z);
        xo = position.x;
        yo = position.y;
        zo = position.z;
        setDeltaMovement(velocity);
        Vec3 rail = launcher.getBodyDirection(0.0F, 0.0F, 1.0F);
        axis = rail.lengthSqr() > 1.0E-9D
            ? rail.normalize()
            : (velocity.lengthSqr() > 1.0E-9D
                ? velocity.normalize() : new Vec3(0.0D, 0.0D, 1.0D));
        pointAlongAxis();
    }

    public int getTargetId() {
        return entityData.get(TARGET_ID) - 1;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_ID, 0);
        builder.define(MOTOR, MOTOR_COLD);
        builder.define(WARN_MODE, (byte) 0);
        builder.define(SPENT, false);
    }

    public MissileProfile profile() {
        return profile;
    }

    public int age() {
        return restoredAge + tickCount;
    }

    @Override
    public void tick() {
        super.tick();
        yRotO = getYRot();
        xRotO = getXRot();
        if (age() > profile.maxLifetimeTicks) {
            if (!level().isClientSide()) {
                amrac.weapons.MissileEndgameLog.spent(profile.id,
                    shooterName(), age(),
                    closestMiss, profile.proximityFuseRadius,
                    getDeltaMovement().length() * 20.0D, lastTargetRange,
                    "entity", seeker.summary(profile.seekerType));
            }
            detonate(false);
            return;
        }

        Vec3 velocity = getDeltaMovement();
        if (!isFinite(velocity)) {
            discard();
            return;
        }

        boolean lit = MissilePolicy.motorLit(profile, age());
        byte motor = lit ? MOTOR_BOOST : MOTOR_COAST;
        if (!level().isClientSide()) {
            entityData.set(MOTOR, motor);
            amrac.weapons.LiveMissileIndex.note(this);
        }

        AircraftTargetSnapshot target = targetSnapshot();
        if (!level().isClientSide()) {
            if (target != null) {
                targetSeen = true;
                lastTargetRange = target.position().distanceTo(position());
            } else if (targetSeen) {
                targetSeen = false;
                amrac.weapons.MissileEndgameLog.targetLost(profile.id,
                    shooterName(), age(),
                    lastTargetRange, "entity");
            }
        }
        amrac.weapons.SeekerPolicy.Solution solution =
            level() instanceof ServerLevel serverLevel
                ? solveSeeker(serverLevel, target)
                : clientSolution(entityData.get(SPENT) ? null : target);
        Vec3 aimPosition = solution.aimPosition();
        Vec3 aimVelocity = solution.aimVelocity();
        if (!level().isClientSide()) {
            guidanceLost = aimPosition == null;
            entityData.set(SPENT, seeker.spent);
        }
        PlaneEntity liveTarget = resolveTarget();
        if (!level().isClientSide()) {
            int warn = amrac.weapons.MissileWarningService.warnMode(
                profile.seekerType, seeker);
            entityData.set(WARN_MODE, (byte) warn);
            if (liveTarget != null && warn > 0) {
                amrac.weapons.MissileWarningService.report(liveTarget.getId(),
                    getId(), position(),
                    warn == amrac.weapons.MissileWarningService.WARN_IN_RANGE);
            }
        }
        if (!level().isClientSide()) {
            amrac.weapons.MissileTrackService.report(
                getUUID().hashCode(), position(), motor == MOTOR_BOOST);
            Vec3 seekerAxis = bodyAxis();
            boolean holding = solution.tracking();
            amrac.weapons.MissileSeekerService.report(
                getUUID().hashCode(), ownerUuid, position(), seekerAxis,
                velocity, holding ? aimPosition : null,
                holding ? aimVelocity : null, holding,
                motor == MOTOR_BOOST);
        }
        steerAxis(aimPosition, aimVelocity);
        if (lit && restoredAge == 0 && tickCount == 1) {
            if (level().isClientSide()) {
                ignitionPuff();
            } else {
                Entity owner = resolveOwner();
                level().playSound(owner instanceof Player p ? p : null,
                    getX(), getY(), getZ(),
                    AmracSounds.MISSILE_LAUNCH, SoundSource.PLAYERS,
                    1.0F, 1.0F);
                if (owner instanceof ServerPlayer pilot) {
                    AmracSounds.playInCockpit(pilot, AmracSounds.MISSILE_LAUNCH_COCKPIT,
                        SoundMixPolicy.MISSILE_LAUNCH_COCKPIT_VOLUME);
                }
            }
        }
        double[] advanced = new double[3];
        if (MissilePolicy.advanceAxial(profile, age(), getY(), toArray(velocity),
            toArray(bodyAxis()), advanced)) {
            velocity = new Vec3(advanced[0], advanced[1], advanced[2]);
        }
        velocity = alignVelocity(velocity,
            guidanceCommand(velocity, aimPosition, aimVelocity));

        double speed = velocity.length();
        double cap = MissilePolicy.speedCap(profile);
        if (cap > 0.0D && speed > cap) {
            velocity = velocity.scale(cap / speed);
        }
        setDeltaMovement(velocity);

        Vec3 before = position();
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel &&
            shouldGoVirtual(serverLevel, position().add(velocity), target)) {
            virtualise(serverLevel, true);
            return;
        }
        move(MoverType.SELF, velocity);
        travelled += position().distanceTo(before);

        if (!level().isClientSide()) {
            if (checkImpact(before, position(), target)) {
                return;
            }
        } else if (entityData.get(MOTOR) == MOTOR_BOOST) {
            trail();
        }
    }

    @Nullable
    private double[] guidanceCommand(Vec3 velocity, @Nullable Vec3 aim,
                                     @Nullable Vec3 aimVelocity) {
        if (aim == null || aimVelocity == null) {
            return null;
        }
        Vec3 toTarget = aim.subtract(position());
        double[] forward = toArray(bodyAxis());
        double[] los = toArray(toTarget);
        if (!MissilePolicy.withinGimbal(profile, forward, los)) {
            return null;
        }

        double[] relative = toArray(aimVelocity.subtract(velocity));
        double[] command = new double[3];
        if (!MissilePolicy.guidance(profile, los, relative, velocity.length(),
            getY(), command)) {
            return null;
        }
        return command;
    }

    private boolean checkImpact(Vec3 from, Vec3 to,
                                @Nullable AircraftTargetSnapshot target) {
        if (target != null) {
            double[] pass = new double[4];
            double miss = MissilePolicy.passDistanceSqr(toArray(from),
                toArray(to), toArray(target.position()),
                toArray(target.velocity()), halfExtents(target),
                profile.proximityFuseRadius, pass);
            Vec3 closest = new Vec3(pass[0], pass[1], pass[2]);
            closestMiss = Math.min(closestMiss, pass[3]);
            if (miss <= 0.0D && MissilePolicy.shouldDetonate(profile, 0.0D, travelled)) {
                setPos(closest.x, closest.y, closest.z);
                PlaneEntity live = resolveTarget();
                boolean hit = false;
                if (live != null && live.isAlive()) {
                    destroyTarget(live);
                    hit = true;
                } else if (target.virtual()) {
                    AircraftVirtualService.destroy(target.id());
                    hit = true;
                }
                if (hit) {
                    amrac.weapons.MissileSeekerService.reportHit(
                        getUUID().hashCode(), ownerUuid);
                }
                detonate(true);
                return true;
            }
        }

        AABB sweep = new AABB(from, to).inflate(0.6D);
        for (Entity entity : level().getEntities(this, sweep, this::canHit)) {
            if (travelled < profile.armingDistance) {
                continue;
            }
            AABB box = entity.getBoundingBox();
            double[] pass = new double[4];
            if (MissilePolicy.passDistanceSqr(toArray(from), toArray(to),
                    toArray(box.getCenter()), toArray(entity.getDeltaMovement()),
                    new double[] {(box.maxX - box.minX) * 0.5D,
                        (box.maxY - box.minY) * 0.5D, (box.maxZ - box.minZ) * 0.5D},
                    0.6D, pass) > 0.0D) {
                continue;
            }
            Vec3 closest = new Vec3(pass[0], pass[1], pass[2]);
            setPos(closest.x, closest.y, closest.z);
            if (entity instanceof PlaneEntity plane) {
                destroyTarget(plane);
                amrac.weapons.MissileSeekerService.reportHit(
                    getUUID().hashCode(), ownerUuid);
            }
            detonate(true);
            return true;
        }

        HitResult block = level().clip(new ClipContext(from, to,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS &&
            travelled >= profile.armingDistance) {
            Vec3 at = block.getLocation();
            setPos(at.x, at.y, at.z);
            detonate(true);
            return true;
        }
        return false;
    }

    private boolean shouldGoVirtual(ServerLevel level, Vec3 next,
                                    @Nullable AircraftTargetSnapshot target) {
        boolean loaded = level.isPositionEntityTicking(BlockPos.containing(next));
        double relevant = MissileLifecyclePolicy.relevantDistance(
            nearestPlayerDistance(level), target == null ? Double.NaN
                : target.position().distanceTo(position()));
        return MissileLifecyclePolicy.shouldVirtualise(relevant, loaded);
    }

    private double nearestPlayerDistance(ServerLevel level) {
        double nearest = Double.MAX_VALUE;
        for (Entity player : level.players()) {
            nearest = Math.min(nearest, player.position().distanceTo(position()));
        }
        return level.players().isEmpty() ? Double.NaN : nearest;
    }

    public boolean handOverIfNotTicked(ServerLevel level) {
        if (level.isPositionEntityTicking(blockPosition())) {
            return false;
        }
        virtualise(level, false);
        return true;
    }

    private void virtualise(ServerLevel level, boolean flown) {
        AircraftTargetSnapshot target = targetSnapshot();
        PlaneEntity launcher = resolveLauncher();
        VirtualMissileState state = new VirtualMissileState(getUUID(), profile,
            level.dimension(), position(), getDeltaMovement(), bodyAxis(),
            targetUuid, ownerUuid,
            sourcePlaneUuid != null ? sourcePlaneUuid
                : launcher == null ? null : launcher.getUUID(), age(), travelled,
            guidanceLost,
            target == null ? null : target.position(),
            target == null ? null : target.velocity());
        state.ownerName = ownerNameAtLaunch;
        state.seeker = seeker.copy();
        state.lastLoadG = lastLoadG;
        System.arraycopy(angularVelocity, 0, state.angularVelocity, 0, 3);
        state.movePending = flown;
        VirtualMissileService.adopt(state);
        discard();
    }

    public static void realise(ServerLevel level, VirtualMissileState state) {
        MissileEntity missile = new MissileEntity(AmracEntities.MISSILE,
            level);
        missile.setUUID(state.id);
        missile.profile = state.profile;
        missile.setPos(state.position.x, state.position.y, state.position.z);
        missile.xo = state.position.x;
        missile.yo = state.position.y;
        missile.zo = state.position.z;
        missile.setDeltaMovement(state.velocity);
        missile.axis = state.axis;
        missile.travelled = state.travelled;
        missile.lastLoadG = state.lastLoadG;
        System.arraycopy(state.angularVelocity, 0, missile.angularVelocity, 0, 3);
        missile.guidanceLost = state.guidanceLost;
        missile.ownerUuid = state.ownerId;
        missile.ownerNameAtLaunch = state.ownerName;
        missile.seeker = state.seeker.copy();
        missile.sourcePlaneUuid = state.sourcePlaneId;
        missile.restoredAge = state.age;
        missile.targetUuid = state.targetId;
        if (state.targetId != null) {
            Entity target = level.getEntity(state.targetId);
            if (target instanceof PlaneEntity plane) {
                missile.entityData.set(TARGET_ID, plane.getId() + 1);
            }
        }
        if (state.sourcePlaneId != null) {
            Entity launcher = level.getEntity(state.sourcePlaneId);
            if (launcher != null) {
                missile.sourcePlaneId = launcher.getId();
            }
        }
        missile.pointAlongAxis();
        level.addFreshEntity(missile);
    }

    private boolean canHit(Entity entity) {
        return entity.isAlive() && !entity.isSpectator() &&
            !(entity instanceof MissileEntity) &&
            entity.getId() != sourcePlaneId &&
            entity.getRootVehicle().getId() != sourcePlaneId &&
            (entity instanceof PlaneEntity || entity instanceof LivingEntity);
    }

    private void destroyTarget(PlaneEntity plane) {
        Entity owner = resolveOwner();
        plane.hurt(AmracDamage.weaponSource(level(),
                AmracDamage.PLANE_SHOT_DOWN, this,
                owner != null ? owner : this, weaponName(),
                owner != null ? null : ownerName()),
            Float.MAX_VALUE);
    }

    public net.minecraft.network.chat.Component weaponName() {
        return amrac.AmracItems.missileName(profile.id);
    }

    private void detonate(boolean impact) {
        if (level().isClientSide()) {
            discard();
            return;
        }
        if (impact) {
            level().explode(this, getX(), getY(), getZ(),
                profile.explosionPower, Level.ExplosionInteraction.TNT);
        }
        discard();
    }

    private Vec3 bodyAxis() {
        if (axis.lengthSqr() > 1.0E-9D) {
            return axis;
        }
        Vec3 forward = getForward();
        axis = forward.lengthSqr() > 1.0E-9D
            ? forward.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        return axis;
    }

    private void steerAxis(@Nullable Vec3 aim, @Nullable Vec3 aimVelocity) {
        Vec3 current = bodyAxis();
        double[] turned = new double[3];
        if (MissilePolicy.advanceAxis(profile, toArray(getDeltaMovement()),
            toArray(current), aim == null ? null : toArray(aim.subtract(position())),
            aimVelocity == null ? null : toArray(aimVelocity), getY(), age(),
            angularVelocity, turned)) {
            axis = new Vec3(turned[0], turned[1], turned[2]);
        }
        pointAlongAxis();
    }

    /**
     * Turning and its energy cost are computed only in MissilePolicy.alignAndCharge, which live and
     * virtual missiles both call; computing them anywhere else makes a missile fly differently
     * across a handover.
     */
    private Vec3 alignVelocity(Vec3 velocity, @Nullable double[] command) {
        double[] aligned = new double[3];
        double[] load = {lastLoadG};
        if (!MissilePolicy.alignFlightVelocity(profile, toArray(velocity),
            toArray(bodyAxis()), command, getY(), load, age(), aligned)) {
            return velocity;
        }
        lastLoadG = load[0];
        return new Vec3(aligned[0], aligned[1], aligned[2]);
    }

    private void pointAlongAxis() {
        Vec3 direction = bodyAxis();
        double horizontal = Math.sqrt(direction.x * direction.x +
            direction.z * direction.z);
        float yaw = (float) (Math.toDegrees(
            Math.atan2(direction.x, direction.z)) * -1.0D);
        float pitch = (float) Math.toDegrees(
            Math.atan2(direction.y, horizontal));
        if (!pointed) {
            pointed = true;
            yRotO = yaw;
            xRotO = pitch;
        }
        setYRot(yaw);
        setXRot(pitch);
    }

    @Override
    public void recreateFromPacket(
            net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        yRotO = getYRot();
        xRotO = getXRot();
        pointed = true;
    }

    private void ignitionPuff() {
        for (int i = 0; i < 12; i++) {
            Vec3 back = getDeltaMovement().normalize().scale(-0.6D);
            level().addParticle(ParticleTypes.FLAME,
                getX() + back.x, getY() + back.y, getZ() + back.z,
                back.x * 0.4D + random.nextGaussian() * 0.05D,
                back.y * 0.4D + random.nextGaussian() * 0.05D,
                back.z * 0.4D + random.nextGaussian() * 0.05D);
        }
    }

    private void trail() {
        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        int puffs = Math.max(1, Math.min(12, (int) (speed * 2.0D)));
        for (int i = 0; i < puffs; i++) {
            double t = i / (double) puffs;
            double x = getX() - velocity.x * t;
            double y = getY() - velocity.y * t;
            double z = getZ() - velocity.z * t;
            level().addParticle(ParticleTypes.SMOKE, x, y, z,
                random.nextGaussian() * 0.006D,
                random.nextGaussian() * 0.006D + 0.01D,
                random.nextGaussian() * 0.006D);
        }
    }

    public byte getMotorState() {
        return entityData.get(MOTOR);
    }

    public boolean isSeekerActive() {
        return profile.seekerType == amrac.weapons.SeekerType.ARH && seeker.active;
    }

    public int getWarnMode() {
        return entityData.get(WARN_MODE);
    }

    private amrac.weapons.SeekerPolicy.Solution solveSeeker(
            ServerLevel level, @Nullable AircraftTargetSnapshot target) {
        Vec3 targetPosition = target == null ? null : target.position();
        Vec3 targetVelocity = target == null ? null : target.velocity();
        amrac.weapons.SeekerPolicy.Link link = amrac.weapons.SeekerLinks.link(
            level, sourcePlaneUuid, targetPosition, targetVelocity);
        seeker.launcherRange = amrac.weapons.SeekerLinks.launcherRange(
            sourcePlaneUuid, targetPosition);
        amrac.weapons.SeekerPolicy.Solution solution =
            amrac.weapons.SeekerPolicy.solve(profile, seeker, position(),
                bodyAxis(), targetPosition, targetVelocity, link,
                profile.seekerType.radar()
                    ? amrac.weapons.CountermeasureService.chaffNear(level, position())
                    : amrac.weapons.CountermeasureService.flaresNear(level, position()),
                amrac.weapons.SeekerPolicy.seed(getUUID()),
                target != null && target.afterburner());
        if (amrac.trace.BvrTraceRecorder.recording()) {
            amrac.trace.BvrTraceRecorder.sampleMissile(
                new amrac.trace.BvrTraceRecorder.MissileSample(getUUID(),
                    profile, position(), getDeltaMovement(), bodyAxis(), age(),
                    travelled, ownerUuid, targetUuid, sourcePlaneUuid,
                    targetPosition, targetVelocity, link, seeker, solution,
                    Double.NaN, false));
        }
        return solution;
    }

    private static amrac.weapons.SeekerPolicy.Solution clientSolution(
            @Nullable AircraftTargetSnapshot target) {
        return new amrac.weapons.SeekerPolicy.Solution(
            target == null ? null : target.position(),
            target == null ? null : target.velocity(), target != null, false,
            amrac.weapons.SeekerPolicy.Phase.TERMINAL,
            amrac.weapons.SeekerPolicy.Loss.NONE);
    }

    @Nullable
    private AircraftTargetSnapshot targetSnapshot() {
        PlaneEntity live = resolveTarget();
        if (live != null && live.isAlive()) {
            return AircraftTargetSnapshot.of(live);
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        AircraftTargetSnapshot target =
            AircraftTargetSnapshot.of(serverLevel, targetUuid);
        if (target != null && !target.virtual()) {
            PlaneEntity back = AircraftRegistry.liveEntity(targetUuid);
            if (back != null) {
                entityData.set(TARGET_ID, back.getId() + 1);
            }
        }
        return target;
    }

    @Nullable
    private PlaneEntity resolveTarget() {
        int id = entityData.get(TARGET_ID) - 1;
        return id >= 0 && level().getEntity(id) instanceof PlaneEntity plane
            ? plane : null;
    }

    @Nullable
    private PlaneEntity resolveLauncher() {
        return sourcePlaneId >= 0 &&
            level().getEntity(sourcePlaneId) instanceof PlaneEntity plane
            ? plane : null;
    }

    @Nullable
    public UUID ownerId() {
        return ownerUuid;
    }

    @Nullable
    public net.minecraft.network.chat.Component ownerName() {
        Entity owner = resolveOwner();
        if (owner != null) {
            return owner.getDisplayName();
        }
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        String name = amrac.entities.ai.AiPilotService
            .displayName(serverLevel, ownerUuid);
        if (name == null) {
            name = ownerNameAtLaunch;
        }
        return name == null ? null
            : net.minecraft.network.chat.Component.literal(name);
    }

    @Nullable
    private String shooterName() {
        net.minecraft.network.chat.Component name = ownerName();
        return name == null ? null : name.getString();
    }

    public boolean chasing(UUID aircraftId) {
        return aircraftId != null && aircraftId.equals(targetUuid)
            && !seeker.spent;
    }

    @Nullable
    private Entity resolveOwner() {
        if (ownerUuid != null && level() instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(ownerUuid);
        }
        return null;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 1400.0D * 1400.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source,
                              float damage) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("SourcePlane", sourcePlaneId);
        output.putInt("Target", entityData.get(TARGET_ID));
        output.putDouble("Travelled", travelled);
        output.putInt("FlightAge", age());
        output.putDouble("LastLoadG", lastLoadG);
        Vec3 savedAxis = bodyAxis();
        output.putDouble("AxisX", savedAxis.x);
        output.putDouble("AxisY", savedAxis.y);
        output.putDouble("AxisZ", savedAxis.z);
        output.putDouble("AngularVelocityX", angularVelocity[0]);
        output.putDouble("AngularVelocityY", angularVelocity[1]);
        output.putDouble("AngularVelocityZ", angularVelocity[2]);
        output.putString("Profile", profile.id);
        if (ownerUuid != null) {
            output.store("Owner", UUIDUtil.CODEC, ownerUuid);
        }
        if (ownerNameAtLaunch != null) {
            output.putString("OwnerName", ownerNameAtLaunch);
        }
        if (targetUuid != null) {
            output.store("TargetUuid", UUIDUtil.CODEC, targetUuid);
        }
        if (sourcePlaneUuid != null) {
            output.store("SourcePlaneUuid", UUIDUtil.CODEC, sourcePlaneUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        sourcePlaneId = input.getIntOr("SourcePlane", -1);
        entityData.set(TARGET_ID, input.getIntOr("Target", 0));
        travelled = input.getDoubleOr("Travelled", 0.0D);
        restoredAge = Math.max(0, input.getIntOr("FlightAge", 0));
        lastLoadG = finiteSaved(input.getDoubleOr("LastLoadG", 0.0D));
        Vec3 savedAxis = new Vec3(input.getDoubleOr("AxisX", 0.0D),
            input.getDoubleOr("AxisY", 0.0D), input.getDoubleOr("AxisZ", 0.0D));
        axis = isFinite(savedAxis) && savedAxis.lengthSqr() > 1.0E-9D
            ? savedAxis.normalize() : Vec3.ZERO;
        angularVelocity[0] = finiteSaved(input.getDoubleOr("AngularVelocityX", 0.0D));
        angularVelocity[1] = finiteSaved(input.getDoubleOr("AngularVelocityY", 0.0D));
        angularVelocity[2] = finiteSaved(input.getDoubleOr("AngularVelocityZ", 0.0D));
        profile = MissileProfiles.byId(input.getStringOr("Profile", "AIM7"));
        ownerUuid = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        String ownerName = input.getStringOr("OwnerName", "");
        ownerNameAtLaunch = ownerName.isEmpty() ? null : ownerName;
        targetUuid = input.read("TargetUuid", UUIDUtil.CODEC).orElse(null);
        sourcePlaneUuid = input.read("SourcePlaneUuid", UUIDUtil.CODEC).orElse(null);
    }

    public static double[] halfExtents(AircraftTargetSnapshot target) {
        return new double[] {target.width() * 0.5D, target.height() * 0.5D,
            target.width() * 0.5D};
    }

    private static double[] toArray(Vec3 v) {
        return new double[] {v.x, v.y, v.z};
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) &&
            Double.isFinite(v.z) && Double.isFinite(v.lengthSqr());
    }

    private static double finiteSaved(double value) {
        return Double.isFinite(value) ? value : 0.0D;
}
}
