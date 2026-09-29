package amrac.entities.loader;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import amrac.AmracItems;
import amrac.entities.PlaneEntity;
import amrac.items.MissileItem;

public class MissileLoaderEntity extends Entity implements MenuProvider {
    public static final int CARGO_SLOTS = LoaderPolicy.MAX_STATIONS;

    public static final int STATE_IDLE = 0;
    public static final int STATE_OUTBOUND = 1;
    public static final int STATE_LOADING = 2;
    public static final int STATE_RETURNING = 3;

    private static final EntityDataAccessor<Integer> AIRFRAME =
        SynchedEntityData.defineId(MissileLoaderEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE =
        SynchedEntityData.defineId(MissileLoaderEntity.class,
            EntityDataSerializers.INT);

    private final SimpleContainer cargo = new SimpleContainer(CARGO_SLOTS) {
        @Override
        public boolean stillValid(Player player) {
            return MissileLoaderEntity.this.isAlive()
                && player.distanceToSqr(MissileLoaderEntity.this) <= 64.0D;
        }
    };

    private final InterpolationHandler interpolation =
        new InterpolationHandler(this);

    @Nullable
    private Vec3 home;
    private int targetId;
    private int driveTicks;
    private int loadTicks;
    private int scanTicks;

    public MissileLoaderEntity(EntityType<? extends MissileLoaderEntity> type,
                               Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AIRFRAME, LoaderAirframe.F16.ordinal());
        builder.define(STATE, STATE_IDLE);
    }

    public SimpleContainer cargo() {
        return cargo;
    }

    public LoaderAirframe airframe() {
        return LoaderAirframe.byOrdinal(entityData.get(AIRFRAME));
    }

    public int state() {
        return entityData.get(STATE);
    }

    private void setState(int state) {
        if (state() != state) {
            entityData.set(STATE, state);
        }
    }

