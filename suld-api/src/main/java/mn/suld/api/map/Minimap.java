package mn.suld.api.map;

import java.util.List;

/**
 * The corner minimap's picture (docs/WORLD_MAP.md): one 128 × 128 map image, north up, drawn the way the atlas draws
 * the world (real blocks only; land never generated is plain parchment), with a thin Mongolian frame, the player's
 * arrow in the middle, nearby places, the party's mates (light blue arrows the way they look, or on the rim toward a
 * mate out of view), the waypoint (or an arrow on the rim pointing to it) and two lines of text under the map: the
 * coordinates and the area's name.
 * <p>
 * Row 0 starts with {@link #MARK}: the resource pack's text shader looks for these four colours to draw this map in
 * the screen's top-left corner instead of in the world (and hides row 0). Nothing else ever paints row 0.
 */
public final class Minimap {

    public static final int SIZE = 128;
    /** The terrain window: {@code VIEW_W × VIEW_H} pixels, one block each, the player at its middle. */
    public static final int VIEW_X = 4, VIEW_Y = 4, VIEW_W = 120, VIEW_H = 98;
    public static final int PANEL_Y = VIEW_Y + VIEW_H + 1;

    static final byte OUTLINE = MapColors.pixel(29, MapColors.SHADE_NORMAL);      // black
    static final byte GOLD = MapColors.pixel(30, MapColors.SHADE_NORMAL);
    static final byte GOLD_DARK = MapColors.pixel(30, MapColors.SHADE_LOW);
    static final byte BLUE = MapColors.pixel(25, MapColors.SHADE_NORMAL);
    static final byte BLUE_DARK = MapColors.pixel(25, MapColors.SHADE_LOW);
    static final byte WHITE = MapColors.pixel(MapColors.SNOW, MapColors.SHADE_HIGH);
    static final byte PARCHMENT = MapColors.pixel(MapColors.SAND, MapColors.SHADE_NORMAL);
    static final byte RED = MapColors.pixel(MapColors.FIRE, MapColors.SHADE_NORMAL);
    static final byte GREY = MapColors.pixel(MapColors.STONE, MapColors.SHADE_HIGH);
    static final byte BROWN = MapColors.pixel(MapColors.WOOD, MapColors.SHADE_HIGH);
    static final byte GREEN = MapColors.pixel(33, MapColors.SHADE_NORMAL);         // emerald
    /** A party mate's arrow: light blue, apart from the gold waypoint and the white player. */
    static final byte MATE = MapColors.pixel(17, MapColors.SHADE_HIGH);

    /** The four colours that mark the minimap for the shader (magenta, dark diamond, dark gold, darkest red). */
    public static final byte[] MARK = {MapColors.pixel(16, MapColors.SHADE_HIGH), MapColors.pixel(31, MapColors.SHADE_LOWEST),
            MapColors.pixel(30, MapColors.SHADE_LOW), MapColors.pixel(28, MapColors.SHADE_LOWEST)};

    /** What the minimap reads: the map byte the world shows at block (x, z), 0 when unknown. */
    @FunctionalInterface
    public interface Terrain {
        byte at(int x, int z);
    }

    /** A place near the player, or a party mate ({@link #PARTY}). */
    public enum Kind { CITY, DUNGEON, OVOO, SITE, BOSS, RELAY, PIN, DEATH, PARTY }

    /** {@code yaw} is a party mate's look (Minecraft's); a place has none. */
    public record Marker(int x, int z, Kind kind, float yaw) {
        public Marker(int x, int z, Kind kind) {
            this(x, z, kind, 0f);
        }
    }

    private Minimap() {
    }

