package mn.suld.api.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The atlas's baked cartography (docs/WORLD_MAP.md, «Baked cartography»): the whole world drawn once the way an atlas
 * draws a country. Dark taiga and lakes in the north, forested highlands and river valleys, the open eastern steppe,
 * snow-capped ranges in the west and the golden Gobi in the south, with lakes, rivers, relief shading, mountain and
 * forest marks, the caravan roads out of Хархорум and the regions' borders.
 * <p>
 * Everything is derived from the world's own layout ({@link Layout}: world.json's regions and areas, ring sectors around
 * the capital, and the dungeon gates and historic sites). The world generator is never asked, and no chunk, block or
 * biome is read. The gazetteer below says what each area's land is like (its {@link Ground}) and where its lakes, rivers
 * and ranges run, each point given within an area's own sector so the features follow the layout. A feature that names
 * an area the layout lacks is left out. Noise is seeded by constants: the same layout bakes to the same bytes, on any
 * machine, every time.
 * <p>
 * {@link #bake} takes about a second on two cores for the 10 001-block world and keeps about 8 MB (three levels, 4, 8 and
 * 16 blocks a pixel). It is the land as surveyed, not the blocks: {@link AtlasLod} puts the real scanned tiles over it
 * where the land has been explored.
 */
public final class Cartography {

    /** What a land is like, as the atlas paints it. */
    public enum Ground {
        //           base  rugged forest arid   dry                                   main                                 lush
        STEPPE(0.16, 0.30, 0.00, false, px(MapColors.GRASS, 0), px(MapColors.GRASS, 1), px(MapColors.GRASS, 2)),
        MEADOW(0.10, 0.25, 0.10, false, px(MapColors.GRASS, 1), px(MapColors.GRASS, 1), px(MapColors.LIGHT_GREEN, 0)),
        DRY_STEPPE(0.20, 0.35, 0.00, true, px(MapColors.WOOD, 1), px(MapColors.SAND, 0), px(MapColors.SAND, 1)),
        FOREST(0.30, 0.55, 0.55, false, px(MapColors.GRASS, 1), px(MapColors.GRASS, 1), px(MapColors.GRASS, 0)),
        TAIGA(0.30, 0.50, 0.85, false, px(MapColors.TERRACOTTA_LIGHT_GREEN, 1), px(MapColors.GREEN, 1), px(MapColors.GREEN, 1)),
        WETLAND(0.06, 0.15, 0.30, false, px(MapColors.GLOW_LICHEN, 1), px(MapColors.GREEN, 1), px(MapColors.GREEN, 1)),
        HIGHLAND(0.42, 0.80, 0.15, false, px(MapColors.GRASS, 0), px(MapColors.TERRACOTTA_LIGHT_GREEN, 1), px(MapColors.GREEN, 1)),
        GOBI(0.18, 0.40, 0.00, true, px(MapColors.TERRACOTTA_YELLOW, 0), px(MapColors.TERRACOTTA_YELLOW, 1), px(MapColors.TERRACOTTA_YELLOW, 2)),
        DUNES(0.14, 0.25, 0.00, true, px(MapColors.SAND, 1), px(MapColors.SAND, 1), px(MapColors.TERRACOTTA_YELLOW, 2)),
        BADLANDS(0.24, 0.60, 0.00, true, px(MapColors.ORANGE, 0), px(MapColors.TERRACOTTA_RED, 1), px(MapColors.TERRACOTTA_WHITE, 1));

        final double base, rugged, forest;
        final boolean arid;
        final byte dry, main, lush;

        Ground(double base, double rugged, double forest, boolean arid, byte dry, byte main, byte lush) {
            this.base = base;
            this.rugged = rugged;
            this.forest = forest;
            this.arid = arid;
            this.dry = dry;
            this.main = main;
            this.lush = lush;
        }
    }

    /** A ring sector around the capital: a region's or an area's land (radii in blocks, degrees clockwise from north). */
    public record Land(String id, double minRadius, double maxRadius, double fromDeg, double toDeg) {

        public Land {
            if (maxRadius <= minRadius || minRadius < 0) throw new IllegalArgumentException(id + ": radii " + minRadius + ".." + maxRadius);
        }

        /** Degrees from {@code fromDeg} clockwise to {@code toDeg} (a full ring when they meet). */
        public double span() {
            double s = norm(toDeg) - norm(fromDeg);
            return s <= 0 ? s + 360 : s;
        }

        boolean contains(double r, double bearing) {
            return r >= minRadius && r < maxRadius && norm(bearing - fromDeg) < span();
        }
    }

    /** Where a caravan road ends: a dungeon gate or a historic site, as an offset from the capital. */
    public record Spot(double dx, double dz) {
    }

    /**
     * The world's layout as world.json gives it: the capital (the spawn) and its walls' half size, the regions' and the
     * areas' sectors (an area is drawn over its region), and the places the caravan roads lead to.
     */
    public record Layout(double spawnX, double spawnZ, double cityHalf, List<Land> regions, List<Land> areas, List<Spot> spots) {

        public Layout {
            if (cityHalf < 0) throw new IllegalArgumentException("city half " + cityHalf);
            regions = List.copyOf(regions);
            areas = List.copyOf(areas);
            spots = List.copyOf(spots);
            if (regions.isEmpty()) throw new IllegalArgumentException("a layout needs its regions");
        }

        /** The outermost land's radius: the edge of the known lands. */
        public double edge() {
            double e = 0;
            for (Land l : regions) e = Math.max(e, l.maxRadius());
            for (Land l : areas) e = Math.max(e, l.maxRadius());
            return e;
        }
    }

    // ------------------------------------------------------------------------------------------------ the gazetteer

    /** A point within an area's own sector: {@code u} across its arc (0 its first bearing), {@code w} out its ring (0 inner). */
    private record At(String area, double u, double w) {
    }

    private record LakeDef(At at, double along, double across, boolean salt) {
    }

    private record RiverDef(double width0, double width1, At... course) {
    }

    private record RidgeDef(double height, double width, At... course) {
    }

    /** A region's land where none of its areas says otherwise. */
    static final Map<String, Ground> REGION_GROUND = Map.of(
            "region.kherlen", Ground.STEPPE, "region.gobi", Ground.GOBI, "region.khangai", Ground.FOREST,
            "region.altai", Ground.HIGHLAND, "region.khentii", Ground.TAIGA, "region.zuungar", Ground.GOBI,
            "region.otgon", Ground.HIGHLAND, "region.khuvsgul", Ground.TAIGA);

    /** Each area's land (docs/world/AREAS.md): north is forest and taiga, west the ranges, south the Gobi, east the steppe. */
    static final Map<String, Ground> AREA_GROUND = Map.ofEntries(
            // north, 0–45°
            Map.entry("area.orkhon", Ground.MEADOW), Map.entry("area.selenge", Ground.FOREST), Map.entry("area.merkit", Ground.TAIGA),
            Map.entry("area.egiin_gol", Ground.TAIGA), Map.entry("area.selenge_adag", Ground.WETLAND),
            // north, 315–360°
            Map.entry("area.khar_balgas", Ground.STEPPE), Map.entry("area.ider", Ground.MEADOW), Map.entry("area.khuvsgul", Ground.TAIGA),
            Map.entry("area.darkhad", Ground.WETLAND), Map.entry("area.tsagaannuur", Ground.TAIGA),
            // east, 45–90°
            Map.entry("area.tuul", Ground.MEADOW), Map.entry("area.burkhan_khaldun", Ground.FOREST), Map.entry("area.onon", Ground.MEADOW),
            Map.entry("area.khentii_taiga", Ground.TAIGA), Map.entry("area.ulz", Ground.STEPPE),
            // east, 90–135°
            Map.entry("area.kherlen", Ground.STEPPE), Map.entry("area.khodoo_aral", Ground.STEPPE), Map.entry("area.buir", Ground.STEPPE),
            Map.entry("area.dariganga", Ground.DRY_STEPPE), Map.entry("area.menen", Ground.STEPPE),
            // south, 135–180°
            Map.entry("area.ongi", Ground.DRY_STEPPE), Map.entry("area.omnii_govi", Ground.GOBI), Map.entry("area.galba", Ground.GOBI),
            Map.entry("area.khongor", Ground.DUNES), Map.entry("area.zamyn_uud", Ground.GOBI),
            // south, 180–225°
            Map.entry("area.taats", Ground.BADLANDS), Map.entry("area.gurvan_saikhan", Ground.GOBI), Map.entry("area.tangut", Ground.BADLANDS),
            Map.entry("area.nemegt", Ground.BADLANDS), Map.entry("area.khar_khot_road", Ground.GOBI),
            // west, 225–270°
            Map.entry("area.khangai_range", Ground.FOREST), Map.entry("area.otgontenger", Ground.HIGHLAND), Map.entry("area.naiman", Ground.DRY_STEPPE),
            Map.entry("area.govi_altai", Ground.HIGHLAND), Map.entry("area.munkh_khairkhan", Ground.HIGHLAND),
            // west, 270–315°
            Map.entry("area.tamir", Ground.MEADOW), Map.entry("area.zavkhan", Ground.DRY_STEPPE), Map.entry("area.kharkhiraa", Ground.HIGHLAND),
            Map.entry("area.khar_us", Ground.DRY_STEPPE), Map.entry("area.tavan_bogd", Ground.HIGHLAND));

