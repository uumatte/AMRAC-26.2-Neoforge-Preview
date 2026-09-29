package amrac.entities.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiFlightRecorder {
    private static final int MAX_LINES = 4000;

    private static final int HEARTBEAT_TICKS = 100;

    private static boolean recording;
    private static long startedAt;
    private static final List<String> LINES = new ArrayList<>();
    private static final Map<UUID, String> LAST_STATE = new HashMap<>();
    private static final Map<UUID, Long> LAST_HEARTBEAT = new HashMap<>();

    private AiFlightRecorder() {
    }

    public static boolean recording() {
        return recording;
    }

    public static void start(long tick) {
        recording = true;
        startedAt = tick;
        LINES.clear();
        LAST_STATE.clear();
        LAST_HEARTBEAT.clear();
        LINES.add("=== AI recording started ===");
    }

    public static List<String> stop() {
        if (!recording) {
            return List.of();
        }
        recording = false;
        List<String> out = new ArrayList<>(LINES);
        out.add("=== AI recording ended, " + LINES.size() + " event(s) ===");
        LINES.clear();
        LAST_STATE.clear();
        LAST_HEARTBEAT.clear();
        return out;
    }

    public static void note(long tick, AiPilotBrain brain, String layer,
                            double x, double y, double z, double speedBps,
                            double targetRange, double offBoresightDegrees) {
        if (!recording) {
            return;
        }
        UUID id = brain.id;
        String fire = brain.fireBlockForDebug();
        UUID enemy = brain.enemyAircraftForDebug();

        String state = brain.phase() + "|"
            + (fire == null ? "FIRING" : fire.replaceAll("-?\\d+", "#"))
            + "|" + (enemy == null ? "-" : enemy.toString().substring(0, 8))
            + "|" + brain.threatenedForDebug() + "|" + layer;

        boolean changed = !state.equals(LAST_STATE.get(id));
        long last = LAST_HEARTBEAT.getOrDefault(id, Long.MIN_VALUE);
        boolean beat = tick - last >= HEARTBEAT_TICKS;
        if (!changed && !beat) {
            return;
        }
        LAST_STATE.put(id, state);
        LAST_HEARTBEAT.put(id, tick);

        add(String.format(
            "[%6.1fs] %-12s %-8s %-7s %-7s xz %7.0f %7.0f alt %5.0f %4.0f b/s"
                + " target %-8s %s%s%s%s",
            (tick - startedAt) / 20.0D, brain.callsign(),
            brain.team().isBlank() ? "(noteam)" : brain.team(),
            layer, brain.phase(), x, z, y, speedBps,
            enemy == null ? "none" : enemy.toString().substring(0, 8),
            targetRange < 0.0D ? "" : String.format("@%.0f ", targetRange),
            Double.isNaN(offBoresightDegrees) ? ""
                : String.format("off %3.0f deg ", offBoresightDegrees),
            brain.threatenedForDebug() ? "EVADING " : "",
            fire == null ? "FIRING" : "hold: " + fire));
    }

    public static void event(long tick, String text) {
        if (recording) {
            add(String.format("[%6.1fs] * %s", (tick - startedAt) / 20.0D,
                text));
        }
    }

    private static void add(String line) {
        if (LINES.size() >= MAX_LINES) {
            LINES.remove(0);
        }
        LINES.add(line);
    }
}