    public void setAirframe(LoaderAirframe airframe) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(AIRFRAME, airframe.ordinal());
        for (int slot = airframe.stations(); slot < CARGO_SLOTS; slot++) {
            ItemStack stranded = cargo.removeItemNoUpdate(slot);
            if (!stranded.isEmpty()) {
                spawnAtLocation((ServerLevel) level(), stranded);
            }
        }
        abandon();
    }

    public void cycleAirframe(int direction) {
        setAirframe(airframe().cycle(direction));
    }

    public void rememberHome() {
        home = position();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            interpolation.interpolate();
            return;
        }
        if (home == null) {
            home = position();
        }
        drive();

        Vec3 motion = getDeltaMovement();
        motion = onGround()
            ? new Vec3(motion.x, Math.max(0.0D, motion.y), motion.z)
            : motion.subtract(0.0D, LoaderPolicy.GRAVITY, 0.0D);
        setDeltaMovement(motion);
        move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    private void drive() {
        switch (state()) {
            case STATE_OUTBOUND -> tickOutbound();
            case STATE_LOADING -> tickLoading();
            case STATE_RETURNING -> tickReturning();
            default -> tickIdle();
        }
    }

    private void tickIdle() {
        halt();
        if (++scanTicks < LoaderPolicy.SCAN_INTERVAL_TICKS) {
            return;
        }
        scanTicks = 0;
        PlaneEntity found = findWork();
        if (found != null) {
            targetId = found.getId();
            driveTicks = 0;
            setState(STATE_OUTBOUND);
        }
    }

    private void tickOutbound() {
        PlaneEntity plane = target();
        if (plane == null || !needsLoading(plane) || strayed(plane)
                || ++driveTicks > LoaderPolicy.DRIVE_TIMEOUT_TICKS) {
            abandon();
            return;
        }
        Vec3 to = plane.position();
        if (LoaderPolicy.arrived(horizontalDistanceSqr(to))) {
            halt();
            loadTicks = 0;
            setState(STATE_LOADING);
            return;
        }
        steerTowards(to);
    }

    private void tickLoading() {
        PlaneEntity plane = target();
        if (plane == null || !plane.isAlive() || strayed(plane)
                || !LoaderPolicy.arrived(horizontalDistanceSqr(plane.position()))) {
            abandon();
            return;
        }
        halt();
        faceTowards(plane.position());
        if (++loadTicks < LoaderPolicy.TICKS_PER_ROUND) {
            return;
        }
        loadTicks = 0;
        if (!hangOneRound(plane)) {
            targetId = 0;
            driveTicks = 0;
            setState(STATE_RETURNING);
        }
    }

    private void tickReturning() {
        Vec3 parked = home == null ? position() : home;
        if (LoaderPolicy.home(horizontalDistanceSqr(parked))
                || ++driveTicks > LoaderPolicy.DRIVE_TIMEOUT_TICKS) {
            halt();
            snapTo(parked.x, getY(), parked.z, getYRot(), getXRot());
            setState(STATE_IDLE);
            scanTicks = 0;
            return;
        }
        steerTowards(parked);
    }

    private boolean strayed(PlaneEntity plane) {
        Vec3 parked = home == null ? position() : home;
        return !LoaderPolicy.withinLeash(plane.position().distanceToSqr(parked));
    }

    private void abandon() {
        targetId = 0;
        driveTicks = 0;
        loadTicks = 0;
        setState(STATE_RETURNING);
    }

    private void steerTowards(Vec3 destination) {
        double dx = destination.x - getX();
        double dz = destination.z - getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            halt();
            return;
        }
        setDeltaMovement(dx / length * LoaderPolicy.SPEED, getDeltaMovement().y,
            dz / length * LoaderPolicy.SPEED);
        faceTowards(destination);
    }

    private void faceTowards(Vec3 destination) {
        double dx = destination.x - getX();
        double dz = destination.z - getZ();
        if (dx * dx + dz * dz > 1.0E-6D) {
            setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
            yRotO = getYRot();
        }
    }

    private void halt() {
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
    }

    private double horizontalDistanceSqr(Vec3 to) {
        double dx = to.x - getX();
        double dz = to.z - getZ();
        return dx * dx + dz * dz;
    }

    @Nullable
    private PlaneEntity target() {
        return targetId != 0 && level().getEntity(targetId) instanceof PlaneEntity plane
            && plane.isAlive() ? plane : null;
    }

    @Nullable
    private PlaneEntity findWork() {
        LoaderAirframe airframe = airframe();
        if (!hasAnyRounds()) {
            return null;
        }
        Vec3 from = home == null ? position() : home;
        AABB reach = AABB.ofSize(from, LoaderPolicy.DETECT_RANGE * 2.0D,
            LoaderPolicy.DETECT_RANGE * 2.0D, LoaderPolicy.DETECT_RANGE * 2.0D);
        List<PlaneEntity> planes = level().getEntitiesOfClass(PlaneEntity.class,
            reach, plane -> plane.isAlive() && airframe.matches(plane)
                && LoaderPolicy.withinDetectRange(
                    plane.position().distanceToSqr(from))
                && needsLoading(plane));

        PlaneEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlaneEntity plane : planes) {
            double distance = plane.position().distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = plane;
            }
        }
        return best;
    }

    private boolean hasAnyRounds() {
        for (int slot = 0; slot < CARGO_SLOTS; slot++) {
            if (cargo.getItem(slot).getItem() instanceof MissileItem) {
                return true;
            }
        }
        return false;
    }

    private boolean needsLoading(PlaneEntity plane) {
        return nextStation(plane) >= 0;
    }

    private int nextStation(PlaneEntity plane) {
        if (!plane.hasMissiles()) {
            return -1;
        }
        String[] loadout = plane.getLoadout();
        int stations = Math.min(Math.min(plane.pylonCount(), loadout.length),
            CARGO_SLOTS);
        for (int station = 0; station < stations; station++) {
            if (loadout[station] != null && !loadout[station].isEmpty()) {
                continue;
            }
            if (cargo.getItem(station).getItem() instanceof MissileItem round
                    && plane.canCarry(round.profileId())) {
                return station;
            }
        }
        return -1;
    }

    private boolean hangOneRound(PlaneEntity plane) {
        int station = nextStation(plane);
        if (station < 0) {
            return false;
        }
        ItemStack stack = cargo.getItem(station);
        if (!(stack.getItem() instanceof MissileItem round)) {
            return false;
        }
        String[] loadout = plane.getLoadout();
        loadout[station] = round.profileId();
        plane.setLoadout(loadout);
        cargo.removeItem(station, 1);
        level().playSound(null, getX(), getY(), getZ(),
            SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 0.7F, 1.1F);
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand,
                                      Vec3 hitVec) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(this);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source,
                              float amount) {
        if (isRemoved() || !(source.getEntity() instanceof Player player)) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            spawnAtLocation(level, new ItemStack(AmracItems.MISSILE_LOADER));
        }
        for (ItemStack stack : cargo.removeAllItems()) {
            spawnAtLocation(level, stack);
        }
        discard();
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("item.amrac.missile_loader");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId,
                                            Inventory playerInventory,
                                            Player player) {
        return new MissileLoaderMenu(containerId, playerInventory, cargo, this);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(AIRFRAME, Math.floorMod(
            input.getIntOr("Airframe", LoaderAirframe.F16.ordinal()),
            LoaderAirframe.values().length));
        double hx = input.getDoubleOr("HomeX", Double.NaN);
        double hy = input.getDoubleOr("HomeY", Double.NaN);
        double hz = input.getDoubleOr("HomeZ", Double.NaN);
        home = Double.isNaN(hx) || Double.isNaN(hy) || Double.isNaN(hz)
            ? null : new Vec3(hx, hy, hz);
        entityData.set(STATE, STATE_IDLE);
        targetId = 0;
        ContainerHelper.loadAllItems(input, cargo.getItems());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Airframe", entityData.get(AIRFRAME));
        if (home != null) {
            output.putDouble("HomeX", home.x);
            output.putDouble("HomeY", home.y);
            output.putDouble("HomeZ", home.z);
        }
        ContainerHelper.saveAllItems(output, cargo.getItems());
    }
}
