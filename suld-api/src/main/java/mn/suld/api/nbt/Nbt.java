package mn.suld.api.nbt;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal, dependency-free NBT reader (Java edition, big-endian), enough to read schematic files
 * (.schem, .litematic, .nbt). Compounds become {@code Map<String,Object>}, lists {@code List<Object>},
 * numbers their boxed Java type, byte/int/long arrays {@code byte[]/int[]/long[]}, strings String.
 * Input may be gzip-compressed (detected by magic number).
 */
public final class Nbt {

    private Nbt() {
    }

    /** A named root tag. */
    public record Root(String name, Map<String, Object> value) {
    }

    public static Root read(InputStream raw) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw);
        in.mark(2);
        int b0 = in.read(), b1 = in.read();
        in.reset();
        InputStream src = (b0 == 0x1f && b1 == 0x8b) ? new GZIPInputStream(in) : in;
        DataInputStream d = new DataInputStream(new BufferedInputStream(src));
        int type = d.readUnsignedByte();
        if (type != 10) throw new IOException("root is not a compound (tag " + type + ")");
        String name = d.readUTF();
        @SuppressWarnings("unchecked")
        Map<String, Object> v = (Map<String, Object>) payload(d, 10, 0);
        return new Root(name, v);
    }

    private static Object payload(DataInputStream d, int type, int depth) throws IOException {
        if (depth > 512) throw new IOException("NBT nesting too deep");
        switch (type) {
            case 1: return d.readByte();
            case 2: return d.readShort();
            case 3: return d.readInt();
            case 4: return d.readLong();
            case 5: return d.readFloat();
            case 6: return d.readDouble();
            case 7: {
                int n = len(d);
                byte[] a = new byte[n];
                d.readFully(a);
                return a;
            }
            case 8: {
                int n = d.readUnsignedShort();
                byte[] a = new byte[n];
                d.readFully(a);
                return modifiedUtf8(a);
            }
            case 9: {
                int et = d.readUnsignedByte();
                int n = len(d);
                List<Object> l = new ArrayList<>(Math.min(n, 1 << 16));
                for (int i = 0; i < n; i++) l.add(payload(d, et, depth + 1));
                return l;
            }
            case 10: {
                Map<String, Object> m = new LinkedHashMap<>();
                while (true) {
                    int t = d.readUnsignedByte();
                    if (t == 0) return m;
                    String k = d.readUTF();
                    m.put(k, payload(d, t, depth + 1));
                }
            }
            case 11: {
                int n = len(d);
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = d.readInt();
                return a;
            }
            case 12: {
                int n = len(d);
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = d.readLong();
                return a;
            }
            default:
                throw new IOException("unknown NBT tag " + type);
        }
    }

    private static int len(DataInputStream d) throws IOException {
        int n = d.readInt();
        if (n < 0 || n > 256 * 1024 * 1024) throw new IOException("bad NBT length " + n);
        return n;
    }

    private static String modifiedUtf8(byte[] a) {
        // Java modified UTF-8 differs from UTF-8 only for NUL and supplementary characters.
        return new String(a, StandardCharsets.UTF_8);
    }

    // ---- typed accessors ----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof List ? (List<Object>) o : List.of();
    }

    public static int integer(Map<String, Object> m, String key, int fallback) {
        Object o = m.get(key);
        return o instanceof Number n ? n.intValue() : fallback;
    }

    public static String string(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof String s ? s : null;
    }
}
