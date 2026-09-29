package amrac.client;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import amrac.AmracMod;
import amrac.AmracSounds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SoundVolumes {
    public static final int MIN = 0;
    public static final int MAX = 200;
    public static final int DEFAULT = 100;

    public enum Channel {
        MASTER("master"),
        ENGINE("engine", "plane_loop", "jet_engine_loop"),
        AFTERBURNER("afterburner", "plane_afterburner"),
        AFTERBURNER_TRANSIENTS("afterburner_transients", "afterburner_light", "afterburner_cut"),
        AIRFLOW("airflow", "plane_airflow"),
        SUPERSONIC("supersonic", "supersonic_rumble"),
        SONIC_BOOM("sonic_boom", "sonic_boom"),
        MISSILE_LAUNCH("missile_launch", "missile_launch", "missile_launch_cockpit", "missile_release"),
        MISSILE_LOCK("missile_lock", "missile_lock"),
        RWR("rwr", "rwr_warning"),
        BOUNDARY("boundary", "boundary_warning"),
        GUNS("guns", "gun_fire");

        final String key;
        final List<Identifier> ids = new ArrayList<>();

        Channel(String key, String... paths) {
            this.key = key;
            for (String path : paths) {
                ids.add(AmracMod.id(path));
            }
        }

        public String translationKey() {
            return "amrac.controls.volume." + key;
        }
    }

    private static final String FILE_NAME = "amrac-sounds.txt";
    private static final Map<Channel, Integer> VALUES = new EnumMap<>(Channel.class);
    private static final Map<Identifier, Channel> BY_ID = new java.util.HashMap<>();

    static {
        for (Channel channel : Channel.values()) {
            VALUES.put(channel, DEFAULT);
            for (Identifier id : channel.ids) {
                BY_ID.put(id, channel);
            }
        }
        BY_ID.put(SoundEvents.FIREWORK_ROCKET_BLAST.location(), Channel.GUNS);
        BY_ID.put(SoundEvents.DISPENSER_LAUNCH.location(), Channel.GUNS);
    }

    private SoundVolumes() {
    }

    public static int get(Channel channel) {
        return VALUES.get(channel);
    }

    public static void preview(Channel channel, int percent) {
        VALUES.put(channel, Math.max(MIN, Math.min(MAX, percent)));
    }

    public static void set(Channel channel, int percent) {
        preview(channel, percent);
        save();
    }

    public static boolean isDefault(Channel channel) {
        return get(channel) == DEFAULT;
    }

    public static void reset(Channel channel) {
        set(channel, DEFAULT);
    }

    public static boolean anyChanged() {
        for (Channel channel : Channel.values()) {
            if (!isDefault(channel)) {
                return true;
            }
        }
        return false;
    }

    public static void resetAll() {
        for (Channel channel : Channel.values()) {
            VALUES.put(channel, DEFAULT);
        }
        save();
    }

    public static float factor(Identifier id) {
        Channel channel = BY_ID.get(id);
        if (channel == null) {
            return 1.0F;
        }
        if (channel == Channel.GUNS && !channel.ids.contains(id)
            && PlaneViewState.ridingPlane(net.minecraft.client.Minecraft.getInstance()) == null) {
            return 1.0F;
        }
        return get(Channel.MASTER) / 100.0F * get(channel) / 100.0F;
    }

    private static Path path() {
        return amrac.platform.Platform.configDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                int eq = line.indexOf('=');
                if (line.isBlank() || line.startsWith("#") || eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                for (Channel channel : Channel.values()) {
                    if (channel.key.equals(key)) {
                        try {
                            preview(channel, Integer.parseInt(line.substring(eq + 1).trim()));
                        } catch (NumberFormatException ignored) {
                            AmracMod.LOGGER.warn("Ignoring {} in {}", line, path);
                        }
                    }
                }
            }
        } catch (IOException exception) {
            AmracMod.LOGGER.error("Could not read {}", path, exception);
        }
    }

    public static void save() {
        Path path = path();
        List<String> lines = new ArrayList<>();
        lines.add("# AMRAC sound volumes, percent. 100 is the mod's own mix.");
        for (Channel channel : Channel.values()) {
            lines.add(channel.key + "=" + get(channel));
        }
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines);
        } catch (IOException exception) {
            AmracMod.LOGGER.error("Could not write {}", path, exception);
        }
    }
}
