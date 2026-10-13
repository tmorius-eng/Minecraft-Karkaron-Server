package mn.suld.api.map;

/**
 * The vanilla map palette (docs/WORLD_MAP.md): a map pixel is one byte, {@code id × 4 + shade}, where {@code id} is a
 * Minecraft map colour (0 = none, drawn transparent) and {@code shade} one of four brightnesses. The client owns the
 * real colours; the RGB values here (the 1.21 map colours) are for previews and tests only — the plugin's export reads
 * Bukkit's own palette.
 */
public final class MapColors {

    public static final int NONE = 0, GRASS = 1, SAND = 2, WOOL = 3, FIRE = 4, ICE = 5, METAL = 6, PLANT = 7, SNOW = 8,
            CLAY = 9, DIRT = 10, STONE = 11, WATER = 12, WOOD = 13, QUARTZ = 14, ORANGE = 15, MAGENTA = 16,
            LIGHT_BLUE = 17, YELLOW = 18, LIGHT_GREEN = 19, PINK = 20, GRAY = 21, LIGHT_GRAY = 22, CYAN = 23, PURPLE = 24,
            BLUE = 25, BROWN = 26, GREEN = 27, RED = 28, BLACK = 29, GOLD = 30, DIAMOND = 31, LAPIS = 32, EMERALD = 33,
            PODZOL = 34, NETHER = 35, TERRACOTTA_WHITE = 36, TERRACOTTA_ORANGE = 37, TERRACOTTA_MAGENTA = 38,
            TERRACOTTA_LIGHT_BLUE = 39, TERRACOTTA_YELLOW = 40, TERRACOTTA_LIGHT_GREEN = 41, TERRACOTTA_PINK = 42,
            TERRACOTTA_GRAY = 43, TERRACOTTA_LIGHT_GRAY = 44, TERRACOTTA_CYAN = 45, TERRACOTTA_PURPLE = 46,
            TERRACOTTA_BLUE = 47, TERRACOTTA_BROWN = 48, TERRACOTTA_GREEN = 49, TERRACOTTA_RED = 50, TERRACOTTA_BLACK = 51,
            DEEPSLATE = 59;

    /** The four brightnesses, as the client numbers them. */
    public static final int SHADE_LOW = 0, SHADE_NORMAL = 1, SHADE_HIGH = 2, SHADE_LOWEST = 3;

    private static final int[] SHADE_MUL = {180, 220, 255, 135};

    private static final int[] RGB = {
            0x000000, 0x7FB238, 0xF7E9A3, 0xC7C7C7, 0xFF0000, 0xA0A0FF, 0xA7A7A7, 0x007C00, 0xFFFFFF, 0xA4A8B8,
            0x976D4D, 0x707070, 0x4040FF, 0x8F7748, 0xFFFCF5, 0xD87F33, 0xB24CD8, 0x6699D8, 0xE5E533, 0x7FCC19,
            0xF27FA5, 0x4C4C4C, 0x999999, 0x4C7F99, 0x7F3FB2, 0x334CB2, 0x664C33, 0x667F33, 0x993333, 0x191919,
            0xFAEE4D, 0x5CDBD5, 0x4A80FF, 0x00D93A, 0x815631, 0x700200, 0xD1B1A1, 0x9F5224, 0x95576C, 0x706C8A,
            0xBA8524, 0x677535, 0xA04D4E, 0x392923, 0x876B62, 0x575C5C, 0x7A4958, 0x4C3E5C, 0x4C3223, 0x4C522A,
            0x8E3C2E, 0x251610, 0xBD3031, 0x943F61, 0x5C191D, 0x167E86, 0x3A8E8C, 0x562C3E, 0x14B485, 0x646464,
            0xD8AF93, 0x7FA796};

    /** How many map colour ids exist (1.21). */
    public static final int IDS = RGB.length;

    private MapColors() {
    }

    /** The byte a map canvas takes for colour {@code id} at {@code shade}. */
    public static byte pixel(int id, int shade) {
        if (id <= 0 || id >= IDS) return 0;
        return (byte) (id * 4 + (shade & 3));
    }

    public static int id(byte pixel) {
        return (pixel & 0xFF) >> 2;
    }

    public static int shade(byte pixel) {
        return pixel & 3;
    }

    /** Preview colour of a pixel as 0xAARRGGBB; id 0 is fully transparent. */
    public static int argb(byte pixel) {
        int id = id(pixel);
        if (id == 0 || id >= IDS) return 0;
        int c = RGB[id], m = SHADE_MUL[shade(pixel)];
        int r = ((c >> 16) & 0xFF) * m / 255, g = ((c >> 8) & 0xFF) * m / 255, b = (c & 0xFF) * m / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The base colour (shade HIGH) of an id as 0xRRGGBB, or -1 for an unknown id. */
    public static int baseRgb(int id) {
        return id > 0 && id < IDS ? RGB[id] : -1;
    }

    /** CIE L*a*b* of every colour at the three shades a flat column may take (LOW, NORMAL, HIGH), by map byte. */
    private static final class Lab {
        static final double[][] OF = new double[IDS * 4][];

        static {
            for (int id = 1; id < IDS; id++) {
                for (int shade : new int[]{SHADE_LOW, SHADE_NORMAL, SHADE_HIGH}) OF[id * 4 + shade] = lab(argb(pixel(id, shade)));
            }
        }
    }

    /**
     * The map byte nearest to {@code rgb} (0xRRGGBB) as the eye sees it (CIE76 distance in L*a*b*), among every colour at
     * shade LOW, NORMAL or HIGH, so the relief can still step one shade either way.
     */
    public static byte nearest(int rgb) {
        double[] want = lab(rgb);
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int p = 4; p < Lab.OF.length; p++) {
            double[] c = Lab.OF[p];
            if (c == null) continue;
            double d = (c[0] - want[0]) * (c[0] - want[0]) + (c[1] - want[1]) * (c[1] - want[1]) + (c[2] - want[2]) * (c[2] - want[2]);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return (byte) best;
    }

    /** sRGB (D65) to CIE L*a*b*. */
    static double[] lab(int rgb) {
        double r = linear((rgb >> 16) & 0xFF), g = linear((rgb >> 8) & 0xFF), b = linear(rgb & 0xFF);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(int c) {
        double v = c / 255.0;
        return v > 0.04045 ? Math.pow((v + 0.055) / 1.055, 2.4) : v / 12.92;
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }
}
