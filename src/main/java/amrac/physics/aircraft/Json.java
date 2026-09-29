package amrac.physics.aircraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The physics packages (physics.*) depend on the JDK only: no Minecraft types, Vec3 or Gson, which
 * the regression tools don't have on their classpath.
 */
public final class Json {
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    private final String source;
    private int index;

    private Json(String source) {
        this.source = source;
    }

    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("no document");
        }
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        Json parser = new Json(stripComments(text));
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index < parser.source.length()) {
            throw new JsonException("trailing text at offset " + parser.index);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("expected an object at the top level");
        }
        return (Map<String, Object>) value;
    }

    public static double number(Map<String, Object> object, String key, double fallback) {
        Object value = object == null ? null : object.get(key);
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        Object value = object == null ? null : object.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    public static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object == null ? null : object.get(key);
        return value instanceof String text ? text : fallback;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> parent, String key) {
        Object value = parent == null ? null : parent.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    public static double[] numbers(Map<String, Object> object, String key) {
        Object value = object == null ? null : object.get(key);
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return null;
        }
        double[] out = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Number number)
                || !Double.isFinite(number.doubleValue())) {
                return null;
            }
            out[i] = number.doubleValue();
        }
        return out;
    }

    public static double[][] curve(Map<String, Object> object, String key) {
        Object value = object == null ? null : object.get(key);
        if (!(value instanceof List<?> rows) || rows.isEmpty()) {
            return null;
        }
        double[][] points = new double[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            if (!(rows.get(i) instanceof List<?> pair) || pair.size() != 2 ||
                !(pair.get(0) instanceof Number first) ||
                !(pair.get(1) instanceof Number second)) {
                return null;
            }
            double x = first.doubleValue();
            double y = second.doubleValue();
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return null;
            }
            points[i] = new double[] {x, y};
        }
        return points;
    }

    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == '/' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    while (i < text.length() && text.charAt(i) != '\n') {
                        i++;
                    }
                    out.append('\n');
                    continue;
                }
                if (next == '*') {
                    i += 2;
                    while (i + 1 < text.length() &&
                        !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i++;
                    out.append(' ');
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private Object readValue() {
        if (index >= source.length()) {
            throw new JsonException("document ended early");
        }
        char c = source.charAt(index);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        index++;
        skipWhitespace();
        if (peek() == '}') {
            index++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonException("expected a key at offset " + index);
            }
            String key = readString();
            skipWhitespace();
            if (peek() != ':') {
                throw new JsonException("expected a colon at offset " + index);
            }
            index++;
            skipWhitespace();
            object.put(key, readValue());
            skipWhitespace();
            char c = peek();
            index++;
            if (c == '}') {
                return object;
            }
            if (c != ',') {
                throw new JsonException("expected a comma or brace at offset " +
                    (index - 1));
            }
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return object;
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        index++;
        skipWhitespace();
        if (peek() == ']') {
            index++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = peek();
            index++;
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new JsonException("expected a comma or bracket at offset " +
                    (index - 1));
            }
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return list;
            }
        }
    }

    private String readString() {
        index++;
        StringBuilder text = new StringBuilder();
        while (true) {
            if (index >= source.length()) {
                throw new JsonException("unterminated string");
            }
            char c = source.charAt(index++);
            if (c == '"') {
                return text.toString();
            }
            if (c != '\\') {
                text.append(c);
                continue;
            }
            if (index >= source.length()) {
                throw new JsonException("unterminated escape");
            }
            char escape = source.charAt(index++);
            switch (escape) {
                case '"' -> text.append('"');
                case '\\' -> text.append('\\');
                case '/' -> text.append('/');
                case 'b' -> text.append('\b');
                case 'f' -> text.append('\f');
                case 'n' -> text.append('\n');
                case 'r' -> text.append('\r');
                case 't' -> text.append('\t');
                case 'u' -> {
                    if (index + 4 > source.length()) {
                        throw new JsonException("truncated unicode escape");
                    }
                    text.append((char) Integer.parseInt(
                        source.substring(index, index + 4), 16));
                    index += 4;
                }
                default -> throw new JsonException("bad escape " + escape);
            }
        }
    }

    private Double readNumber() {
        int start = index;
        if (peek() == '-' || peek() == '+') {
            index++;
        }
        while (index < source.length()) {
            char c = source.charAt(index);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' ||
                c == '-' || c == '+') {
                index++;
            } else {
                break;
            }
        }
        if (start == index) {
            throw new JsonException("expected a value at offset " + start);
        }
        try {
            return Double.valueOf(source.substring(start, index));
        } catch (NumberFormatException exception) {
            throw new JsonException("bad number at offset " + start);
        }
    }

    private void expect(String literal) {
        if (!source.startsWith(literal, index)) {
            throw new JsonException("expected " + literal + " at offset " + index);
        }
        index += literal.length();
    }

    private char peek() {
        if (index >= source.length()) {
            throw new JsonException("document ended early");
        }
        return source.charAt(index);
    }

    private void skipWhitespace() {
        while (index < source.length() &&
            Character.isWhitespace(source.charAt(index))) {
            index++;
        }
    }
}
