package mn.suld.api.map;

/**
 * Where the world map's pixels are (docs/WORLD_MAP.md). A map tile is the 128 × 128 pixels of one vanilla map. At zoom
 * {@code z} (0 … 4) a pixel is {@code 2^z} blocks, so a tile spans {@code 128·2^z} blocks; tiles are aligned to the
 * world's own coordinates (tile 0 starts at block 0), so a moved border or spawn never invalidates a tile. The
 * playable square is the world border: {@code [minX, maxX) × [minZ, maxZ)}. North (−z) is up and east (+x) is right.
 */
public record MapGeometry(int minX, int minZ, int maxX, int maxZ) {

    public static final int TILE = 128;
    public static final int ZOOMS = 5;

    public MapGeometry {
        if (maxX <= minX || maxZ <= minZ) throw new IllegalArgumentException("empty map " + minX + ".." + maxX + " × " + minZ + ".." + maxZ);
    }

    /** The square of a world border: centre and diameter in blocks. */
    public static MapGeometry ofBorder(double centreX, double centreZ, double diameter) {
        double r = diameter / 2;
        return new MapGeometry((int) Math.floor(centreX - r), (int) Math.floor(centreZ - r), (int) Math.ceil(centreX + r), (int) Math.ceil(centreZ + r));
    }

    /** Blocks per pixel at {@code zoom}. */
    public static int scale(int zoom) {
        return 1 << checkZoom(zoom);
    }

    /** Blocks per tile side at {@code zoom}. */
    public static int span(int zoom) {
        return TILE << checkZoom(zoom);
    }

    /** The tile (along one axis) that holds block {@code block} at {@code zoom}. */
    public static int tileOf(int block, int zoom) {
        return Math.floorDiv(block, span(zoom));
    }

    /** The first block (along one axis) of tile {@code tile} at {@code zoom}. */
    public static int tileStart(int tile, int zoom) {
        return tile * span(zoom);
    }

    public static int checkZoom(int zoom) {
        if (zoom < 0 || zoom >= ZOOMS) throw new IllegalArgumentException("zoom " + zoom);
        return zoom;
    }

    public boolean inside(double x, double z) {
        return x >= minX && x < maxX && z >= minZ && z < maxZ;
    }

    public int width() {
        return maxX - minX;
    }

    public int depth() {
        return maxZ - minZ;
    }

    public double centreX() {
        return (minX + maxX) / 2.0;
    }

    public double centreZ() {
        return (minZ + maxZ) / 2.0;
    }

    /** The first and last tile (inclusive) that touch the square along x at {@code zoom}. */
    public int[] tilesX(int zoom) {
        return new int[]{tileOf(minX, zoom), tileOf(maxX - 1, zoom)};
    }

    public int[] tilesZ(int zoom) {
        return new int[]{tileOf(minZ, zoom), tileOf(maxZ - 1, zoom)};
    }

    /** {@code v} kept inside {@code [lo, hi]}. */
    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
