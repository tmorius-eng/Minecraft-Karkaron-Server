package mn.suld.api.worldbuild.schematic;

import mn.suld.api.nbt.Nbt;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * A block volume read from an authored schematic: Sponge {@code .schem} (v2 and v3, as written by
 * WorldEdit, FAWE and Axiom) or Litematica {@code .litematic} (all regions merged). Blocks are
 * block-data strings; air is not stored. Coordinates start at (0, 0, 0) = the volume's minimum corner.
 */
public final class Schematic {

    private final int width, height, length;
    private final int dataVersion;
    private final Map<Long, String> blocks;
    private final String format;

    private Schematic(int width, int height, int length, int dataVersion, Map<Long, String> blocks, String format) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.dataVersion = dataVersion;
        this.blocks = blocks;
        this.format = format;
    }

    public int width() { return width; }
    public int height() { return height; }
    public int length() { return length; }
    public int dataVersion() { return dataVersion; }
    public String format() { return format; }
    public Map<Long, String> blocks() { return blocks; }

    public static long key(int x, int y, int z) {
        return ((long) x << 40) | ((long) y << 20) | z;
    }

    public static int kx(long k) { return (int) (k >>> 40); }
    public static int ky(long k) { return (int) ((k >>> 20) & 0xFFFFF); }
    public static int kz(long k) { return (int) (k & 0xFFFFF); }

    public static Schematic read(InputStream in) throws IOException {
        Nbt.Root root = Nbt.read(in);
        Map<String, Object> m = root.value();
        if (m.containsKey("Regions")) return litematic(m);
        Map<String, Object> s = Nbt.compound(m, "Schematic");
        if (s != null) return sponge(s, 3);                 // v3: { Schematic: { ... } }
        if (m.containsKey("Palette") || m.containsKey("BlockData")) return sponge(m, 2);
        if (m.containsKey("Blocks") && m.get("Blocks") instanceof Map) return sponge(m, 3);
        if (m.containsKey("Materials")) throw new IOException("legacy MCEdit .schematic (numeric ids) is not supported; re-save as .schem");
        throw new IOException("unrecognised schematic format (keys " + m.keySet() + ")");
    }

    private static Schematic sponge(Map<String, Object> s, int version) throws IOException {
        int w = Nbt.integer(s, "Width", 0) & 0xFFFF, h = Nbt.integer(s, "Height", 0) & 0xFFFF, l = Nbt.integer(s, "Length", 0) & 0xFFFF;
        Map<String, Object> palette;
        byte[] data;
        if (version == 3) {
            Map<String, Object> b = Nbt.compound(s, "Blocks");
            if (b == null) throw new IOException("Sponge v3 schematic without Blocks");
            palette = Nbt.compound(b, "Palette");
            data = (byte[]) b.get("Data");
        } else {
            palette = Nbt.compound(s, "Palette");
            data = (byte[]) s.get("BlockData");
        }
        if (palette == null || data == null) throw new IOException("schematic has no palette/data");
        String[] byId = new String[palette.size() + 1];
        int max = 0;
        for (Map.Entry<String, Object> e : palette.entrySet()) {
            int id = ((Number) e.getValue()).intValue();
            if (id >= byId.length) byId = java.util.Arrays.copyOf(byId, id + 1);
            byId[id] = e.getKey();
            max = Math.max(max, id);
        }
        Map<Long, String> blocks = new HashMap<>();
        int i = 0, index = 0, total = w * h * l;
        while (i < data.length && index < total) {
            int value = 0, shift = 0, b;
            do {
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            int y = index / (w * l), rem = index % (w * l), z = rem / w, x = rem % w;
            String block = value < byId.length ? byId[value] : null;
            if (block != null && !isAir(block)) blocks.put(key(x, y, z), block);
            index++;
        }
        return new Schematic(w, h, l, Nbt.integer(s, "DataVersion", 0), blocks, "sponge-v" + version);
    }

    private static Schematic litematic(Map<String, Object> m) throws IOException {
        Map<String, Object> regions = Nbt.compound(m, "Regions");
        record Placed(int x, int y, int z, String b) {}
        List<Placed> all = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Object ro : regions.values()) {
            @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) ro;
            Map<String, Object> pos = Nbt.compound(r, "Position"), size = Nbt.compound(r, "Size");
            int px = Nbt.integer(pos, "x", 0), py = Nbt.integer(pos, "y", 0), pz = Nbt.integer(pos, "z", 0);
            int sx = Nbt.integer(size, "x", 0), sy = Nbt.integer(size, "y", 0), sz = Nbt.integer(size, "z", 0);
            // negative sizes extend toward -axis from the position
            int ox = sx < 0 ? px + sx + 1 : px, oy = sy < 0 ? py + sy + 1 : py, oz = sz < 0 ? pz + sz + 1 : pz;
            int w = Math.abs(sx), h = Math.abs(sy), l = Math.abs(sz);
            List<Object> pal = Nbt.list(r, "BlockStatePalette");
            String[] states = new String[pal.size()];
            for (int i = 0; i < pal.size(); i++) {
                @SuppressWarnings("unchecked") Map<String, Object> e = (Map<String, Object>) pal.get(i);
                String name = Nbt.string(e, "Name");
                Map<String, Object> props = Nbt.compound(e, "Properties");
                if (props != null && !props.isEmpty()) {
                    StringJoiner j = new StringJoiner(",", name + "[", "]");
                    props.forEach((k, v) -> j.add(k + "=" + v));
                    name = j.toString();
                }
                states[i] = name;
            }
            long[] bits = (long[]) r.get("BlockStates");
            int bpe = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, states.length - 1)));
            long mask = (1L << bpe) - 1;
            long total = (long) w * h * l;
            for (long idx = 0; idx < total; idx++) {
                long bit = idx * bpe;
                int word = (int) (bit >>> 6), off = (int) (bit & 63);
                long v = bits[word] >>> off;
                if (off + bpe > 64) v |= bits[word + 1] << (64 - off);
                int id = (int) (v & mask);
                if (id >= states.length) continue;
                String b = states[id];
                if (isAir(b)) continue;
                int y = (int) (idx / ((long) w * l)), rem = (int) (idx % ((long) w * l)), z = rem / w, x = rem % w;
                int X = ox + x, Y = oy + y, Z = oz + z;
                all.add(new Placed(X, Y, Z, b));
                minX = Math.min(minX, X); minY = Math.min(minY, Y); minZ = Math.min(minZ, Z);
                maxX = Math.max(maxX, X); maxY = Math.max(maxY, Y); maxZ = Math.max(maxZ, Z);
            }
        }
        if (all.isEmpty()) throw new IOException("litematic has no blocks");
        Map<Long, String> blocks = new HashMap<>();
        for (Placed p : all) blocks.put(key(p.x - minX, p.y - minY, p.z - minZ), p.b);
        return new Schematic(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1,
                Nbt.integer(m, "MinecraftDataVersion", 0), blocks, "litematic");
    }

    private static boolean isAir(String b) {
        return b.equals("minecraft:air") || b.equals("minecraft:cave_air") || b.equals("minecraft:void_air")
                || b.equals("minecraft:structure_void");
    }
}
