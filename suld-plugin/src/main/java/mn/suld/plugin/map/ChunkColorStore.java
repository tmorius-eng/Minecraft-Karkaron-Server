package mn.suld.plugin.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * What the map knows about the world's blocks (docs/WORLD_MAP.md): one packed {@link mn.suld.api.map.MapColumn} per block
 * column of every chunk the server has generated and seen, kept in region files of 32 × 32 chunks
 * ({@code plugins/SULD/map/columns/r.<rx>.<rz>.bin}, deflated), and a presence bit set over the bordered square.
 * <p>
 * Threads: {@link #columnBlocking} may wait for a region file and is for the map's worker threads only; {@link #put}
 * and {@link #present} never wait. All file work runs on the single {@code io} thread, so a write always finishes
 * before a later read of the same region.
 */
final class ChunkColorStore {

    static final int REGION = 32, CHUNK_COLS = 256, REGION_COLS = REGION * REGION * CHUNK_COLS;

    private final Path dir;
    private final Logger log;
    private final ExecutorService io;
    private final int maxRegions;
    /** Regions in memory, least recently used first. Guarded by {@code this}. */
    private final LinkedHashMap<Long, Region> regions = new LinkedHashMap<>(64, 0.75f, true);
    /** Regions being read. Guarded by {@code this}. */
    private final Map<Long, CompletableFuture<Region>> loading = new HashMap<>();
    /** Chunk writes waiting for their region to be read. Guarded by {@code this}. */
    private final Map<Long, List<Pending>> pending = new HashMap<>();
    /** Which chunks of the presence square are stored (chunk index in the square). Guarded by {@code this}. */
    private BitSet present = new BitSet();
    /** Chunks on disk that never finished generating (nothing to draw): the scan does not ask again. Guarded by {@code this}. */
    private BitSet unfinished = new BitSet();
    private int squareMinCx, squareMinCz, squareSide;

    private record Pending(int cx, int cz, int[] cols) {
    }

    static final class Region {
        final int[] cols;
        boolean dirty;

        Region(int[] cols) {
            this.cols = cols;
        }
    }

    ChunkColorStore(Path dir, Logger log, ExecutorService io, int maxRegions) {
        this.dir = dir;
        this.log = log;
        this.io = io;
        this.maxRegions = Math.max(4, maxRegions);
    }

    static long key(int rx, int rz) {
        return ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
    }

    static int rx(long key) {
        return (int) (key >> 32);
    }

    static int rz(long key) {
        return (int) key;
    }

    static int index(int cx, int cz, int lx, int lz) {
        return (((cz & 31) * REGION + (cx & 31)) * CHUNK_COLS) + (lz << 4) + lx;
    }

    // ------------------------------------------------------------------------------------------------ format

    /**
     * Makes sure the stored columns were written the way they are read now ({@code tag}: the column layout and the
     * colour mode). Files of another format are deleted (the scan reads the world again). Call once at start, on the io
     * thread, before {@link #presenceSquare}. Returns true when the old files were deleted.
     */
    boolean format(String tag) {
        Path f = dir.resolve("FORMAT");
        try {
            if (Files.exists(f) && Files.readString(f).strip().equals(tag)) return false;
            boolean had = false;
            if (Files.isDirectory(dir)) {
                try (var list = Files.list(dir)) {
                    for (Path p : (Iterable<Path>) list::iterator) {
                        String n = p.getFileName().toString();
                        if ((n.startsWith("r.") && (n.endsWith(".bin") || n.endsWith(".tmp"))) || n.startsWith("present.bin") || n.startsWith("unfinished.bin")) {
                            Files.deleteIfExists(p);
                            had = true;
                        }
                    }
                }
            }
            Files.createDirectories(dir);
            Files.writeString(f, tag + "\n");
            if (had) log.info("[map] the stored columns were of another format: deleted, the scan reads the world again (" + tag + ")");
            return had;
        } catch (IOException e) {
            log.log(Level.WARNING, "[map] FORMAT not checked", e);
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------ presence

    /** Sets the square the presence bits cover (chunks), and reads them from disk. Call once at start, off the main thread. */
    synchronized void presenceSquare(int minCx, int minCz, int side) {
        squareMinCx = minCx;
        squareMinCz = minCz;
        squareSide = side;
        present = readBits("present.bin", minCx, minCz, side);
        unfinished = readBits("unfinished.bin", minCx, minCz, side);
    }

    /** A bit file of this square; empty when missing, unreadable or of another square (the region files stay valid). */
    private BitSet readBits(String name, int minCx, int minCz, int side) {
        Path f = dir.resolve(name);
        if (!Files.exists(f)) return new BitSet(side * side);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(f)))) {
            int mx = in.readInt(), mz = in.readInt(), s = in.readInt(), n = in.readInt();
            if (mx != minCx || mz != minCz || s != side) return new BitSet(side * side);
            byte[] b = new byte[n];
            in.readFully(b);
            return BitSet.valueOf(b);
        } catch (IOException e) {
            log.log(Level.WARNING, "[map] " + name + " not read, starting over", e);
            return new BitSet(side * side);
        }
    }

    /** Chunk (cx, cz) is on disk but never finished generating. */
    synchronized void markUnfinished(int cx, int cz) {
        int i = squareIndex(cx, cz);
        if (i >= 0 && !present.get(i)) unfinished.set(i);
    }

    synchronized boolean unfinished(int cx, int cz) {
        int i = squareIndex(cx, cz);
        return i >= 0 && unfinished.get(i);
    }

    private int squareIndex(int cx, int cz) {
        int i = cx - squareMinCx, j = cz - squareMinCz;
        if (squareSide <= 0 || i < 0 || j < 0 || i >= squareSide || j >= squareSide) return -1;
        return j * squareSide + i;
    }

    synchronized boolean present(int cx, int cz) {
        int i = squareIndex(cx, cz);
        return i >= 0 && present.get(i);
    }

    synchronized int presentCount() {
        return present.cardinality();
    }

    // ------------------------------------------------------------------------------------------------ reading

    /** The column at block (x, z), reading its region from disk if needed. Map worker threads only. */
    int columnBlocking(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        long k = key(cx >> 5, cz >> 5);
        Region r;
        CompletableFuture<Region> f;
        synchronized (this) {
            r = regions.get(k);
            f = r == null ? fetch(k) : null;
        }
        if (r == null) {
            try {
                r = f.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                return 0;
            }
            if (r == null) return 0;
        }
        return r.cols[index(cx, cz, x & 15, z & 15)];
    }

    /** Starts reading a region (once); caller holds the lock. */
    private CompletableFuture<Region> fetch(long k) {
        CompletableFuture<Region> f = loading.get(k);
        if (f != null) return f;
        CompletableFuture<Region> nf = new CompletableFuture<>();
        loading.put(k, nf);
        io.execute(() -> {
            int[] cols = read(k);
            Region r = new Region(cols);
            List<Pending> waiting;
            synchronized (this) {
                loading.remove(k);
                waiting = pending.remove(k);
                if (waiting != null) {
                    for (Pending p : waiting) apply(r, p.cx(), p.cz(), p.cols());
                    r.dirty = true;
                }
                regions.put(k, r);
                evictIfNeeded();
            }
            nf.complete(r);
        });
        return nf;
    }

    private int[] read(long k) {
        Path f = file(k);
        int[] cols = new int[REGION_COLS];
        if (!Files.exists(f)) return cols;
        try (InputStream in = new InflaterInputStream(new BufferedInputStream(Files.newInputStream(f)))) {
            byte[] raw = in.readAllBytes();
            if (raw.length != REGION_COLS * 4) {
                log.warning("[map] " + f.getFileName() + " has " + raw.length + " bytes, expected " + REGION_COLS * 4 + ": ignored");
                return cols;
            }
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(cols);
        } catch (IOException e) {
            log.log(Level.WARNING, "[map] " + f.getFileName() + " not read", e);
        }
        return cols;
    }

    private Path file(long k) {
        return dir.resolve("r." + rx(k) + "." + rz(k) + ".bin");
    }

    // ------------------------------------------------------------------------------------------------ writing

    /**
     * Stores one chunk's 256 columns (row by row, z then x) and marks it present. Any thread. The presence bit is set
     * only when the columns are in a region in memory, so a flush never writes a chunk as present without its columns.
     */
    void put(int cx, int cz, int[] cols) {
        if (cols.length != CHUNK_COLS) throw new IllegalArgumentException("columns " + cols.length);
        long k = key(cx >> 5, cz >> 5);
        synchronized (this) {
            Region r = regions.get(k);
            if (r != null) {
                apply(r, cx, cz, cols);
                r.dirty = true;
                return;
            }
            pending.computeIfAbsent(k, x -> new ArrayList<>()).add(new Pending(cx, cz, cols.clone()));
            fetch(k);
        }
    }

    /** Caller holds the lock. */
    private void apply(Region r, int cx, int cz, int[] cols) {
        System.arraycopy(cols, 0, r.cols, index(cx, cz, 0, 0), CHUNK_COLS);
        int i = squareIndex(cx, cz);
        if (i >= 0) {
            present.set(i);
            unfinished.clear(i);
        }
    }

    /** Caller holds the lock. Writes evicted dirty regions on the io thread (before any later read of them). */
    private void evictIfNeeded() {
        Iterator<Map.Entry<Long, Region>> it = regions.entrySet().iterator();
        while (regions.size() > maxRegions && it.hasNext()) {
            Map.Entry<Long, Region> e = it.next();
            if (pending.containsKey(e.getKey())) continue;
            Region r = e.getValue();
            long k = e.getKey();
            it.remove();
            if (r.dirty) io.execute(() -> write(k, r.cols));
        }
    }

    /**
     * Writes every dirty region and the presence bits. The dirty set is taken on the io thread, after every region read
     * queued before the call, so chunk writes still waiting for their region are part of this flush.
     */
    CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(() -> {
            List<Map.Entry<Long, int[]>> dirty = new ArrayList<>();
            byte[] bits, open;
            int mx, mz, side;
            synchronized (this) {
                for (Map.Entry<Long, Region> e : regions.entrySet()) {
                    if (!e.getValue().dirty) continue;
                    e.getValue().dirty = false;
                    dirty.add(Map.entry(e.getKey(), e.getValue().cols));
                }
                bits = present.toByteArray();
                open = unfinished.toByteArray();
                mx = squareMinCx;
                mz = squareMinCz;
                side = squareSide;
            }
            // a column written while its region is copied out is either old or new, both real; the region is dirty again
            for (Map.Entry<Long, int[]> e : dirty) write(e.getKey(), e.getValue());
            if (side > 0) {
                writeBits("present.bin", mx, mz, side, bits);
                writeBits("unfinished.bin", mx, mz, side, open);
            }
        }, io);
    }

    private void write(long k, int[] cols) {
        Path f = file(k), tmp = dir.resolve(f.getFileName() + ".tmp");
        try {
            Files.createDirectories(dir);
            ByteBuffer buf = ByteBuffer.allocate(REGION_COLS * 4).order(ByteOrder.LITTLE_ENDIAN);
            buf.asIntBuffer().put(cols);
            try (OutputStream out = new DeflaterOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.write(buf.array());
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.log(Level.WARNING, "[map] " + f.getFileName() + " not written", e);
        }
    }

    private void writeBits(String name, int mx, int mz, int side, byte[] bits) {
        Path f = dir.resolve(name), tmp = dir.resolve(name + ".tmp");
        try {
            Files.createDirectories(dir);
            ByteBuffer b = ByteBuffer.allocate(16 + bits.length);
            b.putInt(mx).putInt(mz).putInt(side).putInt(bits.length).put(bits);
            Files.write(tmp, b.array());
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.log(Level.WARNING, "[map] " + name + " not written", e);
        }
    }

    /** Region keys that have a file on disk (for status and the export). */
    Set<Long> regionsOnDisk() {
        Set<Long> out = new HashSet<>();
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            s.forEach(p -> {
                String n = p.getFileName().toString();
                if (!n.startsWith("r.") || !n.endsWith(".bin")) return;
                String[] parts = n.substring(2, n.length() - 4).split("\\.");
                if (parts.length != 2) return;
                try {
                    out.add(key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                } catch (NumberFormatException ignored) {
                    // not ours
                }
            });
        } catch (IOException e) {
            log.log(Level.WARNING, "[map] columns folder not listed", e);
        }
        return out;
    }

    synchronized int regionsInMemory() {
        return regions.size();
    }
}
