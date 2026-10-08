package amrac.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import amrac.blocks.ConsoleBlockEntity;
import amrac.blocks.ConsoleMenu;
import amrac.display.ChartKind;
import amrac.display.ConsoleLayout;
import amrac.display.ConsoleMode;
import amrac.network.PlaneNetworking;

import java.util.List;
import java.util.Locale;

public class ConsoleScreen extends AbstractContainerScreen<ConsoleMenu> {
    private static final int PANEL = 0xE0101418;
    private static final int EDGE = 0xFF3C4650;
    private static final int WELL = 0xFF20262E;
    private static final int RULE = 0xFF2A323A;
    private static final int TEXT = 0xFFD8E0E8;
    private static final int DIM = 0xFF7C8894;
    private static final int LIVE = 0xFF56D68A;
    private static final int WARN = 0xFFD46A5A;
    private static final int TAB_ON = 0xFF2E6E8E;

    private EditBox nameBox;
    private MultiLineEditBox valueBox;
    private EditBox altitudeBox;
    private EditBox xMinBox;
    private EditBox xMaxBox;
    private EditBox yMinBox;
    private EditBox yMaxBox;
    private static boolean axisPanelOpen;
    private static String keptXMin = "";
    private static String keptXMax = "";
    private static String keptYMin = "";
    private static String keptYMax = "";
    private EditBox targetSpeedBox;
    private Button chartButton;

    private ConsoleMode laidOutFor;
    private ChartKind laidOutChart;

    private ChartKind pendingChart;

    private String syncedAnswer = "";

    private static String keptName = "";
    private static String keptValue = "";
    private static String keptAltitude = "1000";
    private static String keptTarget = "250";

    private int completionIndex;
    private String completionPrefix = "";

    public ConsoleScreen(ConsoleMenu menu, Inventory inventory,
                         Component title) {
        super(menu, inventory, title, ConsoleLayout.WIDTH, ConsoleLayout.HEIGHT);
    }

    private ConsoleBlockEntity desk() {
        return menu.desk();
    }

    private ConsoleMode mode() {
        ConsoleBlockEntity desk = desk();
        return desk == null ? ConsoleMode.MAP : desk.mode();
    }

    private boolean config() {
        return mode() != ConsoleMode.MAP;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        ConsoleMode mode = mode();
        laidOutFor = mode;

        if (pendingChart != null && pendingChart.mode() != mode) {
            pendingChart = null;
        }
        ChartKind chart = currentChart();
        laidOutChart = chart;

        int tabX = x + ConsoleLayout.MARGIN;
        int tabWidth = (ConsoleLayout.WIDTH - 2 * ConsoleLayout.MARGIN
            - 2 * ConsoleLayout.TAB_GAP) / 3;
        for (ConsoleMode value : ConsoleMode.values()) {
            addRenderableWidget(Button.builder(Component.translatable(value.translationKey()),
                    button -> send(PlaneNetworking.ConsoleAction.SET_MODE,
                        value.getSerializedName(), "", 0.0D, 0.0D))
                .bounds(tabX, y + ConsoleLayout.TABS_Y, tabWidth,
                    ConsoleLayout.TAB_HEIGHT).build());
            tabX += tabWidth + ConsoleLayout.TAB_GAP;
        }

        addRenderableWidget(Button.builder(Component.translatable("amrac.gui.console.connect"),
                button -> send(PlaneNetworking.ConsoleAction.CONNECT,
                    "", "", 0.0D, 0.0D))
            .bounds(x + ConsoleLayout.CONNECT_X, y + ConsoleLayout.CONNECT_Y,
                ConsoleLayout.CONNECT_WIDTH, ConsoleLayout.CONNECT_HEIGHT)
            .build());

        if (config()) {
            buildEditor(x, y);
            buildPlot(x, y, chart);
        } else {
            addRenderableWidget(Button.builder(Component.translatable("amrac.gui.console.draw"),
                    button -> send(PlaneNetworking.ConsoleAction.DRAW,
                        "", "", 0.0D, 0.0D))
                .bounds(x + ConsoleLayout.DRAW_X, y + ConsoleLayout.CHART_BUTTON_Y,
                    ConsoleLayout.DRAW_WIDTH, ConsoleLayout.BUTTON_HEIGHT)
                .build());
        }

        syncFromDesk(true);
    }

