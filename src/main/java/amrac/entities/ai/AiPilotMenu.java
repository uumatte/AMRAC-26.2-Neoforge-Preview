package amrac.entities.ai;

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

public final class AiPilotMenu extends AbstractContainerMenu {
    public static final int PILOT_SLOTS = AiPilotEntity.INVENTORY_SIZE;
    public static final int BUTTON_TEAM_PREVIOUS = 0;
    public static final int BUTTON_TEAM_NEXT = 1;
    public static final int BUTTON_TOGGLE_MISSION = 2;

    public static final int BUTTON_RANK_BASE = 3;

    public static final int MAX_RANK_BUTTONS = 3;

    private final Container pilotInventory;
    @Nullable
    private final AiPilotEntity pilot;
    private final DataSlot pilotIdHigh = DataSlot.standalone();
    private final DataSlot pilotIdLow = DataSlot.standalone();
    private final DataSlot variant = DataSlot.standalone();
    private final DataSlot rank = DataSlot.standalone();
    private final DataSlot mission = DataSlot.standalone();

    public AiPilotMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(PILOT_SLOTS), null);
    }

    public AiPilotMenu(int containerId, Inventory playerInventory,
                       Container pilotInventory, @Nullable AiPilotEntity pilot) {
        super(AmracMenus.AI_PILOT, containerId);
        checkContainerSize(pilotInventory, PILOT_SLOTS);
        this.pilotInventory = pilotInventory;
        this.pilot = pilot;

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                addSlot(new Slot(pilotInventory, slot,
                    8 + column * 18, 100 + row * 18));
            }
        }
        addStandardInventorySlots(playerInventory, 8, 152);

        addDataSlot(pilotIdHigh);
        addDataSlot(pilotIdLow);
        addDataSlot(variant);
        addDataSlot(rank);
        addDataSlot(mission);
        publish();
    }

    @Override
    public void broadcastChanges() {
        publish();
        super.broadcastChanges();
    }

    private void publish() {
        if (pilot == null) {
            return;
        }
        pilotIdHigh.set(MenuSyncPolicy.high(pilot.getId()));
        pilotIdLow.set(MenuSyncPolicy.low(pilot.getId()));
        variant.set(pilot.variant().ordinal());
        rank.set(pilot.rank().ordinal());
        mission.set(pilot.isMissionActive() ? 1 : 0);
    }

    public int pilotId() {
        return MenuSyncPolicy.combine(pilotIdHigh.get(), pilotIdLow.get());
    }

    public AiPilotVariant variant() {
        return AiPilotVariant.byOrdinal(variant.get());
    }

    public AiPilotRank rank() {
        return AiPilotRank.byOrdinal(rank.get());
    }

    public boolean missionActive() {
        return mission.get() != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (pilot == null || !stillValid(player)) {
            return false;
        }
        switch (buttonId) {
            case BUTTON_TEAM_PREVIOUS -> pilot.cycleTeam(-1);
            case BUTTON_TEAM_NEXT -> pilot.cycleTeam(1);
            case BUTTON_TOGGLE_MISSION -> {
                if (pilot.isMissionActive()) {
                    pilot.stopMission();
                } else {
                    pilot.startMission();
                }
            }
            default -> {
                int index = buttonId - BUTTON_RANK_BASE;
                var ranks = pilot.variant().ranks();
                if (index < 0 || index >= ranks.size()) {
                    return false;
                }
                pilot.setRank(ranks.get(index));
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
        if (slotIndex < PILOT_SLOTS) {
            moved = moveItemStackTo(stack, PILOT_SLOTS, slots.size(), true);
        } else {
            moved = moveItemStackTo(stack, 0, PILOT_SLOTS, false);
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
        return pilot == null || pilot.isAlive()
            && player.distanceToSqr(pilot) <= 64.0D;
    }
}
