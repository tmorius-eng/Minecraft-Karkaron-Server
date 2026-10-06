package mn.suld.api.worldbuild;

/**
 * Packs a block position into one {@code long} (x and z: 21 bits each, y: 12 bits), so large
 * compiled cities can be held in a plain hash map. Range: x, z in [-1048576, 1048575], y in [-2048, 2047].
 */
public final class BlockPos {

    private static final int XZ_OFF = 1 << 20;
    private static final int Y_OFF = 1 << 11;

    private BlockPos() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x + XZ_OFF) << 33) | ((long) (z + XZ_OFF) << 12) | (y + Y_OFF);
    }

    public static int x(long p) {
        return (int) (p >>> 33) - XZ_OFF;
    }

    public static int z(long p) {
        return (int) ((p >>> 12) & 0x1FFFFF) - XZ_OFF;
    }

    public static int y(long p) {
        return (int) (p & 0xFFF) - Y_OFF;
    }
}