    /** The lakes: half sizes along the bearing (out from the capital) and across it, in blocks. */
    private static final List<LakeDef> LAKES = List.of(
            new LakeDef(new At("area.khuvsgul", 0.55, 0.5), 430, 130, false),     // Хөвсгөл Нуур, long north to south
            new LakeDef(new At("area.khar_us", 0.45, 0.45), 220, 260, false),     // Хар Ус Нуур
            new LakeDef(new At("area.buir", 0.55, 0.55), 150, 120, false),        // Буйр Нуур, where the Kherlen ends
            new LakeDef(new At("area.tsagaannuur", 0.5, 0.4), 200, 150, false),   // Цагаан Нуур
            new LakeDef(new At("area.dariganga", 0.7, 0.6), 60, 60, false),       // Ганга Нуур
            new LakeDef(new At("area.omnii_govi", 0.45, 0.78), 120, 160, true),   // the Ongi's salt pan
            new LakeDef(new At("area.gurvan_saikhan", 0.3, 0.22), 90, 110, true)); // the Taats's salt pan

    /** The rivers, source first; widths in blocks at the source and the mouth. A course past w = 1 leaves the known lands. */
    private static final List<RiverDef> RIVERS = List.of(
            new RiverDef(26, 60, new At("area.selenge", 0.05, 0.35), new At("area.selenge", 0.3, 0.65), new At("area.merkit", 0.45, 0.3),
                    new At("area.merkit", 0.5, 0.8), new At("area.egiin_gol", 0.55, 0.4), new At("area.selenge_adag", 0.6, 0.2),
                    new At("area.selenge_adag", 0.55, 0.7), new At("area.selenge_adag", 0.6, 1.6)),                               // Сэлэнгэ
            new RiverDef(12, 22, new At("area.ider", 0.15, 0.85), new At("area.ider", 0.5, 0.5), new At("area.ider", 0.95, 0.35),
                    new At("area.selenge", 0.05, 0.35)),                                                                       // Идэр
            new RiverDef(16, 32, new At("area.khangai_range", 0.8, 0.7), new At("area.tamir", 0.5, 0.6), new At("area.khar_balgas", 0.35, 0.6),
                    new At("area.khar_balgas", 0.85, 0.6), new At("area.orkhon", 0.35, 0.75), new At("area.selenge", 0.25, 0.4),
                    new At("area.selenge", 0.3, 0.65)),                                                                        // Орхон
            new RiverDef(10, 18, new At("area.tamir", 0.6, 0.95), new At("area.tamir", 0.85, 0.75), new At("area.khar_balgas", 0.1, 0.7),
                    new At("area.khar_balgas", 0.35, 0.6)),                                                                    // Тамир
            new RiverDef(12, 24, new At("area.burkhan_khaldun", 0.3, 0.6), new At("area.tuul", 0.6, 0.85), new At("area.tuul", 0.3, 0.55),
                    new At("area.orkhon", 0.85, 0.6), new At("area.orkhon", 0.35, 0.75)),                                      // Туул
            new RiverDef(14, 30, new At("area.burkhan_khaldun", 0.55, 0.4), new At("area.khodoo_aral", 0.1, 0.3), new At("area.khodoo_aral", 0.5, 0.55),
                    new At("area.buir", 0.4, 0.3), new At("area.buir", 0.55, 0.55)),                                           // Хэрлэн
            new RiverDef(18, 28, new At("area.buir", 0.6, 0.7), new At("area.dariganga", 0.15, 0.5), new At("area.menen", 0.15, 0.6),
                    new At("area.menen", 0.18, 1.6)),                                                                          // Халх
            new RiverDef(14, 34, new At("area.khentii_taiga", 0.35, 0.15), new At("area.onon", 0.6, 0.6), new At("area.onon", 0.95, 0.9),
                    new At("area.khentii_taiga", 0.95, 0.45), new At("area.ulz", 0.9, 0.5), new At("area.ulz", 0.95, 1.6)),     // Онон
            new RiverDef(10, 20, new At("area.khentii_taiga", 0.6, 0.8), new At("area.ulz", 0.4, 0.3), new At("area.ulz", 0.7, 0.5),
                    new At("area.ulz", 0.9, 0.5)),                                                                             // Улз
            new RiverDef(14, 24, new At("area.khuvsgul", 0.68, 0.42), new At("area.khuvsgul", 0.95, 0.55), new At("area.merkit", 0.1, 0.8),
                    new At("area.egiin_gol", 0.25, 0.25), new At("area.egiin_gol", 0.55, 0.4)),                                // Эгийн Гол
            new RiverDef(8, 12, new At("area.ongi", 0.15, 0.35), new At("area.ongi", 0.45, 0.7), new At("area.omnii_govi", 0.45, 0.4),
                    new At("area.omnii_govi", 0.45, 0.75)),                                                                    // Онги
            new RiverDef(8, 12, new At("area.taats", 0.6, 0.25), new At("area.taats", 0.5, 0.7), new At("area.gurvan_saikhan", 0.35, 0.1),
                    new At("area.gurvan_saikhan", 0.3, 0.2)),                                                                  // Таацын Гол
            new RiverDef(12, 22, new At("area.zavkhan", 0.1, 0.3), new At("area.zavkhan", 0.35, 0.65), new At("area.kharkhiraa", 0.2, 0.4),
                    new At("area.kharkhiraa", 0.3, 0.85), new At("area.khar_us", 0.42, 0.3)),                                  // Завхан
            new RiverDef(10, 18, new At("area.tavan_bogd", 0.6, 0.55), new At("area.tavan_bogd", 0.45, 0.2), new At("area.khar_us", 0.48, 0.6))); // Ховд

    /** The ranges: crest height over the land (snow from about 1.0) and half width in blocks. */
    private static final List<RidgeDef> RIDGES = List.of(
            new RidgeDef(0.50, 190, new At("area.khangai_range", 0.15, 0.55), new At("area.khangai_range", 0.6, 0.8), new At("area.tamir", 0.2, 0.95),
                    new At("area.zavkhan", 0.3, 0.3)),                                                                         // Хангайн Нуруу
            new RidgeDef(0.85, 150, new At("area.otgontenger", 0.3, 0.4), new At("area.otgontenger", 0.65, 0.62)),           // Отгонтэнгэр
            new RidgeDef(0.78, 170, new At("area.kharkhiraa", 0.55, 0.2), new At("area.kharkhiraa", 0.75, 0.5), new At("area.kharkhiraa", 0.9, 0.8)), // Хархираа
            new RidgeDef(0.72, 210, new At("area.govi_altai", 0.0, 0.2), new At("area.govi_altai", 0.5, 0.5), new At("area.govi_altai", 1.0, 0.75)), // Говь-Алтай
            new RidgeDef(0.46, 150, new At("area.gurvan_saikhan", 0.2, 0.45), new At("area.gurvan_saikhan", 0.55, 0.55),
                    new At("area.gurvan_saikhan", 0.9, 0.4)),                                                                  // Гурван Сайхан
            new RidgeDef(0.90, 210, new At("area.munkh_khairkhan", 0.2, 0.3), new At("area.munkh_khairkhan", 0.7, 0.5), new At("area.tavan_bogd", 0.3, 0.6),
                    new At("area.tavan_bogd", 0.8, 0.75)),                                                                     // Алтайн нуруу, Таван Богд
            new RidgeDef(0.52, 160, new At("area.burkhan_khaldun", 0.2, 0.4), new At("area.burkhan_khaldun", 0.5, 0.6), new At("area.burkhan_khaldun", 0.8, 0.55)), // Бурхан Халдун
            new RidgeDef(0.58, 210, new At("area.khentii_taiga", 0.1, 0.25), new At("area.khentii_taiga", 0.45, 0.55), new At("area.khentii_taiga", 0.85, 0.7)), // Хэнтий
            new RidgeDef(0.36, 90, new At("area.dariganga", 0.3, 0.5), new At("area.dariganga", 0.36, 0.42)),                 // Дарьгангын тогоо
            new RidgeDef(0.52, 150, new At("area.khuvsgul", 0.1, 0.3), new At("area.khuvsgul", 0.25, 0.8), new At("area.darkhad", 0.08, 0.2),
                    new At("area.darkhad", 0.15, 0.8)),                                                                        // Хорьдол Сарьдаг
            new RidgeDef(0.40, 160, new At("area.nemegt", 0.2, 0.6), new At("area.nemegt", 0.8, 0.5)));                       // Нэмэгтийн нуруу

