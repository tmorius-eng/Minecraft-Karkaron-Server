package mn.suld.plugin.map;

import mn.suld.api.map.MapColors;
import mn.suld.api.map.MapColumn;
import mn.suld.api.map.MapGeometry;
import mn.suld.api.map.TileComposer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The map's column store: what is put comes back (from memory and from disk), presence follows the data, formats. */
class ChunkColorStoreTest {

    @TempDir
    Path dir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Logger log = Logger.getLogger("test");

    @AfterEach
    void stop() throws InterruptedException {
        io.shutdownNow();
        io.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static int[] chunk(int colourId, int height) {
        int[] c = new int[ChunkColorStore.CHUNK_COLS];
        for (int i = 0; i < c.length; i++) c[i] = MapColumn.pack(colourId, 0, height + (i & 15));
        return c;
    }

    @Test
    void columnsComeBackFromMemoryAndFromDisk() throws Exception {
        ChunkColorStore s = new ChunkColorStore(dir, log, io, 4);
        io.submit(() -> s.presenceSquare(-64, -64, 128)).get();
        s.put(3, -2, chunk(MapColors.GRASS, 70));
        s.put(-40, 17, chunk(MapColors.SAND, 64)); // another region
        s.flush().get(10, TimeUnit.SECONDS);
        // from memory
        int c = s.columnBlocking(3 * 16 + 5, -2 * 16 + 0);
        assertEquals(MapColors.GRASS, MapColumn.id(c));
        assertEquals(75, MapColumn.height(c));
        assertTrue(s.present(3, -2));
        assertTrue(s.present(-40, 17));
        assertFalse(s.present(4, -2));
        assertEquals(0, s.columnBlocking(4 * 16, -2 * 16), "never put: unknown");
        assertEquals(2, s.regionsOnDisk().size());
        // a fresh store reads the same from disk (another run of the server)
        ChunkColorStore again = new ChunkColorStore(dir, log, io, 4);
        io.submit(() -> again.presenceSquare(-64, -64, 128)).get();
        assertEquals(2, again.presentCount());
        assertEquals(MapColors.SAND, MapColumn.id(again.columnBlocking(-40 * 16 + 15, 17 * 16 + 15)));
        assertEquals(64 + 15, MapColumn.height(again.columnBlocking(-40 * 16 + 15, 17 * 16 + 15)));
    }

    @Test
    void evictedRegionsAreWrittenAndReadBackInOrder() throws Exception {
        ChunkColorStore s = new ChunkColorStore(dir, log, io, 4);
        io.submit(() -> s.presenceSquare(-512, -512, 1024)).get();
        // ten regions, more than the four kept in memory: the early ones are written on eviction
        for (int r = 0; r < 10; r++) s.put(r * 32, 0, chunk(MapColors.STONE, 60 + r));
        s.flush().get(10, TimeUnit.SECONDS);
        assertTrue(s.regionsInMemory() <= 4 + 1, "kept " + s.regionsInMemory());
        for (int r = 0; r < 10; r++) {
            int c = s.columnBlocking(r * 32 * 16, 0);
            assertEquals(MapColors.STONE, MapColumn.id(c), "region " + r);
            assertEquals(60 + r, MapColumn.height(c), "region " + r);
        }
        assertEquals(10, s.presentCount());
    }

    @Test
    void unfinishedChunksAreRememberedUntilTheyAreStored() throws Exception {
        ChunkColorStore s = new ChunkColorStore(dir, log, io, 4);
        io.submit(() -> s.presenceSquare(-64, -64, 128)).get();
        s.markUnfinished(5, 5);
        assertTrue(s.unfinished(5, 5));
        s.flush().get(10, TimeUnit.SECONDS);
        ChunkColorStore again = new ChunkColorStore(dir, log, io, 4);
        io.submit(() -> again.presenceSquare(-64, -64, 128)).get();
        assertTrue(again.unfinished(5, 5), "kept across a restart");
        again.put(5, 5, chunk(MapColors.GRASS, 70));
        again.flush().get(10, TimeUnit.SECONDS);
        assertFalse(again.unfinished(5, 5), "a stored chunk is finished");
        assertTrue(again.present(5, 5));
        again.markUnfinished(5, 5);
        assertFalse(again.unfinished(5, 5), "a chunk on the map is never marked unfinished");
    }

    @Test
    void aStoreOfAnotherFormatStartsOver() throws Exception {
        ChunkColorStore s = new ChunkColorStore(dir, log, io, 4);
        assertFalse(io.submit(() -> s.format("columns-2 natural-1")).get(), "an empty folder has nothing to delete");
        io.submit(() -> s.presenceSquare(0, 0, 64)).get();
        s.put(1, 1, chunk(MapColors.GRASS, 70));
        s.flush().get(10, TimeUnit.SECONDS);
        assertFalse(io.submit(() -> s.format("columns-2 natural-1")).get(), "same format: kept");
        assertEquals(1, s.regionsOnDisk().size());
        assertTrue(io.submit(() -> s.format("columns-2 vanilla")).get(), "another colour mode: the old columns go");
        assertEquals(0, s.regionsOnDisk().size());
        assertFalse(Files.exists(dir.resolve("present.bin")));
        assertEquals("columns-2 vanilla", Files.readString(dir.resolve("FORMAT")).strip());
    }

    @Test
    void aRegionReadThatTimesOutIsNotCachedAsBlankLand() throws Exception {
        ChunkColorStore s = new ChunkColorStore(dir, log, io, 8);
        io.submit(() -> s.presenceSquare(-32, -32, 64)).get();
        s.put(0, 0, chunk(MapColors.GRASS, 70));
        s.flush().get(10, TimeUnit.SECONDS);
        ChunkColorStore cold = new ChunkColorStore(dir, log, io, 8); // the region is on disk, not in memory
        io.submit(() -> cold.presenceSquare(-32, -32, 64)).get();
        cold.readTimeoutMs = 50;
        java.util.concurrent.CountDownLatch busy = new java.util.concurrent.CountDownLatch(1);
        io.submit(() -> {
            busy.await(); // the io thread is stuck behind other work (a long flush, the scan)
            return null;
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            MapTiles tiles = new MapTiles(cold, new MapGeometry(-512, -512, 512, 512), workers, 64, log);
            var late = tiles.tile(0, 0, 0);
            java.util.concurrent.ExecutionException e = org.junit.jupiter.api.Assertions.assertThrows(
                    java.util.concurrent.ExecutionException.class, () -> late.get(10, TimeUnit.SECONDS));
            assertTrue(e.getCause() instanceof ChunkColorStore.RegionUnavailable, "unknown, not blank: " + e.getCause());
            assertTrue(tiles.cached(0, 0, 0) == null, "a tile with an unread region is never cached");
            busy.countDown();
            cold.readTimeoutMs = 10_000;
            byte[] t = tiles.tile(0, 0, 0).get(10, TimeUnit.SECONDS);
            assertEquals(MapColors.GRASS, MapColors.id(t[3 * 128 + 3]), "asked again, the land is there");
        } finally {
            busy.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void aTileThatChangesWhileBeingDrawnIsDrawnAgainForTheNextRequest() throws Exception {
        ChunkColorStore s = new ChunkColorStore(dir, log, io, 8);
        io.submit(() -> s.presenceSquare(-32, -32, 64)).get();
        s.put(0, 0, chunk(MapColors.GRASS, 70));
        s.flush().get(10, TimeUnit.SECONDS);
        ChunkColorStore cold = new ChunkColorStore(dir, log, io, 8); // the region is on disk: the first draw waits for it
        io.submit(() -> cold.presenceSquare(-32, -32, 64)).get();
        java.util.concurrent.CountDownLatch busy = new java.util.concurrent.CountDownLatch(1);
        io.submit(() -> {
            busy.await();
            return null;
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            MapTiles tiles = new MapTiles(cold, new MapGeometry(-512, -512, 512, 512), workers, 64, log);
            var first = tiles.tile(0, 0, 0);
            assertTrue(tiles.tile(0, 0, 0) == first, "while it is drawn, a second request shares the draw");
            tiles.chunkChanged(0, 0);
            var second = tiles.tile(0, 0, 0);
            assertTrue(second != first, "after the change the next request draws it again, not the old picture");
            busy.countDown();
            first.get(10, TimeUnit.SECONDS);
            byte[] fresh = second.get(10, TimeUnit.SECONDS);
            assertTrue(tiles.cached(0, 0, 0) == fresh, "only the fresh draw is cached");
        } finally {
            busy.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void tilesAreDrawnFromTheStoreAndRedrawnWhenAChunkChanges() throws Exception {
        ChunkColorStore s = new ChunkColorStore(dir, log, io, 8);
        io.submit(() -> s.presenceSquare(-32, -32, 64)).get();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            MapTiles tiles = new MapTiles(s, new MapGeometry(-512, -512, 512, 512), workers, 64, log);
            s.put(0, 0, chunk(MapColors.GRASS, 70));
            s.flush().get(10, TimeUnit.SECONDS);
            byte[] t = tiles.tile(0, 0, 0).get(10, TimeUnit.SECONDS);
            assertEquals(TileComposer.PIXELS, t.length);
            assertEquals(MapColors.GRASS, MapColors.id(t[3 * 128 + 3]));
            assertEquals(0, t[3 * 128 + 20], "chunk 1,0 was never seen: blank");
            assertTrue(tiles.cached(0, 0, 0) != null);
            int[] changed = new int[1];
            tiles.listen((z, tx, tz) -> changed[0]++);
            s.put(1, 0, chunk(MapColors.SAND, 64));
            tiles.chunkChanged(1, 0);
            assertTrue(tiles.cached(0, 0, 0) == null, "the tile over the chunk is dropped");
            assertTrue(changed[0] >= 5, "every zoom is told: " + changed[0]);
            byte[] again = tiles.tile(0, 0, 0).get(10, TimeUnit.SECONDS);
            assertEquals(MapColors.SAND, MapColors.id(again[3 * 128 + 20]));
            assertNotEquals(t[3 * 128 + 20], again[3 * 128 + 20]);
        } finally {
            workers.shutdownNow();
        }
    }
}
