package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The corner minimap's picture: the shader's mark, the real terrain, the arrow, places, the waypoint, the text. */
class MinimapTest {

    private static final byte GRASS = MapColors.pixel(MapColors.GRASS, MapColors.SHADE_NORMAL);
    // land generated only west of x = 0
    private static final Minimap.Terrain WEST = (x, z) -> x < 0 ? GRASS : 0;

    private static int count(byte[] img, int x0, int y0, int x1, int y1, byte colour) {
        int n = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if (img[y * 128 + x] == colour) n++;
        return n;
    }

    @Test
    void rowZeroCarriesTheMarkAndNothingElseIsUnknown() {
        byte[] img = Minimap.draw(WEST, 0, 0, 0, List.of(), null, "x -30 z -79", "Хархорум");
        assertEquals(Minimap.SIZE * Minimap.SIZE, img.length);
        byte[] head = java.util.Arrays.copyOf(img, Minimap.MARK.length);
        assertArrayEquals(Minimap.MARK, head, "the shader finds the minimap by these four colours");
        for (byte b : img) assertNotEquals(0, b, "every pixel is painted: no hole in the minimap");
        // west half real grass, east half parchment (never generated)
        int mid = Minimap.VIEW_Y + Minimap.VIEW_H / 2;
        assertEquals(GRASS, img[(mid - 20) * 128 + Minimap.VIEW_X + 5]);
        assertEquals(Minimap.PARCHMENT, img[(mid - 20) * 128 + Minimap.VIEW_X + Minimap.VIEW_W - 5]);
        // the marker colours never occur in row 0 beyond the mark
        for (int x = Minimap.MARK.length; x < 128; x++) for (byte m : Minimap.MARK) assertNotEquals(m, img[x]);
    }

    @Test
    void theArrowPointsWhereThePlayerLooks() {
        int cx = Minimap.VIEW_X + Minimap.VIEW_W / 2, cy = Minimap.VIEW_Y + Minimap.VIEW_H / 2;
        byte[] north = Minimap.draw(WEST, 0, 0, 180, List.of(), null, "", "");
        byte[] south = Minimap.draw(WEST, 0, 0, 0, List.of(), null, "", "");
        assertEquals(Minimap.WHITE, north[(cy - 3) * 128 + cx], "looking north: the arrow's tip is up");
        assertNotEquals(Minimap.WHITE, north[(cy + 4) * 128 + cx]);
        assertEquals(Minimap.WHITE, south[(cy + 3) * 128 + cx], "looking south: the tip is down");
    }

    @Test
    void placesTheWaypointAndTheText() {
        byte[] img = Minimap.draw(WEST, 0, 0, 0, List.of(new Minimap.Marker(-20, -20, Minimap.Kind.DUNGEON)), new int[]{5000, 0}, "x 0 z 0", "Туулын Хөндий");
        int cx = Minimap.VIEW_X + Minimap.VIEW_W / 2, cy = Minimap.VIEW_Y + Minimap.VIEW_H / 2;
        assertEquals(Minimap.RED, img[(cy - 20) * 128 + cx - 20], "the dungeon 20 blocks north-west");
        // the waypoint 5 000 blocks east: a gold arrow on the east rim
        int gold = count(img, Minimap.VIEW_X + Minimap.VIEW_W - 12, Minimap.VIEW_Y, Minimap.VIEW_X + Minimap.VIEW_W, Minimap.VIEW_Y + Minimap.VIEW_H, Minimap.GOLD);
        assertTrue(gold >= 4, "rim arrow pixels: " + gold);
        // two lines of text under the map
        int white = count(img, 4, Minimap.PANEL_Y, 124, Minimap.PANEL_Y + 9, Minimap.WHITE);
        int letters = count(img, 4, Minimap.PANEL_Y + 9, 124, 124, Minimap.GOLD);
        assertTrue(white > 20, "coordinates drawn: " + white);
        assertTrue(letters > 20, "area name drawn: " + letters);
        // a waypoint in view is a gold diamond
        byte[] near = Minimap.draw(WEST, 0, 0, 0, List.of(), new int[]{10, 10}, "", "");
        assertEquals(Minimap.GOLD, near[(cy + 10) * 128 + cx + 10]);
    }

    @Test
    void thePixelFontMeasuresAndDrawsMongolian() {
        assertEquals(5, PixelFont.width("А"));
        assertEquals(5 + 1 + 5, PixelFont.width("ӨҮ"));
        assertEquals(PixelFont.width("ХАРХОРУМ"), PixelFont.width("Хархорум"), "lower case is drawn as capitals");
        byte[] img = new byte[128 * 128];
        int end = PixelFont.draw(img, 2, 2, "Х 1", (byte) 5);
        assertTrue(end > 2 + 5);
        assertTrue(count(img, 0, 0, 128, 12, (byte) 5) > 10);
    }
}
