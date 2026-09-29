package amrac.blocks;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import amrac.AmracItems;
import amrac.AmracMenus;
import amrac.refuel.RefuelPolicy;

public final class RefuelerMenu extends AbstractContainerMenu {
    public static final int FUEL_SLOTS = RefuelerBlockEntity.FUEL_SLOTS;

    public static final int BUTTON_FEWER_BARRELS = 0;
    public static final int BUTTON_MORE_BARRELS = 1;
    public static final int BUTTON_TOGGLE_AUTOMATIC = 2;
    public static final int BUTTON_START = 3;

    public static final int COARSE_STEP = 8;

    private final Container drums;
    @Nullable
    private final RefuelerBlockEntity bowser;

    private final DataSlot barrels = DataSlot.standalone();
    private final DataSlot automatic = DataSlot.standalone();
    private final DataSlot coupled = DataSlot.standalone();
    private final DataSlot elapsed = DataSlot.standalone();

    public RefuelerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(FUEL_SLOTS), null);
    }

    public RefuelerMenu(int containerId, Inventory playerInventory,
                        Container drums, @Nullable RefuelerBlockEntity bowser) {
        super(AmracMenus.REFUELER, containerId);
        checkContainerSize(drums, FUEL_SLOTS);
        this.drums = drums;
        this.bowser = bowser;

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9;
                addSlot(new Slot(drums, index, 8 + column * 18, 74 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.is(AmracItems.AVIATION_FUEL);
                    }
                });
            }
        }
        addStandardInventorySlots(playerInventory, 8, 140);

        addDataSlot(barrels);
        addDataSlot(automatic);
        addDataSlot(coupled);
        addDataSlot(elapsed);
        publish();
    }

    @Override
    public void broadcastChanges() {
        publish();
        super.broadcastChanges();
    }

    private void publish() {
        if (bowser == null) {
            return;
        }
        barrels.set(bowser.barrelsPerRun());
        automatic.set(bowser.automatic() ? 1 : 0);
        coupled.set(bowser.coupled() ? 1 : 0);
        elapsed.set(Math.min(RefuelPolicy.TRANSFER_TICKS, bowser.elapsed()));
    }

    public int barrelsPerRun() {
        return barrels.get();
    }

    public boolean automatic() {
        return automatic.get() != 0;
    }

    public boolean coupled() {
        return coupled.get() != 0;
    }

    public float progress() {
        return coupled() ? Math.min(1.0F,
            elapsed.get() / (float) RefuelPolicy.TRANSFER_TICKS) : 0.0F;
    }

    public int stockedBarrels() {
        int total = 0;
        for (int slot = 0; slot < FUEL_SLOTS; slot++) {
            ItemStack stack = drums.getItem(slot);
            if (stack.is(AmracItems.AVIATION_FUEL)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (bowser == null || !stillValid(player)) {
            return false;
        }
        switch (buttonId) {
            case BUTTON_FEWER_BARRELS -> bowser.adjustBarrels(-1);
            case BUTTON_MORE_BARRELS -> bowser.adjustBarrels(1);
            case BUTTON_FEWER_BARRELS + 100 -> bowser.adjustBarrels(-COARSE_STEP);
            case BUTTON_MORE_BARRELS + 100 -> bowser.adjustBarrels(COARSE_STEP);
            case BUTTON_TOGGLE_AUTOMATIC -> bowser.toggleAutomatic();
            case BUTTON_START -> bowser.requestStart(player);
            default -> {
                return false;
            }
        }
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (slotIndex < FUEL_SLOTS) {
            moved = moveItemStackTo(stack, FUEL_SLOTS, slots.size(), true);
        } else {
            moved = moveItemStackTo(stack, 0, FUEL_SLOTS, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return bowser == null || bowser.stillValid(player);
    }
}