    private void buildEditor(int x, int y) {
        nameBox = new EditBox(font, x + ConsoleLayout.PARAM_BOX_X,
            y + ConsoleLayout.PARAM_BOX_Y, ConsoleLayout.PARAM_BOX_WIDTH,
            ConsoleLayout.BOX_HEIGHT, Component.translatable("amrac.gui.console.parameter"));
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.translatable("amrac.gui.console.parameter_hint"));
        nameBox.setValue(keptName);
        nameBox.setResponder(text -> {
            keptName = text;
            completionPrefix = text;
            completionIndex = 0;
        });
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("amrac.gui.console.enter"),
                button -> send(PlaneNetworking.ConsoleAction.ENTER,
                    nameBox.getValue(), "", 0.0D, 0.0D))
            .bounds(x + ConsoleLayout.ENTER_X, y + ConsoleLayout.ENTER_Y,
                ConsoleLayout.ENTER_WIDTH, ConsoleLayout.BUTTON_HEIGHT).build());

        valueBox = MultiLineEditBox.builder()
            .setX(x + ConsoleLayout.VALUE_BOX_X)
            .setY(y + ConsoleLayout.VALUE_BOX_Y)
            .setPlaceholder(Component.translatable("amrac.gui.console.value_hint"))
            .build(font, ConsoleLayout.VALUE_BOX_WIDTH,
                ConsoleLayout.VALUE_BOX_HEIGHT, Component.translatable("amrac.gui.console.value"));
        valueBox.setCharacterLimit(4096);
        valueBox.setValue(keptValue);
        valueBox.setValueListener(text -> keptValue = text);
        addRenderableWidget(valueBox);

        addRenderableWidget(Button.builder(Component.translatable("amrac.gui.console.apply"),
                button -> send(PlaneNetworking.ConsoleAction.APPLY,
                    nameBox.getValue(), valueBox.getValue(), 0.0D, 0.0D))
            .bounds(x + ConsoleLayout.APPLY_X, y + ConsoleLayout.APPLY_Y,
                ConsoleLayout.APPLY_WIDTH, ConsoleLayout.BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("amrac.gui.console.cancel"),
                button -> {
                    valueBox.setValue(currentValue());
                    keptValue = valueBox.getValue();
                })
            .bounds(x + ConsoleLayout.CANCEL_X, y + ConsoleLayout.CANCEL_Y,
                ConsoleLayout.CANCEL_WIDTH, ConsoleLayout.BUTTON_HEIGHT).build());
    }

    private void buildPlot(int x, int y, ChartKind chart) {
        chartButton = addRenderableWidget(Button.builder(
                Component.translatable(chart.translationKey()), button -> cycleChart())
            .bounds(x + ConsoleLayout.CHART_BUTTON_X,
                y + ConsoleLayout.CHART_BUTTON_Y,
                ConsoleLayout.CHART_BUTTON_WIDTH, ConsoleLayout.BUTTON_HEIGHT)
            .build());

        addRenderableWidget(Button.builder(
                Component.translatable(axisPanelOpen
                    ? "amrac.gui.console.data" : "amrac.gui.console.axis"),
                button -> {
                    axisPanelOpen = !axisPanelOpen;
                    rebuildWidgets();
                })
            .bounds(x + ConsoleLayout.AXIS_BUTTON_X,
                y + ConsoleLayout.AXIS_BUTTON_Y,
                ConsoleLayout.AXIS_BUTTON_WIDTH, ConsoleLayout.BUTTON_HEIGHT)
            .build());

        if (axisPanelOpen) {
            xMinBox = axisBox(x, y, ConsoleLayout.AXIS_MIN_X,
                ConsoleLayout.AXIS_X_ROW_Y, "amrac.gui.console.x_min", keptXMin,
                text -> keptXMin = text);
            xMaxBox = axisBox(x, y, ConsoleLayout.AXIS_MAX_X,
                ConsoleLayout.AXIS_X_ROW_Y, "amrac.gui.console.x_max", keptXMax,
                text -> keptXMax = text);
            yMinBox = axisBox(x, y, ConsoleLayout.AXIS_MIN_X,
                ConsoleLayout.AXIS_Y_ROW_Y, "amrac.gui.console.y_min", keptYMin,
                text -> keptYMin = text);
            yMaxBox = axisBox(x, y, ConsoleLayout.AXIS_MAX_X,
                ConsoleLayout.AXIS_Y_ROW_Y, "amrac.gui.console.y_max", keptYMax,
                text -> keptYMax = text);
            altitudeBox = null;
            targetSpeedBox = null;
            addRenderableWidget(Button.builder(Component.translatable("amrac.gui.console.draw"),
                    button -> send(PlaneNetworking.ConsoleAction.DRAW,
                        currentChart().getSerializedName(), "",
                        parse(keptAltitude, 1000.0D), parse(keptTarget, 250.0D)))
                .bounds(x + ConsoleLayout.DRAW_X, y + ConsoleLayout.DRAW_Y,
                    ConsoleLayout.DRAW_WIDTH, ConsoleLayout.BUTTON_HEIGHT)
                .build());
            return;
        }

        if (chart.takesAltitude()) {
            altitudeBox = new EditBox(font, x + ConsoleLayout.ALTITUDE_BOX_X,
                y + ConsoleLayout.ALTITUDE_BOX_Y, ConsoleLayout.NUMBER_BOX_WIDTH,
                ConsoleLayout.BOX_HEIGHT, Component.translatable("amrac.gui.console.altitude"));
            altitudeBox.setValue(keptAltitude);
            altitudeBox.setResponder(text -> keptAltitude = text);
            addRenderableWidget(altitudeBox);
        } else {
            altitudeBox = null;
        }

        if (chart.takesTarget() || chart.takesLaunchSpeed()) {
            targetSpeedBox = new EditBox(font, x + ConsoleLayout.TARGET_BOX_X,
                y + ConsoleLayout.TARGET_BOX_Y, ConsoleLayout.NUMBER_BOX_WIDTH,
                ConsoleLayout.BOX_HEIGHT, Component.translatable("amrac.gui.console.target"));
            targetSpeedBox.setValue(keptTarget);
            targetSpeedBox.setResponder(text -> keptTarget = text);
            addRenderableWidget(targetSpeedBox);
        } else {
            targetSpeedBox = null;
        }

        addRenderableWidget(Button.builder(Component.translatable("amrac.gui.console.draw"),
                button -> send(PlaneNetworking.ConsoleAction.DRAW,
                    currentChart().getSerializedName(), "",
                    parse(keptAltitude, 1000.0D), parse(keptTarget, 250.0D)))
            .bounds(x + ConsoleLayout.DRAW_X, y + ConsoleLayout.DRAW_Y,
                ConsoleLayout.DRAW_WIDTH, ConsoleLayout.BUTTON_HEIGHT).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (mode() != laidOutFor || currentChart() != laidOutChart) {
            rebuildWidgets();
            return;
        }
        syncFromDesk(false);
    }

    private static final String SEPARATOR = "\u0000";

    private void syncFromDesk(boolean initial) {
        ConsoleBlockEntity desk = desk();
        if (desk == null || valueBox == null) {
            return;
        }
        String answer = desk.parameterName() + SEPARATOR + desk.parameterValue();
        if (initial && syncedAnswer.isEmpty()) {
            syncedAnswer = answer;
            if (!desk.parameterName().isEmpty()) {
                keptName = desk.parameterName();
                keptValue = desk.parameterValue();
                nameBox.setValue(keptName);
                valueBox.setValue(keptValue);
            }
            return;
        }
        if (!answer.equals(syncedAnswer)) {
            syncedAnswer = answer;
            keptName = desk.parameterName();
            keptValue = desk.parameterValue();
            if (!nameBox.isFocused()) {
                nameBox.setValue(keptName);
            }
            valueBox.setValue(keptValue);
        }
    }

    private String currentValue() {
        ConsoleBlockEntity desk = desk();
        return desk == null ? "" : desk.parameterValue();
    }

    private ChartKind currentChart() {
        if (pendingChart != null && pendingChart.mode() == mode()) {
            return pendingChart;
        }
        ConsoleBlockEntity desk = desk();
        ChartKind[] offered = ChartKind.forMode(mode());
        if (desk == null) {
            return offered.length > 0 ? offered[0] : ChartKind.TURN_RATE_VS_SPEED;
        }
        ChartKind held = desk.chartKind();
        if (held.mode() == desk.mode()) {
            return held;
        }
        return offered.length > 0 ? offered[0] : ChartKind.TURN_RATE_VS_SPEED;
    }

    private void cycleChart() {
        ChartKind[] offered = ChartKind.forMode(mode());
        if (offered.length == 0) {
            return;
        }
        ChartKind held = currentChart();
        int at = 0;
        for (int i = 0; i < offered.length; i++) {
            if (offered[i] == held) {
                at = i;
                break;
            }
        }
        pendingChart = offered[(at + 1) % offered.length];
        if (chartButton != null) {
            chartButton.setMessage(Component.translatable(pendingChart.translationKey()));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB && nameBox != null
                && nameBox.isFocused()) {
            complete();
            return true;
        }
        if ((event.key() == GLFW.GLFW_KEY_ENTER
                || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && nameBox != null && nameBox.isFocused()) {
            send(PlaneNetworking.ConsoleAction.ENTER, nameBox.getValue(), "",
                0.0D, 0.0D);
            return true;
        }
        return super.keyPressed(event);
    }

    private void complete() {
        ConsoleBlockEntity desk = desk();
        if (desk == null) {
            return;
        }
        String prefix = completionPrefix.toLowerCase(Locale.ROOT);
        List<String> matches = desk.parameterNames().stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        if (matches.isEmpty()) {
            return;
        }
        String chosen = matches.get(completionIndex % matches.size());
        completionIndex++;
        String remembered = completionPrefix;
        nameBox.setValue(chosen);
        completionPrefix = remembered;
        keptName = chosen;
        nameBox.moveCursorToEnd(false);
    }

    private void send(PlaneNetworking.ConsoleAction action, String text,
                      String value, double first, double second) {
        net.minecraft.core.BlockPos pos = menu.deskPos();
        if (pos == null) {
            return;
        }
        PlaneNetworking.sendConsole(pos, action, text, value, first, second,
            axisBounds());
    }

    private amrac.display.AxisBounds axisBounds() {
        if (xMinBox == null) {
            return amrac.display.AxisBounds.AUTO;
        }
        return new amrac.display.AxisBounds(
            bound(xMinBox), bound(xMaxBox), bound(yMinBox), bound(yMaxBox));
    }

    private static double bound(EditBox box) {
        return box == null ? Double.NaN : parse(box.getValue(), Double.NaN);
    }

    private EditBox axisBox(int x, int y, int boxX, int boxY, String hint,
                            String kept,
                            java.util.function.Consumer<String> keep) {
        EditBox box = new EditBox(font, x + boxX, y + boxY,
            ConsoleLayout.AXIS_BOX_WIDTH, ConsoleLayout.BOX_HEIGHT,
            Component.translatable(hint));
        box.setHint(Component.translatable(hint));
        box.setValue(kept);
        box.setResponder(keep);
        addRenderableWidget(box);
        return box;
    }

    private static double parse(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX,
                                  int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.fill(leftPos, topPos, leftPos + imageWidth,
            topPos + imageHeight, PANEL);
        graphics.outline(leftPos, topPos, imageWidth, imageHeight, EDGE);

        graphics.fill(leftPos + ConsoleLayout.SLOT_WELL_X,
            topPos + ConsoleLayout.SLOT_WELL_Y,
            leftPos + ConsoleLayout.SLOT_WELL_X + ConsoleLayout.SLOT_WELL_SIZE,
            topPos + ConsoleLayout.SLOT_WELL_Y + ConsoleLayout.SLOT_WELL_SIZE,
            WELL);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                well(graphics, ConsoleLayout.INVENTORY_X + column * 18,
                    ConsoleLayout.INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            well(graphics, ConsoleLayout.INVENTORY_X + column * 18,
                ConsoleLayout.HOTBAR_Y);
        }

        graphics.fill(leftPos + ConsoleLayout.MARGIN, topPos + 54,
            leftPos + imageWidth - ConsoleLayout.MARGIN, topPos + 55, RULE);
        if (config()) {
            graphics.fill(leftPos + ConsoleLayout.COLUMN_TWO - 6, topPos + 58,
                leftPos + ConsoleLayout.COLUMN_TWO - 5,
                topPos + ConsoleLayout.STATUS_Y - 4, RULE);
        }

        int tabWidth = (ConsoleLayout.WIDTH - 2 * ConsoleLayout.MARGIN
            - 2 * ConsoleLayout.TAB_GAP) / 3;
        int active = mode().ordinal();
        int tabX = leftPos + ConsoleLayout.MARGIN
            + active * (tabWidth + ConsoleLayout.TAB_GAP);
        graphics.fill(tabX, topPos + ConsoleLayout.TABS_Y
                + ConsoleLayout.TAB_HEIGHT, tabX + tabWidth,
            topPos + ConsoleLayout.TABS_Y + ConsoleLayout.TAB_HEIGHT + 2,
            TAB_ON);
    }

    private void well(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1,
            leftPos + x + 17, topPos + y + 17, WELL);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX,
                                 int mouseY) {
        ConsoleBlockEntity desk = desk();

        graphics.text(font, Component.translatable("amrac.gui.console.title",
                Component.translatable(mode().translationKey())),
            ConsoleLayout.MARGIN, ConsoleLayout.TITLE_Y, TEXT);

        Component screen = desk == null || desk.screenPos() == null
            ? Component.translatable("amrac.console.status.no_screen")
            : Component.translatable("amrac.gui.console.screen_at",
                desk.screenPos().getX(), desk.screenPos().getY(),
                desk.screenPos().getZ());
        graphics.text(font, screen,
            ConsoleLayout.SCREEN_TEXT_X, ConsoleLayout.SCREEN_TEXT_Y,
            desk == null || desk.screenPos() == null ? WARN : LIVE);

        Component loaded = menu.slots.get(0).hasItem()
            ? menu.slots.get(0).getItem().getHoverName()
            : Component.translatable("amrac.gui.console.slot_empty");
        graphics.text(font, loaded,
            ConsoleLayout.SCREEN_TEXT_X, ConsoleLayout.DOCUMENT_TEXT_Y,
            menu.slots.get(0).hasItem() ? TEXT : DIM);

        if (config()) {
            graphics.text(font, Component.translatable("amrac.gui.console.parameter"),
                ConsoleLayout.MARGIN, ConsoleLayout.PARAM_LABEL_Y, DIM);
            graphics.text(font, Component.translatable("amrac.gui.console.value"),
                ConsoleLayout.MARGIN, ConsoleLayout.VALUE_LABEL_Y, DIM);
            graphics.text(font, Component.translatable("amrac.gui.console.graph"),
                ConsoleLayout.COLUMN_TWO, ConsoleLayout.CHART_LABEL_Y, DIM);
            ChartKind kind = currentChart();
            if (axisPanelOpen) {
                graphics.text(font, Component.translatable("amrac.gui.console.axis_range"),
                    ConsoleLayout.COLUMN_TWO,
                    ConsoleLayout.ALTITUDE_LABEL_Y, DIM);
            } else {
                if (kind.takesAltitude()) {
                    graphics.text(font, Component.translatable("amrac.gui.console.altitude"),
                        ConsoleLayout.ALTITUDE_LABEL_X,
                        ConsoleLayout.ALTITUDE_LABEL_Y, DIM);
                }
                if (kind.takesTarget() || kind.takesLaunchSpeed()) {
                    graphics.text(font, Component.translatable(
                            kind.takesLaunchSpeed() ? "amrac.gui.console.launch_speed"
                                : "amrac.gui.console.target_speed"),
                        ConsoleLayout.TARGET_LABEL_X,
                        ConsoleLayout.TARGET_LABEL_Y, DIM);
                }
            }
        }

        if (desk != null && !desk.status().isEmpty()) {
            graphics.text(font, amrac.display.SyncedText.component(desk.status()),
                ConsoleLayout.STATUS_X, ConsoleLayout.STATUS_Y, TEXT);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
