package mn.suld.api.map;

/**
 * The relief a vanilla map draws (MapItem.update): a land column is brighter when it is higher than the column one pixel
 * north of it and darker when lower, with a checkerboard dither so gentle slopes read; water is darker the deeper it is.
 * The same rule at every zoom, with the height difference scaled the way the vanilla map scales it.
 * <p>
 * The rule is a step of one shade up or down from the column's flat-ground shade: from NORMAL that is exactly the vanilla
 * map's LOW / NORMAL / HIGH; a natural colour that sits at another shade steps the same way, within the four shades
 * (LOWEST &lt; LOW &lt; NORMAL &lt; HIGH).
 */
public final class ColumnShading {

    /** The four shades from darkest to brightest. */
    private static final int[] BY_LEVEL = {MapColors.SHADE_LOWEST, MapColors.SHADE_LOW, MapColors.SHADE_NORMAL, MapColors.SHADE_HIGH};

    private ColumnShading() {
    }

    /** The relief step of a land column at {@code height}, the column {@code scale} blocks north at {@code northHeight}. */
    public static int landStep(int height, int northHeight, int scale, int px, int pz) {
        double d = (height - northHeight) * 4.0 / (scale + 4) + ((((px + pz) & 1)) - 0.5) * 0.4;
        return d > 0.6 ? 1 : d < -0.6 ? -1 : 0;
    }

    /** The step of water {@code depth} blocks deep: shallow water is bright, deep water dark. */
    public static int waterStep(int depth, int px, int pz) {
        double f = depth * 0.1 + ((px + pz) & 1) * 0.2;
        return f < 0.5 ? 1 : f > 0.9 ? -1 : 0;
    }

    /** Shade {@code shade} moved {@code step} levels brighter (negative: darker), kept within the four shades. */
    public static int relief(int shade, int step) {
        int level = (shade + 1) & 3; // LOW 0 → 1, NORMAL 1 → 2, HIGH 2 → 3, LOWEST 3 → 0
        return BY_LEVEL[Math.max(0, Math.min(3, level + step))];
    }

    /** The vanilla shade of a land column (relief from NORMAL). */
    public static int land(int height, int northHeight, int scale, int px, int pz) {
        return relief(MapColors.SHADE_NORMAL, landStep(height, northHeight, scale, px, pz));
    }

    /** The vanilla shade of water {@code depth} blocks deep. */
    public static int water(int depth, int px, int pz) {
        return relief(MapColors.SHADE_NORMAL, waterStep(depth, px, pz));
    }
}
