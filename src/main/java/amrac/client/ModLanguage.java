package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import amrac.AmracMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The mod's own text language, chosen apart from the game's. Only keys in the mod's en_us.json
// are answered (ClientLanguageMixin); server-sent translation keys follow too.
public final class ModLanguage {

    private static final String FILE_NAME = "amrac-language.properties";
    private static final String OPTION = "language";

    public enum Choice {
        FOLLOW_GAME("follow"),
        ENGLISH("en_us"),
        CHINESE("zh_cn");

        private final String code;

        Choice(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public Choice next() {
            Choice[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        static Choice fromCode(String code) {
            for (Choice choice : values()) {
                if (choice.code.equals(code)) {
                    return choice;
                }
            }
            return null;
        }
    }

    private static Choice choice = Choice.FOLLOW_GAME;
    private static Map<String, String> english;
    private static Map<String, String> chinese;

    private ModLanguage() {
    }

    public static Choice choice() {
        return choice;
    }

    public static boolean isDefault() {
        return choice == Choice.FOLLOW_GAME;
    }

    public static void set(Choice next) {
        if (next == null || next == choice) {
            return;
        }
        choice = next;
        save();
        // A translated component caches its words per language object; a fresh one
        // (language-only reload, not a resource reload) makes every line read again.
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getLanguageManager().onResourceManagerReload(
            minecraft.getResourceManager());
    }

    // Null lets the game's language answer: when following the game, and for keys the mod lacks.
    public static String lookup(String key) {
        Choice current = choice;
        if (current == Choice.FOLLOW_GAME) {
            return null;
        }
        Map<String, String> en = english();
        if (!en.containsKey(key)) {
            return null;
        }
        if (current == Choice.CHINESE) {
            String text = chinese().get(key);
            if (text != null) {
                return text;
            }
        }
        return en.get(key);
    }

    private static Map<String, String> english() {
        Map<String, String> table = english;
        if (table == null) {
            table = english = read("en_us");
        }
        return table;
    }

    private static Map<String, String> chinese() {
        Map<String, String> table = chinese;
        if (table == null) {
            table = chinese = read("zh_cn");
        }
        return table;
    }

    private static Map<String, String> read(String code) {
        Map<String, String> table = new HashMap<>();
        String resource = "/assets/" + AmracMod.MODID + "/lang/" + code + ".json";
        try (InputStream stream = ModLanguage.class.getResourceAsStream(resource)) {
            if (stream == null) {
                AmracMod.LOGGER.error("Missing {}", resource);
            } else {
                Language.loadFromJson(stream, table::put);
            }
        } catch (RuntimeException | IOException exception) {
            AmracMod.LOGGER.error("Could not read {}", resource, exception);
        }
        return table;
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
                if (line.isBlank() || line.startsWith("#") || eq < 0
                        || !line.substring(0, eq).trim().equals(OPTION)) {
                    continue;
                }
                Choice stored = Choice.fromCode(line.substring(eq + 1).trim());
                if (stored == null) {
                    AmracMod.LOGGER.warn("Ignoring {} in {}", line, path);
                } else {
                    choice = stored;
                }
            }
        } catch (IOException exception) {
            AmracMod.LOGGER.error("Could not read {}", path, exception);
        }
    }

    private static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, List.of(
                "# AMRAC text language: follow, en_us or zh_cn",
                OPTION + "=" + choice.code()));
        } catch (IOException exception) {
            AmracMod.LOGGER.error("Could not write {}", path, exception);
        }
    }
}