    /** Every area the gazetteer names (each must be in world.json, or its features are left out). */
    public static Set<String> namedAreas() {
        Set<String> out = new HashSet<>(AREA_GROUND.keySet());
        for (LakeDef l : LAKES) out.add(l.at().area());
        for (RiverDef r : RIVERS) for (At a : r.course()) out.add(a.area());
        for (RidgeDef r : RIDGES) for (At a : r.course()) out.add(a.area());
        return out;
    }

    /** Every region the gazetteer names. */
    public static Set<String> namedRegions() {
        return REGION_GROUND.keySet();
    }

    // ------------------------------------------------------------------------------------------------ the palette

    private static byte px(int id, int shade) {
        return MapColors.pixel(id, shade);
    }

    static final byte LAKE = px(MapColors.LIGHT_BLUE, 1), LAKE_DEEP = px(MapColors.LIGHT_BLUE, 0), LAKE_SHALLOW = px(MapColors.LIGHT_BLUE, 2),
            SHORE = px(MapColors.BLUE, 0), RIVER = px(MapColors.BLUE, 1), SALT = px(MapColors.QUARTZ, 1), SALT_SHORE = px(MapColors.TERRACOTTA_LIGHT_GRAY, 1),
            ROAD_DARK = px(MapColors.TERRACOTTA_BROWN, 2), ROAD_LIGHT = px(MapColors.SAND, 2), BORDER = px(MapColors.RED, 0),
            WALL = px(MapColors.DEEPSLATE, 0), PLAZA = px(MapColors.TERRACOTTA_WHITE, 2), ROOF = px(MapColors.TERRACOTTA_RED, 1),
            BLOSSOM = px(MapColors.PINK, 1), CANOPY = px(MapColors.GREEN, 1), TAIGA_CANOPY = px(MapColors.GREEN, 0),
            TREE_LIT = px(MapColors.PLANT, 0), TREE_DARK = px(MapColors.PLANT, 3), TRUNK = px(MapColors.TERRACOTTA_BROWN, 0),
            SNOW = px(MapColors.SNOW, 1), SNOW_LIT = px(MapColors.SNOW, 2), SNOW_SHADE = px(MapColors.CLAY, 1), SNOW_DEEP = px(MapColors.CLAY, 0),
            ROCK = px(MapColors.STONE, 1), ROCK_WARM = px(MapColors.TERRACOTTA_LIGHT_GRAY, 1), ROCK_ARID = px(MapColors.TERRACOTTA_LIGHT_GRAY, 1),
            ROCK_ARID_DARK = px(MapColors.ORANGE, 3), PEAK_LIT = px(MapColors.STONE, 2), PEAK_DARK = px(MapColors.STONE, 3),
            GRAVEL = px(MapColors.WOOD, 1);

    // ------------------------------------------------------------------------------------------------ the bake

    private Cartography() {
    }

    /** Bakes the world of {@code layout} over the playable square {@code g}. Pure and deterministic; ~1 s, call it off the main thread. */
    public static BakedAtlas bake(Layout layout, MapGeometry g) {
        long t0 = System.nanoTime();
        Baker b = new Baker(layout, g);
        int n = BakedAtlas.WIDEST - BakedAtlas.FINEST + 1;
        int[] ox = new int[n], oz = new int[n], w = new int[n], h = new int[n];
        byte[][] raster = new byte[n][];
        for (int l = 0; l < n; l++) {
            int zoom = l + BakedAtlas.FINEST, s = MapGeometry.scale(zoom);
            ox[l] = Math.floorDiv(g.minX(), s) * s;
            oz[l] = Math.floorDiv(g.minZ(), s) * s;
            w[l] = Math.floorDiv(g.maxX() - 1 - ox[l], s) + 1;
            h[l] = Math.floorDiv(g.maxZ() - 1 - oz[l], s) + 1;
            raster[l] = b.level(zoom, ox[l], oz[l], w[l], h[l]);
        }
        int half = (int) Math.ceil(layout.cityHalf());
        int sx = (int) Math.floor(layout.spawnX()), sz = (int) Math.floor(layout.spawnZ());
        int[] capital = {sx - half, sz - half, sx + half + 1, sz + half + 1};
        return new BakedAtlas(g, ox, oz, w, h, raster, capital, System.nanoTime() - t0);
    }

    // ------------------------------------------------------------------------------------------------ noise

    private static final long SEED_WARP_T = 0x5EEDA1L, SEED_WARP_R = 0x5EEDA2L, SEED_JITTER = 0x5EEDA3L, SEED_DETAIL = 0x5EEDA4L,
            SEED_CRAG = 0x5EEDA5L, SEED_TEXTURE = 0x5EEDA6L, SEED_POND = 0x5EEDA7L, SEED_SHORE = 0x5EEDA8L, SEED_RIDGE = 0x5EEDA9L,
            SEED_MEANDER = 0x5EEDAAL, SEED_DUNES = 0x5EEDABL, SEED_SYMBOL = 0x5EEDACL, SEED_ROAD = 0x5EEDADL, SEED_SPECKLE = 0x5EEDAEL;
    private static final double[] ROT_C = new double[8], ROT_S = new double[8];

    static {
        for (int i = 0; i < 8; i++) {
            ROT_C[i] = Math.cos(0.61 * i);
            ROT_S[i] = Math.sin(0.61 * i);
        }
    }

    static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /** A fixed number in [0, 1) for lattice point (x, z). */
    static double hash01(long seed, long x, long z) {
        return (mix(seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL) >>> 11) * 0x1.0p-53;
    }

    /** Smooth value noise in [−1, 1]. */
    static double noise(long seed, double x, double z) {
        double fx0 = Math.floor(x), fz0 = Math.floor(z);
        long x0 = (long) fx0, z0 = (long) fz0;
        double fx = x - fx0, fz = z - fz0;
        double u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
        double a = hash01(seed, x0, z0), b = hash01(seed, x0 + 1, z0), c = hash01(seed, x0, z0 + 1), d = hash01(seed, x0 + 1, z0 + 1);
        return 2 * (a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v) - 1;
    }

