package mn.suld.api.map;

/**
 * One block column as the map needs it, packed in an int: the map byte the column shows on flat ground (colour id × 4
 * + shade, 8 bits), the water depth when the top is water (8 bits, 0 for land), and the top block's height + 64
 * (16 bits). 0 means "no data" (the chunk has never been seen): a real column always has a colour id of 1 or more.
 * <p>
 * A vanilla map's column is its block's colour at the NORMAL shade; the atlas's natural colours (a biome's grass, a
 * warm ocean) may take any of a colour's three middle shades, and the relief moves it one shade up or down from there.
 */
public final class MapColumn {

    private MapColumn() {
    }

    /** A column with the vanilla colour {@code colorId} on flat ground (shade NORMAL). */
    public static int pack(int colorId, int depth, int height) {
        if (colorId <= 0 || colorId >= MapColors.IDS) throw new IllegalArgumentException("colour id " + colorId);
        return packPixel(MapColors.pixel(colorId, MapColors.SHADE_NORMAL), depth, height);
    }

    /** A column that shows the map byte {@code pixel} on flat ground. */
    public static int packPixel(byte pixel, int depth, int height) {
        int p = pixel & 0xFF;
        if ((p >> 2) == 0 || (p >> 2) >= MapColors.IDS) throw new IllegalArgumentException("map byte " + p);
        return (p << 24) | (Math.max(0, Math.min(255, depth)) << 16) | ((height + 64) & 0xFFFF);
    }

    /** The map byte on flat ground. */
    public static byte pixel(int column) {
        return (byte) (column >>> 24);
    }

    /** The colour id. */
    public static int id(int column) {
        return column >>> 26;
    }

    /** The shade on flat ground. */
    public static int shade(int column) {
        return (column >>> 24) & 3;
    }

    /** How deep the water is (0: land). */
    public static int depth(int column) {
        return (column >>> 16) & 0xFF;
    }

    public static int height(int column) {
        return (column & 0xFFFF) - 64;
    }

    public static boolean known(int column) {
        return column != 0;
    }
}
