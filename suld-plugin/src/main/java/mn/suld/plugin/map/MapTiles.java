package mn.suld.plugin.map;

import mn.suld.api.map.AtlasLod;
import mn.suld.api.map.BakedAtlas;
import mn.suld.api.map.MapGeometry;
import mn.suld.api.map.TileComposer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The world map's tiles (docs/WORLD_MAP.md): 128 × 128 map bytes per (zoom, tile), composed on the map's worker threads
 * from the {@link ChunkColorStore} (the real blocks) and the baked cartography ({@link BakedAtlas}, by {@link AtlasLod}'s
 * rule: the real land where it is explored, the atlas elsewhere) and kept in a bounded cache. A chunk that changes drops
 * the tiles over it at every zoom and tells the listeners (open atlases, minimaps), which ask again; a new bake drops
 * every tile and tells the reset listeners.
 */
final class MapTiles {

    /** Told (on a worker thread) that a tile's picture changed. */
    interface Listener {
        void tileChanged(int zoom, int tx, int tz);
    }

    private final Logger log;
    private final ChunkColorStore store;
    private final ExecutorService workers;
    private final int maxTiles;
    private volatile MapGeometry geometry;
    /** The baked cartography, or null (map.cartography off, or not baked yet): the real blocks only. */
    private volatile BakedAtlas baked;
    private final List<Runnable> resets = new CopyOnWriteArrayList<>();
    /** Guarded by {@code this}. */
    private final LinkedHashMap<Long, byte[]> cache = new LinkedHashMap<>(256, 0.75f, true);
    /** Tiles being composed. Guarded by {@code this}. */
    private final Map<Long, CompletableFuture<byte[]>> inflight = new HashMap<>();
    /**
     * In-flight tiles that changed while being drawn: their result is neither cached nor handed to a later request (that
     * one draws the tile again). Kept by future, not by key, so the redraw's own entry is never mistaken for the old
     * one. Guarded by {@code this}.
     */
    private final Set<CompletableFuture<byte[]>> staleInflight = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    MapTiles(ChunkColorStore store, MapGeometry geometry, ExecutorService workers, int maxTiles, Logger log) {
        this.log = log;
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
            dropAllInflight();
        }
    }

    /** The baked cartography the tiles are drawn over (null: none); every tile is dropped and the reset listeners told. */
    void baked(BakedAtlas b) {
        baked = b;
        synchronized (this) {
            cache.clear();
            dropAllInflight();
        }
        for (Runnable r : resets) r.run();
    }

    BakedAtlas baked() {
        return baked;
    }

    /** Told (on the thread that set it) when every tile changed: a new bake. */
    void onReset(Runnable r) {
        resets.add(r);
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
            BakedAtlas b0 = baked;
            BakedAtlas atlas = b0 != null && b0.geometry().equals(g) ? b0 : null; // a bake for an old border is not drawn
            // the tile is cached before its future completes, so whoever waited on it finds it cached (CI #79 caught a
            // callback that cached it after the waiting thread had already woken); the lock held here keeps the worker's
            // bookkeeping after the inflight entry below
            @SuppressWarnings("unchecked")
            CompletableFuture<byte[]>[] self = new CompletableFuture[1]; // set below while the lock is held
            CompletableFuture<byte[]> nf = CompletableFuture.supplyAsync(() -> {
                byte[] bytes = null;
                try {
                    bytes = AtlasLod.compose(g, store::columnBlocking, atlas, zoom, tx, tz);
                    return bytes;
                } catch (RuntimeException ex) {
                    // an unread region is asked again later; anything else is a fault worth seeing
                    if (!(ex instanceof ChunkColorStore.RegionUnavailable)) log.log(Level.WARNING, "[map] tile " + zoom + "/" + tx + "/" + tz + " not drawn", ex);
                    throw ex;
                } finally {
                    synchronized (MapTiles.this) {
                        CompletableFuture<byte[]> me = self[0]; // null only if the executor ran it here: nothing in flight
                        boolean stale = me != null && staleInflight.remove(me);
                        if (me != null) inflight.remove(k, me);
                        if (bytes != null && !stale) {
                            cache.put(k, bytes);
                            while (cache.size() > maxTiles) cache.remove(cache.keySet().iterator().next());
                        }
                    }
                }
            }, workers);
            self[0] = nf;
            if (!nf.isDone()) inflight.put(k, nf); // done already only if the executor ran it here: nothing in flight
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
                dropInflight(k);
                changed.add(new int[]{zoom, tx, tz});
                // the row south of a chunk's north edge is shaded against this chunk: redraw that tile too when it differs
                int tzs = MapGeometry.tileOf(z + 16, zoom);
                if (tzs != tz) {
                    long ks = key(zoom, tx, tzs);
                    cache.remove(ks);
                    dropInflight(ks);
                    changed.add(new int[]{zoom, tx, tzs});
                }
            }
        }
        for (int[] c : changed) for (Listener l : listeners) l.tileChanged(c[0], c[1], c[2]);
    }

    /** The tile being drawn at {@code k} no longer counts: a request after this draws it again. Caller holds the lock. */
    private void dropInflight(long k) {
        CompletableFuture<byte[]> f = inflight.remove(k);
        if (f != null) staleInflight.add(f);
    }

    private void dropAllInflight() {
        staleInflight.addAll(inflight.values());
        inflight.clear();
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
     * pixel: the real blocks only (staff's evidence of what the server has); parts never generated stay transparent.
     * Runs on a worker thread.
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
