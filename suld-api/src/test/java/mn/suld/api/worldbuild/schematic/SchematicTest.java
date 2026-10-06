package mn.suld.api.worldbuild.schematic;

import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.ModuleContext;
import mn.suld.api.worldbuild.Palette;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SchematicTest {

    // ---- a tiny NBT writer for test fixtures -------------------------------------------------

    private static void tag(DataOutputStream o, String name, Object v) throws IOException {
        int t = type(v);
        o.writeByte(t);
        o.writeUTF(name);
        payload(o, v);
    }

    private static int type(Object v) {
        if (v instanceof Byte) return 1;
        if (v instanceof Short) return 2;
        if (v instanceof Integer) return 3;
        if (v instanceof Long) return 4;
        if (v instanceof byte[]) return 7;
        if (v instanceof String) return 8;
        if (v instanceof List) return 9;
        if (v instanceof Map) return 10;
        if (v instanceof int[]) return 11;
        if (v instanceof long[]) return 12;
        throw new IllegalArgumentException(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private static void payload(DataOutputStream o, Object v) throws IOException {
        switch (type(v)) {
            case 1 -> o.writeByte((Byte) v);
            case 2 -> o.writeShort((Short) v);
            case 3 -> o.writeInt((Integer) v);
            case 4 -> o.writeLong((Long) v);
            case 7 -> { o.writeInt(((byte[]) v).length); o.write((byte[]) v); }
            case 8 -> o.writeUTF((String) v);
            case 9 -> {
                List<Object> l = (List<Object>) v;
                o.writeByte(l.isEmpty() ? 0 : type(l.get(0)));
                o.writeInt(l.size());
                for (Object e : l) payload(o, e);
            }
            case 10 -> {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) tag(o, e.getKey(), e.getValue());
                o.writeByte(0);
            }
            case 11 -> { o.writeInt(((int[]) v).length); for (int i : (int[]) v) o.writeInt(i); }
            case 12 -> { o.writeInt(((long[]) v).length); for (long l : (long[]) v) o.writeLong(l); }
            default -> throw new IllegalStateException();
        }
    }

    private static byte[] gz(String rootName, Map<String, Object> root) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(b))) {
            tag(o, rootName, root);
        }
        return b.toByteArray();
    }

    /** 3 × 2 × 2 volume: a stone floor, one oak stair facing north at (2,1,0), air elsewhere. */
    private static Map<String, Object> spongeV3() {
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:stone", 1);
        palette.put("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 2);
        int w = 3, h = 2, l = 2;
        byte[] data = new byte[w * h * l];
        for (int z = 0; z < l; z++) for (int x = 0; x < w; x++) data[x + z * w] = 1;   // y = 0 floor
        data[2 + 0 * w + 1 * w * l] = 2;                                                 // (2,1,0)
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("Palette", palette);
        blocks.put("Data", data);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("Version", 3);
        s.put("DataVersion", 4325);
        s.put("Width", (short) w);
        s.put("Height", (short) h);
        s.put("Length", (short) l);
        s.put("Blocks", blocks);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Schematic", s);
        return root;
    }

    @Test
    void readsSpongeV3() throws IOException {
        Schematic s = Schematic.read(new ByteArrayInputStream(gz("", spongeV3())));
        assertEquals("sponge-v3", s.format());
        assertEquals(3, s.width());
        assertEquals(7, s.blocks().size());
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", s.blocks().get(Schematic.key(2, 1, 0)));
        assertEquals("minecraft:stone", s.blocks().get(Schematic.key(0, 0, 1)));
    }

    @Test
    void readsSpongeV2WithVarints() throws IOException {
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        for (int i = 1; i < 200; i++) palette.put("minecraft:block_" + i, i);   // ids >= 128 need two varint bytes
        byte[] data = {(byte) 0xC7, 0x01, 0x05};                                   // 199, 5
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("Version", 2);
        s.put("Width", (short) 2);
        s.put("Height", (short) 1);
        s.put("Length", (short) 1);
        s.put("Palette", palette);
        s.put("BlockData", data);
        Schematic sc = Schematic.read(new ByteArrayInputStream(gz("Schematic", s)));
        assertEquals("sponge-v2", sc.format());
        assertEquals("minecraft:block_199", sc.blocks().get(Schematic.key(0, 0, 0)));
        assertEquals("minecraft:block_5", sc.blocks().get(Schematic.key(1, 0, 0)));
    }

    @Test
    void readsLitematicPackedStates() throws IOException {
        // 2 × 1 × 2 region; palette air, stone, dirt (2 bits per entry); entries: stone, dirt, air, stone
        Map<String, Object> air = Map.of("Name", "minecraft:air");
        Map<String, Object> stone = Map.of("Name", "minecraft:stone");
        Map<String, Object> dirt = Map.of("Name", "minecraft:dirt");
        long packed = 1L | (2L << 2) | (0L << 4) | (1L << 6);
        Map<String, Object> region = new LinkedHashMap<>();
        region.put("Position", Map.of("x", 0, "y", 0, "z", 0));
        region.put("Size", Map.of("x", 2, "y", 1, "z", 2));
        region.put("BlockStatePalette", List.of(air, stone, dirt));
        region.put("BlockStates", new long[]{packed});
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("MinecraftDataVersion", 4325);
        root.put("Regions", Map.of("main", region));
        Schematic s = Schematic.read(new ByteArrayInputStream(gz("", root)));
        assertEquals("litematic", s.format());
        assertEquals("minecraft:stone", s.blocks().get(Schematic.key(0, 0, 0)));
        assertEquals("minecraft:dirt", s.blocks().get(Schematic.key(1, 0, 0)));
        assertNull(s.blocks().get(Schematic.key(0, 0, 1)));
        assertEquals("minecraft:stone", s.blocks().get(Schematic.key(1, 0, 1)));
    }

    @Test
    void moduleCentresAndTurnsTheFrontToSouth() throws IOException {
        Schematic s = Schematic.read(new ByteArrayInputStream(gz("", spongeV3())));
        // authored facing north: the module turns it 180° so its front faces +z
        SchematicModule m = new SchematicModule("schem:test", s, Facing.NORTH, 0, Layer.STRUCTURE);
        ModuleCanvas c = new ModuleCanvas(Palette.KHARKHORUM, Layer.STRUCTURE);
        m.build(c, new ModuleContext(Map.of("replace", Map.of("minecraft:stone", "minecraft:mud_bricks")), Palette.KHARKHORUM, 1));
        // the stair at (2,1,0) is centred to (1,1,-1), then rotated 180° to (-1,1,1), facing south
        assertEquals("minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", c.get(-1, 1, 1));
        assertEquals("minecraft:mud_bricks", c.get(0, 0, 0));
        assertEquals("minecraft:air", c.get(0, 1, 0));   // the empty cells are cleared
    }

    @Test
    void rejectsLegacyAndUnknownFiles() {
        assertThrows(IOException.class, () -> Schematic.read(new ByteArrayInputStream(gz("Schematic", Map.of("Materials", "Alpha")))));
        assertThrows(IOException.class, () -> Schematic.read(new ByteArrayInputStream(gz("", Map.of("Foo", 1)))));
    }
}
