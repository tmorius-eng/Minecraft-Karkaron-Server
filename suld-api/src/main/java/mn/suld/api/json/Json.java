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

    /**
     * Compact JSON text for maps (keys in iteration order), lists, strings, numbers (integral doubles without a
     * fraction), booleans and null. Non-finite numbers are refused: they have no JSON form.
     */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String str) {
            quote(sb, str);
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (!Double.isFinite(d)) throw new IllegalArgumentException("non-finite number");
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append(d);
        } else if (v instanceof Number n) {
            sb.append(n.longValue());
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
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