    /**
     * The picture around block (cx, cz) for a player looking toward {@code yaw} (Minecraft's: 0 = south, 90 = west).
     * {@code target} is the waypoint's {x, z} or null.
     */
    public static byte[] draw(Terrain terrain, int cx, int cz, float yaw, List<Marker> markers, int[] target, String line1, String line2) {
        byte[] img = new byte[SIZE * SIZE];
        java.util.Arrays.fill(img, OUTLINE);
        System.arraycopy(MARK, 0, img, 0, MARK.length);
        // the frame: black, gold, blue, gold around the window and the panel
        rect(img, 1, 1, SIZE - 2, SIZE - 2, GOLD_DARK);
        rect(img, 2, 2, SIZE - 4, SIZE - 4, BLUE);
        rect(img, 3, 3, SIZE - 6, SIZE - 6, GOLD);
        // the terrain
        int x0 = cx - VIEW_W / 2, z0 = cz - VIEW_H / 2;
        for (int vy = 0; vy < VIEW_H; vy++) {
            for (int vx = 0; vx < VIEW_W; vx++) {
                byte b = terrain.at(x0 + vx, z0 + vy);
                img[(VIEW_Y + vy) * SIZE + VIEW_X + vx] = b == 0 ? PARCHMENT : b;
            }
        }
        // north: a gold notch on the top edge
        for (int i = -2; i <= 2; i++) img[2 * SIZE + VIEW_X + VIEW_W / 2 + i] = GOLD;
        img[1 * SIZE + VIEW_X + VIEW_W / 2] = GOLD;
        // places, the party, then the waypoint, then the player on top
        for (Marker m : markers) {
            if (m.kind() == Kind.PARTY) continue;
            int px = VIEW_X + m.x() - x0, py = VIEW_Y + m.z() - z0;
            if (px < VIEW_X + 2 || px >= VIEW_X + VIEW_W - 2 || py < VIEW_Y + 2 || py >= VIEW_Y + VIEW_H - 2) continue;
            marker(img, px, py, m.kind());
        }
        for (Marker m : markers) {
            if (m.kind() != Kind.PARTY) continue;
            int px = VIEW_X + m.x() - x0, py = VIEW_Y + m.z() - z0;
            if (px >= VIEW_X + 4 && px < VIEW_X + VIEW_W - 4 && py >= VIEW_Y + 4 && py < VIEW_Y + VIEW_H - 4) {
                double a = Math.toRadians(m.yaw());
                triangle(img, px + 0.5, py + 0.5, -Math.sin(a), Math.cos(a), 4.0, OUTLINE);
                triangle(img, px + 0.5, py + 0.5, -Math.sin(a), Math.cos(a), 2.8, MATE);
            } else {
                rimArrow(img, m.x() - cx, m.z() - cz, MATE, 3.5, 2.3); // which way to the mate
            }
        }
        if (target != null) {
            int tx = VIEW_X + target[0] - x0, ty = VIEW_Y + target[1] - z0;
            if (tx >= VIEW_X + 3 && tx < VIEW_X + VIEW_W - 3 && ty >= VIEW_Y + 3 && ty < VIEW_Y + VIEW_H - 3) {
                diamond(img, tx, ty, 3, OUTLINE);
                diamond(img, tx, ty, 2, GOLD);
            } else {
                rimArrow(img, target[0] - cx, target[1] - cz, GOLD, 4.5, 3.0);
            }
        }
        arrow(img, VIEW_X + VIEW_W / 2, VIEW_Y + VIEW_H / 2, yaw);
        // the panel under the map
        for (int y = PANEL_Y; y < SIZE - 4; y++) for (int x = 4; x < SIZE - 4; x++) img[y * SIZE + x] = BLUE_DARK;
        for (int x = 4; x < SIZE - 4; x++) img[(PANEL_Y - 1) * SIZE + x] = GOLD;
        centred(img, PANEL_Y + 2, fit(line1), WHITE);
        centred(img, PANEL_Y + 2 + PixelFont.HEIGHT + 2, fit(line2), GOLD);
        return img;
    }

    private static String fit(String s) {
        if (s == null) return "";
        String t = s.toUpperCase(java.util.Locale.ROOT);
        while (!t.isEmpty() && PixelFont.width(t) > SIZE - 12) t = t.substring(0, t.length() - 1);
        return t;
    }

    private static void centred(byte[] img, int y, String text, byte colour) {
        if (text.isEmpty()) return;
        PixelFont.draw(img, (SIZE - PixelFont.width(text)) / 2, y, text, colour);
    }

    private static void rect(byte[] img, int x, int y, int w, int h, byte colour) {
        for (int i = x; i < x + w; i++) {
            img[y * SIZE + i] = colour;
            img[(y + h - 1) * SIZE + i] = colour;
        }
        for (int j = y; j < y + h; j++) {
            img[j * SIZE + x] = colour;
            img[j * SIZE + x + w - 1] = colour;
        }
    }

