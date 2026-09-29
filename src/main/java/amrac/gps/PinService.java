package amrac.gps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.PlayerTeam;
import amrac.AmracMod;

public final class PinService {
    public static final int MAX_PER_PLAYER = 32;
    public static final int MAX_NAME_LENGTH = 24;

    private static final String FILE = "amrac-gps-pins.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<UUID, List<GpsPin>> BY_OWNER = new LinkedHashMap<>();
    private static Path file;
    private static boolean dirty;

    private PinService() {
    }

    public static void load(MinecraftServer server) {
        BY_OWNER.clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<GpsPin> stored = GSON.fromJson(reader,
                new TypeToken<List<GpsPin>>() { }.getType());
            if (stored == null) {
                return;
            }
            for (GpsPin pin : stored) {
                if (pin != null && pin.id() != null && pin.owner() != null) {
                    BY_OWNER.computeIfAbsent(pin.owner(),
                        ignored -> new ArrayList<>()).add(pin);
                }
            }
        } catch (IOException | RuntimeException error) {
            AmracMod.LOGGER.error("Could not read GPS pins", error);
        }
    }

    public static void save() {
        if (!dirty || file == null) {
            return;
        }
        dirty = false;
        List<GpsPin> all = new ArrayList<>();
        BY_OWNER.values().forEach(all::addAll);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(all, writer);
        } catch (IOException error) {
            AmracMod.LOGGER.error("Could not write GPS pins", error);
        }
    }

    public static List<GpsPin> visibleTo(ServerPlayer player) {
        List<GpsPin> out = new ArrayList<>(
            BY_OWNER.getOrDefault(player.getUUID(), List.of()));
        String team = teamOf(player);
        if (!team.isBlank()) {
            for (Map.Entry<UUID, List<GpsPin>> entry : BY_OWNER.entrySet()) {
                if (entry.getKey().equals(player.getUUID())) {
                    continue;
                }
                for (GpsPin pin : entry.getValue()) {
                    if (team.equals(pin.sharedTeam())) {
                        out.add(pin);
                    }
                }
            }
        }
        return out;
    }

    private static String teamOf(ServerPlayer player) {
        PlayerTeam team = player.level().getScoreboard()
            .getPlayersTeam(player.getScoreboardName());
        return team == null ? "" : team.getName();
    }

    public static GpsPin add(ServerPlayer player, String name, double x, double z) {
        List<GpsPin> own = BY_OWNER.computeIfAbsent(player.getUUID(),
            ignored -> new ArrayList<>());
        if (own.size() >= MAX_PER_PLAYER) {
            return null;
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            trimmed = "Pin " + (own.size() + 1);
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        GpsPin pin = new GpsPin(UUID.randomUUID(), player.getUUID(),
            player.getGameProfile().name(), trimmed, x, z, "");
        own.add(pin);
        dirty = true;
        return pin;
    }

    public static boolean remove(ServerPlayer player, UUID pinId) {
        List<GpsPin> own = BY_OWNER.get(player.getUUID());
        if (own == null) {
            return false;
        }
        boolean removed = own.removeIf(pin -> pin.id().equals(pinId));
        dirty |= removed;
        return removed;
    }

    public static String toggleShare(ServerPlayer player, UUID pinId) {
        List<GpsPin> own = BY_OWNER.get(player.getUUID());
        if (own == null) {
            return null;
        }
        String team = teamOf(player);
        for (int i = 0; i < own.size(); i++) {
            GpsPin pin = own.get(i);
            if (!pin.id().equals(pinId)) {
                continue;
            }
            if (pin.shared()) {
                own.set(i, pin.sharedWith(""));
                dirty = true;
                return "";
            }
            if (team.isBlank()) {
                return null;
            }
            own.set(i, pin.sharedWith(team));
            dirty = true;
            return team;
        }
        return null;
    }
}
