package mn.suld.api.dungeon.hall;

import java.util.List;
import java.util.Locale;

/**
 * The look of a dungeon hall: block palettes for floor, walls, pillars, decoration and light. Each dungeon has one
 * (docs/world/DUNGEON_HALLS.md). Roofed themes are caves (a stone ceiling); the others are open to the sky behind an
 * invisible ceiling. Palettes use vanilla blocks only.
 */
public enum HallTheme {

    /** Хасарын Агуй: a wolf den under the steppe; dark stone, bones, cobwebs, soul fire. */
    DEN(true, "minecraft:deepslate",
            List.of("minecraft:coarse_dirt", "minecraft:gravel", "minecraft:cobbled_deepslate", "minecraft:rooted_dirt", "minecraft:coarse_dirt"),
            List.of("minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:tuff", "minecraft:deepslate", "minecraft:mossy_cobblestone"),
            "minecraft:polished_deepslate", "minecraft:deepslate_bricks", "minecraft:chiseled_deepslate",
            List.of("minecraft:bone_block", "minecraft:cobweb", "minecraft:moss_carpet", "minecraft:dead_bush", "minecraft:skeleton_skull"),
            0.035, "minecraft:soul_lantern[hanging=false]", "minecraft:shroomlight"),

    /** Говийн Булш: a buried tomb in the Gobi; sandstone, gold, sand drifts, open to the night sky. */
    TOMB(false, "minecraft:sandstone",
            List.of("minecraft:sand", "minecraft:smooth_sandstone", "minecraft:sand", "minecraft:cut_sandstone", "minecraft:suspicious_sand"),
            List.of("minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:sandstone", "minecraft:smooth_sandstone"),
            "minecraft:chiseled_sandstone", "minecraft:cut_red_sandstone", "minecraft:gold_block",
            List.of("minecraft:decorated_pot", "minecraft:dead_bush", "minecraft:sandstone_wall", "minecraft:sand"),
            0.03, "minecraft:lantern[hanging=false]", "minecraft:glowstone"),

    /** Баавгайн Үүр: a bear's lair in the Khangai taiga; earth, roots, spruce, moss, under a canopy. */
    LAIR(true, "minecraft:dirt",
            List.of("minecraft:podzol", "minecraft:coarse_dirt", "minecraft:moss_block", "minecraft:rooted_dirt", "minecraft:podzol"),
            List.of("minecraft:mossy_cobblestone", "minecraft:stone", "minecraft:spruce_log", "minecraft:mossy_stone_bricks", "minecraft:stone"),
            "minecraft:stripped_spruce_log", "minecraft:spruce_planks", "minecraft:moss_block",
            List.of("minecraft:fern", "minecraft:moss_carpet", "minecraft:spruce_leaves[persistent=true]", "minecraft:brown_mushroom"),
            0.045, "minecraft:lantern[hanging=false]", "minecraft:shroomlight"),

    /** Мөсөн Оргил: a frozen summit hall in the Altai; packed ice, snow, blue ice, open to the sky. */
    PEAK(false, "minecraft:stone",
            List.of("minecraft:snow_block", "minecraft:packed_ice", "minecraft:snow_block", "minecraft:calcite", "minecraft:snow_block"),
            List.of("minecraft:packed_ice", "minecraft:stone", "minecraft:blue_ice", "minecraft:packed_ice", "minecraft:calcite"),
            "minecraft:blue_ice", "minecraft:polished_diorite", "minecraft:blue_ice",
            List.of("minecraft:snow[layers=3]", "minecraft:ice", "minecraft:snow[layers=2]"),
            0.035, "minecraft:soul_lantern[hanging=false]", "minecraft:sea_lantern"),

    /** Далайн Гүн: a drowned hall under Хөвсгөл, "Далай ээж"; prismarine, dark water stone, sea lanterns. */
    DEEP(true, "minecraft:dark_prismarine",
            List.of("minecraft:prismarine", "minecraft:gravel", "minecraft:prismarine_bricks", "minecraft:clay", "minecraft:sand"),
            List.of("minecraft:dark_prismarine", "minecraft:prismarine", "minecraft:prismarine_bricks", "minecraft:dark_prismarine"),
            "minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:sea_lantern",
            List.of("minecraft:dead_tube_coral_block", "minecraft:dead_brain_coral_block", "minecraft:wet_sponge", "minecraft:sea_pickle[pickles=3,waterlogged=false]"),
            0.03, "minecraft:sea_pickle[pickles=4,waterlogged=false]", "minecraft:sea_lantern"),

    /** Хар Хотын Балгас: the ruined Tangut city of Khara-Khoto (INSPIRED); red sandstone, mud brick, dead brush, open sky. */
    RUIN(false, "minecraft:red_sandstone",
            List.of("minecraft:red_sand", "minecraft:packed_mud", "minecraft:red_sand", "minecraft:coarse_dirt", "minecraft:mud_bricks"),
            List.of("minecraft:mud_bricks", "minecraft:red_sandstone", "minecraft:packed_mud", "minecraft:cut_red_sandstone", "minecraft:mud_bricks"),
            "minecraft:cut_red_sandstone", "minecraft:chiseled_red_sandstone", "minecraft:terracotta",
            List.of("minecraft:dead_bush", "minecraft:decorated_pot", "minecraft:red_sandstone_wall", "minecraft:mud_brick_wall"),
            0.04, "minecraft:lantern[hanging=false]", "minecraft:glowstone"),

