package mn.suld.plugin.map;

import mn.suld.api.map.MapColors;
import mn.suld.api.map.MapColumn;
import mn.suld.api.map.NaturalPalette;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a chunk snapshot the way a vanilla map reads the world (docs/WORLD_MAP.md): for each column the highest block
 * that has a map colour (air, glass and the like have none: look further down), its height, and for water how deep it
 * is. The block's colour is Minecraft's own ({@code BlockData#getMapColor}), matched to the palette id once per block
 * state. Pure reads of an immutable snapshot, so any thread may call it.
 */
final class ChunkColors {

    /**
     * How far below the surface a column looks for a coloured block, and how deep water is measured. The snapshot's
     * height is Minecraft's MOTION_BLOCKING heightmap, which skips grass, flowers, snow layers and carpets; a vanilla
     * map starts from every block, so the look starts {@code ABOVE} blocks higher.
     */
    static final int LOOK_DOWN = 24, MAX_DEPTH = 32, ABOVE = 2;

    /** Map colour (RGB, as {@code MapColor#col}) → map colour id, from the 1.21 table in suld-api. */
    private static final Map<Integer, Integer> ID_BY_RGB = new HashMap<>();
    private static final Map<BlockData, Integer> ID_BY_STATE = new ConcurrentHashMap<>();

    static {
        for (int id = 1; id < MapColors.IDS; id++) ID_BY_RGB.putIfAbsent(MapColors.baseRgb(id), id);
    }

    private ChunkColors() {
    }

    /** The map colour id of a block state (0 = none: the map looks through it). */
    static int id(BlockData data) {
        Integer cached = ID_BY_STATE.get(data);
        if (cached != null) return cached;
        int id;
        try {
            int rgb = data.getMapColor().asRGB() & 0xFFFFFF;
            id = rgb == 0 ? 0 : ID_BY_RGB.getOrDefault(rgb, 0);
        } catch (RuntimeException e) {
            id = 0;
        }
        ID_BY_STATE.put(data, id);
        return id;
    }

    private static final Map<Material, String> BLOCK_KEYS = new ConcurrentHashMap<>();
    private static final Map<Biome, String> BIOME_KEYS = new ConcurrentHashMap<>();

    /**
     * The 256 packed columns of a snapshot (row by row: index z·16 + x); 0 where nothing coloured was found.
     * {@code minY} is the world's lowest block, {@code maxY} one above its highest. With a {@code natural} palette the
     * snapshot must hold its biomes: grass, leaves and water then take the colour of the biome they stand in.
     */
    static int[] columns(ChunkSnapshot s, int minY, int maxY, NaturalPalette natural) {
        int[] out = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top;
                try {
                    top = s.getHighestBlockYAt(x, z);
                } catch (RuntimeException e) {
                    continue;
                }
                if (top < minY) continue; // an empty column
                for (int y = Math.min(maxY - 1, top + ABOVE), stop = Math.max(minY, top - LOOK_DOWN); y >= stop; y--) {
                    BlockData data = s.getBlockData(x, y, z);
                    int id = id(data);
                    if (id == 0) continue;
                    int depth = 0;
                    if (id == MapColors.WATER) {
                        depth = 1;
                        for (int d = y - 1, end = Math.max(minY, y - MAX_DEPTH); d >= end && id(s.getBlockData(x, d, z)) == MapColors.WATER; d--) depth++;
                    }
                    byte px = 0;
                    if (natural != null) {
                        String biome = BIOME_KEYS.computeIfAbsent(s.getBiome(x, y, z), b -> b.getKey().toString());
                        px = depth > 0 ? natural.water(biome)
                                : natural.pixel(BLOCK_KEYS.computeIfAbsent(data.getMaterial(), m -> m.getKey().toString()), biome);
                    }
                    if (px == 0) px = MapColors.pixel(id, MapColors.SHADE_NORMAL);
                    out[(z << 4) | x] = MapColumn.packPixel(px, depth, y);
                    break;
                }
            }
        }
        return out;
    }
}