    /** Fractal noise of {@code octaves} octaves from {@code wavelength} blocks down, each lattice turned so no axis shows. */
    static double fbm(long seed, double x, double z, double wavelength, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1 / wavelength;
        for (int i = 0; i < octaves; i++) {
            double rx = x * f, rz = z * f;
            sum += amp * noise(seed + i * 1013L, rx * ROT_C[i] - rz * ROT_S[i], rx * ROT_S[i] + rz * ROT_C[i]);
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }

    static double norm(double deg) {
        return (deg % 360 + 360) % 360;
    }

    /** Compass bearing of an offset: 0 north (−z), 90 east (+x). */
    static double bearing(double dx, double dz) {
        return norm(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    // ------------------------------------------------------------------------------------------------ the baker

    /** A line drawn on the map: points every few blocks, with a width (blocks) at each. */
    private record Line(double[] x, double[] z, double[] w) {
        int size() {
            return x.length;
        }
    }

    private record Lake(double cx, double cz, double ux, double uz, double along, double across, boolean salt) {
        /** 0 at the middle, 1 on the shore (a ragged one), more outside. */
        double q(double x, double z) {
            double dx = x - cx, dz = z - cz;
            double a = (dx * ux + dz * uz) / along, c = (-dx * uz + dz * ux) / across;
            return Math.sqrt(a * a + c * c) * (1 + 0.12 * noise(SEED_SHORE, x / 90, z / 90));
        }

        double reach() {
            return Math.max(along, across) * 1.35;
        }
    }

    /** What a baked pixel holds, for the overlays: marks keep off water, roads and the city. */
    private static final byte LAND = 0, LAKE_C = 1, RIVER_C = 2, ROAD_C = 3, LINE_C = 4, CITY_C = 5, MARK_C = 6, CANOPY_C = 7;

    private static final Ground[] GROUNDS = Ground.values();

    private static final class Baker {
        static final double BIN_DEG = 0.5, BIN_R = 8;
        /** The radius of the capital's plain (blocks, before its edge is warped). */
        static final double PLAIN = 420;
        static final int CELL = 32, BAND = 48;

        final Layout layout;
        final MapGeometry g;
        final double sx, sz, edge;
        final int bBins = (int) Math.round(360 / BIN_DEG), rBins;
        final byte[] polar;
        final Map<String, Land> areas = new LinkedHashMap<>();
        final List<Lake> lakes = new ArrayList<>();
        final List<Line> rivers = new ArrayList<>(), ridges = new ArrayList<>(), roads = new ArrayList<>(), borders = new ArrayList<>();
        final List<double[]> ridgeShape = new ArrayList<>(); // {height, width}
        /** The places (world x, z): a pond never covers one. */
        final double[] spotX, spotZ;
        // the coarse fields, one value a CELL-block cell
        final int cx0, cz0, cw, ch;
        final float[] base, rugged, ridge, valley, warpT, warpR;

        Baker(Layout layout, MapGeometry g) {
            this.layout = layout;
            this.g = g;
            this.sx = layout.spawnX();
            this.sz = layout.spawnZ();
            this.edge = layout.edge();
            for (Land a : layout.areas()) areas.put(a.id(), a);
            this.spotX = layout.spots().stream().mapToDouble(p -> sx + p.dx()).toArray();
            this.spotZ = layout.spots().stream().mapToDouble(p -> sz + p.dz()).toArray();
            this.rBins = (int) Math.ceil(edge / BIN_R);
            this.polar = polar();
            resolveFeatures();
            int span16 = MapGeometry.scale(BakedAtlas.WIDEST);
            this.cx0 = Math.floorDiv(g.minX(), span16) * span16 - 2 * CELL;
            this.cz0 = Math.floorDiv(g.minZ(), span16) * span16 - 2 * CELL;
            this.cw = (g.maxX() + span16 + 2 * CELL - cx0) / CELL + 1;
            this.ch = (g.maxZ() + span16 + 2 * CELL - cz0) / CELL + 1;
            int n = cw * ch;
            base = new float[n];
            rugged = new float[n];
            ridge = new float[n];
            valley = new float[n];
            warpT = new float[n];
            warpR = new float[n];
            coarse();
        }

        // -------------------------------------------------------------------------------- where each land is

        private byte[] polar() {
            byte[] out = new byte[bBins * rBins];
            for (int bi = 0; bi < bBins; bi++) {
                double b = (bi + 0.5) * BIN_DEG;
                for (int ri = 0; ri < rBins; ri++) {
                    double r = (ri + 0.5) * BIN_R;
                    out[bi * rBins + ri] = (byte) groundAt(r, b).ordinal();
                }
            }
            return out;
        }

        private Ground groundAt(double r, double b) {
            for (Land a : layout.areas()) {
                if (a.contains(r, b)) {
                    Ground gr = AREA_GROUND.get(a.id());
                    if (gr != null) return gr;
                }
            }
            for (Land reg : layout.regions()) if (reg.contains(r, b)) return REGION_GROUND.getOrDefault(reg.id(), Ground.STEPPE);
            return Ground.STEPPE;
        }

        /** The land at polar point (r, b): the land of the bins, and the edge's land past the edge. */
        Ground lookup(double r, double b) {
            int bi = (int) (norm(b) / BIN_DEG);
            if (bi >= bBins) bi = bBins - 1;
            int ri = (int) Math.max(0, Math.min(rBins - 1, r / BIN_R));
            return GROUNDS[polar[bi * rBins + ri]];
        }

        /**
         * The land at block (x, z), its borders moved {@code wt} degrees round (and {@code jt} blocks more) and {@code jr}
         * blocks out, so no border is a ruled line. Within {@link #PLAIN} of the capital it is the capital's own plain:
         * Хархорум stands in the Orkhon valley's meadows, so the regions grow out of one plain instead of meeting at its
         * walls in wedges.
         */
        Ground ground(double x, double z, double wt, double jt, double jr) {
            double dx = x - sx, dz = z - sz, r = Math.hypot(dx, dz);
            if (r + 0.25 * jr < PLAIN) return Ground.MEADOW;
            return lookup(r + jr, bearing(dx, dz) + wt * Math.min(1, r / 600) + Math.toDegrees(jt / Math.max(r, 240)));
        }

        // -------------------------------------------------------------------------------- the gazetteer, placed

        private double[] at(At a) {
            Land l = areas.get(a.area());
            if (l == null) return null;
            double b = Math.toRadians(l.fromDeg() + a.u() * l.span()), r = l.minRadius() + a.w() * (l.maxRadius() - l.minRadius());
            return new double[]{sx + Math.sin(b) * r, sz - Math.cos(b) * r};
        }

        private List<double[]> course(At... pts) {
            List<double[]> out = new ArrayList<>();
            for (At a : pts) {
                double[] p = at(a);
                if (p == null) return null;
                out.add(p);
            }
            return out;
        }

        private void resolveFeatures() {
            for (LakeDef d : LAKES) {
                double[] c = at(d.at());
                if (c == null) continue;
                double dx = c[0] - sx, dz = c[1] - sz, r = Math.max(1, Math.hypot(dx, dz));
                lakes.add(new Lake(c[0], c[1], dx / r, dz / r, d.along(), d.across(), d.salt()));
            }
            int i = 0;
            for (RiverDef d : RIVERS) {
                List<double[]> c = course(d.course());
                i++;
                if (c == null) continue;
                rivers.add(meander(spline(c, 12), 70, 300, SEED_MEANDER + i, d.width0(), d.width1(), true));
            }
            for (RidgeDef d : RIDGES) {
                List<double[]> c = course(d.course());
                if (c == null) continue;
                ridges.add(spline(c, 48));
                ridgeShape.add(new double[]{d.height(), d.width()});
            }
            roads();
            borders();
        }

        /** A Catmull-Rom curve through {@code pts}, a point about every {@code step} blocks. */
        static Line spline(List<double[]> pts, double step) {
            List<double[]> out = new ArrayList<>();
            int n = pts.size();
            for (int i = 0; i < n - 1; i++) {
                double[] p0 = pts.get(Math.max(0, i - 1)), p1 = pts.get(i), p2 = pts.get(i + 1), p3 = pts.get(Math.min(n - 1, i + 2));
                int k = Math.max(1, (int) Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / step));
                for (int j = 0; j < k; j++) {
                    double t = (double) j / k, t2 = t * t, t3 = t2 * t;
                    double x = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                    double z = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
                    out.add(new double[]{x, z});
                }
            }
            out.add(pts.get(n - 1));
            double[] xs = new double[out.size()], zs = new double[out.size()];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = out.get(i)[0];
                zs[i] = out.get(i)[1];
            }
            return new Line(xs, zs, new double[xs.length]);
        }

        /**
         * {@code l} wound side to side up to {@code amp} blocks (a bend about every {@code wavelength}), calm at its ends
         * so it still meets what it joins; widths from {@code w0} to {@code w1}.
         */
        static Line meander(Line l, double amp, double wavelength, long seed, double w0, double w1, boolean calmEnds) {
            int n = l.size();
            double[] len = new double[n];
            for (int i = 1; i < n; i++) len[i] = len[i - 1] + Math.hypot(l.x()[i] - l.x()[i - 1], l.z()[i] - l.z()[i - 1]);
            double total = Math.max(1, len[n - 1]);
            double[] xs = new double[n], zs = new double[n], ws = new double[n];
            for (int i = 0; i < n; i++) {
                int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
                double tx = l.x()[b] - l.x()[a], tz = l.z()[b] - l.z()[a], tl = Math.max(1e-9, Math.hypot(tx, tz));
                double calm = calmEnds ? Math.min(1, Math.min(len[i], total - len[i]) / 220) : Math.min(1, len[i] / 220);
                double off = amp * calm * fbm(seed, len[i], 0.5, wavelength, 2);
                xs[i] = l.x()[i] - tz / tl * off;
                zs[i] = l.z()[i] + tx / tl * off;
                ws[i] = w0 + (w1 - w0) * len[i] / total;
            }
            return new Line(xs, zs, ws);
        }

        /**
         * The caravan roads: a trunk out of each of the capital's gates along the middle of each inner region (the old
         * routes north to the taiga, east to the steppe, south across the Gobi, west to the Altai) to the edge of the known
         * lands, and from the nearest trunk a branch to every dungeon gate and historic site. They bend gently and go
         * round the lakes.
         */
        private void roads() {
            List<Double> trunks = new ArrayList<>();
            for (Land r : layout.regions()) if (r.minRadius() <= layout.cityHalf()) trunks.add(norm(r.fromDeg() + r.span() / 2));
            if (trunks.isEmpty()) trunks = List.of(0.0, 90.0, 180.0, 270.0);
            List<Line> trunkLines = new ArrayList<>();
            int ti = 0;
            for (double b : trunks) {
                List<double[]> pts = new ArrayList<>();
                double rad = Math.toRadians(b), ux = Math.sin(rad), uz = -Math.cos(rad);
                for (double r = layout.cityHalf(); r < edge - 120; r += 400) {
                    double off = r <= layout.cityHalf() ? 0 : 160 * fbm(SEED_ROAD + ti, r, 0.5, 1100, 2) * Math.min(1, (r - layout.cityHalf()) / 600);
                    pts.add(new double[]{sx + ux * r - uz * off, sz + uz * r + ux * off});
                }
                pts.add(new double[]{sx + ux * (edge - 120), sz + uz * (edge - 120)});
                ti++;
                Line t = aroundLakes(spline(pts, 16));
                trunkLines.add(t);
                roads.add(t);
            }
            int si = 0;
            for (Spot s : layout.spots()) {
                si++;
                double rs = Math.hypot(s.dx(), s.dz()), bs = bearing(s.dx(), s.dz());
                if (rs < layout.cityHalf() + 80) continue;
                int best = 0;
                double bestD = 999;
                for (int i = 0; i < trunks.size(); i++) {
                    double d = Math.abs(norm(bs - trunks.get(i) + 180) - 180);
                    if (d < bestD) {
                        bestD = d;
                        best = i;
                    }
                }
                Line trunk = trunkLines.get(best);
                double[] to = {sx + s.dx(), sz + s.dz()};
                if (distance(trunk, to[0], to[1], 60) < 40) continue; // it stands on the trunk
                double delta = Math.toRadians(bestD);
                double rj = Math.max(layout.cityHalf() + 40, rs * Math.cos(delta) - 0.7 * rs * Math.sin(delta));
                int j = nearestByRadius(trunk, rj);
                double[] from = {trunk.x()[j], trunk.z()[j]};
                double len = Math.hypot(to[0] - from[0], to[1] - from[1]);
                List<double[]> pts = new ArrayList<>();
                pts.add(from);
                int k = Math.max(1, (int) (len / 350));
                for (int i = 1; i < k; i++) {
                    double t = (double) i / k, off = Math.min(110, 0.07 * len) * fbm(SEED_ROAD + 97 + si, t * len, 0.5, 700, 2);
                    double nx = -(to[1] - from[1]) / len, nz = (to[0] - from[0]) / len;
                    pts.add(new double[]{from[0] + (to[0] - from[0]) * t + nx * off, from[1] + (to[1] - from[1]) * t + nz * off});
                }
                pts.add(to);
                roads.add(aroundLakes(spline(pts, 16)));
            }
        }

        private int nearestByRadius(Line l, double r) {
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < l.size(); i++) {
                double d = Math.abs(Math.hypot(l.x()[i] - sx, l.z()[i] - sz) - r);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            return best;
        }

        /** {@code l} with every point that falls in a lake pushed out to its shore and a little past. */
        private Line aroundLakes(Line l) {
            double[] xs = l.x().clone(), zs = l.z().clone();
            for (Lake k : lakes) {
                for (int i = 0; i < xs.length; i++) {
                    if (Math.abs(xs[i] - k.cx()) > k.reach() * 1.3 || Math.abs(zs[i] - k.cz()) > k.reach() * 1.3) continue;
                    double q = k.q(xs[i], zs[i]);
                    if (q >= 1.3) continue;
                    double dx = xs[i] - k.cx(), dz = zs[i] - k.cz(), d = Math.max(1e-6, Math.hypot(dx, dz));
                    double f = 1.3 / Math.max(q, 0.05);
                    xs[i] = k.cx() + dx / d * d * f;
                    zs[i] = k.cz() + dz / d * d * f;
                }
            }
            return new Line(xs, zs, l.w());
        }

        /** The regions' borders: their straight sides and their arcs, each drawn once. */
        private void borders() {
            Set<String> seen = new HashSet<>();
            for (Land r : layout.regions()) {
                double inner = Math.max(r.minRadius(), layout.cityHalf() * 1.5), outer = r.maxRadius();
                if (r.span() < 360) {
                    for (double b : new double[]{norm(r.fromDeg()), norm(r.toDeg())}) {
                        if (seen.add("s" + Math.round(b * 10) + ":" + Math.round(inner) + ":" + Math.round(outer))) {
                            double rad = Math.toRadians(b);
                            List<double[]> pts = List.of(new double[]{sx + Math.sin(rad) * inner, sz - Math.cos(rad) * inner},
                                    new double[]{sx + Math.sin(rad) * outer, sz - Math.cos(rad) * outer});
                            borders.add(spline(pts, 8));
                        }
                    }
                }
                for (double rad : new double[]{r.minRadius(), r.maxRadius()}) {
                    if (rad < layout.cityHalf() * 1.5) continue;
                    if (!seen.add("a" + Math.round(rad) + ":" + Math.round(norm(r.fromDeg()) * 10) + ":" + Math.round(r.span() * 10))) continue;
                    List<double[]> pts = new ArrayList<>();
                    int k = Math.max(8, (int) (r.span() * rad * Math.PI / 180 / 40));
                    for (int i = 0; i <= k; i++) {
                        double b = Math.toRadians(r.fromDeg() + r.span() * i / k);
                        pts.add(new double[]{sx + Math.sin(b) * rad, sz - Math.cos(b) * rad});
                    }
                    borders.add(spline(pts, 8));
                }
            }
        }

        // -------------------------------------------------------------------------------- the coarse fields

        private void coarse() {
            float[] b0 = new float[cw * ch], r0 = new float[cw * ch];
            IntStream.range(0, ch).parallel().forEach(j -> {
                for (int i = 0; i < cw; i++) {
                    double x = cx0 + (i + 0.5) * CELL, z = cz0 + (j + 0.5) * CELL;
                    int k = j * cw + i;
                    warpT[k] = (float) (24 * fbm(SEED_WARP_T, x, z, 1300, 3));
                    warpR[k] = (float) (520 * fbm(SEED_WARP_R, x, z, 1100, 3));
                    Ground gr = ground(x, z, warpT[k], 0, warpR[k]);
                    b0[k] = (float) gr.base;
                    r0[k] = (float) gr.rugged;
                    ridge[k] = (float) ridgeAt(x, z);
                    valley[k] = (float) valleyAt(x, z);
                }
            });
            blur(b0, base);
            blur(r0, rugged);
        }

        /** Two box blurs 3 cells wide each way: the land's height eases from one area to the next. */
        private void blur(float[] in, float[] out) {
            float[] tmp = new float[in.length], a = in.clone();
            for (int pass = 0; pass < 2; pass++) {
                for (int j = 0; j < ch; j++) {
                    for (int i = 0; i < cw; i++) {
                        double s = 0;
                        int n = 0;
                        for (int d = -3; d <= 3; d++) {
                            int ii = Math.max(0, Math.min(cw - 1, i + d));
                            s += a[j * cw + ii];
                            n++;
                        }
                        tmp[j * cw + i] = (float) (s / n);
                    }
                }
                for (int j = 0; j < ch; j++) {
                    for (int i = 0; i < cw; i++) {
                        double s = 0;
                        int n = 0;
                        for (int d = -3; d <= 3; d++) {
                            int jj = Math.max(0, Math.min(ch - 1, j + d));
                            s += tmp[jj * cw + i];
                            n++;
                        }
                        a[j * cw + i] = (float) (s / n);
                    }
                }
            }
            System.arraycopy(a, 0, out, 0, a.length);
        }

        /** The ranges' height at (x, z): the highest crest's, falling off with the distance, its flanks ragged. */
        private double ridgeAt(double x, double z) {
            double best = 0;
            double rag = 1 + 0.35 * noise(SEED_RIDGE, x / 260, z / 260), lift = 0.85 + 0.15 * noise(SEED_RIDGE + 1, x / 500, z / 500);
            for (int i = 0; i < ridges.size(); i++) {
                double h = ridgeShape.get(i)[0], w = ridgeShape.get(i)[1];
                double d = distance(ridges.get(i), x, z, 3 * w) * rag;
                if (d >= 3 * w) continue;
                best = Math.max(best, h * lift * Math.exp(-(d / w) * (d / w)));
            }
            return best;
        }

        /** How deep the river valleys cut at (x, z). */
        private double valleyAt(double x, double z) {
            double best = 0;
            for (Line r : rivers) {
                double d = distance(r, x, z, 600);
                if (d < 600) best = Math.max(best, 0.10 * Math.exp(-(d / 210) * (d / 210)));
            }
            return best;
        }

        /** The distance from (x, z) to line {@code l}, or {@code cap} when it is farther (a cheap box test first). */
        static double distance(Line l, double x, double z, double cap) {
            double best = cap * cap;
            double[] xs = l.x(), zs = l.z();
            for (int i = 0; i < xs.length - 1; i++) {
                double ax = xs[i], az = zs[i], bx = xs[i + 1], bz = zs[i + 1];
                if (x < Math.min(ax, bx) - cap || x > Math.max(ax, bx) + cap || z < Math.min(az, bz) - cap || z > Math.max(az, bz) + cap) continue;
                double vx = bx - ax, vz = bz - az, len2 = vx * vx + vz * vz;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / len2));
                double dx = x - (ax + t * vx), dz = z - (az + t * vz), d2 = dx * dx + dz * dz;
                if (d2 < best) best = d2;
            }
            return Math.sqrt(best);
        }

