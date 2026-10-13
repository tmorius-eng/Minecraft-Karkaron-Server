package mn.suld.api.map;

/**
 * Draws one map tile (128 × 128 bytes, row by row, north row first) of the world at a zoom, from real blocks only
 * (owner, 2026-10-13: no predicted land): a column the server has generated and scanned is drawn the way a vanilla map
 * draws it (its top block's colour, the relief shade against the column one pixel north, water by depth); a column with
 * no data, and anything outside the playable square, stays 0 (transparent, so the atlas page shows through as
 * unexplored parchment).
 */
public final class TileComposer {

    public static final int PIXELS = MapGeometry.TILE * MapGeometry.TILE;

    /** What the composer reads: the packed {@link MapColumn} at a block, or 0 when the server has no data for it. */
    @FunctionalInterface
    public interface Source {

        int column(int x, int z);
    }

    private TileComposer() {
    }

    public static byte[] compose(MapGeometry g, Source src, int zoom, int tx, int tz) {
        int s = MapGeometry.scale(zoom), x0 = MapGeometry.tileStart(tx, zoom), z0 = MapGeometry.tileStart(tz, zoom);
        byte[] out = new byte[PIXELS];
        for (int pz = 0; pz < MapGeometry.TILE; pz++) {
            int z = z0 + pz * s;
            for (int px = 0; px < MapGeometry.TILE; px++) {
                int x = x0 + px * s;
                if (!g.inside(x, z)) continue;
                out[pz * MapGeometry.TILE + px] = pixel(src, x, z, s);
            }
        }
        return out;
    }

    /** One pixel of a zoom with {@code scale} blocks per pixel, at block {@code (x, z)}. */
    public static byte pixel(Source src, int x, int z, int scale) {
        int gx = Math.floorDiv(x, scale), gz = Math.floorDiv(z, scale);
        int col = src.column(x, z);
        if (col == 0) return 0;
        int step;
        if (MapColumn.depth(col) > 0) { // water (whatever colour its biome gives it) shades by depth
            step = ColumnShading.waterStep(MapColumn.depth(col), gx, gz);
        } else {
            int north = src.column(x, z - scale);
            int nh = north != 0 ? MapColumn.height(north) : MapColumn.height(col);
            step = ColumnShading.landStep(MapColumn.height(col), nh, scale, gx, gz);
        }
        return MapColors.pixel(MapColumn.id(col), ColumnShading.relief(MapColumn.shade(col), step));
    }
}
