package mn.suld.api.map;

/**
 * What one open atlas shows (docs/WORLD_MAP.md): a wall of {@code cols × rows} map frames, each 128 × 128 map pixels,
 * looking at the world centred on block ({@code centreX}, {@code centreZ}) at {@code zoom} ({@code 2^zoom} blocks per
 * pixel). North is up. The view never leaves the world: the centre is kept so the wall stays over the playable square
 * (or centred on it when the square is smaller than the wall).
 * <p>
 * Panel coordinates are blocks on the wall: {@code u} to the right and {@code v} up from the wall's middle; one frame is
 * one block, so a panel unit is {@code 128 · 2^zoom} world blocks.
 * <p>
 * Below zoom 0 the view is magnified: zoom −1 draws each block as 2 × 2 map pixels and zoom −2 as 4 × 4, cut from the
 * same 1-block tiles (zoom 0 is the finest the world map stores), so close up the wall shows streets and buildings
 * instead of a 640-block window at one pixel a block.
 *
 * @param cols    frames across
 * @param rows    frames down
 * @param centreX world x at the wall's middle
 * @param centreZ world z at the wall's middle
 * @param zoom    {@link #MIN_ZOOM} … {@link #MAX_ZOOM}
 */
public record AtlasView(int cols, int rows, double centreX, double centreZ, int zoom) {

    /** The closest zoom: 4 × 4 map pixels a block. */
    public static final int MIN_ZOOM = -2;
    /** The farthest zoom: the world map's own farthest tiles. */
    public static final int MAX_ZOOM = MapGeometry.ZOOMS - 1;

    public AtlasView {
        if (cols < 1 || rows < 1 || cols > 16 || rows > 16) throw new IllegalArgumentException("wall " + cols + "x" + rows);
        checkZoom(zoom);
    }

