package amrac.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import amrac.weapons.GunLeadPolicy;
import amrac.AmracEntities;

public class MachineGunBulletEntity extends ThrowableProjectile {
    public static final float DEFAULT_DAMAGE = 15.0F;
    public static final double MAX_DISTANCE = GunLeadPolicy.MAX_RANGE;
    private static final int MAX_LIFETIME_TICKS = GunLeadPolicy.MAX_LIFE_TICKS;
    private static final float MAX_DAMAGE = 20.0F;

    private static final EntityDataAccessor<Integer> SOURCE_PLANE =
        SynchedEntityData.defineId(MachineGunBulletEntity.class,
            EntityDataSerializers.INT);

    private double relativeDistance;
    private float damage = DEFAULT_DAMAGE;
    private boolean clientPrediction;

    public MachineGunBulletEntity(EntityType<? extends MachineGunBulletEntity> entityType,
                                  Level level) {
        super(entityType, level);
        setNoGravity(true);
    }

    public MachineGunBulletEntity(Level level, LivingEntity owner, PlaneEntity sourcePlane,
                                  Vec3 position, Vec3 velocity, float damage) {
        this(AmracEntities.MACHINE_GUN_BULLET, level);
        setOwner(owner);
        this.damage = sanitizeDamage(damage);
        setPos(position.x, position.y, position.z);
        setDeltaMovement(velocity);
        entityData.set(SOURCE_PLANE, sourcePlane.getId() + 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SOURCE_PLANE, 0);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    public void tick() {
        Vec3 velocity = getDeltaMovement();
        if (!isFinite(velocity)) {
            discard();
            return;
        }

        double leash = GunLeadPolicy.maxRange();
        double remainingDistance = leash - relativeDistance;
        if (remainingDistance <= 0.0D) {
            discard();
            return;
        }

        double perTick = muzzleRelativeSpeed();
        double spent = GunLeadPolicy.leashSpent(perTick, remainingDistance);
        if (spent <= 0.0D) {
            discard();
            return;
        }
        if (spent < perTick) {
            velocity = velocity.scale(spent / perTick);
            setDeltaMovement(velocity);
        }

        Vec3 segmentMotion = getDeltaMovement();
        if (!level().isClientSide() &&
            !level().hasChunkAt(BlockPos.containing(position().add(segmentMotion)))) {
            discard();
            return;
        }

        super.tick();
        if (isAlive()) {
            setDeltaMovement(velocity);
        }
        relativeDistance += spent;
        if (isAlive() && (relativeDistance >= leash ||
            tickCount >= MAX_LIFETIME_TICKS)) {
            discard();
        }
    }

    private double muzzleRelativeSpeed() {
        double stated = getSourcePlane() instanceof PlaneEntity plane
            ? plane.getMachineGunMuzzleVelocity()
            : amrac.upgrades.shooter.MachineGunFirePolicy.BIPLANE_PROJECTILE_SPEED;
        return amrac.upgrades.shooter.MachineGunFirePolicy.muzzleSpeed(stated);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity sourcePlane = getSourcePlane();
        if (entity instanceof MachineGunBulletEntity || entity == sourcePlane ||
            (sourcePlane != null && entity.getRootVehicle() == sourcePlane)) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide()) {
            return;
        }
        Entity target = result.getEntity();
        if (target instanceof PlaneEntity plane) {
            Vec3 segmentStart = position();
            Player passengerTarget = plane.findPassengerHit(segmentStart,
                segmentStart.add(getDeltaMovement()), 0.30D);
            if (passengerTarget != null) {
                target = passengerTarget;
            }
        }
        int previousInvulnerableTime = target.invulnerableTime;
        target.invulnerableTime = 0;
        target.hurt(damageSources().thrown(this, getOwner()), damage);
        target.invulnerableTime = previousInvulnerableTime;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide()) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 128.0D * 128.0D;
    }

    public void markAsClientPrediction() {
        if (level().isClientSide()) {
            clientPrediction = true;
        }
    }

    public boolean isClientPrediction() {
        return clientPrediction;
    }

    public void setSourcePlaneId(int planeId) {
        entityData.set(SOURCE_PLANE, planeId + 1);
    }

    public int getSourcePlaneId() {
        return entityData.get(SOURCE_PLANE) - 1;
    }

    private Entity getSourcePlane() {
        int id = getSourcePlaneId();
        return id < 0 ? null : level().getEntity(id);
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) &&
            Double.isFinite(vector.z()) && Double.isFinite(vector.lengthSqr());
    }

    private static float sanitizeDamage(float value) {
        if (!Float.isFinite(value)) {
            return DEFAULT_DAMAGE;
        }
        return Math.max(0.0F, Math.min(value, MAX_DAMAGE));
    }
}
