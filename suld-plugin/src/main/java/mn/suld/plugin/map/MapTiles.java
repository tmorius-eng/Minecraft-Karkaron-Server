package mn.suld.plugin.map;

import mn.suld.api.map.MapGeometry;
import mn.suld.api.map.TileComposer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * The world map's tiles (docs/WORLD_MAP.md): 128 × 128 map bytes per (zoom, tile), composed on the map's worker threads
 * from the {@link ChunkColorStore} (real blocks only) and kept in a bounded cache. A chunk that changes drops the tiles
 * over it at every zoom and tells the listeners (open atlases, minimaps), which ask again.
 */
final class MapTiles {

    /** Told (on a worker thread) that a tile's picture changed. */
    interface Listener {
        void tileChanged(int zoom, int tx, int tz);
    }

    private final ChunkColorStore store;
    private final ExecutorService workers;
    private final int maxTiles;
    private volatile MapGeometry geometry;
    /** Guarded by {@code this}. */
    private final LinkedHashMap<Long, byte[]> cache = new LinkedHashMap<>(256, 0.75f, true);
    /** Tiles being composed. Guarded by {@code this}. */
    private final Map<Long, CompletableFuture<byte[]>> inflight = new HashMap<>();
    /** In-flight tiles that changed while being drawn: their result is not cached. Guarded by {@code this}. */
    private final Set<Long> staleInflight = new HashSet<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    MapTiles(ChunkColorStore store, MapGeometry geometry, ExecutorService workers, int maxTiles) {
        this.store = store;
        this.geometry = geometry;
        this.workers = workers;
        this.maxTiles = Math.max(64, maxTiles);
    }

    static long key(int zoom, int tx, int tz) {
        return ((long) zoom << 60) | ((long) (tx & 0x3FFFFFFF) << 30) | (tz & 0x3FFFFFFFL);
    }

    MapGeometry geometry() {
        return geometry;
    }

    /** A new playable square (border moved): every tile is dropped. */
    void geometry(MapGeometry g) {
        geometry = g;
        synchronized (this) {
            cache.clear();
            staleInflight.addAll(inflight.keySet());
        }
    }

    void listen(Listener l) {
        listeners.add(l);
    }

    void unlisten(Listener l) {
        listeners.remove(l);
    }

    /** The tile's bytes (cached, or composed on a worker). */
    CompletableFuture<byte[]> tile(int zoom, int tx, int tz) {
        MapGeometry.checkZoom(zoom);
        long k = key(zoom, tx, tz);
        synchronized (this) {
            byte[] b = cache.get(k);
            if (b != null) return CompletableFuture.completedFuture(b);
            CompletableFuture<byte[]> f = inflight.get(k);
            if (f != null) return f;
            MapGeometry g = geometry;
            CompletableFuture<byte[]> nf = CompletableFuture.supplyAsync(() -> TileComposer.compose(g, store::columnBlocking, zoom, tx, tz), workers);
            inflight.put(k, nf);
            nf.whenComplete((bytes, ex) -> {
                synchronized (this) {
                    inflight.remove(k);
                    boolean stale = staleInflight.remove(k);
                    if (bytes != null && !stale) {
                        cache.put(k, bytes);
                        while (cache.size() > maxTiles) cache.remove(cache.keySet().iterator().next());
                    }
                }
            });
            return nf;
        }
    }

    /** The tile if it is cached right now, else null (never composes; any thread). */
    synchronized byte[] cached(int zoom, int tx, int tz) {
        return cache.get(key(zoom, tx, tz));
    }

