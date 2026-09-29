package amrac;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AmracConfig {
    private static final String FILE_NAME = "amrac.properties";

    private static final String CRASH_DAMAGE_KEY = "planeCrashDamage";
    private static final String FUEL_COST_KEY = "planeFuelCost";
    private static final String INFINITE_FUEL_KEY = "infiniteFuel";
    private static final String CRASH_BLAST_KEY = "planeCrashBlast";

    private static final boolean DEFAULT_CRASH_DAMAGE = true;
    private static final int DEFAULT_FUEL_COST = 3;
    private static final boolean DEFAULT_INFINITE_FUEL = false;
    private static final double DEFAULT_CRASH_BLAST = 1.0D;

    private static boolean crashDamage = DEFAULT_CRASH_DAMAGE;
    private static int fuelCost = DEFAULT_FUEL_COST;
    private static boolean infiniteFuel = DEFAULT_INFINITE_FUEL;
    private static double crashBlast = DEFAULT_CRASH_BLAST;

    private AmracConfig() {
    }

    public static boolean crashDamage() {
        return crashDamage;
    }

    public static int fuelCost() {
        return fuelCost;
    }

    public static boolean infiniteFuel() {
        return infiniteFuel;
    }

    public static double crashBlast() {
        return crashBlast;
    }

    public static void load() {
        Path path = amrac.platform.Platform.configDir().resolve(FILE_NAME);
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException exception) {
                AmracMod.LOGGER.error("Could not read {}", path, exception);
            }
        }

        crashDamage = readBoolean(properties, CRASH_DAMAGE_KEY, DEFAULT_CRASH_DAMAGE);
        fuelCost = readInt(properties, FUEL_COST_KEY, DEFAULT_FUEL_COST, 0, 1000);
        infiniteFuel = readBoolean(properties, INFINITE_FUEL_KEY, DEFAULT_INFINITE_FUEL);
        crashBlast = readDouble(properties, CRASH_BLAST_KEY, DEFAULT_CRASH_BLAST,
            0.0D, 4.0D);
        save(path);
    }

    private static void save(Path path) {
        Properties properties = new Properties();
        properties.setProperty(CRASH_DAMAGE_KEY, Boolean.toString(crashDamage));
        properties.setProperty(FUEL_COST_KEY, Integer.toString(fuelCost));
        properties.setProperty(INFINITE_FUEL_KEY, Boolean.toString(infiniteFuel));
        properties.setProperty(CRASH_BLAST_KEY, Double.toString(crashBlast));
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "AMRAC world rules");
            }
        } catch (IOException exception) {
            AmracMod.LOGGER.error("Could not write {}", path, exception);
        }
    }

    private static boolean readBoolean(Properties properties, String key,
                                       boolean fallback) {
        String value = properties.getProperty(key);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        if (value != null) {
            AmracMod.LOGGER.warn("Ignoring invalid {}={}", key, value);
        }
        return fallback;
    }

    private static double readDouble(Properties properties, String key,
                                     double fallback, double minimum,
                                     double maximum) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
                throw new NumberFormatException("out of range");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            AmracMod.LOGGER.warn("Ignoring invalid {}={}", key, value);
            return fallback;
        }
    }

    private static int readInt(Properties properties, String key, int fallback,
                               int minimum, int maximum) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < minimum || parsed > maximum) {
                throw new NumberFormatException("out of range");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            AmracMod.LOGGER.warn("Ignoring invalid {}={}", key, value);
            return fallback;
        }
    }
}
