package amrac.display;

import net.minecraft.network.chat.Component;

// Console statuses and panel captions are saved and synced as strings, so a translation key
// travels inside the string and is translated where it is drawn. Unpacked strings (names,
// captions saved before this) show as written.
public final class SyncedText {

    private static final char KEY = '\u0001';
    private static final char SEPARATOR = '\u0002';
    private static final char NESTED_KEY = '\u0003';

    private SyncedText() {
    }

    public static String of(String key, Object... args) {
        StringBuilder out = new StringBuilder().append(KEY).append(key);
        for (Object arg : args) {
            out.append(SEPARATOR).append(clean(String.valueOf(arg)));
        }
        return out.toString();
    }

    // An argument to of() that is itself translated.
    public static String key(String key) {
        return NESTED_KEY + key;
    }

    public static Component component(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != KEY) {
            return Component.literal(text == null ? "" : text);
        }
        String[] parts = text.substring(1).split(String.valueOf(SEPARATOR), -1);
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            String arg = parts[i];
            args[i - 1] = !arg.isEmpty() && arg.charAt(0) == NESTED_KEY
                ? Component.translatable(arg.substring(1)) : arg;
        }
        return Component.translatable(parts[0], args);
    }

    private static String clean(String text) {
        return text.replace(String.valueOf(KEY), "")
            .replace(String.valueOf(SEPARATOR), "");
    }
}
