package amrac.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import amrac.AmracMenus;
import amrac.display.ConsoleActions;
import amrac.display.ConsoleLayout;
import amrac.entities.ai.MenuSyncPolicy;

public final class ConsoleMenu extends AbstractContainerMenu {
    private final SimpleContainer contents;
    @Nullable
    private final ConsoleBlockEntity desk;

    private final Level level;

    private final DataSlot present = DataSlot.standalone();
    private final DataSlot deskXHigh = DataSlot.standalone();
    private final DataSlot deskXLow = DataSlot.standalone();
    private final DataSlot deskYHigh = DataSlot.standalone();
    private final DataSlot deskYLow = DataSlot.standalone();
    private final DataSlot deskZHigh = DataSlot.standalone();
    private final DataSlot deskZLow = DataSlot.standalone();

    public ConsoleMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory,
            new SimpleContainer(ConsoleBlockEntity.SLOT_COUNT), null);
    }

    public ConsoleMenu(int containerId, Inventory playerInventory,
                       SimpleContainer contents,
                       @Nullable ConsoleBlockEntity desk) {
        super(AmracMenus.CONSOLE, containerId);
        this.contents = contents;
        this.desk = desk;
        this.level = playerInventory.player.level();

        addSlot(new Slot(contents, 0, ConsoleLayout.SLOT_X, ConsoleLayout.SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ConsoleActions.accepts(stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                    ConsoleLayout.INVENTORY_X + column * 18,
                    ConsoleLayout.INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column,
                ConsoleLayout.INVENTORY_X + column * 18,
                ConsoleLayout.HOTBAR_Y));
        }

        addDataSlot(present);
        addDataSlot(deskXHigh);
        addDataSlot(deskXLow);
        addDataSlot(deskYHigh);
        addDataSlot(deskYLow);
        addDataSlot(deskZHigh);
        addDataSlot(deskZLow);
        publishPosition();
    }

    private void publishPosition() {
        if (desk == null) {
            present.set(0);
            return;
        }
        BlockPos pos = desk.getBlockPos();
        present.set(1);
        deskXHigh.set(MenuSyncPolicy.high(pos.getX()));
        deskXLow.set(MenuSyncPolicy.low(pos.getX()));
        deskYHigh.set(MenuSyncPolicy.high(pos.getY()));
        deskYLow.set(MenuSyncPolicy.low(pos.getY()));
        deskZHigh.set(MenuSyncPolicy.high(pos.getZ()));
        deskZLow.set(MenuSyncPolicy.low(pos.getZ()));
    }

    @Nullable
    public BlockPos deskPos() {
        if (desk != null) {
            return desk.getBlockPos();
        }
        if (present.get() == 0) {
            return null;
        }
        return new BlockPos(
            MenuSyncPolicy.combine(deskXHigh.get(), deskXLow.get()),
            MenuSyncPolicy.combine(deskYHigh.get(), deskYLow.get()),
            MenuSyncPolicy.combine(deskZHigh.get(), deskZLow.get()));
    }

    @Nullable
    public ConsoleBlockEntity desk() {
        if (desk != null) {
            return desk;
        }
        BlockPos pos = deskPos();
        if (pos == null || level == null) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof ConsoleBlockEntity found
            ? found : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (slotIndex == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return desk == null || !desk.isRemoved();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (desk != null && !player.level().isClientSide()) {
            amrac.display.ConsoleService.refreshNames(desk);
        }
    }
}
