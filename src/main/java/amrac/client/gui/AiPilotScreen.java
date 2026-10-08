package amrac.client.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import amrac.entities.ai.AiPilotEntity;
import amrac.entities.ai.AiPilotMenu;
import amrac.entities.ai.AiPilotRank;

public final class AiPilotScreen extends AbstractContainerScreen<AiPilotMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 246;

    private static final int RANK_ROW_Y = 42;
    private static final int MISSION_ROW_Y = 66;
    private static final int RANK_BUTTON_WIDTH = 52;
    private static final int RANK_BUTTON_GAP = 4;

    private final Button[] rankButtons =
        new Button[AiPilotMenu.MAX_RANK_BUTTONS];
    private Button missionToggle;

    public AiPilotScreen(AiPilotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("amrac.gui.ai_pilot"), WIDTH, HEIGHT);
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = 140;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + 18;
        addRenderableWidget(Button.builder(Component.literal("<"),
                button -> sendButton(AiPilotMenu.BUTTON_TEAM_PREVIOUS))
            .bounds(leftPos + 8, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                button -> sendButton(AiPilotMenu.BUTTON_TEAM_NEXT))
            .bounds(leftPos + 148, y, 20, 20).build());

        for (int index = 0; index < rankButtons.length; index++) {
            int slot = index;
            int x = leftPos + 8
                + slot * (RANK_BUTTON_WIDTH + RANK_BUTTON_GAP);
            rankButtons[slot] = addRenderableWidget(Button.builder(
                    Component.literal(""),
                    button -> sendButton(AiPilotMenu.BUTTON_RANK_BASE + slot))
                .bounds(x, topPos + RANK_ROW_Y, RANK_BUTTON_WIDTH, 20).build());
        }

        missionToggle = addRenderableWidget(Button.builder(
                Component.translatable("amrac.gui.ai_pilot.start"),
                button -> sendButton(AiPilotMenu.BUTTON_TOGGLE_MISSION))
            .bounds(leftPos + 38, topPos + MISSION_ROW_Y, 100, 20).build());
        refreshButtons();
    }

    private void sendButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId, buttonId);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    private void refreshButtons() {
        if (rankButtons[0] == null) {
            return;
        }
        List<AiPilotRank> ranks = menu.variant().ranks();
        for (int index = 0; index < rankButtons.length; index++) {
            Button button = rankButtons[index];
            boolean present = index < ranks.size();
            button.visible = present;
            if (!present) {
                button.active = false;
                continue;
            }
            AiPilotRank rank = ranks.get(index);
            button.setMessage(Component.translatable(rank.translationKey()));
            button.active = menu.rank() != rank;
        }
        missionToggle.setMessage(Component.translatable(menu.missionActive()
            ? "amrac.gui.ai_pilot.stop" : "amrac.gui.ai_pilot.start"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX,
                                  int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.fill(leftPos, topPos, leftPos + imageWidth,
            topPos + imageHeight, 0xE0181B20);
        graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFF697582);
        drawSlotGrid(graphics, 8, 100, 2);
        drawSlotGrid(graphics, 8, 152, 3);
        drawSlotGrid(graphics, 8, 210, 1);
    }

    private void drawSlotGrid(GuiGraphicsExtractor graphics, int x, int y,
                              int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                int sx = leftPos + x + column * 18 - 1;
                int sy = topPos + y + row * 18 - 1;
                graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF30363D);
                graphics.outline(sx, sy, 18, 18, 0xFF65717D);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX,
                                 int mouseY) {
        AiPilotEntity pilot = resolvePilot();
        graphics.text(font, pilot == null
                ? Component.translatable("amrac.gui.ai_pilot.default_title")
                : Component.literal(pilot.callsign()),
            titleLabelX, titleLabelY, 0xFFFFFFFF);
        Component team = pilot == null
            ? Component.translatable("amrac.gui.ai_pilot.team_unavailable")
            : pilot.teamName().isBlank()
                ? Component.translatable("amrac.gui.ai_pilot.team_none")
                : Component.literal(pilot.teamName());
        graphics.centeredText(font,
            Component.translatable("amrac.gui.ai_pilot.team", team),
            imageWidth / 2, 24, 0xFFE6EDF3);
        graphics.text(font, Component.translatable("amrac.gui.ai_pilot.supplies"), 8, 90, 0xFFB9C4CE);
        graphics.text(font, Component.translatable("amrac.gui.ai_pilot.player_inventory"),
            inventoryLabelX, inventoryLabelY, 0xFFB9C4CE);
    }

    private AiPilotEntity resolvePilot() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getEntity(menu.pilotId())
            instanceof AiPilotEntity pilot ? pilot : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
