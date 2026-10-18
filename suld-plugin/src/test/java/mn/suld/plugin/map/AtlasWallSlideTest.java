package mn.suld.plugin.map;

import mn.suld.api.map.AtlasView;
import mn.suld.api.map.MapGeometry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A W / A / S / D pan moves the atlas by whole frames, and the wall's map views slide with the picture (forensic audit
 * 2026-10-18, P1): the frames still in view keep the pictures their views already hold, so a pan sends one new column
 * or row instead of all 25 maps (each about 16 KB).
 */
class AtlasWallSlideTest {

    private static final MapGeometry WORLD = MapGeometry.ofBorder(0, 0, 10_000);
    private static final int N = AtlasSky.COLS * AtlasSky.ROWS;

    @Test
    void aWasdStepIsOneWholeFrameAndReadsAsASlide() {
        AtlasView v = new AtlasView(AtlasSky.COLS, AtlasSky.ROWS, 0, 0, 1);
        assertEquals(1, AtlasSky.PAN_FRAMES, "a whole frame: a part of one would change every picture on the wall");
        assertArrayEquals(new int[]{1, 0}, AtlasSky.frameShift(v, v.pan(AtlasSky.PAN_FRAMES, 0, WORLD)), "east");
        assertArrayEquals(new int[]{-1, 0}, AtlasSky.frameShift(v, v.pan(-AtlasSky.PAN_FRAMES, 0, WORLD)), "west");
        assertArrayEquals(new int[]{0, -1}, AtlasSky.frameShift(v, v.pan(0, AtlasSky.PAN_FRAMES, WORLD)), "north: the rows count down");
        assertArrayEquals(new int[]{1, 1}, AtlasSky.frameShift(v, v.pan(1, -1, WORLD)), "south-east");
    }

    @Test
    void anythingButAWholeFrameSlideRedrawsEveryFrame() {
        AtlasView v = new AtlasView(AtlasSky.COLS, AtlasSky.ROWS, 0, 0, 1);
        assertNull(AtlasSky.frameShift(v, v.pan(0.25, 0, WORLD)), "a part of a frame");
        assertNull(AtlasSky.frameShift(v, v.zoomAt(2, 0, 0, WORLD)), "a zoom");
        assertNull(AtlasSky.frameShift(v, v.pan(AtlasSky.COLS, 0, WORLD)), "a jump past the whole wall");
        AtlasView edge = new AtlasView(AtlasSky.COLS, AtlasSky.ROWS, 0, 0, 1).centreOn(4_999, 0, WORLD);
        AtlasView pushed = edge.pan(1, 0, WORLD);
        assertEquals(edge, pushed, "at the world's edge the pan is cut to nothing");
    }

    @Test
    void theViewsSlideWithThePicture() {
        int[] wall = AtlasSky.identityWall();
        int cols = AtlasSky.COLS, rows = AtlasSky.ROWS;
        int[] east = AtlasSky.slid(wall, cols, rows, 1, 0);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols - 1; c++) {
                assertEquals(wall[r * cols + c + 1], east[r * cols + c], "position (" + c + ", " + r + ") shows the picture that was to its right");
            }
        }
        int[] north = AtlasSky.slid(wall, cols, rows, 0, -1);
        for (int r = 1; r < rows; r++) {
            for (int c = 0; c < cols; c++) assertEquals(wall[(r - 1) * cols + c], north[r * cols + c], "a row moves down when the view goes north");
        }
        for (int[] w : new int[][]{east, north, AtlasSky.slid(east, cols, rows, -1, 1)}) {
            int[] sorted = w.clone();
            Arrays.sort(sorted);
            assertArrayEquals(AtlasSky.identityWall(), sorted, "every view is on the wall exactly once: " + Arrays.toString(w));
        }
    }

    @Test
    void onlyTheStripASlideUncoversIsDrawnAgain() {
        int cols = AtlasSky.COLS, rows = AtlasSky.ROWS;
        int[] wall = AtlasSky.identityWall();
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, -1}}) {
            boolean[] fresh = AtlasSky.uncovered(cols, rows, d[0], d[1]);
            int[] next = AtlasSky.slid(wall, cols, rows, d[0], d[1]);
            int count = 0;
            for (int pos = 0; pos < fresh.length; pos++) {
                if (fresh[pos]) {
                    count++;
                    continue;
                }
                int from = (pos / cols + d[1]) * cols + pos % cols + d[0];
                assertEquals(wall[from], next[pos], "a kept position shows the view that already holds its picture");
            }
            int strip = (d[0] != 0 ? rows : 0) + (d[1] != 0 ? cols : 0) - (d[0] != 0 && d[1] != 0 ? 1 : 0);
            assertEquals(strip, count, "a slide by " + Arrays.toString(d) + " uncovers one column and/or one row");
        }
    }

    @Test
    void aSlideAndBackRestoresTheWall() {
        int[] wall = AtlasSky.identityWall();
        int[] there = AtlasSky.slid(wall, AtlasSky.COLS, AtlasSky.ROWS, 1, 0);
        int[] back = AtlasSky.slid(there, AtlasSky.COLS, AtlasSky.ROWS, -1, 0);
        assertArrayEquals(wall, back, "the column that slid off comes back in its views");
        assertEquals(N, back.length);
    }
}
