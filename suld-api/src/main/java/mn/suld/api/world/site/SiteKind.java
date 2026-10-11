package mn.suld.api.world.site;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a historical site looks like in the world (docs/world/HISTORIC_SITES.md). Each kind is a small, original,
 * simplified build — never a copy of a real monument's carving or text. Local coordinates: y = 0 is the first block
 * above the ground, the front faces +z (south). Deterministic: the same kind always gives the same blocks.
 */
public enum SiteKind {
    /** Буган хөшөө: Bronze Age deer stones, standing slabs in a row inside a stone ring (khirigsuur). */
    DEER_STONES("Буган хөшөө", 6, 5),
    /** Хүн чулуу: Turkic-era stone figures on a low stone platform. */
    STONE_MEN("Хүн чулуу", 5, 4),
    /** Өртөө: a relay post of the yam, a ger with a horse pen, hay and water. */
    YAM_STATION("Өртөө", 7, 6),
    /** Есөн хөлт цагаан туг: nine white standards on a stone platform, the tallest in the middle. */
    NINE_BANNERS("Есөн хөлт цагаан туг", 6, 9),
    /** A great ovoo: a broad cairn of stones with poles and khadag. */
    GREAT_OVOO("Их овоо", 5, 9),
    /** The low earthen platforms and column bases of a palace camp, long fallen. */
    ORDO_RUINS("Ордны туурь", 8, 4),
    /** Rammed-earth city walls in fragments, with a broken corner tower. */
    RUIN_WALLS("Хэрмийн балгас", 8, 7),
    /** A stone stele on a turtle base. */
    STELE("Гэрэлт хөшөө", 4, 6),
    /** A marked birthplace stone on a small knoll, with a khadag. */
    BIRTH_STONE("Дурсгалт чулуу", 4, 5),
    /** A long earthen rampart. */
    EARTH_WALL("Шороон далан", 16, 3),
    /** Dark boulders with pecked figures. */
    PETROGLYPHS("Хадны зураг", 6, 4),
    /** A ruined rammed-earth watchtower. */
    WATCHTOWER_RUIN("Харуулын цамхаг", 4, 8),
    /** A ring of dry-stone wall with one gap. */
    STONE_WALL_RING("Чулуун хэрэм", 9, 3);

    /** One block of the build, in local coordinates. */
    public record Block(int x, int y, int z, String block) {
    }

    private final String label;
    private final int radius;
    private final int height;

    SiteKind(String label, int radius, int height) {
        this.label = label;
        this.radius = radius;
        this.height = height;
    }

    /** What the kind is called in game. */
    public String label() {
        return label;
    }

    /** The build reaches this far from its centre (x and z): the plugin clears plants and protects this square. */
    public int radius() {
        return radius;
    }

    /** The build is at most this tall. */
    public int height() {
        return height;
    }