        // -------------------------------------------------------------------------------- one level

        /** The raster of one zoom: the land row band by row band (in parallel), then the waters, roads, borders, city, marks. */
        byte[] level(int zoom, int ox, int oz, int w, int h) {
            int s = MapGeometry.scale(zoom);
            byte[] out = new byte[w * h], cls = new byte[w * h];
            boolean marks = zoom > BakedAtlas.FINEST;
            float[] heights = marks ? new float[w * h] : null;
            byte[] grounds = marks ? new byte[w * h] : null;
            int bands = (h + BAND - 1) / BAND;
            IntStream.range(0, bands).parallel().forEach(band -> land(zoom, s, ox, oz, w, h, band * BAND, Math.min(h, band * BAND + BAND), out, cls, heights, grounds));
            for (Line r : rivers) river(r, s, ox, oz, w, h, out, cls);
            int dash = zoom == BakedAtlas.FINEST ? 5 : 4;
            for (Line r : roads) road(r, s, ox, oz, w, h, dash, out, cls);
            for (Line b : borders) border(b, s, ox, oz, w, h, out, cls);
            city(s, ox, oz, w, h, out, cls);
            if (marks) symbols(zoom, s, ox, oz, w, h, out, cls, heights, grounds);
            return out;
        }

        /** How many detail octaves a level resolves: none finer than 2.5 pixels. */
        private static int octaves(int s) {
            int n = 0;
            for (double wl = 520; wl >= 2.5 * s && n < 6; wl /= 2) n++;
            return Math.max(1, n);
        }