    public static int checkZoom(int zoom) {
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) throw new IllegalArgumentException("atlas zoom " + zoom);
        return zoom;
    }

    /** World blocks across one frame at {@code zoom}. */
    public static int blocksPerFrame(int zoom) {
        return checkZoom(zoom) >= 0 ? MapGeometry.span(zoom) : MapGeometry.TILE >> -zoom;
    }

    /** World blocks across one frame. */
    public int blocksPerFrame() {
        return blocksPerFrame(zoom);
    }

    /** Blocks per map pixel (1 when magnified: the wall then starts on a whole block). */
    public int scale() {
        return zoom >= 0 ? MapGeometry.scale(zoom) : 1;
    }

    /** Map pixels per block along one side: 1, or 2 / 4 when magnified. */
    public int magnify() {
        return zoom >= 0 ? 1 : 1 << -zoom;
    }

    /** The zoom of the world tiles the wall is cut from (a magnified view uses the 1-block tiles). */
    public int tileZoom() {
        return Math.max(0, zoom);
    }

    /** The world x at the wall's left edge (snapped to a whole pixel, so frames meet without seams). */
    public int left() {
        return snap(centreX - cols * blocksPerFrame() / 2.0);
    }

    /** The world z at the wall's top edge. */
    public int top() {
        return snap(centreZ - rows * blocksPerFrame() / 2.0);
    }

    private int snap(double block) {
        int s = scale();
        return (int) Math.floor(block / s) * s;
    }

    /** The world x of the first block of frame column {@code col}. */
    public int frameX(int col) {
        return left() + col * blocksPerFrame();
    }

    /** The world z of the first block of frame row {@code row}. */
    public int frameZ(int row) {
        return top() + row * blocksPerFrame();
    }

    /** Panel point → world x. */
    public double worldX(double u) {
        return left() + (u + cols / 2.0) * blocksPerFrame();
    }

    /** Panel point → world z. */
    public double worldZ(double v) {
        return top() + (rows / 2.0 - v) * blocksPerFrame();
    }

    /** World x → panel u. */
    public double u(double x) {
        return (x - left()) / blocksPerFrame() - cols / 2.0;
    }

    /** World z → panel v. */
    public double v(double z) {
        return rows / 2.0 - (z - top()) / blocksPerFrame();
    }

    /** True when panel point (u, v) is on the wall. */
    public boolean onWall(double u, double v) {
        return Math.abs(u) <= cols / 2.0 && Math.abs(v) <= rows / 2.0;
    }

    /** The view moved by {@code du, dv} frames (right, up), kept over the world. */
    public AtlasView pan(double du, double dv, MapGeometry g) {
        return new AtlasView(cols, rows, centreX + du * blocksPerFrame(), centreZ - dv * blocksPerFrame(), zoom).clamp(g);
    }

    /**
     * The view at {@code newZoom}, keeping the world point under panel point (u, v) where it is on the wall (the way a
     * web map zooms toward the cursor), kept over the world. Out-of-range zooms stay where they are.
     */
    public AtlasView zoomAt(int newZoom, double u, double v, MapGeometry g) {
        if (newZoom < MIN_ZOOM || newZoom > MAX_ZOOM || newZoom == zoom) return this;
        double wx = worldX(u), wz = worldZ(v);
        double span = blocksPerFrame(newZoom);
        double cx = wx - u * span, cz = wz + v * span;
        return new AtlasView(cols, rows, cx, cz, newZoom).clamp(g);
    }

    /** The same zoom centred on (x, z), kept over the world. */
    public AtlasView centreOn(double x, double z, MapGeometry g) {
        return new AtlasView(cols, rows, x, z, zoom).clamp(g);
    }

    /** The view kept over the playable square: a wall wider than the square is centred on it. */
    public AtlasView clamp(MapGeometry g) {
        double halfW = cols * blocksPerFrame() / 2.0, halfH = rows * blocksPerFrame() / 2.0;
        double cx = g.width() <= 2 * halfW ? g.centreX() : MapGeometry.clamp(centreX, g.minX() + halfW, g.maxX() - halfW);
        double cz = g.depth() <= 2 * halfH ? g.centreZ() : MapGeometry.clamp(centreZ, g.minZ() + halfH, g.maxZ() - halfH);
        return cx == centreX && cz == centreZ ? this : new AtlasView(cols, rows, cx, cz, zoom);
    }

    /** The farthest zoom whose wall covers the whole square (the «whole world» view), or the farthest zoom there is. */
    public static int wholeWorldZoom(int cols, int rows, MapGeometry g) {
        for (int z = 0; z < MapGeometry.ZOOMS; z++) {
            long span = MapGeometry.span(z);
            if (cols * span >= g.width() && rows * span >= g.depth()) return z;
        }
        return MapGeometry.ZOOMS - 1;
    }

    /**
     * Copies the part of world tile ({@code tx}, {@code tz}) that falls in frame ({@code col}, {@code row}) into
     * {@code frame} (128 × 128, row by row). A frame starts at a whole pixel but not on a tile edge, so it is made of up to
     * four tiles; call this for each of {@link #tilesOf}.
     */
    public void copy(byte[] tile, int tx, int tz, int col, int row, byte[] frame) {
        int m = magnify();
        if (m > 1) {
            copyMagnified(tile, tx, tz, col, row, frame, m);
            return;
        }
        int s = scale(), t = MapGeometry.TILE;
        int fx = Math.floorDiv(frameX(col), s), fz = Math.floorDiv(frameZ(row), s); // the frame's first pixel, in world pixels
        int ox = tx * t, oz = tz * t;                                               // the tile's first pixel
        int x0 = Math.max(fx, ox), x1 = Math.min(fx + t, ox + t), z0 = Math.max(fz, oz), z1 = Math.min(fz + t, oz + t);
        if (x0 >= x1 || z0 >= z1) return;
        for (int z = z0; z < z1; z++) System.arraycopy(tile, (z - oz) * t + (x0 - ox), frame, (z - fz) * t + (x0 - fx), x1 - x0);
    }

    /** {@link #copy} for a magnified view: each block of the 1-block tile becomes {@code m × m} frame pixels. */
    private void copyMagnified(byte[] tile, int tx, int tz, int col, int row, byte[] frame, int m) {
        int t = MapGeometry.TILE, n = t / m;                  // n blocks across a frame
        int fx = frameX(col), fz = frameZ(row), ox = tx * t, oz = tz * t;
        int x0 = Math.max(fx, ox), x1 = Math.min(fx + n, ox + t), z0 = Math.max(fz, oz), z1 = Math.min(fz + n, oz + t);
        for (int z = z0; z < z1; z++) {
            for (int x = x0; x < x1; x++) {
                byte b = tile[(z - oz) * t + (x - ox)];
                int px = (x - fx) * m, pz = (z - fz) * m;
                for (int d = 0; d < m; d++) java.util.Arrays.fill(frame, (pz + d) * t + px, (pz + d) * t + px + m, b);
            }
        }
    }

    /** The world tiles (at {@link #tileZoom}) frame ({@code col}, {@code row}) is made of: {tx0, tz0, tx1, tz1} inclusive. */
    public int[] tilesOf(int col, int row) {
        int x = frameX(col), z = frameZ(row), span = blocksPerFrame(), tz = tileZoom();
        return new int[]{MapGeometry.tileOf(x, tz), MapGeometry.tileOf(z, tz),
                MapGeometry.tileOf(x + span - 1, tz), MapGeometry.tileOf(z + span - 1, tz)};
    }
}