    /** Chunk (cx, cz) has new columns: the tiles over it at every zoom are redrawn on demand. */
    void chunkChanged(int cx, int cz) {
        int x = cx << 4, z = cz << 4;
        List<int[]> changed = new ArrayList<>(MapGeometry.ZOOMS);
        synchronized (this) {
            for (int zoom = 0; zoom < MapGeometry.ZOOMS; zoom++) {
                int tx = MapGeometry.tileOf(x, zoom), tz = MapGeometry.tileOf(z, zoom);
                long k = key(zoom, tx, tz);
                cache.remove(k);
                if (inflight.containsKey(k)) staleInflight.add(k);
                changed.add(new int[]{zoom, tx, tz});
                // the row south of a chunk's north edge is shaded against this chunk: redraw that tile too when it differs
                int tzs = MapGeometry.tileOf(z + 16, zoom);
                if (tzs != tz) {
                    long ks = key(zoom, tx, tzs);
                    cache.remove(ks);
                    if (inflight.containsKey(ks)) staleInflight.add(ks);
                    changed.add(new int[]{zoom, tx, tzs});
                }
            }
        }
        for (int[] c : changed) for (Listener l : listeners) l.tileChanged(c[0], c[1], c[2]);
    }

    synchronized int cachedCount() {
        return cache.size();
    }

    // ------------------------------------------------------------------------------------------------ export

    /** The ARGB colour of every map byte (0 stays transparent). */
    static int[] argbTable() {
        int[] t = new int[256];
        for (int i = 4; i < 256; i++) t[i] = mn.suld.api.map.MapColors.argb((byte) i);
        return t;
    }

    /**
     * How many map bytes the server's own palette shows differently from the suld-api table the map draws with (0 when
     * they agree; logged once at start as evidence). Never throws.
     */
    @SuppressWarnings("removal")
    static int paletteMismatches() {
        int bad = 0;
        try {
            for (int i = 4; i < 248; i++) {
                java.awt.Color c = org.bukkit.map.MapPalette.getColor((byte) i);
                if ((c.getRGB() & 0xFFFFFF) != (mn.suld.api.map.MapColors.argb((byte) i) & 0xFFFFFF)) bad++;
            }
        } catch (Throwable t) {
            return -1; // the server has no such palette any more: nothing to compare
        }
        return bad;
    }

    /**
     * Writes the map of the square {@code [x0, x1) × [z0, z1)} (blocks) at {@code zoom} as a PNG, one image pixel per map
     * pixel; parts never generated stay transparent. Runs on a worker thread.
     */
    CompletableFuture<Path> export(int zoom, int x0, int z0, int x1, int z1, Path file) {
        return CompletableFuture.supplyAsync(() -> {
            int s = MapGeometry.scale(zoom);
            int w = Math.max(1, (x1 - x0) / s), h = Math.max(1, (z1 - z0) / s);
            if ((long) w * h > 64L * 1024 * 1024) throw new IllegalArgumentException("export too large: " + w + "×" + h);
            int[] argb = argbTable();
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            MapGeometry g = geometry;
            TileComposer.Source src = store::columnBlocking;
            // walk tile by tile so a tile's regions stay in memory while it is drawn
            int tx0 = MapGeometry.tileOf(x0, zoom), tx1 = MapGeometry.tileOf(x1 - 1, zoom);
            int tz0 = MapGeometry.tileOf(z0, zoom), tz1 = MapGeometry.tileOf(z1 - 1, zoom);
            for (int tz = tz0; tz <= tz1; tz++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    byte[] t = TileComposer.compose(g, src, zoom, tx, tz);
                    int bx = MapGeometry.tileStart(tx, zoom), bz = MapGeometry.tileStart(tz, zoom);
                    for (int pz = 0; pz < MapGeometry.TILE; pz++) {
                        int iy = (bz + pz * s - z0) / s;
                        if (bz + pz * s < z0 || iy >= h) continue;
                        for (int px = 0; px < MapGeometry.TILE; px++) {
                            int ix = (bx + px * s - x0) / s;
                            if (bx + px * s < x0 || ix >= w) continue;
                            img.setRGB(ix, iy, argb[t[pz * MapGeometry.TILE + px] & 0xFF]);
                        }
                    }
                }
            }
            try {
                Files.createDirectories(file.getParent());
                ImageIO.write(img, "png", file.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return file;
        }, workers);
    }
}