        private void land(int zoom, int s, int ox, int oz, int w, int h, int r0, int r1, byte[] out, byte[] cls, float[] heights, byte[] grounds) {
            int oct = octaves(s), cols = w + 2;
            float[][] e = new float[3][cols], t = new float[3][cols];
            byte[][] gr = new byte[3][cols];
            row(r0 - 1, s, ox, oz, oct, e[0], t[0], gr[0]);
            row(r0, s, ox, oz, oct, e[1], t[1], gr[1]);
            for (int pz = r0; pz < r1; pz++) {
                row(pz + 1, s, ox, oz, oct, e[2], t[2], gr[2]);
                float[] en = e[0], ec = e[1], es = e[2];
                double z = oz + (pz + 0.5) * s;
                for (int px = 0; px < w; px++) {
                    int c = px + 1, idx = pz * w + px;
                    double x = ox + (px + 0.5) * s;
                    Ground ground = GROUNDS[gr[1][c]];
                    if (ground == Ground.GOBI && fbm(SEED_DUNES + 5, x, z, 900, 3) > 0.26) ground = Ground.DUNES; // a sand sea
                    out[idx] = colour(zoom, s, ground, ec[c], ec[c - 1], ec[c + 1], en[c], es[c], t[1][c], x, z, px, pz, cls, idx);
                    if (heights != null) {
                        heights[idx] = ec[c];
                        grounds[idx] = gr[1][c];
                    }
                }
                float[] fe = e[0];
                e[0] = e[1];
                e[1] = e[2];
                e[2] = fe;
                float[] ft = t[0];
                t[0] = t[1];
                t[1] = t[2];
                t[2] = ft;
                byte[] fg = gr[0];
                gr[0] = gr[1];
                gr[1] = gr[2];
                gr[2] = fg;
            }
        }

        /** The heights, textures and lands of raster row {@code pz}, one pixel past each side. */
        private void row(int pz, int s, int ox, int oz, int oct, float[] e, float[] t, byte[] gr) {
            double z = oz + (pz + 0.5) * s;
            for (int c = 0; c < e.length; c++) {
                double x = ox + (c - 0.5) * s;
                double fx = (x - cx0) / CELL - 0.5, fz = (z - cz0) / CELL - 0.5;
                int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
                double ax = fx - i, az = fz - j;
                i = Math.max(0, Math.min(cw - 2, i));
                j = Math.max(0, Math.min(ch - 2, j));
                int k = j * cw + i;
                double w00 = (1 - ax) * (1 - az), w10 = ax * (1 - az), w01 = (1 - ax) * az, w11 = ax * az;
                double b = lerp(base, k, w00, w10, w01, w11), rg = lerp(rugged, k, w00, w10, w01, w11), ri = lerp(ridge, k, w00, w10, w01, w11);
                double va = lerp(valley, k, w00, w10, w01, w11), wt = lerp(warpT, k, w00, w10, w01, w11), wr = lerp(warpR, k, w00, w10, w01, w11);
                double jt = 60 * fbm(SEED_JITTER, x, z, 120, 2), jr = 60 * fbm(SEED_JITTER + 1, x, z, 120, 2);
                double height = b + ri - va + rg * 0.22 * fbm(SEED_DETAIL, x, z, 520, oct);
                if (ri > 0.12) height += ri * 0.28 * fbm(SEED_CRAG, x, z, 170, Math.max(1, oct - 1));
                e[c] = (float) height;
                t[c] = (float) fbm(SEED_TEXTURE, x, z, 600, 4);
                gr[c] = (byte) ground(x, z, wt, jt, wr + jr).ordinal();
            }
        }

        private double lerp(float[] f, int k, double w00, double w10, double w01, double w11) {
            return f[k] * w00 + f[k + 1] * w10 + f[k + cw] * w01 + f[k + cw + 1] * w11;
        }

