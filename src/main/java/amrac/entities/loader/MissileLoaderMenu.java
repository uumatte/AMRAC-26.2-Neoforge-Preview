package amrac.entities.loader;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import amrac.AmracMenus;
import amrac.items.MissileItem;

public final class MissileLoaderMenu extends AbstractContainerMenu {
    public static final int CARGO_SLOTS = MissileLoaderEntity.CARGO_SLOTS;

    public static final int BUTTON_PREVIOUS_AIRFRAME = 0;
    public static final int BUTTON_NEXT_AIRFRAME = 1;

    public static final int CARGO_X = 8;
    public static final int CARGO_Y = 62;

    private final Container cargo;
    @Nullable
    private final MissileLoaderEntity truck;

    private final DataSlot airframe = DataSlot.standalone();
    private final DataSlot state = DataSlot.standalone();

    public MissileLoaderMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CARGO_SLOTS), null);
    }

    public MissileLoaderMenu(int containerId, Inventory playerInventory,
                             Container cargo,
                             @Nullable MissileLoaderEntity truck) {
        super(AmracMenus.MISSILE_LOADER, containerId);
        checkContainerSize(cargo, CARGO_SLOTS);
        this.cargo = cargo;
        this.truck = truck;

        for (int index = 0; index < CARGO_SLOTS; index++) {
            int station = index;
            addSlot(new Slot(cargo, station,
                    CARGO_X + station * 18, CARGO_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof MissileItem;
                }

                @Override
                public boolean isActive() {
                    return station < airframe().stations();
                }
            });
        }
        addStandardInventorySlots(playerInventory, 8, 100);

        addDataSlot(airframe);
        addDataSlot(state);
        publish();
    }

    @Override
    public void broadcastChanges() {
        publish();
        super.broadcastChanges();
    }

    private void publish() {
        if (truck == null) {
            return;
        }
        airframe.set(truck.airframe().ordinal());
        state.set(truck.state());
    }

    public LoaderAirframe airframe() {
        return LoaderAirframe.byOrdinal(airframe.get());
    }

    public int truckState() {
        return state.get();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (truck == null || !stillValid(player)) {
            return false;
        }
        switch (buttonId) {
            case BUTTON_PREVIOUS_AIRFRAME -> truck.cycleAirframe(-1);
            case BUTTON_NEXT_AIRFRAME -> truck.cycleAirframe(1);
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
        if (slotIndex < CARGO_SLOTS) {
            moved = moveItemStackTo(stack, CARGO_SLOTS, slots.size(), true);
        } else {
            moved = moveItemStackTo(stack, 0, airframe().stations(), false);
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
        return truck == null || truck.isAlive()
            && player.distanceToSqr(truck) <= 64.0D;
    }
}
