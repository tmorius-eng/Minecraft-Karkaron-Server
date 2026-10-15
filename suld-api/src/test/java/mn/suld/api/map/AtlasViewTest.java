package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The atlas wall: panel ↔ world, pan, zoom toward the cursor, staying over the world, frames built from tiles. */
class AtlasViewTest {

    private static final MapGeometry WORLD = MapGeometry.ofBorder(-15.5, -45.5, 10_000);

    @Test
    void panelAndWorldMeetBothWays() {
        AtlasView v = new AtlasView(5, 5, 0, 0, 1); // 256 blocks per frame
        assertEquals(256, v.blocksPerFrame());
        assertEquals(-640, v.left());
        assertEquals(-640, v.top());
        assertEquals(0, v.worldX(0), 1e-9);
        assertEquals(0, v.worldZ(0), 1e-9);
        assertEquals(-640, v.worldX(-2.5), 1e-9);
        assertEquals(-640, v.worldZ(2.5), 1e-9, "the wall's top edge is north");
        assertEquals(1.0, v.u(256), 1e-9);
        assertEquals(-1.0, v.v(256), 1e-9, "south is down");
        assertTrue(v.onWall(2.5, -2.5));
        assertFalse(v.onWall(2.6, 0));
        assertEquals(-640 + 3 * 256, v.frameX(3));
        assertThrows(IllegalArgumentException.class, () -> new AtlasView(5, 5, 0, 0, 5));
    }

    @Test
    void zoomKeepsThePointUnderTheCursorAndPanMovesByFrames() {
        AtlasView v = new AtlasView(5, 5, 100, 200, 2);
        double wx = v.worldX(1.2), wz = v.worldZ(-0.7);
        AtlasView in = v.zoomAt(1, 1.2, -0.7, WORLD);
        assertEquals(1, in.zoom());
        assertEquals(wx, in.worldX(1.2), in.scale(), "the same block under the cursor, within a pixel");
        assertEquals(wz, in.worldZ(-0.7), in.scale());
        assertSame(v, v.zoomAt(2, 0, 0, WORLD), "same zoom: unchanged");
        assertSame(v, v.zoomAt(5, 0, 0, WORLD), "beyond the farthest zoom: unchanged");
        AtlasView east = v.pan(1, 0, WORLD);
        assertEquals(v.centreX() + 512, east.centreX(), 1e-9);
        AtlasView north = v.pan(0, 1, WORLD);
        assertEquals(v.centreZ() - 512, north.centreZ(), 1e-9);
    }

    @Test
    void theViewStaysOverTheWorld() {
        AtlasView v = new AtlasView(5, 5, 4900, -5000, 2).clamp(WORLD); // 2560 blocks across
        assertTrue(v.left() >= WORLD.minX() - v.scale());
        assertTrue(v.frameX(5) <= WORLD.maxX() + v.scale(), "the east edge stays inside: " + v.frameX(5));
        assertTrue(v.top() >= WORLD.minZ() - v.scale());
        // the whole world fits a 5 x 5 wall at zoom 4 (2048 blocks a frame): centred on the square
        int z = AtlasView.wholeWorldZoom(5, 5, WORLD);
        assertEquals(4, z);
        AtlasView whole = new AtlasView(5, 5, 123, 456, z).clamp(WORLD);
        assertEquals(WORLD.centreX(), whole.centreX(), 1e-9);
        assertEquals(WORLD.centreZ(), whole.centreZ(), 1e-9);
        assertTrue(whole.left() <= WORLD.minX() && whole.frameX(5) >= WORLD.maxX() - whole.scale());
    }

