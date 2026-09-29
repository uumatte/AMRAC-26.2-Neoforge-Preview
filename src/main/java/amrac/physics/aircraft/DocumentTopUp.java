package amrac.physics.aircraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Existing config files are topped up only by whole top-level sections; a new parameter must go in
 * a new top-level block (like flightControl, postStall), or old files never get it.
 */
public final class DocumentTopUp {
    private DocumentTopUp() {
    }

    public record Result(String text, List<String> added) {
        public boolean changed() {
            return !added.isEmpty();
        }
    }

    public static Result topUp(String existing, String shipped) {
        List<String> added = new ArrayList<>();
        Map<String, Object> haveTree;
        Map<String, Object> wantTree;
        try {
            haveTree = Json.parseObject(existing);
            wantTree = Json.parseObject(shipped);
        } catch (RuntimeException malformed) {
            return new Result(existing, added);
        }

        Map<String, String> sections = sections(shipped);
        StringBuilder additions = new StringBuilder();
        for (Map.Entry<String, String> section : sections.entrySet()) {
            if (haveTree.containsKey(section.getKey())
                || !wantTree.containsKey(section.getKey())) {
                continue;
            }
            if (!added.isEmpty()) {
                additions.append(',');
            }
            additions.append("\n\n").append(section.getValue());
            added.add(section.getKey());
        }
        if (added.isEmpty()) {
            return new Result(existing, added);
        }

        int close = existing.lastIndexOf('}');
        if (close < 0) {
            return new Result(existing, new ArrayList<>());
        }
        String head = trimTrailing(existing.substring(0, close));
        if (!head.endsWith(",")) {
            head = head + ",";
        }
        String banner = "\n\n  // ---- Added by a mod update ---------------------"
            + "-------------\n  // Sections your file did not have. Everything above"
            + " is exactly as\n  // you left it; these are the shipped defaults and"
            + " are yours to edit.";
        return new Result(head + banner + additions + "\n" + existing.substring(close),
            added);
    }

    static Map<String, String> sections(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        int depth = 0;
        int i = 0;
        final int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"') {
                int keyStart = i;
                int keyEnd = endOfString(text, i);
                if (depth == 1) {
                    String key = text.substring(keyStart + 1, keyEnd - 1);
                    int colon = skipSpace(text, keyEnd);
                    if (colon < n && text.charAt(colon) == ':') {
                        int valueStart = skipSpace(text, colon + 1);
                        int valueEnd = endOfValue(text, valueStart);
                        if (valueEnd > valueStart) {
                            out.put(key, text.substring(
                                startOfLeadingComments(text, keyStart), valueEnd));
                        }
                        i = valueEnd;
                        continue;
                    }
                }
                i = keyEnd;
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                i = endOfLine(text, i);
                continue;
            }
            if (c == '{' || c == '[') {
                ++depth;
            } else if (c == '}' || c == ']') {
                --depth;
            }
            ++i;
        }
        return out;
    }

    private static int startOfLeadingComments(String text, int keyStart) {
        int lineStart = startOfLine(text, keyStart);
        int candidate = lineStart;
        while (lineStart > 0) {
            int previousStart = startOfLine(text, lineStart - 1);
            String line = text.substring(previousStart, lineStart).trim();
            if (!line.startsWith("//")) {
                break;
            }
            candidate = previousStart;
            lineStart = previousStart;
        }
        return candidate;
    }

    private static int startOfLine(String text, int index) {
        int i = Math.min(index, text.length());
        while (i > 0 && text.charAt(i - 1) != '\n') {
            --i;
        }
        return i;
    }

    private static int endOfLine(String text, int index) {
        int i = index;
        while (i < text.length() && text.charAt(i) != '\n') {
            ++i;
        }
        return i;
    }

    static int endOfString(String text, int i) {
        int j = i + 1;
        while (j < text.length()) {
            char c = text.charAt(j);
            if (c == '\\') {
                j += 2;
                continue;
            }
            if (c == '"') {
                return j + 1;
            }
            ++j;
        }
        return text.length();
    }

    static int skipSpace(String text, int i) {
        int j = i;
        while (j < text.length()) {
            char c = text.charAt(j);
            if (c == '/' && j + 1 < text.length() && text.charAt(j + 1) == '/') {
                j = endOfLine(text, j);
                continue;
            }
            if (!Character.isWhitespace(c)) {
                return j;
            }
            ++j;
        }
        return text.length();
    }

    static int endOfValue(String text, int i) {
        if (i >= text.length()) {
            return i;
        }
        char first = text.charAt(i);
        if (first == '{' || first == '[') {
            int depth = 0;
            int j = i;
            while (j < text.length()) {
                char c = text.charAt(j);
                if (c == '"') {
                    j = endOfString(text, j);
                    continue;
                }
                if (c == '/' && j + 1 < text.length() && text.charAt(j + 1) == '/') {
                    j = endOfLine(text, j);
                    continue;
                }
                if (c == '{' || c == '[') {
                    ++depth;
                } else if (c == '}' || c == ']') {
                    --depth;
                    if (depth == 0) {
                        return j + 1;
                    }
                }
                ++j;
            }
            return text.length();
        }
        int j = i;
        while (j < text.length()) {
            char c = text.charAt(j);
            if (c == '"') {
                j = endOfString(text, j);
                continue;
            }
            if (c == '/' && j + 1 < text.length() && text.charAt(j + 1) == '/') {
                break;
            }
            if (c == ',' || c == '}' || c == ']' || c == '\n') {
                break;
            }
            ++j;
        }
        return trimTrailing(text.substring(i, j)).length() + i;
    }

    private static String trimTrailing(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            --end;
        }
        return text.substring(0, end);
    }
}
