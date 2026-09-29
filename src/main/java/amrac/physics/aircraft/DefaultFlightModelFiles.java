package amrac.physics.aircraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DefaultFlightModelFiles {
    private static final String ROOT = "/amrac/flightmodel/";

    /**
     * A new aircraft or missile JSON in resources must also be listed in AIRCRAFT / MISSILES (the
     * jar is not scanned), or its document is missing at runtime.
     */
    private static final List<String> AIRCRAFT = List.of(
        "typhoon", "f16", "f15", "f4j", "mig21", "mig29", "su27", "j10c",
        "j8ii",
        "f18", "mig23", "rafale", "su30",
        "f15e");

    private static final List<String> MISSILES = List.of(
        "aim120", "aim7", "r27", "r77", "pl12",
        "aim120l", "r771", "pl12a", "pl15",
        "pl10", "aim9", "r73", "pl8",
        "mica",
        "meteor");

    private static final List<String> AI = List.of(
        "pilot", "pilot-half", "pilot-quarter");

    private DefaultFlightModelFiles() {
    }

    public static Map<String, String> ai() {
        return readAll(ROOT + "ai/", AI);
    }

    public static String environment() {
        return read(ROOT + "environment.json");
    }

    public static Map<String, String> aircraft() {
        return readAll(ROOT + "aircraft/", AIRCRAFT);
    }

    public static Map<String, String> missiles() {
        return readAll(ROOT + "missile/", MISSILES);
    }

    public static Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("environment.json", environment());
        for (Map.Entry<String, String> entry : aircraft().entrySet()) {
            out.put("aircraft/" + entry.getKey() + ".json", entry.getValue());
        }
        for (Map.Entry<String, String> entry : missiles().entrySet()) {
            out.put("missile/" + entry.getKey() + ".json", entry.getValue());
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, String> readAll(String directory,
                                               List<String> names) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            String text = readOrNull(directory + name + ".json");
            if (text == null) {
                missing.add(name);
            } else {
                out.put(name, text);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Flight-model documents missing from the jar: " + missing);
        }
        return out;
    }

    private static String read(String path) {
        String text = readOrNull(path);
        if (text == null) {
            throw new IllegalStateException(
                "Flight-model document missing from the jar: " + path);
        }
        return text;
    }

    private static String readOrNull(String path) {
        String relativePath = path.startsWith("/") ? path.substring(1) : path;

        String fabricText = readFromFabricContainer(relativePath);
        if (fabricText != null) {
            return fabricText;
        }

        try (InputStream stream = DefaultFlightModelFiles.class
            .getResourceAsStream("/" + relativePath)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            return null;
        }
        return null;
    }

    private static String readFromFabricContainer(String relativePath) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object containerOptional = loaderClass
                .getMethod("getModContainer", String.class)
                .invoke(loader, "amrac");
            if (!(containerOptional instanceof Optional<?> containerResult)
                || containerResult.isEmpty()) {
                return null;
            }

            Class<?> containerClass = Class.forName("net.fabricmc.loader.api.ModContainer");
            Object pathOptional = containerClass.getMethod("findPath", String.class)
                .invoke(containerResult.get(), relativePath);
            if (!(pathOptional instanceof Optional<?> pathResult)
                || pathResult.isEmpty()
                || !(pathResult.get() instanceof Path resource)) {
                return null;
            }
            return Files.readString(resource, StandardCharsets.UTF_8);
        } catch (ReflectiveOperationException | IOException exception) {
            return null;
        }
    }
}
