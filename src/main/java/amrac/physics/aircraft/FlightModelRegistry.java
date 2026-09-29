package amrac.physics.aircraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class FlightModelRegistry {
    private static final String ENVIRONMENT_FILE = "environment.json";
    private static final String AIRCRAFT_DIRECTORY = "aircraft";
    private static final String MISSILE_DIRECTORY = "missile";
    private static final String JSON_SUFFIX = ".json";

    private static final long RESCAN_INTERVAL_MILLIS = 500L;

    private static final FlightModelRegistry INSTANCE = new FlightModelRegistry();

    private static volatile BiConsumer<String, Throwable> errorHandler =
        (message, error) -> {
            System.err.println("[amrac] " + message);
            if (error != null) {
                error.printStackTrace(System.err);
            }
        };

    private final Map<String, FlightModel> models = new ConcurrentHashMap<>();
    private final Map<String, amrac.physics.missile.MissileFlightModel>
        missileModels = new ConcurrentHashMap<>();
    private final Map<Path, FileStamp> stamps = new HashMap<>();
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    private volatile AtmosphereModel atmosphere = AtmosphereModel.standard();
    private volatile Path directory;
    private long lastScanMillis;
    private volatile boolean serverAuthoritative;

    private FlightModelRegistry() {
        loadBuiltInDefaults();
    }

    public static FlightModelRegistry instance() {
        return INSTANCE;
    }

    public static void setErrorHandler(BiConsumer<String, Throwable> handler) {
        if (handler != null) {
            errorHandler = handler;
        }
    }

    public synchronized void configure(Path flightModelDirectory) {
        this.directory = flightModelDirectory;
        if (flightModelDirectory == null) {
            return;
        }
        try {
            Files.createDirectories(flightModelDirectory.resolve(AIRCRAFT_DIRECTORY));
            Files.createDirectories(flightModelDirectory.resolve(MISSILE_DIRECTORY));
            for (Map.Entry<String, String> shipped
                : DefaultFlightModelFiles.all().entrySet()) {
                writeIfAbsent(flightModelDirectory.resolve(entry(shipped.getKey())),
                    shipped.getValue());
            }
        } catch (IOException exception) {
            errorHandler.accept("Could not prepare " + flightModelDirectory +
                "; flying on built-in flight model parameters", exception);
            return;
        }
        lastScanMillis = 0L;
        reload();
    }

    public void refresh() {
        Path root = directory;
        if (root == null || serverAuthoritative) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanMillis < RESCAN_INTERVAL_MILLIS) {
            return;
        }
        synchronized (this) {
            if (now - lastScanMillis < RESCAN_INTERVAL_MILLIS) {
                return;
            }
            lastScanMillis = now;
            if (anythingChanged(root)) {
                reload();
            }
        }
    }

    public AtmosphereModel atmosphere() {
        return atmosphere;
    }

    public FlightModel flightModel(String id) {
        return id == null ? null : models.get(id);
    }

    public amrac.physics.missile.MissileFlightModel
            missileFlightModel(String id) {
        return id == null ? null : missileModels.get(id);
    }

    public amrac.physics.missile.MissilePhysicsProfile
            missileProfile(String id) {
        var model = missileFlightModel(id);
        return model == null ? null : model.profile();
    }

    public AircraftPhysicsProfile profile(String id) {
        FlightModel model = flightModel(id);
        return model == null ? null : model.profile();
    }

    public Set<String> aircraftIds() {
        return Set.copyOf(models.keySet());
    }

    /**
     * Physics runs on the pilot's client, so the client flies the documents the server sends
     * (editing local files is no cheat); a document the server does not send falls back to the
     * jar's copy. Sending and receiving are at the FLIGHT_MODEL_SYNC receiver in
     * PlaneClientControls.
     */
    public synchronized void applyServerDocuments(Map<String, String> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        AtmosphereModel loadedAtmosphere = null;
        Map<String, FlightModel> loadedModels = new LinkedHashMap<>();
        Map<String, amrac.physics.missile.MissileFlightModel>
            loadedMissiles = new LinkedHashMap<>();

        for (Map.Entry<String, String> document : documents.entrySet()) {
            String name = document.getKey();
            String text = document.getValue();
            if (ENVIRONMENT_FILE.equals(name)) {
                loadedAtmosphere = parseAtmosphere(text, "server " + name);
            } else if (name.startsWith(AIRCRAFT_DIRECTORY + "/")) {
                AircraftPhysicsProfile profile = parseProfile(text,
                    "server " + name, stem(name));
                if (profile != null) {
                    loadedModels.put(profile.id(), new FlightModel(profile));
                }
            } else if (name.startsWith(MISSILE_DIRECTORY + "/")) {
                var profile = parseMissileProfile(text, "server " + name,
                    stem(name));
                if (profile != null) {
                    loadedMissiles.put(profile.id(), new amrac
                        .physics.missile.MissileFlightModel(profile));
                }
            }
        }

        for (Map.Entry<String, FlightModel> builtIn : builtInModels().entrySet()) {
            loadedModels.putIfAbsent(builtIn.getKey(), builtIn.getValue());
        }
        for (var builtIn : builtInMissileModels().entrySet()) {
            loadedMissiles.putIfAbsent(builtIn.getKey(), builtIn.getValue());
        }

        serverAuthoritative = true;
        if (loadedAtmosphere != null) {
            atmosphere = loadedAtmosphere;
        }
        models.keySet().retainAll(loadedModels.keySet());
        models.putAll(loadedModels);
        missileModels.keySet().retainAll(loadedMissiles.keySet());
        missileModels.putAll(loadedMissiles);
    }

    public synchronized void clearServerDocuments() {
        if (!serverAuthoritative) {
            return;
        }
        serverAuthoritative = false;
        lastScanMillis = 0L;
        if (directory == null) {
            loadBuiltInDefaults();
        } else {
            reload();
        }
    }

    private static String stem(String relative) {
        int slash = relative.lastIndexOf('/');
        String name = slash < 0 ? relative : relative.substring(slash + 1);
        return name.endsWith(JSON_SUFFIX)
            ? name.substring(0, name.length() - JSON_SUFFIX.length()) : name;
    }

    public synchronized Map<String, String> documentsForSync() {
        Map<String, String> out = new LinkedHashMap<>(DefaultFlightModelFiles.all());
        Path root = directory;
        if (root == null) {
            return out;
        }
        for (String name : out.keySet().toArray(new String[0])) {
            String text = read(root.resolve(entry(name)));
            if (text != null) {
                out.put(name, text);
            }
        }
        collectExtra(root.resolve(AIRCRAFT_DIRECTORY), AIRCRAFT_DIRECTORY, out);
        collectExtra(root.resolve(MISSILE_DIRECTORY), MISSILE_DIRECTORY, out);
        return out;
    }

    private void collectExtra(Path directoryToScan, String prefix,
                              Map<String, String> out) {
        if (!Files.isDirectory(directoryToScan)) {
            return;
        }
        try (DirectoryStream<Path> files =
                 Files.newDirectoryStream(directoryToScan, "*" + JSON_SUFFIX)) {
            for (Path file : files) {
                String name = prefix + "/" + file.getFileName();
                if (out.containsKey(name)) {
                    continue;
                }
                String text = read(file);
                if (text != null) {
                    out.put(name, text);
                }
            }
        } catch (IOException exception) {
            errorHandler.accept("Could not list " + directoryToScan, exception);
        }
    }

    private synchronized void reload() {
        Path root = directory;
        AtmosphereModel loadedAtmosphere = null;
        Map<String, FlightModel> loadedModels = new LinkedHashMap<>();
        Map<String, amrac.physics.missile.MissileFlightModel>
            loadedMissiles = new LinkedHashMap<>();
        stamps.clear();

        if (root != null) {
            Path environmentFile = root.resolve(ENVIRONMENT_FILE);
            String environmentText = read(environmentFile);
            if (environmentText != null) {
                loadedAtmosphere = parseAtmosphere(environmentText,
                    environmentFile.toString());
            }

            Path aircraftDirectory = root.resolve(AIRCRAFT_DIRECTORY);
            if (Files.isDirectory(aircraftDirectory)) {
                try (DirectoryStream<Path> files =
                         Files.newDirectoryStream(aircraftDirectory, "*" + JSON_SUFFIX)) {
                    for (Path file : files) {
                        String text = read(file);
                        if (text == null) {
                            continue;
                        }
                        AircraftPhysicsProfile profile = parseProfile(text,
                            file.toString(), fileId(file));
                        if (profile != null) {
                            loadedModels.put(profile.id(), new FlightModel(profile));
                        }
                    }
                } catch (IOException exception) {
                    errorHandler.accept("Could not list " + aircraftDirectory,
                        exception);
                }
            }

            Path missileDirectory = root.resolve(MISSILE_DIRECTORY);
            if (Files.isDirectory(missileDirectory)) {
                try (DirectoryStream<Path> files =
                         Files.newDirectoryStream(missileDirectory, "*" + JSON_SUFFIX)) {
                    for (Path file : files) {
                        String text = read(file);
                        if (text == null) {
                            continue;
                        }
                        var profile = parseMissileProfile(text, file.toString(),
                            fileId(file));
                        if (profile != null) {
                            loadedMissiles.put(profile.id(),
                                new amrac.physics.missile
                                    .MissileFlightModel(profile));
                        }
                    }
                } catch (IOException exception) {
                    errorHandler.accept("Could not list " + missileDirectory,
                        exception);
                }
            }
        }

        if (loadedAtmosphere == null) {
            loadedAtmosphere = parseAtmosphere(DefaultFlightModelFiles.environment(),
                "built-in environment");
        }
        atmosphere = loadedAtmosphere != null ? loadedAtmosphere
            : AtmosphereModel.standard();

        for (Map.Entry<String, FlightModel> builtIn : builtInModels().entrySet()) {
            loadedModels.putIfAbsent(builtIn.getKey(), builtIn.getValue());
        }
        models.keySet().retainAll(loadedModels.keySet());
        models.putAll(loadedModels);

        for (var builtIn : builtInMissileModels().entrySet()) {
            loadedMissiles.putIfAbsent(builtIn.getKey(), builtIn.getValue());
        }
        missileModels.keySet().retainAll(loadedMissiles.keySet());
        missileModels.putAll(loadedMissiles);

        Runnable listener = changeListener;
        if (listener != null) {
            listener.run();
        }
    }

    private volatile Runnable changeListener;

    public synchronized Path aircraftDocument(String id) {
        Path root = directory;
        return root == null || id == null ? null
            : root.resolve(AIRCRAFT_DIRECTORY).resolve(id + JSON_SUFFIX);
    }

    public synchronized Path missileDocument(String id) {
        Path root = directory;
        return root == null || id == null ? null
            : root.resolve(MISSILE_DIRECTORY).resolve(
                id.toLowerCase(java.util.Locale.ROOT) + JSON_SUFFIX);
    }

    public String documentText(Path file) {
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            reportOnce(file.toString(), "Could not read " + file, unreadable);
            return null;
        }
    }

    public synchronized boolean writeDocument(Path file, String text) {
        if (file == null || text == null) {
            return false;
        }
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            reportOnce(file.toString(), "Could not write " + file, unwritable);
            return false;
        }
        reload();
        return true;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    private void loadBuiltInDefaults() {
        AtmosphereModel builtIn = parseAtmosphere(
            DefaultFlightModelFiles.environment(), "built-in environment");
        atmosphere = builtIn != null ? builtIn : AtmosphereModel.standard();
        models.putAll(builtInModels());
        missileModels.putAll(builtInMissileModels());
    }

    private static Path entry(String relative) {
        int slash = relative.indexOf('/');
        return slash < 0 ? Path.of(relative)
            : Path.of(relative.substring(0, slash), relative.substring(slash + 1));
    }

    private Map<String, FlightModel> builtInModels() {
        Map<String, FlightModel> builtIn = new LinkedHashMap<>();
        for (Map.Entry<String, String> shipped
            : DefaultFlightModelFiles.aircraft().entrySet()) {
            AircraftPhysicsProfile profile = parseProfile(shipped.getValue(),
                "built-in " + shipped.getKey(), shipped.getKey());
            if (profile != null) {
                builtIn.put(profile.id(), new FlightModel(profile));
            }
        }
        return builtIn;
    }

    private Map<String, amrac.physics.missile.MissileFlightModel>
            builtInMissileModels() {
        Map<String, amrac.physics.missile.MissileFlightModel>
            builtIn = new LinkedHashMap<>();
        for (Map.Entry<String, String> shipped
            : DefaultFlightModelFiles.missiles().entrySet()) {
            var profile = parseMissileProfile(shipped.getValue(),
                "built-in " + shipped.getKey(), shipped.getKey());
            if (profile != null) {
                builtIn.put(profile.id(), new amrac.physics
                    .missile.MissileFlightModel(profile));
            }
        }
        return builtIn;
    }

    private amrac.physics.missile.MissilePhysicsProfile
            parseMissileProfile(String text, String source, String fallbackId) {
        try {
            Map<String, Object> root = Json.parseObject(text);
            var existing = missileProfile(Json.string(root, "id", fallbackId));
            var profile = amrac.physics.missile
                .MissilePhysicsProfile.fromJson(root, existing);
            reportedFailures.remove(source);
            return profile;
        } catch (RuntimeException exception) {
            reportOnce(source, "Could not read " + source +
                "; keeping the previous parameters", exception);
            return null;
        }
    }

    private AtmosphereModel parseAtmosphere(String text, String source) {
        try {
            AtmosphereModel model = AtmosphereModel.fromJson(Json.parseObject(text));
            reportedFailures.remove(source);
            return model;
        } catch (RuntimeException exception) {
            reportOnce(source, "Could not read " + source +
                "; keeping the previous atmosphere", exception);
            return null;
        }
    }

    private AircraftPhysicsProfile parseProfile(String text, String source,
                                                String fallbackId) {
        try {
            Map<String, Object> root = Json.parseObject(text);
            AircraftPhysicsProfile existing = profile(
                Json.string(root, "id", fallbackId));
            AircraftPhysicsProfile profile =
                AircraftPhysicsProfile.fromJson(root, existing);
            reportedFailures.remove(source);
            return profile;
        } catch (RuntimeException exception) {
            reportOnce(source, "Could not read " + source +
                "; keeping the previous parameters", exception);
            return null;
        }
    }

    private void reportOnce(String source, String message, Throwable error) {
        if (errorHandler == null) {
            System.err.println("[amrac] " + message);
            return;
        }
        if (reportedFailures.add(source)) {
            errorHandler.accept(message, error);
        }
    }

    private String read(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            stamps.put(file, FileStamp.of(file));
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            reportOnce(file.toString(), "Could not read " + file, exception);
            return null;
        }
    }

    private void writeIfAbsent(Path file, String contents) throws IOException {
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
        errorHandler.accept("Added " + String.join(", ", result.added())
            + " to " + file + " (new since that file was written; your own"
            + " values are untouched)", null);
    }

    private boolean anythingChanged(Path root) {
        for (Map.Entry<Path, FileStamp> entry : stamps.entrySet()) {
            if (!FileStamp.of(entry.getKey()).equals(entry.getValue())) {
                return true;
            }
        }
        Path environmentFile = root.resolve(ENVIRONMENT_FILE);
        if (Files.isRegularFile(environmentFile) && !stamps.containsKey(environmentFile)) {
            return true;
        }
        return hasNewFile(root.resolve(AIRCRAFT_DIRECTORY)) ||
            hasNewFile(root.resolve(MISSILE_DIRECTORY));
    }

    private boolean hasNewFile(Path directoryToScan) {
        if (!Files.isDirectory(directoryToScan)) {
            return false;
        }
        try (DirectoryStream<Path> files =
                 Files.newDirectoryStream(directoryToScan, "*" + JSON_SUFFIX)) {
            for (Path file : files) {
                if (!stamps.containsKey(file)) {
                    return true;
                }
            }
        } catch (IOException exception) {
            return false;
        }
        return false;
    }

    private static String fileId(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(JSON_SUFFIX)
            ? name.substring(0, name.length() - JSON_SUFFIX.length()) : name;
    }

    private record FileStamp(long modifiedMillis, long size, boolean present) {
        static FileStamp of(Path file) {
            try {
                if (!Files.isRegularFile(file)) {
                    return new FileStamp(0L, 0L, false);
                }
                return new FileStamp(Files.getLastModifiedTime(file).toMillis(),
                    Files.size(file), true);
            } catch (IOException exception) {
                return new FileStamp(0L, 0L, false);
            }
        }
    }
}