    /** The blocks of the build (later blocks win on the same cell). */
    public List<Block> blocks() {
        B b = new B();
        switch (this) {
            case DEER_STONES -> {
                b.ring(0, 0, 0, 5, "minecraft:cobblestone");
                b.ring(0, 0, 0, 4.4, "minecraft:mossy_cobblestone");
                for (int x : new int[]{-2, 0, 2}) {
                    int h = x == 0 ? 4 : 3;
                    for (int y = 0; y < h; y++) b.set(x, y, 0, y == h - 1 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
                    b.set(x, h, 0, "minecraft:stone_brick_slab");
                }
            }
            case STONE_MEN -> {
                b.fill(-4, -1, -1, 4, -1, 1, "minecraft:cobblestone");
                for (int x : new int[]{-3, 0, 3}) {
                    b.set(x, 0, 0, "minecraft:polished_andesite");
                    b.set(x, 1, 0, "minecraft:polished_andesite");
                    b.set(x, 2, 0, "minecraft:chiseled_stone_bricks");
                    b.set(x, 3, 0, "minecraft:andesite_slab");
                }
            }
            case YAM_STATION -> {
                b.ger(0, 0, -1, 3, "minecraft:white_wool", "minecraft:white_terracotta");
                b.set(0, 0, 2, "minecraft:air"); // the door faces the road (south)
                b.set(0, 1, 2, "minecraft:air");
                b.ring(5, 0, 2, 2, "minecraft:spruce_fence"); // a horse pen east of the ger
                b.set(5, 0, 4, "minecraft:spruce_fence_gate[facing=south]");
                b.set(-4, 0, 2, "minecraft:hay_block");
                b.set(-4, 1, 2, "minecraft:hay_block");
                b.set(-5, 0, 2, "minecraft:hay_block");
                b.set(-4, 0, 4, "minecraft:water_cauldron[level=3]");
                for (int y = 0; y < 4; y++) b.set(-2, y, 4, "minecraft:spruce_fence");
                b.set(-2, 4, 4, "minecraft:lantern");
            }
            case NINE_BANNERS -> {
                b.fill(-5, -1, -5, 5, -1, 5, "minecraft:smooth_stone");
                b.fill(-4, 0, -4, 4, 0, 4, "minecraft:smooth_stone_slab");
                int[][] at = {{0, 0}, {-3, -3}, {0, -3}, {3, -3}, {-3, 0}, {3, 0}, {-3, 3}, {0, 3}, {3, 3}};
                for (int i = 0; i < at.length; i++) {
                    int h = i == 0 ? 7 : 5;
                    for (int y = 1; y <= h; y++) b.set(at[i][0], y, at[i][1], "minecraft:birch_fence");
                    b.set(at[i][0], h + 1, at[i][1], "minecraft:white_wool"); // the horse-hair tuft of the standard
                    b.set(at[i][0], h, at[i][1] + 1, "minecraft:white_wall_banner[facing=south]");
                }
            }
            case GREAT_OVOO -> {
                String[] stones = {"minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone", "minecraft:andesite", "minecraft:tuff"};
                int k = 0;
                for (int y = 0; y < 6; y++) {
                    double r = 4.4 - y * 0.75;
                    for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                        if (Math.hypot(x, z) <= r) b.set(x, y, z, stones[Math.floorMod(k++ * 7 + x * 3 + z, stones.length)]);
                    }
                }
                for (int y = 6; y <= 8; y++) b.set(0, y, 0, "minecraft:spruce_fence");
                b.set(0, 9, 0, "minecraft:light_blue_wool");
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.set(d[0], 7, d[1], d[0] == 0 ? "minecraft:white_wool" : "minecraft:light_blue_wool");
            }
            case ORDO_RUINS -> {
                b.fill(-7, -1, -5, 7, -1, 5, "minecraft:packed_mud");
                b.fill(-6, 0, -4, 6, 0, 4, "minecraft:coarse_dirt");
                for (int x = -5; x <= 5; x += 2) for (int z = -3; z <= 3; z += 3) b.set(x, 1, z, "minecraft:polished_andesite"); // column bases
                b.fill(-6, 1, -4, -6, 2, 0, "minecraft:mud_bricks");
                b.fill(6, 1, -1, 6, 1, 4, "minecraft:mud_bricks");
                b.fill(-2, 1, -4, 3, 1, -4, "minecraft:mud_brick_wall");
                for (int[] t : new int[][]{{-3, 1, 2}, {2, 1, -1}, {4, 1, 3}, {-1, 1, 0}}) b.set(t[0], t[1], t[2], "minecraft:red_terracotta"); // fallen roof tiles
            }
            case RUIN_WALLS -> {
                for (int i = -7; i <= 7; i++) {
                    int h = 2 + Math.floorMod(i * 5 + 3, 4);
                    if (Math.floorMod(i, 5) != 2) b.fill(i, 0, -7, i, h, -7, "minecraft:packed_mud");
                    if (Math.floorMod(i + 2, 6) != 0) b.fill(-7, 0, i, -7, h - 1, i, "minecraft:mud_bricks");
                    if (i < 2) b.fill(i, 0, 7, i, Math.max(1, h - 2), 7, "minecraft:packed_mud");
                }
                b.fill(-7, 0, -7, -5, 6, -5, "minecraft:mud_bricks"); // the corner tower
                b.fill(-6, 5, -6, -6, 6, -6, "minecraft:air");
            }
            case STELE -> {
                b.fill(-1, 0, -2, 1, 0, 2, "minecraft:polished_andesite"); // the turtle's shell
                b.set(0, 0, 3, "minecraft:andesite_wall"); // its head
                b.set(0, 1, -1, "minecraft:polished_andesite");
                for (int y = 1; y <= 4; y++) b.set(0, y, 0, "minecraft:smooth_stone");
                b.set(0, 5, 0, "minecraft:chiseled_stone_bricks");
                b.set(0, 6, 0, "minecraft:stone_brick_slab");
            }
            case BIRTH_STONE -> {
                for (int y = 0; y < 2; y++) {
                    double r = 3.4 - y * 1.4;
                    for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (Math.hypot(x, z) <= r) b.set(x, y, z, "minecraft:grass_block");
                }
                b.set(0, 2, 0, "minecraft:polished_andesite");
                b.set(0, 3, 0, "minecraft:polished_andesite");
                b.set(0, 4, 0, "minecraft:light_blue_carpet");
                b.set(1, 2, 0, "minecraft:cobblestone");
                b.set(-1, 2, 1, "minecraft:mossy_cobblestone");
            }
            case EARTH_WALL -> {
                for (int x = -16; x <= 16; x++) {
                    int h = 2 + Math.floorMod(x * 7, 3) / 2;
                    if (Math.floorMod(x, 11) == 5) h = 1; // worn gaps
                    b.fill(x, 0, -1, x, h - 1, 1, "minecraft:coarse_dirt");
                    b.fill(x, h - 1, -1, x, h - 1, 1, "minecraft:grass_block");
                    b.fill(x, 0, -2, x, 0, -2, "minecraft:coarse_dirt");
                    b.fill(x, 0, 2, x, 0, 2, "minecraft:coarse_dirt");
                }
            }
            case PETROGLYPHS -> {
                int[][] rocks = {{-3, 0, -2, 2}, {0, 0, -2, 3}, {3, 0, 2, 2}, {-2, 0, 3, 1}, {4, 0, -3, 1}};
                for (int[] r : rocks) {
                    for (int x = -r[3]; x <= r[3]; x++) for (int z = -r[3]; z <= r[3]; z++) for (int y = 0; y <= r[3]; y++) {
                        if (Math.hypot(x, Math.hypot(z, y * 1.3)) <= r[3] + 0.3) b.set(r[0] + x, y, r[2] + z, "minecraft:polished_basalt");
                    }
                    b.set(r[0], 0, r[2] + r[3], "minecraft:chiseled_deepslate"); // a pecked figure on the south face
                }
            }
            case WATCHTOWER_RUIN -> {
                for (int y = 0; y < 8; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                    boolean broken = y >= 5 && (x + z + y) % 3 == 0 || y >= 7 && x > 0;
                    if (wall && !broken) b.set(x, y, z, y < 2 ? "minecraft:mud_bricks" : "minecraft:packed_mud");
                }
                b.set(0, 0, 2, "minecraft:air");
                b.set(0, 1, 2, "minecraft:air");
            }
            case STONE_WALL_RING -> {
                for (int a = 0; a < 360; a += 4) {
                    if (a > 170 && a < 190) continue; // the gap faces south
                    double t = Math.toRadians(a);
                    int x = (int) Math.round(Math.sin(t) * 8), z = (int) Math.round(Math.cos(t) * 8);
                    b.set(x, 0, z, Math.floorMod(a, 12) == 0 ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone");
                    b.set(x, 1, z, Math.floorMod(a, 20) == 0 ? "minecraft:cobblestone_slab" : "minecraft:cobblestone");
                }
            }
        }
        return b.list();
    }

    /** A tiny canvas: later sets win; air is kept (it clears the build's own cells). */
    private static final class B {
        private final Map<Long, Block> cells = new LinkedHashMap<>();

        void set(int x, int y, int z, String block) {
            long k = ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
            cells.remove(k);
            cells.put(k, new Block(x, y, z, block));
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, block);
        }

        /** A one-block ring of radius r at height y around (cx, cz). */
        void ring(int cx, int y, int cz, double r, String block) {
            int n = (int) Math.ceil(r);
            for (int x = -n; x <= n; x++) for (int z = -n; z <= n; z++) {
                double d = Math.hypot(x, z);
                if (d <= r + 0.5 && d > r - 0.5) set(cx + x, y, cz + z, block);
            }
        }

        /** A ger: round felt walls two high, a felt dome and a crown on top. */
        void ger(int cx, int y, int cz, int r, String felt, String trim) {
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                double d = Math.hypot(x, z);
                if (d <= r + 0.3 && d > r - 0.7) {
                    set(cx + x, y, cz + z, trim);
                    set(cx + x, y + 1, cz + z, felt);
                }
                if (d <= r - 0.6) set(cx + x, y + 2, cz + z, felt);
                if (d <= r - 1.6) set(cx + x, y + 3, cz + z, felt);
            }
            set(cx, y + 3, cz, "minecraft:spruce_trapdoor[half=top]");
        }

        List<Block> list() {
            return new ArrayList<>(cells.values());
        }
    }
}
