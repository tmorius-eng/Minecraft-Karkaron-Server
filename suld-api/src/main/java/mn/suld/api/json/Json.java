package mn.suld.api.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON reader (RFC 8259 subset sufficient for SÜLD data files):
 * objects → {@code Map<String,Object>} (insertion-ordered), arrays → {@code List<Object>}, strings,
 * numbers ({@code Long} when integral, else {@code Double}), booleans, null. suld-api must stay free of
 * third-party libraries, and the world assets (master plan, points, slices) are JSON.
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.error("trailing content");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object o) {
        if (!(o instanceof Map)) throw new IllegalArgumentException("expected JSON object");
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object o) {
        if (!(o instanceof List)) throw new IllegalArgumentException("expected JSON array");
        return (List<Object>) o;
    }

    public static int integer(Object o) {
        if (o instanceof Number n) return Math.toIntExact(Math.round(n.doubleValue()));
        throw new IllegalArgumentException("expected number, got " + o);
    }

    private Object value() {
        if (i >= s.length()) throw error("unexpected end");
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> obj();
            case '[' -> arr();
            case '"' -> str();
            case 't' -> lit("true", Boolean.TRUE);
            case 'f' -> lit("false", Boolean.FALSE);
            case 'n' -> lit("null", null);
            default -> num();
        };
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            if (peek() != '"') throw error("expected key");
            String k = str();
            ws();
            expect(':');
            ws();
            m.put(k, value());
            ws();
            char c = next();
            if (c == '}') return m;
            if (c != ',') throw error("expected , or }");
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (peek() == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = next();
            if (c == ']') return l;
            if (c != ',') throw error("expected , or ]");
        }
    }

    private String str() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> b.append(e);
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error("bad \\u escape");
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error("bad escape");
                }
            } else {
                b.append(c);
            }
        }
    }

    private Object num() {
        int start = i;
        if (peek() == '-') i++;
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
        String t = s.substring(start, i);
        if (t.isEmpty() || t.equals("-")) throw error("bad value");
        try {
            if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
            return Long.parseLong(t);
        } catch (NumberFormatException ex) {
            throw error("bad number " + t);
        }
    }

    private Object lit(String word, Object v) {
        if (!s.startsWith(word, i)) throw error("bad literal");
        i += word.length();
        return v;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private char next() {
        if (i >= s.length()) throw error("unexpected end");
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (next() != c) throw error("expected " + c);
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("JSON " + msg + " at " + i);
    }
}
