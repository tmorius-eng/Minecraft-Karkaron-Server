package mn.suld.api.payment.qr;

/**
 * A QR code drawn on a square canvas of whole pixels, the way a map shows it (128 × 128): each module the same whole
 * number of pixels, centred, with the light quiet zone around it. Pure; the map renderer copies {@link #dark}.
 */
public final class QrRaster {

    /** Pixels on a side of a Minecraft map. */
    public static final int MAP = 128;

    /** The quiet zone a scanner wants, in modules (the standard asks 4; a light map border adds to it). */
    public static final int QUIET = 2;

    private final QrCode code;
    private final int canvas;
    private final int scale;
    private final int offset;

    private QrRaster(QrCode code, int canvas, int scale) {
        this.code = code;
        this.canvas = canvas;
        this.scale = scale;
        this.offset = (canvas - code.size() * scale) / 2;
    }

    /**
     * {@code code} at the largest whole scale that keeps {@link #QUIET} modules of quiet zone on {@code canvas}; at
     * least one pixel a module (a code too large even for that is refused).
     */
    public static QrRaster fit(QrCode code, int canvas) {
        if (code.size() > canvas) throw new IllegalArgumentException("a " + code.size() + "-module code does not fit " + canvas + " px");
        int scale = Math.max(1, canvas / (code.size() + 2 * QUIET));
        return new QrRaster(code, canvas, scale);
    }

    public int canvas() {
        return canvas;
    }

    /** Pixels per module. */
    public int scale() {
        return scale;
    }

    /** The light margin on each side, in pixels. */
    public int margin() {
        return offset;
    }

    /** True for a dark pixel at ({@code x}, {@code y}); the margin is light. */
    public boolean dark(int x, int y) {
        if (x < offset || y < offset) return false;
        return code.dark((x - offset) / scale, (y - offset) / scale);
    }
}
