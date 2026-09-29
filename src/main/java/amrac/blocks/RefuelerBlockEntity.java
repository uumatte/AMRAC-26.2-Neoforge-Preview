package amrac.blocks;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import amrac.AmracBlockEntities;
import amrac.AmracItems;
import amrac.AmracMod;
import amrac.entities.PlaneEntity;
import amrac.refuel.RefuelPolicy;

public class RefuelerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FUEL_SLOTS = 27;

    private final SimpleContainer drums = new SimpleContainer(FUEL_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            RefuelerBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return RefuelerBlockEntity.this.stillValid(player);
        }
    };

    private int barrelsPerRun = RefuelPolicy.DEFAULT_BARRELS;
    private boolean automatic;

    private int targetId;
    private int elapsed;
    private int planned;
    private int delivered;
    private int cooldown;
    private int scanTicks;
    private boolean startRequested;
    @Nullable
    private UUID requester;

    public RefuelerBlockEntity(BlockPos pos, BlockState state) {
        super(AmracBlockEntities.REFUELER, pos, state);
    }

    public SimpleContainer drums() {
        return drums;
    }

    public int barrelsPerRun() {
        return barrelsPerRun;
    }

    public boolean automatic() {
        return automatic;
    }

    public boolean coupled() {
        return targetId != 0;
    }

    public int coupledTo() {
        return targetId;
    }

    public int elapsed() {
        return elapsed;
    }

    public void adjustBarrels(int delta) {
        int wanted = RefuelPolicy.clampBarrels(barrelsPerRun + delta);
        if (wanted != barrelsPerRun) {
            barrelsPerRun = wanted;
            setChanged();
        }
    }

    public void toggleAutomatic() {
        automatic = !automatic;
        setChanged();
    }

    public void requestStart(Player player) {
        startRequested = true;
        requester = player.getUUID();
    }

    public int stockedBarrels() {
        int total = 0;
        for (int slot = 0; slot < drums.getContainerSize(); slot++) {
            ItemStack stack = drums.getItem(slot);
            if (stack.is(AmracItems.AVIATION_FUEL)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    public Vec3 nozzle() {
        Direction facing = getBlockState().hasProperty(RefuelerBlock.FACING)
            ? getBlockState().getValue(RefuelerBlock.FACING) : Direction.NORTH;
        return Vec3.atCenterOf(worldPosition)
            .add(facing.getStepX() * 0.55D, 0.35D, facing.getStepZ() * 0.55D);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  RefuelerBlockEntity bowser) {
        if (bowser.cooldown > 0) {
            bowser.cooldown--;
        }
        if (bowser.coupled()) {
            bowser.tickTransfer(level);
            return;
        }
        boolean manual = bowser.startRequested;
        bowser.startRequested = false;
        if (manual) {
            bowser.scanTicks = 0;
            bowser.tryCouple(level, false);
            return;
        }
        if (!bowser.automatic || bowser.cooldown > 0) {
            return;
        }
        if (++bowser.scanTicks < RefuelPolicy.SCAN_INTERVAL_TICKS) {
            return;
        }
        bowser.scanTicks = 0;
        bowser.tryCouple(level, true);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  RefuelerBlockEntity bowser) {
        if (bowser.coupled()) {
            bowser.elapsed++;
        } else {
            bowser.elapsed = 0;
        }
    }

    private void tickTransfer(Level level) {
        PlaneEntity plane = target(level);
        if (plane == null || !plane.isAlive()
                || !RefuelPolicy.inReach(
                    plane.position().distanceToSqr(Vec3.atCenterOf(worldPosition)))) {
            uncouple(level);
            return;
        }
        elapsed++;
        int due = RefuelPolicy.barrelsDueBy(elapsed, planned);
        while (delivered < due) {
            if (!pumpOneBarrel(plane)) {
                break;
            }
            delivered++;
        }
        if (elapsed >= RefuelPolicy.TRANSFER_TICKS || delivered >= planned) {
            uncouple(level);
        }
    }

    private boolean pumpOneBarrel(PlaneEntity plane) {
        int slot = firstDrumSlot();
        if (slot < 0) {
            return false;
        }
        if (!RefuelPolicy.worthPumping(
                plane.getFuelCapacityLitres() - plane.getFuelLitres())) {
            return false;
        }
        double accepted = plane.addFuelLitres(RefuelPolicy.BARREL_LITRES);
        if (accepted <= 0.0D) {
            return false;
        }
        drums.removeItem(slot, 1);
        return true;
    }

    private int firstDrumSlot() {
        for (int slot = 0; slot < drums.getContainerSize(); slot++) {
            if (drums.getItem(slot).is(AmracItems.AVIATION_FUEL)) {
                return slot;
            }
        }
        return -1;
    }

    private void tryCouple(Level level, boolean requireParked) {
        int stocked = stockedBarrels();
        if (stocked <= 0) {
            refuse(level);
            return;
        }
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        AABB reach = AABB.ofSize(centre, RefuelPolicy.REACH * 2.0D,
            RefuelPolicy.REACH * 2.0D, RefuelPolicy.REACH * 2.0D);
        List<PlaneEntity> nearby = level.getEntitiesOfClass(PlaneEntity.class,
            reach, plane -> plane.isAlive() && plane.isFuelMetered()
                && RefuelPolicy.inReach(plane.position().distanceToSqr(centre)));

        PlaneEntity best = null;
        int bestPlanned = 0;
        double bestDistance = Double.MAX_VALUE;
        for (PlaneEntity plane : nearby) {
            if (requireParked && (!plane.onGround() || !RefuelPolicy.parked(
                    plane.getDeltaMovement().x, plane.getDeltaMovement().z))) {
                continue;
            }
            int wanted = RefuelPolicy.plannedBarrels(barrelsPerRun, stocked,
                plane.getFuelCapacityLitres() - plane.getFuelLitres());
            if (wanted <= 0) {
                continue;
            }
            double distance = plane.position().distanceToSqr(centre);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = plane;
                bestPlanned = wanted;
            }
        }
        if (best == null) {
            refuse(level);
            return;
        }
        requester = null;
        targetId = best.getId();
        planned = bestPlanned;
        delivered = 0;
        elapsed = 0;
        push(level);
        level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL,
            SoundSource.BLOCKS, 0.6F, 0.7F);
    }

    private void refuse(Level level) {
        UUID asked = requester;
        requester = null;
        if (asked == null || level.getServer() == null) {
            return;
        }
        Player player = level.getServer().getPlayerList().getPlayer(asked);
        if (player != null) {
            AmracMod.sendOverlay(player, Component.translatable(
                    "amrac.message.refuel_no_target")
                .withStyle(ChatFormatting.RED), true);
        }
    }

    private void uncouple(Level level) {
        if (targetId == 0) {
            return;
        }
        targetId = 0;
        elapsed = 0;
        planned = 0;
        delivered = 0;
        cooldown = RefuelPolicy.COOLDOWN_TICKS;
        push(level);
        level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY,
            SoundSource.BLOCKS, 0.5F, 1.3F);
    }

    @Nullable
    private PlaneEntity target(Level level) {
        return targetId != 0 && level.getEntity(targetId) instanceof PlaneEntity plane
            ? plane : null;
    }

    private void push(Level level) {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
            Block.UPDATE_ALL);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.amrac.refueler");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId,
                                            Inventory playerInventory,
                                            Player player) {
        return new RefuelerMenu(containerId, playerInventory, drums, this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, drums);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        barrelsPerRun = RefuelPolicy.clampBarrels(
            input.getIntOr("Barrels", RefuelPolicy.DEFAULT_BARRELS));
        automatic = input.getBooleanOr("Automatic", false);
        targetId = input.getIntOr("Target", 0);
        planned = input.getIntOr("Planned", 0);
        delivered = input.getIntOr("Delivered", 0);
        ContainerHelper.loadAllItems(input, drums.getItems());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Barrels", barrelsPerRun);
        output.putBoolean("Automatic", automatic);
        output.putInt("Target", targetId);
        output.putInt("Planned", planned);
        output.putInt("Delivered", delivered);
        ContainerHelper.saveAllItems(output, drums.getItems());
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
