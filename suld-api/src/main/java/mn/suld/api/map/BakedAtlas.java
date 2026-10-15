package mn.suld.api.map;

/**
 * The world as {@link Cartography} baked it (docs/WORLD_MAP.md, «Baked cartography»): one map-byte raster per level,
 * zooms {@link #FINEST} … {@link #WIDEST} (4, 8 and 16 blocks a pixel), aligned to the world tiles, built once and never
 * changed. A pixel is one array read: no chunk, block or biome is ever asked for, so the atlas and the minimap can draw
 * it on any thread at any time. Zooms 0 and 1 read the finest level, each block nudged up to two blocks by a fixed hash so
 * the 4-block cells melt into each other instead of showing as squares.
 * <p>
 * {@link #survey} is the same land as an unsurveyed land is drawn at the close zooms: a shade lighter and hatched with
 * fine diagonal parchment lines, so it reads at a glance as land nobody has explored yet beside the solid, vivid colours
 * of the real scanned blocks.
 */
public final class BakedAtlas {

    /** The finest and the widest baked zoom. */
    public static final int FINEST = 2, WIDEST = MapGeometry.ZOOMS - 1;
    /** The atlas page's parchment (tools/map/atlas_art.py «parch»): the survey hatching's colour is the byte nearest it. */
    static final int PARCHMENT = 0xEADAB4;
    /** The hatching, by zoom: a parchment line every this many pixels (diagonal); 0 for none (the far zooms are the atlas). */
    static final int[] HATCH = {4, 4, 6, 0, 0};

    private final MapGeometry geometry;
    private final int[] originX, originZ, width, height;
    private final byte[][] raster;
    /** The capital's walls {minX, minZ, maxX, maxZ} (blocks, max exclusive): its real blocks show at every zoom. */
    private final int[] capital;
    private final long bakeNanos;

    BakedAtlas(MapGeometry geometry, int[] originX, int[] originZ, int[] width, int[] height, byte[][] raster, int[] capital, long bakeNanos) {
        this.geometry = geometry;
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.height = height;
        this.raster = raster;
        this.capital = capital;
        this.bakeNanos = bakeNanos;
    }

    /** The playable square it was baked for: a moved border needs a new bake. */
    public MapGeometry geometry() {
        return geometry;
    }

    /** The baked map byte at block (x, z) for {@code zoom}; 0 off the raster. */
    public byte pixel(int zoom, int x, int z) {
        MapGeometry.checkZoom(zoom);
        if (zoom < FINEST) { // a fixed nudge of −2 … +1 blocks each way: cell edges dither instead of stepping
            int h = mix(x, z);
            x += (h & 3) - 2;
            z += ((h >>> 2) & 3) - 2;
        }
        int l = Math.max(zoom, FINEST) - FINEST, s = MapGeometry.scale(l + FINEST);
        int ix = Math.floorDiv(x - originX[l], s), iz = Math.floorDiv(z - originZ[l], s);
        if (ix < 0 || iz < 0 || ix >= width[l] || iz >= height[l]) return 0;
        return raster[l][iz * width[l] + ix];
    }

    /**
     * {@link #pixel} as unexplored land at {@code zoom}: at the close zooms a shade lighter and hatched (the lines fixed to
     * world pixels, so tiles meet without a seam); far out, the atlas as it is.
     */
    public byte survey(int zoom, int x, int z) {
        byte b = pixel(zoom, x, z);
        int every = HATCH[zoom];
        if (every == 0 || b == 0) return b;
        int s = MapGeometry.scale(zoom);
        if (Math.floorMod(Math.floorDiv(x, s) + Math.floorDiv(z, s), every) == 0) return Survey.LINE;
        return Survey.LIGHTER[b & 0xFF];
    }

    /** True when block (x, z) is within the capital's walls. */
    public boolean capital(int x, int z) {
        return x >= capital[0] && x < capital[2] && z >= capital[1] && z < capital[3];
    }

    /** True when the block square {@code [x0, x1) × [z0, z1)} touches the capital. */
    public boolean touchesCapital(int x0, int z0, int x1, int z1) {
        return x0 < capital[2] && capital[0] < x1 && z0 < capital[3] && capital[1] < z1;
    }

    /** Bytes held (the rasters): what the bake costs in memory for good. */
    public long bytes() {
        long n = 0;
        for (byte[] r : raster) n += r.length;
        return n;
    }

    /** How long the bake took (ms). */
    public long bakeMillis() {
        return bakeNanos / 1_000_000;
    }

    /** The raster of {@code zoom} ({@link #FINEST} … {@link #WIDEST}), row by row, north row first; a copy. */
    public byte[] raster(int zoom) {
        return raster[level(zoom)].clone();
    }

    /** The world x of the raster's first column at {@code zoom}. */
    public int originX(int zoom) {
        return originX[level(zoom)];
    }

    public int originZ(int zoom) {
        return originZ[level(zoom)];
    }

    public int width(int zoom) {
        return width[level(zoom)];
    }

    public int height(int zoom) {
        return height[level(zoom)];
    }

    private static int level(int zoom) {
        if (zoom < FINEST || zoom > WIDEST) throw new IllegalArgumentException("baked zoom " + zoom);
        return zoom - FINEST;
    }

    static int mix(int x, int z) {
        int h = x * 0x27D4EB2D ^ z * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }

    /**
     * The survey ink: every map byte one shade lighter (the hue kept), a dark one lighter still until it is as light as
     * {@link #LIGHT_ENOUGH} (so the minimap's black-outlined marks read on it; a colour too dark even at its lightest shade,
     * the badlands' red rock, steps toward the parchment), and the hatching's parchment byte.
     */
    static final class Survey {
        static final double LIGHT_ENOUGH = 0.125;
        static final byte[] LIGHTER = new byte[256];
        static final byte LINE = MapColors.nearest(PARCHMENT);

        /** The byte nearest {@code b}'s colour moved {@code k} of the way to the parchment. */
        static byte toward(byte b, double k) {
            int c = MapColors.argb(b), out = 0;
            for (int sh = 16; sh >= 0; sh -= 8) {
                out |= (int) Math.round(((c >> sh) & 0xFF) * (1 - k) + ((PARCHMENT >> sh) & 0xFF) * k) << sh;
            }
            return MapColors.nearest(out);
        }

        static {
            for (int b = 4; b < 256; b++) {
                int id = MapColors.id((byte) b);
                if (id >= MapColors.IDS) {
                    LIGHTER[b] = (byte) b;
                    continue;
                }
                int shade = ColumnShading.relief(MapColors.shade((byte) b), 1);
                while (shade != MapColors.SHADE_HIGH && Cartography.luminance(MapColors.pixel(id, shade)) < LIGHT_ENOUGH) {
                    shade = ColumnShading.relief(shade, 1);
                }
                byte out = MapColors.pixel(id, shade);
                for (double k = 0.1; Cartography.luminance(out) < LIGHT_ENOUGH && k < 0.7; k += 0.05) out = toward(MapColors.pixel(id, shade), k);
                LIGHTER[b] = out;
            }
        }
    }
}
