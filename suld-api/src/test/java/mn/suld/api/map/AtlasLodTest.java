package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which land a tile shows: the real blocks where explored, the baked atlas elsewhere, by zoom. */
class AtlasLodTest {

    private static final MapGeometry G = CartographyFixture.WORLD;
    private static final BakedAtlas B = CartographyFixture.baked();
    private static final int GRASS = MapColumn.pack(MapColors.GRASS, 0, 70);

    /** Real land in the given block squares {x0, z0, x1, z1} (max exclusive), nothing elsewhere. */
    private static TileComposer.Source explored(int[]... squares) {
        return (x, z) -> {
            for (int[] q : squares) if (x >= q[0] && x < q[2] && z >= q[1] && z < q[3]) return GRASS;
            return 0;
        };
    }

    /** How many of the tiles' pixels (zoom, tiles over the block square) show the real blocks. */
    private static int realShown(TileComposer.Source src, int zoom, int x0, int z0, int x1, int z1) {
        int n = 0;
        for (int tz = MapGeometry.tileOf(z0, zoom); tz <= MapGeometry.tileOf(z1 - 1, zoom); tz++) {
            for (int tx = MapGeometry.tileOf(x0, zoom); tx <= MapGeometry.tileOf(x1 - 1, zoom); tx++) {
                byte[] real = TileComposer.compose(G, src, zoom, tx, tz), out = AtlasLod.compose(G, src, B, zoom, tx, tz);
                for (int i = 0; i < out.length; i++) if (real[i] != 0 && out[i] == real[i]) n++;
            }
        }
        return n;
    }

    @Test
    void withoutABakeATileIsTheRealBlocksOnly() {
        TileComposer.Source src = explored(new int[]{100, 100, 300, 300});
        for (int zoom = 0; zoom < MapGeometry.ZOOMS; zoom++) {
            assertArrayEquals(TileComposer.compose(G, src, zoom, 0, 0), AtlasLod.compose(G, src, null, zoom, 0, 0), "zoom " + zoom);
        }
    }

    @Test
    void closeUpTheRealBlocksShowWhereverTheyAreAndEveryHoleIsSurveyInk() {
        int[] sq = {1000, 1000, 1064, 1064};
        TileComposer.Source src = explored(sq);
        int tx = MapGeometry.tileOf(1000, 0), tz = MapGeometry.tileOf(1000, 0);
        byte[] real = TileComposer.compose(G, src, 0, tx, tz), out = AtlasLod.compose(G, src, B, 0, tx, tz);
        for (int pz = 0; pz < 128; pz++) {
            for (int px = 0; px < 128; px++) {
                int x = tx * 128 + px, z = tz * 128 + pz, i = pz * 128 + px;
                boolean in = x >= sq[0] && x < sq[2] && z >= sq[1] && z < sq[3];
                if (in) assertEquals(real[i], out[i], "explored: the real block");
                else assertEquals(B.survey(0, x, z), out[i], "unexplored: the survey ink");
                assertNotEquals(0, out[i], "no blank parchment hole");
            }
        }
    }

    @Test
    void aDungeonGatesLonePatchVanishesFarOutButALargeExploredLandStays() {
        int[] gate = CartographyFixture.polar(165, 650);
        int[] patch = {gate[0] - 24, gate[1] - 24, gate[0] + 24, gate[1] + 24};         // three chunks across
        int[] land = {-3000, -3000, -1800, -1800};                                         // 1 200 blocks explored
        TileComposer.Source src = explored(patch, land);
        assertEquals(0, realShown(src, 3, patch[0], patch[1], patch[2], patch[3]), "no square under the gate at zoom 3");
        assertEquals(0, realShown(src, 4, patch[0], patch[1], patch[2], patch[3]), "nor at zoom 4");
        assertTrue(realShown(src, 2, patch[0], patch[1], patch[2], patch[3]) > 0, "zoom 2 shows it, over the survey land");
        int inner = realShown(src, 3, -2900, -2900, -1900, -1900);
        assertTrue(inner > 0.9 * (1000 / 8) * (1000 / 8), "the explored land shows at zoom 3: " + inner);
        assertEquals(0, realShown(src, 4, land[0], land[1], land[2], land[3]), "the whole world is the atlas");
    }

    @Test
    void theRealShareFallsAsTheViewWidens() {
        int[] sq = {2000, 2000, 2320, 2320};
        TileComposer.Source src = explored(sq);
        double last = 2;
        for (int zoom = 0; zoom < MapGeometry.ZOOMS; zoom++) {
            int s = MapGeometry.scale(zoom), all = (320 / s) * (320 / s);
            double share = realShown(src, zoom, sq[0], sq[1], sq[2], sq[3]) / (double) all;
            assertTrue(share <= last + 1e-9, "zoom " + zoom + ": " + share + " after " + last);
            last = share;
        }
        assertEquals(0, last, 1e-9);
    }

    @Test
    void theCapitalsRealBlocksShowAtEveryZoom() {
        TileComposer.Source src = explored(new int[]{-60, -60, 60, 60});
        for (int zoom = 0; zoom < MapGeometry.ZOOMS; zoom++) {
            int s = MapGeometry.scale(zoom), want = 0;
            for (int z = -48; z < 48; z += s) for (int x = -48; x < 48; x += s) if (Math.floorMod(x, s) == 0 && Math.floorMod(z, s) == 0) want++;
            assertTrue(realShown(src, zoom, -48, -48, 48, 48) >= want, "zoom " + zoom);
        }
    }

    @Test
    void tilesMeetWithoutASeamAndTheWholeWorldViewReadsNoColumn() {
        // land explored all round: every pixel of a zoom-3 tile is real, also by its edges (the share looks past them)
        TileComposer.Source all = (x, z) -> GRASS;
        int tx = MapGeometry.tileOf(-2500, 3), tz = MapGeometry.tileOf(2500, 3);
        assertArrayEquals(TileComposer.compose(G, all, 3, tx, tz), AtlasLod.compose(G, all, B, 3, tx, tz));
        // zoom 4 away from the capital never asks for a column
        AtomicInteger reads = new AtomicInteger();
        TileComposer.Source counting = (x, z) -> {
            reads.incrementAndGet();
            return GRASS;
        };
        byte[] far = AtlasLod.compose(G, counting, B, 4, 1, 1);
        assertEquals(0, reads.get());
        Set<Byte> colours = new HashSet<>();
        for (byte b : far) colours.add(b);
        assertTrue(colours.size() > 8, "the atlas, not one flat colour");
        assertTrue(!AtlasLod.realShows(4) && AtlasLod.realShows(3) && AtlasLod.realShows(0));
    }
}
