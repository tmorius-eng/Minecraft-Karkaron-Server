package mn.suld.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader/writer for the report (keeps key order; no dependency). */
final class MiniJson {

    private MiniJson() {
    }

    static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb, 0);
        return sb.toString();
    }

    private static void write(Object o, StringBuilder sb, int indent) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            quote(s, sb);
        } else if (o instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) sb.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append(d.longValue());
            else sb.append(d);
        } else if (o instanceof Float f) {
            write((double) f, sb, indent);
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(sb, indent + 2);
                quote(String.valueOf(e.getKey()), sb);
                sb.append(": ");
                write(e.getValue(), sb, indent + 2);
                if (++i < m.size()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (o instanceof List<?> l) {
            boolean flat = l.stream().allMatch(x -> x == null || x instanceof Number || x instanceof String || x instanceof Boolean);
            if (flat) {
                sb.append('[');
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) sb.append(", ");
                    write(l.get(i), sb, indent);
                }
                sb.append(']');
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                pad(sb, indent + 2);
                write(l.get(i), sb, indent + 2);
                if (i + 1 < l.size()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append(']');
        } else {
            quote(o.toString(), sb);
        }
    }

    private static void pad(StringBuilder sb, int n) {
        sb.append(" ".repeat(n));
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (char c : s.toCharArray()) {
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

    static Object parse(String s) {
        int[] pos = {0};
        Object o = value(s, pos);
        return o;
    }

    private static void ws(String s, int[] p) {
        while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++;
    }

    private static Object value(String s, int[] p) {
        ws(s, p);
        char c = s.charAt(p[0]);
        if (c == '{') {
            p[0]++;
            Map<String, Object> m = new LinkedHashMap<>();
            ws(s, p);
            if (s.charAt(p[0]) == '}') {
                p[0]++;
                return m;
            }
            while (true) {
                ws(s, p);
                String k = string(s, p);
                ws(s, p);
                p[0]++; // :
                m.put(k, value(s, p));
                ws(s, p);
                char n = s.charAt(p[0]++);
                if (n == '}') return m;
            }
        }
        if (c == '[') {
            p[0]++;
            List<Object> l = new ArrayList<>();
            ws(s, p);
            if (s.charAt(p[0]) == ']') {
                p[0]++;
                return l;
            }
            while (true) {
                l.add(value(s, p));
                ws(s, p);
                char n = s.charAt(p[0]++);
                if (n == ']') return l;
            }
        }
        if (c == '"') return string(s, p);
        if (s.startsWith("true", p[0])) {
            p[0] += 4;
            return true;
        }
        if (s.startsWith("false", p[0])) {
            p[0] += 5;
            return false;
        }
        if (s.startsWith("null", p[0])) {
            p[0] += 4;
            return null;
        }
        int st = p[0];
        while (p[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(p[0])) >= 0) p[0]++;
        String num = s.substring(st, p[0]);
        if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
        return Long.parseLong(num);
    }

    private static String string(String s, int[] p) {
        StringBuilder sb = new StringBuilder();
        p[0]++; // opening quote
        while (true) {
            char c = s.charAt(p[0]++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(p[0]++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16));
                        p[0] += 4;
                    }
                    default -> sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
    }
}
