package amrac.physics.aircraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Console edits are string replacements in the original text, using the same scanner as
 * DocumentTopUp; change how the format is read in both.
 */
public final class DocumentEdit {
    public record Leaf(String name, String path, String value,
                       int valueStart, int valueEnd) {
    }

    private DocumentEdit() {
    }

    public static Map<String, Leaf> leaves(String text) {
        Map<String, Leaf> out = new LinkedHashMap<>();
        List<String> path = new ArrayList<>();
        int i = 0;
        int depth = 0;
        final int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                i = endOfLine(text, i);
                continue;
            }
            if (c == '"') {
                int keyEnd = DocumentTopUp.endOfString(text, i);
                String key = text.substring(i + 1, keyEnd - 1);
                int colon = DocumentTopUp.skipSpace(text, keyEnd);
                if (colon < n && text.charAt(colon) == ':') {
                    int valueStart = DocumentTopUp.skipSpace(text, colon + 1);
                    int valueEnd = DocumentTopUp.endOfValue(text, valueStart);
                    if (valueEnd > valueStart) {
                        char first = text.charAt(valueStart);
                        if (first == '{') {
                            path.add(key);
                            depth++;
                            i = valueStart + 1;
                            continue;
                        }
                        String full = path.isEmpty() ? key
                            : String.join(".", path) + "." + key;
                        out.putIfAbsent(key, new Leaf(key, full,
                            text.substring(valueStart, valueEnd).trim(),
                            valueStart, valueEnd));
                        i = valueEnd;
                        continue;
                    }
                }
                i = keyEnd;
                continue;
            }
            if (c == '}') {
                if (depth > 0 && !path.isEmpty()) {
                    path.remove(path.size() - 1);
                    depth--;
                }
            }
            ++i;
        }
        return out;
    }

    public static Leaf find(String text, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Map<String, Leaf> leaves = leaves(text);
        Leaf exact = leaves.get(name);
        if (exact != null) {
            return exact;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (Leaf leaf : leaves.values()) {
            if (leaf.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return leaf;
            }
        }
        return null;
    }

    public static List<String> complete(String text, String prefix) {
        String wanted = prefix == null ? ""
            : prefix.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : leaves(text).keySet()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(wanted)) {
                out.add(name);
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static List<String> complete(String text, String prefix,
                                        Map<String, String> superseded) {
        Map<String, Leaf> leaves = leaves(text);
        List<String> out = new ArrayList<>();
        for (String name : complete(text, prefix)) {
            if (!isSuperseded(leaves, leaves.get(name), superseded)) {
                out.add(name);
            }
        }
        return out;
    }

    public static String liveName(String text, String name,
                                  Map<String, String> superseded) {
        if (text == null || name == null) {
            return name;
        }
        Map<String, Leaf> leaves = leaves(text);
        Leaf leaf = find(text, name);
        if (!isSuperseded(leaves, leaf, superseded)) {
            return name;
        }
        return byPath(leaves, superseded.get(leaf.path())).name();
    }

    private static boolean isSuperseded(Map<String, Leaf> leaves, Leaf leaf,
                                        Map<String, String> superseded) {
        if (leaf == null || superseded == null) {
            return false;
        }
        String replacement = superseded.get(leaf.path());
        return replacement != null && byPath(leaves, replacement) != null;
    }

    private static Leaf byPath(Map<String, Leaf> leaves, String path) {
        for (Leaf leaf : leaves.values()) {
            if (leaf.path().equals(path)) {
                return leaf;
            }
        }
        return null;
    }

    public static String setValue(String text, String name, String value) {
        Leaf leaf = find(text, name);
        if (leaf == null || value == null) {
            return null;
        }
        return text.substring(0, leaf.valueStart()) + value.trim()
            + text.substring(leaf.valueEnd());
    }

    private static int endOfLine(String text, int i) {
        while (i < text.length() && text.charAt(i) != '\n') {
            ++i;
        }
        return i;
    }
}
