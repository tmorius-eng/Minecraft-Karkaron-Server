package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The world map's terrain: palette, geometry, the vanilla relief shade and tile composition from real columns only. */
class MapTerrainTest {

    @Test
    void paletteBytesRoundTripAndPreviewTheVanillaColours() {
        byte p = MapColors.pixel(MapColors.GRASS, MapColors.SHADE_HIGH);
        assertEquals(MapColors.GRASS, MapColors.id(p));
        assertEquals(MapColors.SHADE_HIGH, MapColors.shade(p));
        assertEquals(0xFF7FB238, MapColors.argb(p), "grass at full brightness is the map's grass green");
        assertEquals(0, MapColors.pixel(MapColors.NONE, MapColors.SHADE_HIGH));
        assertEquals(0, MapColors.argb((byte) 0), "colour 0 is transparent");
        assertEquals(62, MapColors.IDS);
        // a darker shade is darker in every channel
        int hi = MapColors.argb(MapColors.pixel(MapColors.SAND, MapColors.SHADE_HIGH));
        int lo = MapColors.argb(MapColors.pixel(MapColors.SAND, MapColors.SHADE_LOW));
        assertTrue((hi & 0xFF) > (lo & 0xFF) && ((hi >> 8) & 0xFF) > ((lo >> 8) & 0xFF));
    }

