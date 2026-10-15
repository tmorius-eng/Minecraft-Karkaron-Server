package mn.suld.api.map;

/**
 * Which land a world tile shows, the real scanned blocks or the baked cartography (docs/WORLD_MAP.md, «Baked
 * cartography»), by zoom:
 * <ul>
 *   <li>zooms 0 and 1 (and the magnified views cut from zoom 0): the real blocks wherever the server has them, crisp;
 *   anything else is the baked land in the pale survey ink, so the explored land stands out vivid;</li>
 *   <li>zoom 2: the real blocks where the land around a pixel (3 × 3 pixels) is explored, and the baked land elsewhere:
 *   a stray chunk or two no longer shows as a speck;</li>
 *   <li>zoom 3: the atlas, with the real blocks only where most of the land around (9 × 9 pixels, 72 blocks) has been
 *   explored, the edge dithered into it, so a dungeon gate's lone patch never sits on the map as a square;</li>
 *   <li>zoom 4, the whole world: the atlas alone, and the capital's real blocks within its walls. Such a tile reads no
 *   column at all unless it touches the capital.</li>
 * </ul>
 * The capital's real blocks show at every zoom. The dither is fixed to world pixels, so tiles meet without a seam. With
 * no bake (map.cartography off, or before it is done) a tile is the real blocks only, as before.
 */
public final class AtlasLod {

    /** By tile zoom: the explored share is measured over (2r + 1)² pixels; below LO the atlas shows, from HI the real land. */
    static final int[] RADIUS = {0, 0, 1, 4, 0};
    static final double[] LO = {0, 0, 0.25, 0.55, Double.POSITIVE_INFINITY};
    static final double[] HI = {0, 0, 0.6, 0.95, Double.POSITIVE_INFINITY};
    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private AtlasLod() {
    }

    /** True when the real blocks can show at {@code zoom} outside the capital. */
    public static boolean realShows(int zoom) {
        return LO[MapGeometry.checkZoom(zoom)] <= 1;
    }

    /** Tile ({@code tx}, {@code tz}) of {@code zoom}: the real blocks from {@code src} and the baked land from {@code baked} (may be null). */
    public static byte[] compose(MapGeometry g, TileComposer.Source src, BakedAtlas baked, int zoom, int tx, int tz) {
        if (baked == null) return TileComposer.compose(g, src, zoom, tx, tz);
        int s = MapGeometry.scale(zoom), t = MapGeometry.TILE, x0 = MapGeometry.tileStart(tx, zoom), z0 = MapGeometry.tileStart(tz, zoom);
        boolean capital = baked.touchesCapital(x0, z0, x0 + MapGeometry.span(zoom), z0 + MapGeometry.span(zoom));
        byte[] out = new byte[TileComposer.PIXELS];
        if (!realShows(zoom) && !capital) { // the whole world: the atlas alone, not one column read
            for (int pz = 0; pz < t; pz++) {
                int z = z0 + pz * s;
                for (int px = 0; px < t; px++) {
                    int x = x0 + px * s;
                    if (g.inside(x, z)) out[pz * t + px] = baked.survey(zoom, x, z);
                }
            }
            return out;
        }
        byte[] real = TileComposer.compose(g, src, zoom, tx, tz);
        int r = RADIUS[zoom];
        if (r == 0) { // crisp: the real blocks wherever they are (or, far out, only in the capital)
            boolean everywhere = realShows(zoom);
            for (int pz = 0; pz < t; pz++) {
                int z = z0 + pz * s;
                for (int px = 0; px < t; px++) {
                    int x = x0 + px * s, i = pz * t + px;
                    if (!g.inside(x, z)) continue;
                    out[i] = real[i] != 0 && (everywhere || baked.capital(x, z)) ? real[i] : baked.survey(zoom, x, z);
                }
            }
            return out;
        }
        // the explored share around each pixel: a summed table over the tile and r pixels round it
        int n = t + 2 * r, n1 = n + 1;
        int[] known = new int[n1 * n1], inside = new int[n1 * n1];
        for (int j = 0; j < n; j++) {
            int z = z0 + (j - r) * s;
            for (int i = 0; i < n; i++) {
                int x = x0 + (i - r) * s;
                boolean in = g.inside(x, z);
                int k;
                if (!in) k = 0;
                else if (i >= r && i < r + t && j >= r && j < r + t) k = real[(j - r) * t + (i - r)] != 0 ? 1 : 0;
                else k = src.column(x, z) != 0 ? 1 : 0;
                int c = (j + 1) * n1 + i + 1;
                known[c] = k + known[c - 1] + known[c - n1] - known[c - n1 - 1];
                inside[c] = (in ? 1 : 0) + inside[c - 1] + inside[c - n1] - inside[c - n1 - 1];
            }
        }
        double lo = LO[zoom], hi = HI[zoom];
        int w = 2 * r + 1;
        for (int pz = 0; pz < t; pz++) {
            int z = z0 + pz * s;
            for (int px = 0; px < t; px++) {
                int x = x0 + px * s, i = pz * t + px;
                if (!g.inside(x, z)) continue;
                boolean showReal = false;
                if (real[i] != 0) {
                    if (baked.capital(x, z)) showReal = true;
                    else {
                        int a = pz * n1 + px, b = a + w, c = a + w * n1, d = c + w; // the window's corners in the table
                        int kn = known[d] - known[b] - known[c] + known[a], in = inside[d] - inside[b] - inside[c] + inside[a];
                        double share = in == 0 ? 0 : (double) kn / in, f = (share - lo) / (hi - lo);
                        int gx = Math.floorDiv(x, s), gz = Math.floorDiv(z, s);
                        showReal = f > (BAYER[(gz & 3) * 4 + (gx & 3)] + 0.5) / 16;
                    }
                }
                out[i] = showReal ? real[i] : baked.survey(zoom, x, z);
            }
        }
        return out;
    }
}