        private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        /** Heights over which the land is snow or bare rock (an arid range keeps less snow). */
        static final double SNOW_LINE = 1.02, SNOW_ARID = 1.12, ROCK_LINE = 0.74, ROCK_ARID_LINE = 0.62;

        /** One pixel of the land: a lake or pond, else snow, rock or the land's own colours, shaded by the relief. */
        private byte colour(int zoom, int s, Ground ground, float e, float eW, float eE, float eN, float eS, float t, double x, double z,
                            int px, int pz, byte[] cls, int idx) {
            for (Lake k : lakes) {
                if (Math.abs(x - k.cx()) > k.reach() + s || Math.abs(z - k.cz()) > k.reach() + s) continue;
                double q = k.q(x, z);
                double rim = 1 + 1.3 * s / Math.min(k.along(), k.across());
                if (q < 1) {
                    cls[idx] = LAKE_C;
                    if (k.salt()) return SALT;
                    return q > 0.82 ? LAKE_SHALLOW : q < 0.45 ? LAKE_DEEP : LAKE;
                }
                if (q < rim) {
                    cls[idx] = LINE_C;
                    return k.salt() ? SALT_SHORE : SHORE;
                }
            }
            if ((ground == Ground.WETLAND || ground == Ground.TAIGA && e < 0.42) && e < 0.5 && !nearSpot(x, z, 140)) {
                double p = fbm(SEED_POND, x, z, 110, 2);
                if (p > (ground == Ground.WETLAND ? 0.42 : 0.6)) {
                    cls[idx] = LAKE_C;
                    return LAKE;
                }
            }
            // the relief: lit from the north-west, as maps are; slopes per block, made comparable across the levels
            double gx = (eE - eW) / (2.0 * s), gz = (eS - eN) / (2.0 * s);
            double light = (gx + gz) * 260;
            double dither = (BAYER[(pz & 3) * 4 + (px & 3)] + 0.5) / 16 - 0.5;
            double lit = light + dither * 0.3;
            int step = lit > 0.32 ? 1 : lit < -1.0 ? -2 : lit < -0.32 ? -1 : 0;
            double snowLine = (ground.arid ? SNOW_ARID : SNOW_LINE) + 0.05 * t, rockLine = (ground.arid ? ROCK_ARID_LINE : ROCK_LINE) + 0.05 * t;
            if (zoom > BakedAtlas.FINEST && !ground.arid && e > 0.4 && e < rockLine
                    && (Math.floor(e * 10) != Math.floor(eE * 10) || Math.floor(e * 10) != Math.floor(eS * 10))) step--; // contour lines
            if (e > snowLine) {
                return step > 0 ? SNOW_LIT : step == 0 ? SNOW : step == -1 ? SNOW_SHADE : SNOW_DEEP;
            }
            byte b;
            if (e > rockLine) {
                b = ground.arid ? (t > 0.1 ? ROCK_ARID_DARK : ROCK_ARID) : (t > 0.15 ? ROCK_WARM : ROCK);
            } else if (ground == Ground.DUNES) {
                double a = Math.toRadians(28), wl = Math.max(56, 3.5 * s);
                double phase = (x * Math.cos(a) + z * Math.sin(a)) / wl + 2.2 * fbm(SEED_DUNES, x, z, 420, 2);
                double crest = Math.sin(2 * Math.PI * phase);
                b = t + dither * 0.16 > 0.25 ? ground.lush : ground.main;
                step = crest > 0.55 ? 1 : crest < -0.55 ? -1 : step;
            } else if (ground == Ground.BADLANDS) {
                int band = Math.floorMod((int) Math.floor(e * 32 + 0.8 * t), 3);
                b = band == 0 ? ground.dry : band == 1 ? ground.main : ground.lush;
            } else {
                double density = forest(ground, e, eN, eS, t, s);
                if (density > 0.5) {
                    cls[idx] = CANOPY_C;
                    b = ground == Ground.TAIGA || ground == Ground.WETLAND || e > 0.5 ? TAIGA_CANOPY : CANOPY;
                    if (zoom == BakedAtlas.FINEST && Cartography.hash01(SEED_SPECKLE, Math.round(x), Math.round(z)) < 0.28) {
                        return b == TAIGA_CANOPY ? TREE_DARK : TREE_LIT;
                    }
                } else {
                    double tt = t + dither * 0.16; // the second colours dithered in where the texture leans their way
                    b = tt < -0.2 ? ground.dry : tt > 0.2 ? ground.lush : ground.main;
                    if (ground == Ground.GOBI && Cartography.hash01(SEED_SPECKLE, Math.round(x), Math.round(z)) < 0.05) b = GRAVEL;
                }
            }
            return MapColors.pixel(MapColors.id(b), ColumnShading.relief(MapColors.shade(b), Math.max(-2, Math.min(1, step))));
        }

        /** True when a place stands within {@code d} blocks of (x, z). */
        private boolean nearSpot(double x, double z, double d) {
            for (int i = 0; i < spotX.length; i++) {
                double dx = x - spotX[i], dz = z - spotZ[i];
                if (dx * dx + dz * dz < d * d) return true;
            }
            return false;
        }

        /** How wooded a pixel is (over 0.5: forest): forest-steppe keeps its trees on the slopes facing north. */
        private static double forest(Ground ground, double e, double eN, double eS, double t, int s) {
            if (ground.forest <= 0) return 0;
            double north = (eS - eN) / (2.0 * s) * 260; // the ground rises to the south: the slope faces north
            double d = ground.forest + 0.45 * t + (ground == Ground.FOREST || ground == Ground.HIGHLAND ? 0.9 * north : 0.3 * north);
            if (e > 0.62) d -= (e - 0.62) * 4; // the tree line
            return d;
        }

        // -------------------------------------------------------------------------------- the overlays

        private void river(Line r, int s, int ox, int oz, int w, int h, byte[] out, byte[] cls) {
            for (int i = 0; i < r.size() - 1; i++) {
                double ax = (r.x()[i] - ox) / s, az = (r.z()[i] - oz) / s, bx = (r.x()[i + 1] - ox) / s, bz = (r.z()[i + 1] - oz) / s;
                double rad = Math.max(0, r.w()[i] / s / 2 - 0.5);
                int k = Math.max(1, (int) Math.ceil(Math.hypot(bx - ax, bz - az) * 2));
                for (int j = 0; j < k; j++) {
                    double fx = ax + (bx - ax) * j / k, fz = az + (bz - az) * j / k;
                    int ri = (int) Math.ceil(rad);
                    for (int dz = -ri; dz <= ri; dz++) {
                        for (int dx = -ri; dx <= ri; dx++) {
                            if (dx * dx + dz * dz > rad * rad + 0.25) continue;
                            int px = (int) Math.floor(fx) + dx, pz = (int) Math.floor(fz) + dz;
                            if (px < 0 || pz < 0 || px >= w || pz >= h) continue;
                            int idx = pz * w + px;
                            if (cls[idx] == LAKE_C) continue;
                            out[idx] = RIVER;
                            cls[idx] = RIVER_C;
                        }
                    }
                }
            }
        }

        /**
         * A caravan road: dashes of dark brown and pale sand in turn ({@code dash} pixels the pair), so it reads on the
         * Gobi's sand and in the taiga alike; dark over a river (a bridge or a ford).
         */
        private void road(Line r, int s, int ox, int oz, int w, int h, int dash, byte[] out, byte[] cls) {
            double run = 0;
            int lastIdx = -1;
            for (int i = 0; i < r.size() - 1; i++) {
                double ax = (r.x()[i] - ox) / s, az = (r.z()[i] - oz) / s, bx = (r.x()[i + 1] - ox) / s, bz = (r.z()[i + 1] - oz) / s;
                double len = Math.hypot(bx - ax, bz - az);
                int k = Math.max(1, (int) Math.ceil(len * 2));
                for (int j = 0; j < k; j++) {
                    double fx = ax + (bx - ax) * j / k, fz = az + (bz - az) * j / k;
                    run += len / k;
                    int px = (int) Math.floor(fx), pz = (int) Math.floor(fz);
                    if (px < 0 || pz < 0 || px >= w || pz >= h) continue;
                    int idx = pz * w + px;
                    if (idx == lastIdx || cls[idx] == LAKE_C || cls[idx] == ROAD_C && out[idx] != 0) continue;
                    lastIdx = idx;
                    out[idx] = run % dash < dash * 0.5 || cls[idx] == RIVER_C ? ROAD_DARK : ROAD_LIGHT;
                    cls[idx] = ROAD_C;
                }
            }
        }