    @Test
    void closeUpZoomsMagnifyTheOneBlockTiles() {
        assertEquals(64, AtlasView.blocksPerFrame(-1));
        assertEquals(32, AtlasView.blocksPerFrame(-2));
        assertThrows(IllegalArgumentException.class, () -> new AtlasView(5, 5, 0, 0, AtlasView.MIN_ZOOM - 1));
        // the wheel steps from the old floor (zoom 0) on in: 1:1 → 2:1 → 4:1, then stops
        AtlasView v = new AtlasView(5, 5, 100, 200, 0);
        double wx = v.worldX(0.8), wz = v.worldZ(-1.1);
        AtlasView in = v.zoomAt(-1, 0.8, -1.1, WORLD);
        assertEquals(-1, in.zoom(), "zoom 0 is no longer the closest");
        assertEquals(2, in.magnify());
        assertEquals(0, in.tileZoom(), "magnified views are cut from the 1-block tiles");
        assertEquals(wx, in.worldX(0.8), 1.0, "the same block under the cursor");
        assertEquals(wz, in.worldZ(-1.1), 1.0);
        AtlasView closest = in.zoomAt(-2, 0, 0, WORLD);
        assertEquals(-2, closest.zoom());
        assertSame(closest, closest.zoomAt(-3, 0, 0, WORLD), "beyond the closest zoom: unchanged");
        assertEquals(160, closest.cols() * closest.blocksPerFrame(), "160 blocks across the wall close up");
        // W / A / S / D still move by a quarter of the wall in blocks of the zoom
        AtlasView east = closest.pan(1.25, 0, WORLD);
        assertEquals(closest.centreX() + 40, east.centreX(), 1e-9);
        // back out again: the cursor's block holds, and the zoom-in / zoom-out pair is symmetric
        AtlasView out = closest.zoomAt(-1, 0, 0, WORLD).zoomAt(0, 0, 0, WORLD);
        assertEquals(0, out.zoom());
        assertEquals(closest.worldX(0), out.worldX(0), 1.0);
    }

    @Test
    void aMagnifiedFrameDrawsEachBlockAsASquareOfPixels() {
        AtlasView v = new AtlasView(1, 1, 16 + 120, 16 + 8, -2); // 32 blocks a frame, starting at block (120, 8)
        assertEquals(120, v.left());
        assertEquals(8, v.top());
        assertArrayEquals(new int[]{0, 0, 1, 0}, v.tilesOf(0, 0), "blocks 120..151 span tiles 0 and 1 of zoom 0");
        byte[] t0 = new byte[TileComposer.PIXELS], t1 = new byte[TileComposer.PIXELS];
        for (int i = 0; i < t0.length; i++) t0[i] = (byte) (4 + (i & 127) % 8);
        java.util.Arrays.fill(t1, (byte) 60);
        byte[] frame = new byte[TileComposer.PIXELS];
        v.copy(t0, 0, 0, 0, 0, frame);
        v.copy(t1, 1, 0, 0, 0, frame);
        byte b120 = t0[8 * 128 + 120], b121 = t0[8 * 128 + 121];
        for (int d = 0; d < 4; d++) {
            assertEquals(b120, frame[d * 128], "block (120, 8) fills pixels 0..3 of rows 0..3");
            assertEquals(b120, frame[d * 128 + 3]);
            assertEquals(b121, frame[d * 128 + 4], "the next block starts at pixel 4");
        }
        assertEquals(60, frame[8 * 4], "block 128 (pixel 32) comes from tile 1");
        assertEquals(60, frame[TileComposer.PIXELS - 1]);
    }

    @Test
    void aFrameIsCopiedFromTheTilesUnderIt() {
        AtlasView v = new AtlasView(1, 1, 64 + 10, 64 + 20, 0); // a frame starting at block (10, 20): four tiles
        assertEquals(10, v.left());
        assertEquals(20, v.top());
        assertArrayEquals(new int[]{0, 0, 1, 1}, v.tilesOf(0, 0));
        byte[] frame = new byte[TileComposer.PIXELS];
        for (int tz = 0; tz <= 1; tz++) {
            for (int tx = 0; tx <= 1; tx++) {
                byte[] tile = new byte[TileComposer.PIXELS];
                java.util.Arrays.fill(tile, (byte) (4 + tx + 2 * tz)); // one colour per tile
                v.copy(tile, tx, tz, 0, 0, frame);
            }
        }
        assertEquals(4, frame[0], "top-left from tile 0,0");
        assertEquals(5, frame[127 - 10 + 1], "x 118 is block 128: tile 1,0");
        assertEquals(6, frame[(127 - 20 + 1) * 128], "z 108 is block 128: tile 0,1");
        assertEquals(7, frame[TileComposer.PIXELS - 1]);
        assertEquals(4, frame[117 + 107 * 128], "block 127,127 is still tile 0,0");
    }
}
