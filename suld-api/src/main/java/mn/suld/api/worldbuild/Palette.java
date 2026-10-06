package mn.suld.api.worldbuild;

import java.util.HashMap;
import java.util.Map;

/**
 * Material roles → block ids. Modules write {@code @role} or {@code @role[props]} tokens; the palette
 * resolves them at compile time, so the same module restyles per district (palette substitution).
 * Overrides layer on top of a base palette.
 */
public final class Palette {

    private final Map<String, String> roles;

    private Palette(Map<String, String> roles) {
        this.roles = Map.copyOf(roles);
    }

    public static Palette of(Map<String, String> roles) {
        return new Palette(roles);
    }

    public Palette with(Map<String, String> overrides) {
        Map<String, String> m = new HashMap<>(roles);
        m.putAll(overrides);
        return new Palette(m);
    }

    public Map<String, String> roles() {
        return roles;
    }

    /** Resolve a token or plain block string. Unknown roles are an error (no silent fallback). */
    public String resolve(String token) {
        if (!token.startsWith("@")) return token;
        int b = token.indexOf('[');
        String role = b < 0 ? token.substring(1) : token.substring(1, b);
        String base = roles.get(role);
        if (base == null) throw new IllegalArgumentException("palette has no role '" + role + "'");
        if (b < 0) return base;
        String props = token.substring(b);
        return base.indexOf('[') < 0 ? base + props : base.substring(0, base.length() - 1) + "," + props.substring(1);
    }

    /** SÜLD Kharkhorum default materials (see docs/world/MODULE_LIBRARY.md). */
    public static final Palette KHARKHORUM = Palette.of(Map.ofEntries(
            Map.entry("stone", "minecraft:stone_bricks"),
            Map.entry("stone_alt", "minecraft:tuff_bricks"),
            Map.entry("stone_cracked", "minecraft:cracked_stone_bricks"),
            Map.entry("stone_stairs", "minecraft:stone_brick_stairs"),
            Map.entry("stone_slab", "minecraft:stone_brick_slab"),
            Map.entry("stone_wall", "minecraft:stone_brick_wall"),
            Map.entry("base", "minecraft:mud_bricks"),
            Map.entry("base_stairs", "minecraft:mud_brick_stairs"),
            Map.entry("base_wall", "minecraft:mud_brick_wall"),
            Map.entry("packed", "minecraft:packed_mud"),
            Map.entry("trim", "minecraft:polished_andesite"),
            Map.entry("trim_dark", "minecraft:polished_deepslate"),
            Map.entry("paving", "minecraft:stone_bricks"),
            Map.entry("paving_alt", "minecraft:andesite"),
            Map.entry("paving_accent", "minecraft:polished_andesite"),
            Map.entry("road_edge", "minecraft:mud_bricks"),
            Map.entry("roof", "minecraft:deepslate_tiles"),
            Map.entry("roof_stairs", "minecraft:deepslate_tile_stairs"),
            Map.entry("roof_slab", "minecraft:deepslate_tile_slab"),
            Map.entry("ridge", "minecraft:gold_block"),
            Map.entry("ridge_stairs", "minecraft:cut_copper_stairs"),
            Map.entry("pillar", "minecraft:stripped_mangrove_log"),
            Map.entry("beam", "minecraft:stripped_dark_oak_log"),
            Map.entry("beam_dark", "minecraft:dark_oak_log"),
            Map.entry("plank", "minecraft:spruce_planks"),
            Map.entry("plank_stairs", "minecraft:spruce_stairs"),
            Map.entry("plank_slab", "minecraft:spruce_slab"),
            Map.entry("fence", "minecraft:spruce_fence"),
            Map.entry("felt", "minecraft:white_wool"),
            Map.entry("felt_trim", "minecraft:light_blue_wool"),
            Map.entry("felt_accent", "minecraft:orange_wool"),
            Map.entry("cloth", "minecraft:red_wool"),
            Map.entry("cloth_emblem", "minecraft:yellow_wool"),
            Map.entry("glass", "minecraft:brown_stained_glass_pane"),
            Map.entry("metal", "minecraft:iron_bars"),
            Map.entry("light", "minecraft:lantern"),
            Map.entry("fire", "minecraft:campfire"),
            Map.entry("statue", "minecraft:polished_blackstone"),
            Map.entry("statue_dark", "minecraft:blackstone"),
            Map.entry("statue_gold", "minecraft:gilded_blackstone")));
}