        /** A region's border: a dot every third pixel, faint red, off the waters and the roads. */
        private void border(Line b, int s, int ox, int oz, int w, int h, byte[] out, byte[] cls) {
            double run = 0;
            for (int i = 0; i < b.size() - 1; i++) {
                double ax = (b.x()[i] - ox) / s, az = (b.z()[i] - oz) / s, bx = (b.x()[i + 1] - ox) / s, bz = (b.z()[i + 1] - oz) / s;
                double len = Math.hypot(bx - ax, bz - az);
                int k = Math.max(1, (int) Math.ceil(len * 2));
                for (int j = 0; j < k; j++) {
                    double before = run;
                    run += len / k;
                    if (Math.floor(before / 3) == Math.floor(run / 3)) continue;
                    int px = (int) Math.floor(ax + (bx - ax) * j / k), pz = (int) Math.floor(az + (bz - az) * j / k);
                    if (px < 0 || pz < 0 || px >= w || pz >= h) continue;
                    int idx = pz * w + px;
                    if (cls[idx] == LAKE_C || cls[idx] == RIVER_C || cls[idx] == ROAD_C) continue;
                    out[idx] = BORDER;
                    cls[idx] = LINE_C;
                }
            }
        }

        /** Хархорум when its blocks are not on the map yet: walls with four gates, red roofs, white streets, cherry trees. */
        private void city(int s, int ox, int oz, int w, int h, byte[] out, byte[] cls) {
            double half = layout.cityHalf();
            if (half <= 0) return;
            int x0 = (int) Math.floor((sx - half - ox) / s), x1 = (int) Math.floor((sx + half - ox) / s);
            int z0 = (int) Math.floor((sz - half - oz) / s), z1 = (int) Math.floor((sz + half - oz) / s);
            int mx = (int) Math.floor((sx - ox) / s), mz = (int) Math.floor((sz - oz) / s);
            int gate = Math.max(0, (int) Math.round(6.0 / s));
            int street = Math.max(0, (int) Math.round(3.0 / s));
            for (int pz = z0; pz <= z1; pz++) {
                for (int px = x0; px <= x1; px++) {
                    if (px < 0 || pz < 0 || px >= w || pz >= h) continue;
                    int idx = pz * w + px;
                    boolean wall = px == x0 || px == x1 || pz == z0 || pz == z1;
                    boolean gap = Math.abs(px - mx) <= gate || Math.abs(pz - mz) <= gate;
                    byte b;
                    if (wall) b = gap ? PLAZA : WALL;
                    else if (Math.abs(px - mx) <= street || Math.abs(pz - mz) <= street) b = PLAZA;
                    else {
                        double hh = Cartography.hash01(SEED_SYMBOL + 7, Math.floorDiv(ox + px * s, 8), Math.floorDiv(oz + pz * s, 8));
                        b = hh < 0.5 ? ROOF : hh < 0.62 ? BLOSSOM : PLAZA;
                    }
                    out[idx] = b;
                    cls[idx] = CITY_C;
                }
            }
        }

        private static final String[] CONIFER = {".d.", "ldd", "ldd", ".t."};
        private static final String[] BROADLEAF = {".l.", "ldd", ".t."};
        private static final String[] PEAK = {"..p..", ".lpd.", "lllddd"};

        /** The marks of the two widest levels: peaks on the ranges, trees in the forests, each on whole free land. */
        private void symbols(int zoom, int s, int ox, int oz, int w, int h, byte[] out, byte[] cls, float[] heights, byte[] grounds) {
            int cell = zoom == BakedAtlas.WIDEST ? 6 : 7;
            for (int cz = 0; cz < h; cz += cell) {
                for (int cx = 0; cx < w; cx += cell) {
                    long wx = Math.floorDiv(ox + (long) cx * s, (long) cell * s), wz = Math.floorDiv(oz + (long) cz * s, (long) cell * s);
                    int px = cx + (int) (Cartography.hash01(SEED_SYMBOL, wx, wz) * (cell - 4)) + 2;
                    int pz = cz + (int) (Cartography.hash01(SEED_SYMBOL + 1, wx, wz) * (cell - 3)) + 2;
                    if (px >= w || pz >= h) continue;
                    float e = heights[pz * w + px];
                    Ground gr = GROUNDS[grounds[pz * w + px]];
                    double snow = gr.arid ? SNOW_ARID : SNOW_LINE;
                    if (e > (gr.arid ? ROCK_ARID_LINE : ROCK_LINE) - 0.04 && Cartography.hash01(SEED_SYMBOL + 2, wx, wz) < 0.8) {
                        boolean white = e > snow - 0.06;
                        stamp(PEAK, px, pz, w, h, out, cls, white ? SNOW_LIT : PEAK_LIT, white ? SNOW_DEEP : PEAK_DARK, white ? SNOW_LIT : PEAK_LIT, false);
                    }
                }
            }
            int tcell = 4;
            for (int cz = 0; cz < h; cz += tcell) {
                for (int cx = 0; cx < w; cx += tcell) {
                    long wx = Math.floorDiv(ox + (long) cx * s, (long) tcell * s), wz = Math.floorDiv(oz + (long) cz * s, (long) tcell * s);
                    int px = cx + (int) (Cartography.hash01(SEED_SYMBOL + 3, wx, wz) * 2) + 1;
                    int pz = cz + (int) (Cartography.hash01(SEED_SYMBOL + 4, wx, wz) * 2) + 1;
                    if (px >= w || pz >= h || cls[pz * w + px] != CANOPY_C) continue;
                    if (Cartography.hash01(SEED_SYMBOL + 5, wx, wz) > 0.7) continue;
                    Ground gr = GROUNDS[grounds[pz * w + px]];
                    boolean conifer = gr == Ground.TAIGA || gr == Ground.WETLAND || heights[pz * w + px] > 0.5;
                    stamp(conifer ? CONIFER : BROADLEAF, px, pz, w, h, out, cls, TREE_LIT, TREE_DARK, TRUNK, true);
                }
            }
        }

        /**
         * Stamps a mark (rows of '.' clear, 'l' lit, 'd' shaded, 'p' / 't' its tip or trunk) with its middle-top at
         * (px, pz), only where every one of its pixels is free land (or canopy, for a tree).
         */
        private static void stamp(String[] glyph, int px, int pz, int w, int h, byte[] out, byte[] cls, byte lit, byte dark, byte tip, boolean onCanopy) {
            int gw = glyph[glyph.length - 1].length(), x0 = px - gw / 2;
            for (int r = 0; r < glyph.length; r++) {
                for (int c = 0; c < glyph[r].length(); c++) {
                    if (glyph[r].charAt(c) == '.') continue;
                    int x = x0 + c, z = pz + r;
                    if (x < 0 || z < 0 || x >= w || z >= h) return;
                    byte k = cls[z * w + x];
                    if (onCanopy ? k != CANOPY_C : k != LAND && k != CANOPY_C) return;
                }
            }
            for (int r = 0; r < glyph.length; r++) {
                for (int c = 0; c < glyph[r].length(); c++) {
                    char ch = glyph[r].charAt(c);
                    if (ch == '.') continue;
                    int idx = (pz + r) * w + x0 + c;
                    out[idx] = ch == 'l' ? lit : ch == 'd' ? dark : tip;
                    cls[idx] = MARK_C;
                }
            }
        }
    }

    /** The relative luminance (WCAG) of a map byte's colour; 1 for byte 0 (the parchment shows: light). */
    static double luminance(byte b) {
        int argb = MapColors.argb(b);
        if (argb == 0) return 1;
        return luminance(argb & 0xFFFFFF);
    }

    /** The relative luminance (WCAG 2) of 0xRRGGBB. */
    public static double luminance(int rgb) {
        return 0.2126 * lin((rgb >> 16) & 0xFF) + 0.7152 * lin((rgb >> 8) & 0xFF) + 0.0722 * lin(rgb & 0xFF);
    }

    /** The WCAG contrast ratio of two colours (0xRRGGBB): 1 … 21. */
    public static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double lin(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