    private static void set(byte[] img, int x, int y, byte colour) {
        if (x >= VIEW_X && x < VIEW_X + VIEW_W && y >= VIEW_Y && y < VIEW_Y + VIEW_H) img[y * SIZE + x] = colour;
    }

    private static void square(byte[] img, int cx, int cy, int r, byte colour) {
        for (int y = cy - r; y <= cy + r; y++) for (int x = cx - r; x <= cx + r; x++) set(img, x, y, colour);
    }

    private static void diamond(byte[] img, int cx, int cy, int r, byte colour) {
        for (int y = -r; y <= r; y++) for (int x = -r; x <= r; x++) if (Math.abs(x) + Math.abs(y) <= r) set(img, cx + x, cy + y, colour);
    }

    private static void marker(byte[] img, int x, int y, Kind k) {
        switch (k) {
            case CITY -> {
                square(img, x, y, 2, OUTLINE);
                square(img, x, y, 1, WHITE);
            }
            case DUNGEON, BOSS -> {
                square(img, x, y, 2, OUTLINE);
                square(img, x, y, 1, RED);
            }
            case OVOO -> {
                diamond(img, x, y, 2, OUTLINE);
                diamond(img, x, y, 1, GREY);
            }
            case SITE, RELAY -> {
                square(img, x, y, 1, OUTLINE);
                set(img, x, y, BROWN);
            }
            case PIN -> {
                diamond(img, x, y, 2, OUTLINE);
                diamond(img, x, y, 1, GREEN);
            }
            case DEATH -> {
                for (int i = -2; i <= 2; i++) {
                    set(img, x + i, y + i, OUTLINE);
                    set(img, x + i, y - i, OUTLINE);
                }
                for (int i = -1; i <= 1; i++) {
                    set(img, x + i, y + i, WHITE);
                    set(img, x + i, y - i, WHITE);
                }
            }
        }
    }

    /** An arrow on the window's rim, pointing toward something (dx, dz) blocks away: the waypoint, a party mate. */
    private static void rimArrow(byte[] img, int dx, int dz, byte colour, double outer, double inner) {
        double len = Math.hypot(dx, dz);
        if (len < 1) return;
        double ux = dx / len, uz = dz / len;
        double halfW = VIEW_W / 2.0 - 4, halfH = VIEW_H / 2.0 - 4;
        double t = Math.min(halfW / Math.max(1e-9, Math.abs(ux)), halfH / Math.max(1e-9, Math.abs(uz)));
        double px = VIEW_X + VIEW_W / 2.0 + ux * t, py = VIEW_Y + VIEW_H / 2.0 + uz * t;
        triangle(img, px, py, ux, uz, outer, OUTLINE);
        triangle(img, px, py, ux, uz, inner, colour);
    }

    /** The player's arrow: Minecraft yaw 0 looks south (+z, down on the map), 90 west (left). */
    private static void arrow(byte[] img, int cx, int cy, float yaw) {
        double a = Math.toRadians(yaw);
        double fx = -Math.sin(a), fz = Math.cos(a);
        // centred on the middle of pixel (cx, cy), so it looks the same whichever way it points
        triangle(img, cx + 0.5, cy + 0.5, fx, fz, 5.5, OUTLINE);
        triangle(img, cx + 0.5, cy + 0.5, fx, fz, 4.0, WHITE);
    }

    /** A filled arrowhead centred at (cx, cy) pointing along (fx, fz), {@code size} pixels from the centre to the tip. */
    private static void triangle(byte[] img, double cx, double cy, double fx, double fz, double size, byte colour) {
        double sx = -fz, sz = fx;
        double ax = cx + fx * size, ay = cy + fz * size;
        double bx = cx - fx * size * 0.7 + sx * size * 0.8, by = cy - fz * size * 0.7 + sz * size * 0.8;
        double qx = cx - fx * size * 0.7 - sx * size * 0.8, qy = cy - fz * size * 0.7 - sz * size * 0.8;
        int r = (int) Math.ceil(size) + 1;
        for (int y = (int) cy - r; y <= (int) cy + r; y++) {
            for (int x = (int) cx - r; x <= (int) cx + r; x++) {
                double px = x + 0.5, py = y + 0.5;
                if (inside(px, py, ax, ay, bx, by, qx, qy)) set(img, x, y, colour);
            }
        }
    }

    private static boolean inside(double px, double py, double ax, double ay, double bx, double by, double cx, double cy) {
        double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
        double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
        double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }
}
