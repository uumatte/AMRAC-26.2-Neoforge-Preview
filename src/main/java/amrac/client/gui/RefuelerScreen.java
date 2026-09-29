package amrac.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import amrac.blocks.RefuelerMenu;
import amrac.refuel.RefuelPolicy;

public final class RefuelerScreen extends AbstractContainerScreen<RefuelerMenu> {
    private static final int PANEL = 0xE0181B20;
    private static final int EDGE = 0xFF697582;
    private static final int SLOT = 0xFF30363D;
    private static final int SLOT_EDGE = 0xFF65717D;
    private static final int LABEL = 0xFFB9C4CE;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int BAR_BACK = 0xFF23282E;
    private static final int BAR_FILL = 0xFFCEA62C;

    private Button fewer;
    private Button more;
    private Button automatic;
    private Button start;

    public RefuelerScreen(RefuelerMenu menu, Inventory inventory,
                          Component title) {
        super(menu, inventory, title, 176, 224);
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = 129;
    }

    @Override
    protected void init() {
        super.init();
        fewer = addRenderableWidget(Button.builder(Component.literal("-"),
                button -> sendButton(RefuelerMenu.BUTTON_FEWER_BARRELS))
            .bounds(leftPos + 8, topPos + 18, 20, 20).build());
        more = addRenderableWidget(Button.builder(Component.literal("+"),
                button -> sendButton(RefuelerMenu.BUTTON_MORE_BARRELS))
            .bounds(leftPos + 60, topPos + 18, 20, 20).build());
        automatic = addRenderableWidget(Button.builder(Component.empty(),
                button -> sendButton(RefuelerMenu.BUTTON_TOGGLE_AUTOMATIC))
            .bounds(leftPos + 86, topPos + 18, 82, 20).build());
        start = addRenderableWidget(Button.builder(Component.empty(),
                button -> sendButton(RefuelerMenu.BUTTON_START))
            .bounds(leftPos + 8, topPos + 41, 96, 20).build());
        refresh();
    }

    private void sendButton(int buttonId) {
        if (minecraft == null || minecraft.gameMode == null) {
            return;
        }
        boolean coarse = minecraft.hasShiftDown()
            && (buttonId == RefuelerMenu.BUTTON_FEWER_BARRELS
                || buttonId == RefuelerMenu.BUTTON_MORE_BARRELS);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
            coarse ? buttonId + 100 : buttonId);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        if (start == null) {
            return;
        }
        automatic.setMessage(Component.translatable(menu.automatic()
            ? "amrac.refueler.auto_on"
            : "amrac.refueler.auto_off"));
        boolean busy = menu.coupled();
        start.setMessage(Component.translatable(busy
            ? "amrac.refueler.fuelling"
            : "amrac.refueler.start"));
        start.active = !busy && menu.stockedBarrels() > 0;
        fewer.active = menu.barrelsPerRun() > RefuelPolicy.MIN_BARRELS;
        more.active = menu.barrelsPerRun() < RefuelPolicy.MAX_BARRELS;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX,
                                  int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.fill(leftPos, topPos, leftPos + imageWidth,
            topPos + imageHeight, PANEL);
        graphics.outline(leftPos, topPos, imageWidth, imageHeight, EDGE);
        drawSlotGrid(graphics, 8, 74, 3, 9);
        drawSlotGrid(graphics, 8, 140, 3, 9);
        drawSlotGrid(graphics, 8, 198, 1, 9);

        int x0 = leftPos + 110;
        int y0 = topPos + 46;
        graphics.fill(x0, y0, x0 + 58, y0 + 10, BAR_BACK);
        graphics.outline(x0, y0, 58, 10, SLOT_EDGE);
        int filled = Math.round(56 * menu.progress());
        if (filled > 0) {
            graphics.fill(x0 + 1, y0 + 1, x0 + 1 + filled, y0 + 9, BAR_FILL);
        }
    }

    private void drawSlotGrid(GuiGraphicsExtractor graphics, int x, int y,
                              int rows, int columns) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
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
        graphics.text(font, title, titleLabelX, titleLabelY, TEXT);
        graphics.centeredText(font, Component.literal(
            String.valueOf(menu.barrelsPerRun())), 44, 24, TEXT);
        graphics.text(font, Component.translatable(
                "amrac.refueler.stock", menu.stockedBarrels()),
            8, 64, LABEL);
        graphics.text(font, playerInventoryTitle, inventoryLabelX,
            inventoryLabelY, LABEL);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
