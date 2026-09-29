package amrac.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import amrac.AmracEntities;
import amrac.weapons.BombingPolicy;

import java.util.UUID;

public class ImpactTntEntity extends PrimedTnt {
    private static final int DISPLAY_FUSE_TICKS = 80;

    private static final EntityDataAccessor<Integer> SOURCE_PLANE =
        SynchedEntityData.defineId(ImpactTntEntity.class,
            EntityDataSerializers.INT);

    @Nullable
    private UUID ownerUuid;
    private int ownerNetworkId;

    public ImpactTntEntity(EntityType<? extends PrimedTnt> entityType, Level level) {
        super(entityType, level);
        setFuse(DISPLAY_FUSE_TICKS);
    }

    public ImpactTntEntity(Level level, @Nullable LivingEntity owner,
                           PlaneEntity sourcePlane, Vec3 position, Vec3 velocity) {
        this(AmracEntities.IMPACT_TNT, level);
        setImpactOwner(owner);
        entityData.set(SOURCE_PLANE, sourcePlane.getId() + 1);
        setPos(position.x, position.y, position.z);
        xo = position.x;
        yo = position.y;
        zo = position.z;
        setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SOURCE_PLANE, 0);
    }

    @Override
    public void tick() {
        baseTick();
        if (!isAlive()) {
            return;
        }

        Vec3 motion = getDeltaMovement();
        if (!isFinite(motion)) {
            discard();
            return;
        }
        if (!isNoGravity()) {
            motion = motion.add(0.0D, -BombingPolicy.GRAVITY_PER_TICK, 0.0D);
            setDeltaMovement(motion);
        }

        if (!level().isClientSide()) {
            if (!level().hasChunkAt(BlockPos.containing(position().add(motion)))) {
                discard();
                return;
            }
            HitResult impact = ProjectileUtil.getHitResultOnMoveVector(this,
                this::canImpactEntity);
            if (impact.getType() != HitResult.Type.MISS) {
                Vec3 location = impact.getLocation();
                setPos(location.x, location.y, location.z);
                detonate();
                return;
            }
        }

        move(MoverType.SELF, motion);
        if (!level().isClientSide() &&
            (horizontalCollision || verticalCollision || onGround())) {
            detonate();
            return;
        }

        setDeltaMovement(getDeltaMovement().scale(0.98D));
        updateFluidInteraction();
        if (level().isClientSide()) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5D,
                getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    private boolean canImpactEntity(Entity entity) {
        return entity.isAlive() && !entity.isSpectator() &&
            !(entity instanceof ImpactTntEntity) &&
            !isSourceAircraftOrPassenger(entity);
    }

    private boolean isSourceAircraftOrPassenger(Entity entity) {
        return BombingPolicy.isSourceAircraftOrPassenger(getSourcePlaneId(),
            entity.getId(), entity.getRootVehicle().getId());
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return !isSourceAircraftOrPassenger(entity) && super.canCollideWith(entity);
    }

    @Override
    public void push(Entity entity) {
        if (!isSourceAircraftOrPassenger(entity)) {
            super.push(entity);
        }
    }

    private void detonate() {
        discard();
        level().explode(this, getX(), getY(0.0625D), getZ(),
            BombingPolicy.EXPLOSION_POWER, Level.ExplosionInteraction.TNT);
    }

    public int getSourcePlaneId() {
        return entityData.get(SOURCE_PLANE) - 1;
    }

    private void setImpactOwner(@Nullable LivingEntity owner) {
        if (owner == null) {
            return;
        }
        ownerUuid = owner.getUUID();
        ownerNetworkId = owner.getId();
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        Entity owner = null;
        if (ownerUuid != null && level() instanceof ServerLevel serverLevel) {
            owner = serverLevel.getEntity(ownerUuid);
        } else if (ownerNetworkId != 0) {
            owner = level().getEntity(ownerNetworkId);
        }
        return owner instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SourcePlane", getSourcePlaneId());
        if (ownerUuid != null) {
            output.store("ImpactOwner", UUIDUtil.CODEC, ownerUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(SOURCE_PLANE, input.getIntOr("SourcePlane", -1) + 1);
        ownerUuid = input.read("ImpactOwner", UUIDUtil.CODEC).orElse(null);
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) &&
            Double.isFinite(vector.z()) && Double.isFinite(vector.lengthSqr());
    }
}
