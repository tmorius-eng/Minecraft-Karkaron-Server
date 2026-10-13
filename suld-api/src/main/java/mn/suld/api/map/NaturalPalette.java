package mn.suld.api.map;

import mn.suld.api.json.Json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The atlas's natural colours (docs/WORLD_MAP.md): grass, leaves and water take the colour of the biome they really stand
 * in, the way the game draws them, instead of the one green and one blue of a vanilla map. The data is Minecraft's own
 * ({@code map/colors.json}, written by {@code tools/map/gen_map_colors.py} from the client jar): every biome's grass,
 * foliage and water colour, and for each tinted block its tint and texture brightness. A block's colour is
 * {@code tint × brightness × gain}, matched to the nearest map byte ({@link MapColors#nearest}). The gains lift the
 * dark textures to the brightness of a vanilla map, so the atlas stays as readable as one.
 * <p>
 * Every other block keeps its vanilla map colour (stone grey, sand, terracotta, snow…): the caller draws it when
 * {@link #pixel} answers 0. Thread-safe.
 */
public final class NaturalPalette {

    /** Where a tinted block's colour comes from. */
    public enum Tint { GRASS, FOLIAGE, WATER, FIXED }

    static final double GRASS_GAIN = 1.55, FOLIAGE_GAIN = 1.1, WATER_GAIN = 1.3;
    private static final String FALLBACK_BIOME = "minecraft:plains";

    private record Tinted(Tint tint, int rgb, int brightness) {
    }

    private final Map<String, int[]> biomes;
    private final Map<String, Tinted> blocks;
    private final Map<String, Byte> cache = new ConcurrentHashMap<>();

    private NaturalPalette(Map<String, int[]> biomes, Map<String, Tinted> blocks) {
        this.biomes = biomes;
        this.blocks = blocks;
    }

    /** Reads {@code map/colors.json} (format 1). */
    public static NaturalPalette parse(String json) {
        Map<String, Object> root = Json.object(Json.parse(json));
        Object format = root.get("format");
        if (!(format instanceof Number n) || n.intValue() != 1) throw new IllegalArgumentException("colors.json format " + format);
        Map<String, int[]> biomes = new HashMap<>();
        for (Map.Entry<String, Object> e : Json.object(root.get("biomes")).entrySet()) {
            List<Object> c = Json.array(e.getValue());
            if (c.size() != 3) throw new IllegalArgumentException("biome " + e.getKey() + ": three colours expected");
            biomes.put(e.getKey(), new int[]{hex(c.get(0)), hex(c.get(1)), hex(c.get(2))});
        }
        if (!biomes.containsKey(FALLBACK_BIOME)) throw new IllegalArgumentException("colors.json has no " + FALLBACK_BIOME);
        Map<String, Tinted> blocks = new HashMap<>();
        for (Map.Entry<String, Object> e : Json.object(root.get("tinted")).entrySet()) {
            List<Object> t = Json.array(e.getValue());
            String tint = String.valueOf(t.get(0));
            int brightness = ((Number) t.get(1)).intValue();
            if (brightness < 1 || brightness > 255) throw new IllegalArgumentException("block " + e.getKey() + ": brightness " + brightness);
            Tinted v = tint.startsWith("#") ? new Tinted(Tint.FIXED, hex(tint.substring(1)), brightness)
                    : new Tinted(Tint.valueOf(tint.toUpperCase(java.util.Locale.ROOT)), 0, brightness);
            blocks.put(e.getKey(), v);
        }
        if (!blocks.containsKey("minecraft:water")) throw new IllegalArgumentException("colors.json has no minecraft:water");
        return new NaturalPalette(Map.copyOf(biomes), Map.copyOf(blocks));
    }

    private static int hex(Object o) {
        return Integer.parseInt(String.valueOf(o), 16);
    }

    /** True when {@code block} (e.g. {@code minecraft:grass_block}) takes a tint. */
    public boolean tinted(String block) {
        return blocks.containsKey(block);
    }

    /** The flat-ground map byte of {@code block} in {@code biome}; 0 when the block is not tinted. */
    public byte pixel(String block, String biome) {
        Tinted t = blocks.get(block);
        if (t == null) return 0;
        String b = biomes.containsKey(biome) ? biome : FALLBACK_BIOME;
        return cache.computeIfAbsent(block + '|' + b, k -> MapColors.nearest(rgb(t, biomes.get(b))));
    }

    /** Water in {@code biome}: everything a vanilla map draws as water (water, kelp, seagrass, bubble columns). */
    public byte water(String biome) {
        return pixel("minecraft:water", biome);
    }

    /** The colour {@code t} has in a biome with colours {@code c} (grass, foliage, water), as 0xRRGGBB. */
    static int rgb(Tinted t, int[] c) {
        int tint;
        double gain;
        switch (t.tint()) {
            case GRASS -> {
                tint = c[0];
                gain = GRASS_GAIN;
            }
            case FOLIAGE -> {
                tint = c[1];
                gain = FOLIAGE_GAIN;
            }
            case WATER -> {
                tint = c[2];
                gain = WATER_GAIN;
            }
            default -> {
                tint = t.rgb();
                gain = FOLIAGE_GAIN;
            }
        }
        double k = t.brightness() / 255.0 * gain;
        int r = (int) Math.min(255, ((tint >> 16) & 0xFF) * k), g = (int) Math.min(255, ((tint >> 8) & 0xFF) * k),
                b = (int) Math.min(255, (tint & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }
}
