package amrac.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import amrac.entities.loader.LoaderAirframe;
import amrac.entities.loader.MissileLoaderEntity;
import amrac.entities.loader.MissileLoaderMenu;

public final class MissileLoaderScreen
        extends AbstractContainerScreen<MissileLoaderMenu> {
    private static final int PANEL = 0xE0181B20;
    private static final int EDGE = 0xFF697582;
    private static final int SLOT = 0xFF30363D;
    private static final int SLOT_EDGE = 0xFF65717D;
    private static final int SLOT_OFF = 0xFF23262B;
    private static final int SLOT_OFF_EDGE = 0xFF3A4047;
    private static final int LABEL = 0xFFB9C4CE;
    private static final int TEXT = 0xFFE6EDF3;

    public MissileLoaderScreen(MissileLoaderMenu menu, Inventory inventory,
                               Component title) {
        super(menu, inventory, title, 176, 184);
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = 89;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"),
                button -> sendButton(MissileLoaderMenu.BUTTON_PREVIOUS_AIRFRAME))
            .bounds(leftPos + 8, topPos + 18, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                button -> sendButton(MissileLoaderMenu.BUTTON_NEXT_AIRFRAME))
            .bounds(leftPos + 148, topPos + 18, 20, 20).build());
    }

    private void sendButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                buttonId);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX,
                                  int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.fill(leftPos, topPos, leftPos + imageWidth,
            topPos + imageHeight, PANEL);
        graphics.outline(leftPos, topPos, imageWidth, imageHeight, EDGE);

        int live = menu.airframe().stations();
        for (int station = 0; station < MissileLoaderMenu.CARGO_SLOTS; station++) {
            int sx = leftPos + MissileLoaderMenu.CARGO_X + station * 18 - 1;
            int sy = topPos + MissileLoaderMenu.CARGO_Y - 1;
            boolean on = station < live;
            graphics.fill(sx, sy, sx + 18, sy + 18, on ? SLOT : SLOT_OFF);
            graphics.outline(sx, sy, 18, 18, on ? SLOT_EDGE : SLOT_OFF_EDGE);
        }

        drawSlotGrid(graphics, 8, 100, 3);
        drawSlotGrid(graphics, 8, 158, 1);
    }

    private void drawSlotGrid(GuiGraphicsExtractor graphics, int x, int y,
                              int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                int sx = leftPos + x + column * 18 - 1;
                int sy = topPos + y + row * 18 - 1;
                graphics.fill(sx, sy, sx + 18, sy + 18, SLOT);
                graphics.outline(sx, sy, 18, 18, SLOT_EDGE);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX,
                                 int mouseY) {
        LoaderAirframe airframe = menu.airframe();
        graphics.text(font, title, titleLabelX, titleLabelY, TEXT);
        graphics.centeredText(font, Component.literal(airframe.label()),
            imageWidth / 2, 20, TEXT);
        graphics.centeredText(font, Component.translatable(
                "amrac.loader.stations", airframe.stations()),
            imageWidth / 2, 31, LABEL);
        graphics.centeredText(font, stateLabel(), imageWidth / 2, 42, LABEL);
        graphics.text(font, Component.translatable(
            "amrac.loader.magazines"), 8, 52, LABEL);
        graphics.text(font, playerInventoryTitle, inventoryLabelX,
            inventoryLabelY, LABEL);
    }

    private Component stateLabel() {
        return Component.translatable(switch (menu.truckState()) {
            case MissileLoaderEntity.STATE_OUTBOUND ->
                "amrac.loader.state.outbound";
            case MissileLoaderEntity.STATE_LOADING ->
                "amrac.loader.state.loading";
            case MissileLoaderEntity.STATE_RETURNING ->
                "amrac.loader.state.returning";
            default -> "amrac.loader.state.idle";
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
