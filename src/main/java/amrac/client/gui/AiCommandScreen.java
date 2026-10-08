package amrac.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import amrac.AmracItems;
import amrac.blocks.AiCommandBlockEntity;
import amrac.entities.ai.AiCommandLaunchService;
import amrac.entities.ai.AiLaunchCatalog;
import amrac.entities.ai.AiLaunchCheck;
import amrac.entities.ai.AiLaunchOrder;
import amrac.entities.ai.AiPilotRank;
import amrac.network.AiCommandNetworking;
import amrac.physics.aircraft.AircraftPhysicsProfile;
import amrac.physics.aircraft.CountermeasureProfile;
import amrac.physics.aircraft.FlightModelRegistry;
import amrac.weapons.SummonLoadout;

public final class AiCommandScreen extends Screen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 264;
    private static final int LABEL_X = 10;
    private static final int FIELD_X = 82;
    private static final int FIELD_WIDTH = WIDTH - FIELD_X - 10;
    private static final int ROW = 24;
    private static final int STATIONS_PER_ROW = 4;
    private static final int GAP = 4;

    private static final int PANEL = 0xE8181C22;
    private static final int FRAME = 0xFF5B6B7E;
    private static final int LABEL = 0xFFB9C4CE;
    private static final int HINT = 0xFF8C98A4;
    private static final int GOOD = 0xFF7EE08A;
    private static final int BAD = 0xFFFF7A6E;

    private final BlockPos pos;
    private final AiLaunchCatalog catalog;
    private final List<String> teams;

    private AiPilotRank rank;
    private String aircraft;
    private String team;
    private final List<String> stations = new ArrayList<>();
    private String fuel;
    private String heading;
    private String position;

    private boolean rebuild;
    private String focusKey;
    private final Map<String, AbstractWidget> controls = new HashMap<>();
    private int left;
    private int top;

    public AiCommandScreen(BlockPos pos, AiLaunchCatalog catalog,
                           List<String> teams, AiLaunchOrder current) {
        super(Component.translatable("amrac.gui.ai_command.title"));
        this.pos = pos.immutable();
        this.catalog = catalog;
        this.teams = List.copyOf(teams);
        AiLaunchOrder order = current == null ? AiLaunchOrder.DEFAULT : current;
        rank = order.rank();
        aircraft = order.aircraft();
        team = order.team();
        fuel = Integer.toString(order.fuelPercent());
        heading = number(order.heading());
        position = order.position();
        try {
            for (String station : SummonLoadout.parse(order.loadout()).stations()) {
                stations.add(station == null ? "" : station);
            }
        } catch (SummonLoadout.Invalid unreadable) {
            stations.clear();
        }
        fitStations(false);
    }

    private void fitStations(boolean emptyUncarried) {
        AiLaunchCatalog.Airframe airframe = catalog.airframe(aircraft);
        if (airframe == null) {
            return;
        }
        int count = airframe.hasMissiles() ? airframe.pylons() : 0;
        while (stations.size() > count) {
            stations.remove(stations.size() - 1);
        }
        while (stations.size() < count) {
            stations.add("");
        }
        if (emptyUncarried) {
            stations.replaceAll(id -> id.isEmpty() || airframe.carries(id) ? id : "");
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        controls.clear();

        control("rank", CycleButton.builder(
                (AiPilotRank value) -> Component.translatable(value.translationKey()), rank)
            .withValues(AiPilotRank.values()).displayOnlyValue()
            .create(left + FIELD_X, rowY(0), FIELD_WIDTH, 20,
                Component.translatable("amrac.gui.ai_command.rank"),
                (button, value) -> {
                    rank = value;
                    changed("rank");
                }));

        control("aircraft", CycleButton.builder(this::aircraftName, aircraft)
            .withValues(aircraftChoices()).displayOnlyValue()
            .create(left + FIELD_X, rowY(1), FIELD_WIDTH, 20,
                Component.translatable("amrac.gui.ai_command.aircraft"),
                (button, value) -> {
                    aircraft = value;
                    fitStations(true);
                    changed("aircraft");
                }));

        List<String> teamValues = new ArrayList<>();
        teamValues.add("");
        teamValues.addAll(teams);
        if (!teamValues.contains(team)) {
            teamValues.add(team);
        }
        control("team", CycleButton.builder(
                (String value) -> value.isEmpty()
                    ? Component.translatable("amrac.gui.ai_command.team_none")
                    : Component.literal(value), team)
            .withValues(teamValues).displayOnlyValue()
            .create(left + FIELD_X, rowY(2), FIELD_WIDTH, 20,
                Component.translatable("amrac.gui.ai_command.team"),
                (button, value) -> team = value));

        box(left + FIELD_X, rowY(3), 40, fuel, 3, "amrac.gui.ai_command.fuel",
            text -> fuel = text);
        box(left + FIELD_X + 124, rowY(3), 44, heading, 8,
            "amrac.gui.ai_command.heading", text -> heading = text);
        box(left + FIELD_X, rowY(4), FIELD_WIDTH, position,
            AiLaunchOrder.MAX_TEXT, "amrac.gui.ai_command.position",
            text -> position = text).setHint(Component.literal("~ ~1 ~"));

        AiLaunchCatalog.Airframe airframe = catalog.airframe(aircraft);
        if (airframe != null && airframe.hasMissiles()) {
            int buttonWidth = (FIELD_WIDTH - GAP * (STATIONS_PER_ROW - 1))
                / STATIONS_PER_ROW;
            for (int i = 0; i < stations.size(); i++) {
                station(airframe, i,
                    left + FIELD_X + (i % STATIONS_PER_ROW) * (buttonWidth + GAP),
                    rowY(6 + i / STATIONS_PER_ROW), buttonWidth);
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
                button -> done())
            .bounds(left + WIDTH / 2 - 104, top + HEIGHT - 26, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,
                button -> onClose())
            .bounds(left + WIDTH / 2 + 4, top + HEIGHT - 26, 100, 20).build());
    }

    private int rowY(int row) {
        return top + 20 + ROW * row;
    }

    private <T extends AbstractWidget> T control(String key, T widget) {
        controls.put(key, widget);
        return addRenderableWidget(widget);
    }

    private EditBox box(int x, int y, int w, String value, int maxLength,
                        String narration, java.util.function.Consumer<String> into) {
        EditBox box = new EditBox(font, x, y, w, 20,
            Component.translatable(narration));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setResponder(into);
        return addRenderableWidget(box);
    }

    private void station(AiLaunchCatalog.Airframe airframe, int index,
                         int x, int y, int w) {
        String current = stations.get(index);
        List<String> values = new ArrayList<>();
        values.add("");
        values.addAll(airframe.missiles());
        if (!values.contains(current)) {
            values.add(current);
        }
        control("station" + index, CycleButton.builder(
                (String id) -> storeName(airframe, id), current)
            .withValues(values).displayOnlyValue()
            .withTooltip(id -> id.isEmpty() ? null
                : Tooltip.create(AmracItems.missileName(id)))
            .create(x, y, w, 20,
                Component.translatable("amrac.gui.ai_command.station", index + 1),
                (button, value) -> {
                    stations.set(index, value);
                    changed("station" + index);
                }));
    }

    private List<String> aircraftChoices() {
        List<String> names = new ArrayList<>();
        for (AiLaunchCatalog.Airframe airframe : catalog.airframes()) {
            if (catalog.cleared(rank, airframe)) {
                names.add(airframe.name());
            }
        }
        if (!names.contains(aircraft)) {
            names.add(0, aircraft);
        }
        return names;
    }

    private Component aircraftName(String name) {
        MutableComponent label = Component.translatable("item.amrac." + name);
        return catalog.cleared(rank, catalog.airframe(name)) ? label
            : label.withStyle(ChatFormatting.RED);
    }

    private static Component storeName(AiLaunchCatalog.Airframe airframe,
                                       String id) {
        if (id.isEmpty()) {
            return Component.translatable("amrac.gui.ai_command.station_empty");
        }
        Component name = AmracItems.missileShortName(id);
        return airframe.carries(id) ? name
            : name.copy().withStyle(ChatFormatting.RED);
    }

    private void changed(String key) {
        focusKey = key;
        rebuild = true;
    }

    @Override
    public void tick() {
        if (rebuild) {
            rebuild = false;
            rebuildWidgets();
            AbstractWidget focus = controls.get(focusKey);
            if (focus != null) {
                setFocused(focus);
            }
        }
    }

    private static CountermeasureProfile standardLoad(
            AiLaunchCatalog.Airframe airframe) {
        AircraftPhysicsProfile profile = FlightModelRegistry.instance()
            .profile(airframe.flightModelId());
        return profile != null ? profile.countermeasures()
            : CountermeasureProfile.of(airframe.chaff() + airframe.flare(),
                airframe.chaff(), airframe.flare());
    }

    private static String number(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value)
            : String.format(Locale.ROOT, "%.1f", value);
    }

    private float headingDegrees() {
        try {
            return Float.parseFloat(heading.trim());
        } catch (NumberFormatException notANumber) {
            return Float.NaN;
        }
    }

    private AiLaunchOrder draft() {
        int percent;
        try {
            percent = Integer.parseInt(fuel.trim());
        } catch (NumberFormatException notANumber) {
            percent = -1;
        }
        return new AiLaunchOrder(aircraft, rank, team, percent,
            SummonLoadout.write(stations), position, headingDegrees());
    }

    private Component problem() {
        if (!Float.isFinite(headingDegrees())) {
            return Component.translatable("amrac.ai_command.bad_heading");
        }
        AiLaunchOrder order = draft();
        AiLaunchCheck.Problem problem = AiLaunchCheck.problem(order, catalog,
            teams::contains, AiCommandLaunchService.parses(order.position()));
        return problem == null ? null : AiCommandLaunchService.message(problem);
    }

    private void done() {
        AiCommandNetworking.send(pos, draft());
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER
                || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            done();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX,
                                  int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.outline(left, top, WIDTH, HEIGHT, FRAME);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                   int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, left + WIDTH / 2, top + 7, 0xFFFFFFFF);
        label(graphics, "amrac.gui.ai_command.rank", 0);
        label(graphics, "amrac.gui.ai_command.aircraft", 1);
        label(graphics, "amrac.gui.ai_command.team", 2);
        label(graphics, "amrac.gui.ai_command.fuel", 3);
        graphics.text(font, Component.translatable("amrac.gui.ai_command.heading"),
            left + FIELD_X + 52, rowY(3) + 6, LABEL);
        float degrees = headingDegrees();
        if (Float.isFinite(degrees)) {
            graphics.text(font, Component.translatable("amrac.gui.ai_command.facing."
                    + AiLaunchOrder.compass(degrees)),
                left + FIELD_X + 174, rowY(3) + 6, HINT);
        }
        label(graphics, "amrac.gui.ai_command.position", 4);

        AiLaunchCatalog.Airframe airframe = catalog.airframe(aircraft);
        if (airframe != null) {
            CountermeasureProfile load = standardLoad(airframe);
            label(graphics, "amrac.gui.ai_command.decoys", 5);
            graphics.text(font, Component.translatable(
                    "amrac.gui.ai_command.decoys_value", load.chaff(),
                    load.flare(), airframe.flightModelId() + ".json"),
                left + FIELD_X, rowY(5) + 6, HINT);
            label(graphics, "amrac.gui.ai_command.missiles", 6);
            if (!airframe.hasMissiles()) {
                graphics.text(font, Component.translatable(
                        "amrac.ai_command.no_missiles"),
                    left + FIELD_X, rowY(6) + 6, HINT);
            }
        }

        Component problem = problem();
        graphics.text(font, problem == null
                ? Component.translatable("amrac.ai_command.ready") : problem,
            left + LABEL_X, top + HEIGHT - 52, problem == null ? GOOD : BAD);

        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(pos)
                    instanceof AiCommandBlockEntity block
                && !block.lastTime().isEmpty()) {
            Component last = Component.translatable("amrac.gui.ai_command.last",
                "[" + block.lastTime() + "]", block.lastOutput());
            List<FormattedCharSequence> lines = font.split(last,
                WIDTH - LABEL_X * 2);
            if (!lines.isEmpty()) {
                graphics.text(font, lines.get(0), left + LABEL_X,
                    top + HEIGHT - 40, block.lastSucceeded() ? LABEL : BAD);
            }
        }
    }

    private void label(GuiGraphicsExtractor graphics, String key, int row) {
        graphics.text(font, Component.translatable(key), left + LABEL_X,
            rowY(row) + 6, LABEL);
    }
}
