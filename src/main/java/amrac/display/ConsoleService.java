package amrac.display;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import amrac.blocks.ConsoleBlockEntity;
import amrac.blocks.ScreenBlockEntity;
import amrac.network.PlaneNetworking;

import java.util.List;

public final class ConsoleService {
    private static final double REACH = 8.0D;

    private ConsoleService() {
    }

    public static void handle(ServerPlayer player, BlockPos pos, int actionId,
                              String text, String value, double first,
                              double second, AxisBounds bounds) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        PlaneNetworking.ConsoleAction[] actions =
            PlaneNetworking.ConsoleAction.values();
        if (actionId < 0 || actionId >= actions.length) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ConsoleBlockEntity part)) {
            return;
        }
        ConsoleBlockEntity desk =
            level.getBlockEntity(part.corePos()) instanceof ConsoleBlockEntity core
                ? core : part;
        if (player.distanceToSqr(desk.getBlockPos().getX() + 0.5D,
                desk.getBlockPos().getY() + 0.5D,
                desk.getBlockPos().getZ() + 0.5D) > REACH * REACH) {
            return;
        }

        switch (actions[actionId]) {
            case SET_MODE -> setMode(desk, ConsoleMode.byName(text));
            case CONNECT -> connect(level, desk);
            case ENTER -> enter(desk, text);
            case APPLY -> {
                if (!net.minecraft.commands.Commands.LEVEL_GAMEMASTERS
                        .check(player.permissions())) {
                    amrac.AmracMod.sendOverlay(player,
                        net.minecraft.network.chat.Component.translatable(
                            "amrac.message.console_op_only"), true);
                    return;
                }
                apply(desk, text, value);
            }
            case DRAW -> draw(level, desk, text, first, second, bounds);
        }
    }

    private static void setMode(ConsoleBlockEntity desk, ConsoleMode mode) {
        desk.setMode(mode);
        ChartKind[] offered = ChartKind.forMode(mode);
        if (offered.length > 0 && desk.chartKind().mode() != mode) {
            desk.setChartKind(offered[0]);
        }
        desk.setParameter("", "", "");
        refreshNames(desk);
    }

    private static void connect(ServerLevel level, ConsoleBlockEntity desk) {
        BlockPos screen = desk.findNearestScreen(level);
        desk.connect(screen);
        desk.setStatus(screen == null
            ? SyncedText.of("amrac.message.console_no_screen")
            : SyncedText.of("amrac.message.console_connected",
                screen.getX(), screen.getY(), screen.getZ()));
    }

    private static void enter(ConsoleBlockEntity desk, String name) {
        refreshNames(desk);
        String id = documentId(desk);
        if (id == null) {
            desk.setParameter("", "", "");
            desk.setStatus(SyncedText.of("amrac.console.status.slot_empty"));
            return;
        }
        String current = ConsoleActions.readParameter(desk.mode(), id, name);
        if (current == null) {
            desk.setParameter(name, "", "");
            desk.setStatus(SyncedText.of("amrac.console.status.no_parameter", name));
            return;
        }
        desk.setParameter(name,
            ConsoleActions.parameterPath(desk.mode(), id, name), current);
        desk.setStatus("");
    }

    private static void apply(ConsoleBlockEntity desk, String name, String value) {
        String id = documentId(desk);
        if (id == null) {
            desk.setStatus(SyncedText.of("amrac.console.status.slot_empty"));
            return;
        }
        if (!ConsoleActions.applyParameter(desk.mode(), id, name, value)) {
            desk.setStatus(SyncedText.of("amrac.console.status.write_failed", name));
            return;
        }
        String current = ConsoleActions.readParameter(desk.mode(), id, name);
        desk.setParameter(name,
            ConsoleActions.parameterPath(desk.mode(), id, name), current);
        desk.setStatus(SyncedText.of("amrac.console.status.applied", name));
    }

    private static void draw(ServerLevel level, ConsoleBlockEntity desk,
                             String kindName, double altitude,
                             double targetSpeed, AxisBounds bounds) {
        ChartKind kind = ChartKind.byName(kindName, desk.mode());
        desk.setChartKind(kind);
        desk.setChartInputs(altitude, targetSpeed);

        BlockPos screenPos = desk.screenPos();
        if (screenPos == null
                || !(level.getBlockEntity(screenPos) instanceof ScreenBlockEntity panel)) {
            desk.setStatus(SyncedText.of("amrac.console.status.no_screen"));
            return;
        }

        if (desk.mode() == ConsoleMode.MAP) {
            drawMap(level, desk, panel);
            return;
        }

        String id = documentId(desk);
        if (id == null) {
            desk.setStatus(SyncedText.of("amrac.console.status.slot_empty"));
            return;
        }
        AxisBounds pinned = bounds == null ? AxisBounds.AUTO : bounds;
        ChartData data = ConsoleActions.chart(desk.mode(), id, kind, altitude,
            targetSpeed).pinned(pinned.xMin(), pinned.xMax(),
                pinned.yMin(), pinned.yMax());
        if (data.isEmpty()) {
            desk.setStatus(SyncedText.of("amrac.console.status.nothing_to_plot"));
            return;
        }
        panel.content().showChart(data);
        panel.contentChanged();
        desk.setStatus(SyncedText.of("amrac.console.status.drew",
            SyncedText.key(kind.translationKey())));
    }

    private static void drawMap(ServerLevel level, ConsoleBlockEntity desk,
                                ScreenBlockEntity panel) {
        ItemStack stack = desk.slot().getItem(0);
        if (stack.is(amrac.AmracItems.GPS)) {
            GpsMirror.refresh(level, desk, panel);
            desk.setStatus(SyncedText.of("amrac.console.status.mirroring_gps"));
            return;
        }
        double[] centre = ConsoleActions.mapCentre(level, stack, desk.getBlockPos());
        double span = ConsoleActions.mapSpan();
        panel.content().showMap(
            ConsoleActions.map(level, centre[0], centre[1], span),
            centre[0], centre[1], span,
            SyncedText.of(ConsoleMode.MAP.translationKey()));
        panel.contentChanged();
        desk.setStatus(SyncedText.of("amrac.console.status.drew_map"));
    }

    private static String documentId(ConsoleBlockEntity desk) {
        return ConsoleActions.documentId(desk.slot().getItem(0), desk.mode());
    }

    public static void slotChanged(ConsoleBlockEntity desk) {
        refreshNames(desk);
        if (!desk.parameterName().isEmpty()) {
            desk.setParameter("", "", "");
        }
    }

    public static void refreshNames(ConsoleBlockEntity desk) {
        String id = documentId(desk);
        List<String> names = id == null ? List.of()
            : ConsoleActions.complete(desk.mode(), id, "");
        desk.setParameterNames(names);
    }
}
