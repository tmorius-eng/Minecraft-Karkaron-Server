package mn.suld.api.map;

/**
 * What one open atlas shows (docs/WORLD_MAP.md): a wall of {@code cols × rows} map frames, each 128 × 128 map pixels,
 * looking at the world centred on block ({@code centreX}, {@code centreZ}) at {@code zoom} ({@code 2^zoom} blocks per
 * pixel). North is up. The view never leaves the world: the centre is kept so the wall stays over the playable square
 * (or centred on it when the square is smaller than the wall).
 * <p>
 * Panel coordinates are blocks on the wall: {@code u} to the right and {@code v} up from the wall's middle; one frame is
 * one block, so a panel unit is {@code 128 · 2^zoom} world blocks.
 *
 * @param cols    frames across
 * @param rows    frames down
 * @param centreX world x at the wall's middle
 * @param centreZ world z at the wall's middle
 * @param zoom    0 … {@link MapGeometry#ZOOMS} − 1
 */
public record AtlasView(int cols, int rows, double centreX, double centreZ, int zoom) {

    public AtlasView {
        if (cols < 1 || rows < 1 || cols > 16 || rows > 16) throw new IllegalArgumentException("wall " + cols + "x" + rows);
        MapGeometry.checkZoom(zoom);
    }

    /** World blocks across one frame. */
    public int blocksPerFrame() {
        return MapGeometry.span(zoom);
    }

    public int scale() {
        return MapGeometry.scale(zoom);
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
        if (newZoom < 0 || newZoom >= MapGeometry.ZOOMS || newZoom == zoom) return this;
        double wx = worldX(u), wz = worldZ(v);
        double span = MapGeometry.span(newZoom);
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
        int s = scale(), t = MapGeometry.TILE;
        int fx = Math.floorDiv(frameX(col), s), fz = Math.floorDiv(frameZ(row), s); // the frame's first pixel, in world pixels
        int ox = tx * t, oz = tz * t;                                               // the tile's first pixel
        int x0 = Math.max(fx, ox), x1 = Math.min(fx + t, ox + t), z0 = Math.max(fz, oz), z1 = Math.min(fz + t, oz + t);
        if (x0 >= x1 || z0 >= z1) return;
        for (int z = z0; z < z1; z++) System.arraycopy(tile, (z - oz) * t + (x0 - ox), frame, (z - fz) * t + (x0 - fx), x1 - x0);
    }

    /** The world tiles frame ({@code col}, {@code row}) is made of: {tx0, tz0, tx1, tz1} inclusive. */
    public int[] tilesOf(int col, int row) {
        int x = frameX(col), z = frameZ(row), span = blocksPerFrame();
        return new int[]{MapGeometry.tileOf(x, zoom), MapGeometry.tileOf(z, zoom),
                MapGeometry.tileOf(x + span - 1, zoom), MapGeometry.tileOf(z + span - 1, zoom)};
    }
}
