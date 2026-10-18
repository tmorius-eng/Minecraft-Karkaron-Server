package mn.suld.api.hologram;

/**
 * How a hologram (a text display) looks, from content/holograms.json: the plugin applies it to the entity.
 *
 * @param billboard  how it turns to the viewer
 * @param scale      the text's size (1 = vanilla: a line is a quarter block)
 * @param background ARGB behind the text (0: none)
 * @param lineWidth  pixels before a line wraps
 * @param viewRange  vanilla's view range factor (1 = 64 blocks)
 */
public record HologramStyle(Billboard billboard, float scale, int background, int lineWidth, Align align, boolean shadow, float viewRange) {

    public enum Billboard { CENTER, VERTICAL, HORIZONTAL, FIXED }

    public enum Align { CENTER, LEFT, RIGHT }

    /** Vanilla's text display, as the plugin spawned it before a style was given. */
    public static final HologramStyle DEFAULT = new HologramStyle(Billboard.CENTER, 1f, 0x40000000, 200, Align.CENTER, true, 1f);
}