    @Test
    void tilesAreAlignedToTheWorldAndTheBorderIsTheSquare() {
        assertEquals(128, MapGeometry.span(0));
        assertEquals(2048, MapGeometry.span(4));
        assertEquals(16, MapGeometry.scale(4));
        assertEquals(-1, MapGeometry.tileOf(-1, 0));
        assertEquals(0, MapGeometry.tileOf(127, 0));
        assertEquals(-20, MapGeometry.tileOf(-5000, 1), "−5000 ÷ 256 = −19.5, rounded down");
        assertEquals(-256, MapGeometry.tileStart(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> MapGeometry.span(5));

        MapGeometry g = MapGeometry.ofBorder(-15.5, -45.5, 10_000);
        assertEquals(-5016, g.minX());
        assertEquals(4985, g.maxX());
        assertEquals(10_001, g.width(), "the floor/ceil of a half-block centre adds one block");
        assertTrue(g.inside(-5016, -5046));
        assertFalse(g.inside(4985, 0));
        // the whole world fits in 5 tiles a side at the farthest zoom
        int[] xs = g.tilesX(4);
        assertTrue(xs[1] - xs[0] + 1 <= 6, xs[0] + ".." + xs[1]);
    }

    @Test
    void reliefIsTheVanillaMapRule() {
        // flat ground alternates normal with the dither only when the slope is gentle
        assertEquals(MapColors.SHADE_NORMAL, ColumnShading.land(70, 70, 1, 0, 0));
        assertEquals(MapColors.SHADE_HIGH, ColumnShading.land(72, 70, 1, 0, 0), "higher than the column north: bright");
        assertEquals(MapColors.SHADE_LOW, ColumnShading.land(68, 70, 1, 0, 0), "lower: dark");
        // at a far zoom the same step is gentler (×4 / (scale + 4))
        assertEquals(MapColors.SHADE_NORMAL, ColumnShading.land(71, 70, 16, 1, 0));
        // water: shallow bright, deep dark
        assertEquals(MapColors.SHADE_HIGH, ColumnShading.water(1, 0, 0));
        assertEquals(MapColors.SHADE_LOW, ColumnShading.water(20, 0, 0));
    }

    @Test
    void naturalColoursPickTheNearestMapByteAndKeepRoomForTheRelief() {
        // an exact palette colour is found again
        byte grass = MapColors.pixel(MapColors.GRASS, MapColors.SHADE_NORMAL);
        assertEquals(grass, MapColors.nearest(MapColors.argb(grass) & 0xFFFFFF));
        byte lapisHigh = MapColors.pixel(32, MapColors.SHADE_HIGH); // LAPIS 0x4A80FF
        assertEquals(lapisHigh, MapColors.nearest(0x4A80FF));
        // never the darkest shade: the relief must be able to step down
        for (int rgb : new int[]{0x000000, 0x101010, 0x3F76E4, 0x91BD59, 0x43D5EE, 0xFFFFFF}) {
            assertNotEquals(MapColors.SHADE_LOWEST, MapColors.shade(MapColors.nearest(rgb)), Integer.toHexString(rgb));
            assertNotEquals(0, MapColors.id(MapColors.nearest(rgb)));
        }
        // the relief steps one shade from the flat-ground shade, within the four shades
        assertEquals(MapColors.SHADE_HIGH, ColumnShading.relief(MapColors.SHADE_NORMAL, 1));
        assertEquals(MapColors.SHADE_LOW, ColumnShading.relief(MapColors.SHADE_NORMAL, -1));
        assertEquals(MapColors.SHADE_HIGH, ColumnShading.relief(MapColors.SHADE_HIGH, 1));
        assertEquals(MapColors.SHADE_NORMAL, ColumnShading.relief(MapColors.SHADE_HIGH, -1));
        assertEquals(MapColors.SHADE_LOWEST, ColumnShading.relief(MapColors.SHADE_LOW, -1));
        assertEquals(MapColors.SHADE_LOWEST, ColumnShading.relief(MapColors.SHADE_LOWEST, -1));
        // a natural water column (any colour) shades by depth: deep is one shade darker than its flat shade
        int deep = MapColumn.packPixel(lapisHigh, 20, 62);
        TileComposer.Source src = (x, z) -> deep;
        assertEquals(MapColors.pixel(32, MapColors.SHADE_NORMAL), TileComposer.pixel(src, 0, 0, 1));
        int shallow = MapColumn.packPixel(lapisHigh, 1, 62);
        assertEquals(lapisHigh, TileComposer.pixel((x, z) -> shallow, 0, 0, 1));
    }

    @Test
    void columnsPackAndUnpack() {
        int c = MapColumn.pack(MapColors.WATER, 7, 62);
        assertEquals(MapColors.WATER, MapColumn.id(c));
        assertEquals(7, MapColumn.depth(c));
        assertEquals(62, MapColumn.height(c));
        assertTrue(MapColumn.known(c));
        assertEquals(-64, MapColumn.height(MapColumn.pack(MapColors.STONE, 0, -64)));
        assertThrows(IllegalArgumentException.class, () -> MapColumn.pack(0, 0, 70));
        // a vanilla column sits at the NORMAL shade; any map byte with a colour can be the flat shade
        assertEquals(MapColors.SHADE_NORMAL, MapColumn.shade(c));
        int n = MapColumn.packPixel(MapColors.pixel(MapColors.PLANT, MapColors.SHADE_LOW), 0, 90);
        assertEquals(MapColors.PLANT, MapColumn.id(n));
        assertEquals(MapColors.SHADE_LOW, MapColumn.shade(n));
        assertEquals(0, MapColumn.depth(n));
        assertEquals(90, MapColumn.height(n));
        assertThrows(IllegalArgumentException.class, () -> MapColumn.packPixel((byte) 3, 0, 70), "colour 0 is no colour");
    }

    @Test
    void aTileDrawsOnlyRealColumnsAndLeavesTheRestTransparent() {
        MapGeometry g = new MapGeometry(0, 0, 100, 100);
        // only the north half (z < 50) has been generated and scanned: grass at height 70, a lake at x 10..19
        TileComposer.Source src = (x, z) -> {
            if (x < 0 || z < 0 || z >= 50) return 0;
            if (x >= 10 && x < 20) return MapColumn.pack(MapColors.WATER, 20, 62);
            return MapColumn.pack(MapColors.GRASS, 0, z == 30 ? 75 : 70);
        };
        byte[] t = TileComposer.compose(g, src, 0, 0, 0);
        assertEquals(TileComposer.PIXELS, t.length);
        assertEquals(MapColors.GRASS, MapColors.id(t[5 * 128 + 5]));
        assertEquals(MapColors.WATER, MapColors.id(t[5 * 128 + 15]));
        assertEquals(MapColors.SHADE_LOW, MapColors.shade(t[5 * 128 + 15]), "deep water is dark");
        assertEquals(MapColors.SHADE_HIGH, MapColors.shade(t[30 * 128 + 5]), "the ridge at z 30 is lit from the north");
        assertEquals(0, t[60 * 128 + 5], "land never generated: nothing is drawn (no guessed terrain)");
        assertEquals(0, t[5 * 128 + 110], "outside the square: transparent");
        assertEquals(0, t[110 * 128 + 5]);
        // a farther zoom samples every 2nd column
        byte[] z1 = TileComposer.compose(g, src, 1, 0, 0);
        assertEquals(t[4 * 128 + 4], z1[2 * 128 + 2] == 0 ? 0 : t[4 * 128 + 4]);
        assertNotEquals(0, z1[10 * 128 + 10]);
        assertEquals(0, z1[30 * 128 + 5], "z 60 is beyond the scanned half at zoom 1 too");
        // deterministic
        assertArrayEquals(t, TileComposer.compose(g, src, 0, 0, 0));
    }
}