    /** Улаан Хадны Хүрээ: a bandit stronghold in the red cliffs; red terracotta, granite, iron, braziers. */
    REDROCK(true, "minecraft:granite",
            List.of("minecraft:red_terracotta", "minecraft:coarse_dirt", "minecraft:granite", "minecraft:brown_terracotta", "minecraft:gravel"),
            List.of("minecraft:red_terracotta", "minecraft:granite", "minecraft:orange_terracotta", "minecraft:polished_granite", "minecraft:red_terracotta"),
            "minecraft:spruce_log", "minecraft:iron_block", "minecraft:polished_granite",
            List.of("minecraft:barrel", "minecraft:hay_block", "minecraft:iron_bars", "minecraft:anvil", "minecraft:barrel"),
            0.04, "minecraft:campfire[lit=true]", "minecraft:shroomlight"),

    /** Бурхан Халдуны Агуй: the sacred mountain's cave; old stone, moss, blue silk on the ovoo, candles. */
    SACRED(true, "minecraft:stone",
            List.of("minecraft:moss_block", "minecraft:stone", "minecraft:mossy_cobblestone", "minecraft:gravel", "minecraft:moss_block"),
            List.of("minecraft:stone", "minecraft:andesite", "minecraft:mossy_stone_bricks", "minecraft:stone_bricks", "minecraft:cracked_stone_bricks"),
            "minecraft:stone_bricks", "minecraft:light_blue_wool", "minecraft:chiseled_stone_bricks",
            List.of("minecraft:cobblestone_wall", "minecraft:candle[candles=3,lit=true]", "minecraft:moss_carpet", "minecraft:blue_carpet"),
            0.045, "minecraft:candle[candles=4,lit=true]", "minecraft:shroomlight"),

    /** Тэнгэрийн Шат: the stair to the sky; calcite, quartz, pale blue, open to the stars. */
    SKYSTAIR(false, "minecraft:calcite",
            List.of("minecraft:calcite", "minecraft:smooth_quartz", "minecraft:white_concrete", "minecraft:calcite", "minecraft:diorite"),
            List.of("minecraft:quartz_bricks", "minecraft:calcite", "minecraft:smooth_quartz", "minecraft:quartz_block"),
            "minecraft:quartz_pillar", "minecraft:light_blue_concrete", "minecraft:light_blue_glazed_terracotta",
            List.of("minecraft:end_rod", "minecraft:white_carpet", "minecraft:quartz_slab", "minecraft:light_blue_carpet"),
            0.03, "minecraft:end_rod", "minecraft:sea_lantern"),

    /** Тэнгэрийн Ордон: the palace of the Blue Banner (SÜLD fiction); gold, quartz, deep blue, a roof of stone. */
    PALACE(true, "minecraft:quartz_block",
            List.of("minecraft:smooth_quartz", "minecraft:blue_concrete", "minecraft:smooth_quartz", "minecraft:polished_diorite"),
            List.of("minecraft:quartz_bricks", "minecraft:blue_concrete", "minecraft:quartz_block", "minecraft:quartz_bricks"),
            "minecraft:quartz_pillar", "minecraft:gold_block", "minecraft:blue_glazed_terracotta",
            List.of("minecraft:blue_carpet", "minecraft:yellow_carpet", "minecraft:lantern", "minecraft:blue_banner"),
            0.025, "minecraft:lantern[hanging=false]", "minecraft:glowstone");

    private final boolean roofed;
    private final String base;
    private final List<String> floor, wall, decor;
    private final String pillar, accent, dais;
    private final double scatter;
    private final String lamp, lightBlock;

    HallTheme(boolean roofed, String base, List<String> floor, List<String> wall, String pillar, String accent, String dais,
              List<String> decor, double scatter, String lamp, String lightBlock) {
        this.roofed = roofed;
        this.base = base;
        this.floor = List.copyOf(floor);
        this.wall = List.copyOf(wall);
        this.pillar = pillar;
        this.accent = accent;
        this.dais = dais;
        this.decor = List.copyOf(decor);
        this.scatter = scatter;
        this.lamp = lamp;
        this.lightBlock = lightBlock;
    }

    public boolean roofed() { return roofed; }
    public String base() { return base; }
    public List<String> floor() { return floor; }
    public List<String> wall() { return wall; }
    public String pillar() { return pillar; }
    public String accent() { return accent; }
    public String dais() { return dais; }
    public List<String> decor() { return decor; }
    public double scatter() { return scatter; }
    public String lamp() { return lamp; }
    public String lightBlock() { return lightBlock; }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
