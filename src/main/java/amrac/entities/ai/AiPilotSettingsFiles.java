package amrac.entities.ai;

import amrac.AmracMod;
import amrac.physics.aircraft.DefaultFlightModelFiles;
import amrac.physics.aircraft.DocumentTopUp;
import amrac.platform.ServerLifecycleEvents;
import amrac.platform.ServerTickEvents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class AiPilotSettingsFiles {
    public static final String DIRECTORY = "ai";

    public static final String LIVE_FILE = "pilot.json";

    private static final long RESCAN_INTERVAL_MILLIS = 500L;

    private static volatile Path live;
    private static long lastScanMillis;
    private static long seenModified = Long.MIN_VALUE;
    private static long seenSize = -1L;
    private static boolean reportedMissing;

    private AiPilotSettingsFiles() {
    }

    public static void register(Path flightModelDirectory) {
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
            prepare(flightModelDirectory));
        ServerTickEvents.END_SERVER_TICK.register(server -> refresh());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            live = null;
            AiPilotSettings.apply(null);
        });
    }

    private static synchronized void prepare(Path flightModelDirectory) {
        Path folder = flightModelDirectory.resolve(DIRECTORY);
        try {
            Files.createDirectories(folder);
            for (Map.Entry<String, String> shipped
                : DefaultFlightModelFiles.ai().entrySet()) {
                writeIfAbsent(folder.resolve(shipped.getKey() + ".json"),
                    shipped.getValue());
            }
        } catch (IOException | RuntimeException exception) {
            AmracMod.LOGGER.error("Could not prepare " + folder
                + "; AI pilots fly on the shipped settings", exception);
        }
        live = folder.resolve(LIVE_FILE);
        seenModified = Long.MIN_VALUE;
        seenSize = -1L;
        reportedMissing = false;
        load();
    }

    private static void refresh() {
        Path file = live;
        if (file == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanMillis < RESCAN_INTERVAL_MILLIS) {
            return;
        }
        lastScanMillis = now;
        long modified;
        long size;
        try {
            if (!Files.isRegularFile(file)) {
                modified = -1L;
                size = -1L;
            } else {
                modified = Files.getLastModifiedTime(file).toMillis();
                size = Files.size(file);
            }
        } catch (IOException exception) {
            return;
        }
        if (modified != seenModified || size != seenSize) {
            load();
        }
    }

    private static synchronized void load() {
        Path file = live;
        if (file == null) {
            return;
        }
        if (!Files.isRegularFile(file)) {
            seenModified = -1L;
            seenSize = -1L;
            AiPilotSettings.apply(null);
            if (!reportedMissing) {
                reportedMissing = true;
                AmracMod.LOGGER.warn("{} is missing; AI pilots fly on the"
                    + " shipped settings until it is back", file);
            }
            return;
        }
        reportedMissing = false;
        String text;
        try {
            seenModified = Files.getLastModifiedTime(file).toMillis();
            seenSize = Files.size(file);
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AmracMod.LOGGER.error("Could not read " + file
                + "; AI pilots keep the settings they had", exception);
            return;
        }
        AiPilotSettings.Parsed parsed;
        try {
            parsed = AiPilotSettings.parse(text);
        } catch (RuntimeException notJson) {
            AmracMod.LOGGER.error("{} is not valid JSON ({}); AI pilots keep"
                + " the settings they had", file, notJson.getMessage());
            return;
        }
        AiPilotSettings.apply(parsed.settings());
        for (String problem : parsed.problems()) {
            AmracMod.LOGGER.warn("{}: {}", file.getFileName(), problem);
        }
        AmracMod.LOGGER.info("AI pilot settings loaded from {}{}", file,
            parsed.problems().isEmpty() ? ""
                : " with " + parsed.problems().size()
                    + " value(s) kept at the shipped setting");
    }

    private static void writeIfAbsent(Path file, String contents)
            throws IOException {
        if (!Files.exists(file)) {
            Files.writeString(file, contents, StandardCharsets.UTF_8);
            return;
        }
        String existing;
        try {
            existing = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return;
        }
        DocumentTopUp.Result result = DocumentTopUp.topUp(existing, contents);
        if (!result.changed()) {
            return;
        }
        Files.writeString(file, result.text(), StandardCharsets.UTF_8);
        AmracMod.LOGGER.info("Added {} to {} (new since that file was written;"
            + " your own values are untouched)",
            String.join(", ", result.added()), file);
    }
}
