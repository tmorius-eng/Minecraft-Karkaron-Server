package mn.suld.plugin.map;

import mn.suld.api.map.MapGeometry;
import mn.suld.api.map.NaturalPalette;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Сүлдийн Атлас»'s picture of the world (docs/WORLD_MAP.md): real blocks only. Every chunk of the overworld inside
 * the border that the server has generated is read the way a vanilla map reads it ({@link ChunkColors}) and kept in
 * the {@link ChunkColorStore}; {@link MapTiles} draws map tiles from it for the atlas, the minimap and the PNG export.
 * Nothing is predicted: a chunk the server never generated stays blank parchment.
 * <p>
 * Where the columns come from, all on the main thread within a small budget per tick (the columns themselves are
 * worked out on the map's worker threads from an immutable snapshot):
 * <ul>
 *   <li>a chunk that loads and is not on the map yet (new land a player walks into, or old land from before);</li>
 *   <li>a chunk whose surface changed (block events mark it; it is read again within 5 s, or as it unloads);</li>
 *   <li>the chunks around every player once a minute (changes no event reports, e.g. a city build);</li>
 *   <li>the scan of the world already on disk: the region files' chunk tables name every chunk that exists; each one
 *       not on the map is loaded without generating anything, read and let go, throttled by the tick time like the
 *       pre-generator. It starts by itself a minute after start (and finishes at once when there is nothing new). A
 *       chunk that never finished generating is remembered and not asked again (it is read when it finishes, as it
 *       loads); {@code scan full} asks again for everything.</li>
 * </ul>
 */
public final class MapService implements Listener {

    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private final Plugin plugin;
    private final Logger log;
    private final Path root;
    private final ExecutorService io, workers;
    private final ChunkColorStore store;
    private final MapTiles tiles;
    private final World world;
    private final int minY, maxY;
    private final long budgetNanos;
    private final int maxBacklog;
    /** Biome colours for grass, leaves and water (map.colors: natural), or null for the vanilla map's colours. */
    private final NaturalPalette natural;

    // ------------------------------------------------------------------ main thread state
    /** Chunks waiting for a snapshot; the value says whether to read it even if it is on the map already. */
    private final Map<Long, Boolean> queued = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    /** Loaded chunks whose surface changed since they were last read. */
    private final Set<Long> dirty = new HashSet<>();
    /** Queued chunks the map holds loaded with a plugin ticket until it has read them (a pre-generator lets go at once). */
    private final Set<Long> held = new HashSet<>();
    /** Main-thread time spent on snapshots in tick {@link #budgetTick}. */
    private long budgetUsed;
    private int budgetTick = -1;
    /** Snapshots handed to the workers and not yet stored (any thread). */
    private final AtomicInteger backlog = new AtomicInteger();
    private BukkitTask ticker;
    private boolean ready;
    private long ticks, snapshots;
    private int minCx, minCz, maxCx, maxCz;
    private Scan scan;
    private String lastScan = "хийгээгүй";
    /** The baked cartography's state for /suld map status. */
    private volatile String cartography = "унтраалттай";
    /** The spawn and the square the last bake was for (main thread). */
    private double bakedSpawnX = Double.NaN, bakedSpawnZ = Double.NaN;
    /** The atlas's places (set by SuldPlugin once the city, gates, ovoos and sites are placed). */
    public volatile java.util.function.Supplier<List<MapPois.Poi>> pois = List::of;

    /** The scan of the chunks already on disk. Main thread. */
    private static final class Scan {
        final boolean full;
        final long startedAt = System.currentTimeMillis();
        /** Chunk keys, region by region, the region nearest the centre first; null while the region tables are read. */
        long[] chunks;
        int next, inFlight, allowance = 2, calm;
        long loaded, empty, failed, skipped;
        /** Loads this scan asked for: their ChunkLoadEvent is not queued a second time. */
        final Set<Long> requested = new HashSet<>();
        long windowStart = System.currentTimeMillis(), windowCount;
        double rate;
        final CommandSender reportTo;

        Scan(boolean full, CommandSender reportTo) {
            this.full = full;
            this.reportTo = reportTo;
        }
    }

    public MapService(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.root = plugin.getDataFolder().toPath().resolve("map");
        this.world = Bukkit.getWorlds().get(0);
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight();
        var c = plugin.getConfig();
        this.budgetNanos = (long) (Math.max(0.2, c.getDouble("map.snapshot-budget-ms", 2.0)) * 1_000_000L);
        this.maxBacklog = Math.max(8, c.getInt("map.max-backlog", 64));
        this.io = Executors.newSingleThreadExecutor(threads("suld-map-io"));
        this.workers = Executors.newFixedThreadPool(Math.max(1, c.getInt("map.workers", 2)), threads("suld-map"));
        this.store = new ChunkColorStore(root.resolve("columns"), log, io, c.getInt("map.max-regions", 32));
        this.natural = "vanilla".equalsIgnoreCase(c.getString("map.colors", "natural")) ? null : loadPalette(plugin, log);
        MapGeometry g = borderGeometry();
        this.tiles = new MapTiles(store, g, workers, c.getInt("map.max-tiles", 512), log);
        bounds(g);
    }

    /** The bundled {@code map/colors.json}; null (vanilla colours) if it cannot be read. */
    private static NaturalPalette loadPalette(Plugin plugin, Logger log) {
        try (java.io.InputStream in = plugin.getResource("map/colors.json")) {
            if (in == null) throw new IOException("map/colors.json is not in the jar");
            return NaturalPalette.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "[map] natural colours not read; the map uses the vanilla colours", e);
            return null;
        }
    }

    /** The stored columns' format: the column layout and the colour mode. */
    private String formatTag() {
        return "columns-2 " + (natural == null ? "vanilla" : "natural-1");
    }

    private static ThreadFactory threads(String prefix) {
        AtomicInteger n = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
    }

    /** The map's tiles (for the atlas and the minimap). */
    MapTiles tiles() {
        return tiles;
    }

    /** The map's worker threads (pictures are composed there, never on the main thread). */
    ExecutorService workers() {
        return workers;
    }

    public MapGeometry geometry() {
        return tiles.geometry();
    }

    /** The world border's square, at most {@code map.max-diameter} blocks wide (an unset border is 60 million). */
    private MapGeometry borderGeometry() {
        WorldBorder b = world.getWorldBorder();
        double size = Math.min(b.getSize(), Math.max(512, plugin.getConfig().getDouble("map.max-diameter", 20_000)));
        return MapGeometry.ofBorder(b.getCenter().getX(), b.getCenter().getZ(), size);
    }

    private void bounds(MapGeometry g) {
        minCx = Math.floorDiv(g.minX(), 16);
        minCz = Math.floorDiv(g.minZ(), 16);
        maxCx = Math.floorDiv(g.maxX() - 1, 16);
        maxCz = Math.floorDiv(g.maxZ() - 1, 16);
    }

    private boolean inside(int cx, int cz) {
        return cx >= minCx && cx <= maxCx && cz >= minCz && cz <= maxCz;
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    static int cx(long key) {
        return (int) (key >> 32);
    }

    static int cz(long key) {
        return (int) key;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Reads the presence bits off the main thread, then listens and ticks. Main thread. */
    public void start() {
        int side = Math.max(maxCx - minCx, maxCz - minCz) + 1, mx = minCx, mz = minCz;
        String tag = formatTag();
        CompletableFuture.runAsync(() -> {
            store.format(tag);
            store.presenceSquare(mx, mz, side);
        }, io).whenComplete((v, err) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null) log.log(Level.WARNING, "[map] presence bits not read; the map starts empty", err);
                ready = true;
                Bukkit.getPluginManager().registerEvents(this, plugin);
                for (Chunk c : world.getLoadedChunks()) {
                    if (inside(c.getX(), c.getZ()) && !store.present(c.getX(), c.getZ())) enqueue(key(c.getX(), c.getZ()), false);
                }
                ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
                MapGeometry g = tiles.geometry();
                int mismatch = MapTiles.paletteMismatches();
                log.info("[map] " + g.width() + " x " + g.depth() + " blocks, " + store.presentCount() + " chunks on the map, "
                        + (natural == null ? "vanilla" : "natural") + " colours"
                        + (mismatch == 0 ? ", palette checked" : mismatch < 0 ? "" : ", " + mismatch + " palette colours differ from the server's"));
                bake();
                if (plugin.getConfig().getBoolean("map.scan.auto-start", true)) {
                    long delay = 20L * Math.max(5, plugin.getConfig().getInt("map.scan.auto-delay-seconds", 60));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (ready && scan == null) startScan(false, null);
                    }, delay);
                }
            });
        });
    }

    /** Stops listening, lets the workers finish, then writes everything (bounded waits). Main thread, on disable. */
    public void shutdown() {
        ready = false;
        if (ticker != null) ticker.cancel();
        ticker = null;
        scan = null;
        HandlerList.unregisterAll(this);
        for (long k : held) world.removePluginChunkTicket(cx(k), cz(k), plugin);
        held.clear();
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) log.warning("[map] column work still running at shutdown");
            store.flush().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warning("[map] not every region written at shutdown (" + e.getClass().getSimpleName() + "); those chunks are read again");
        }
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ the tick

    /** True while this tick's snapshot budget is not spent. */
    private boolean budgetLeft() {
        int now = Bukkit.getCurrentTick();
        if (now != budgetTick) {
            budgetTick = now;
            budgetUsed = 0;
        }
        return budgetUsed < budgetNanos && backlog.get() < maxBacklog;
    }

    private void tick() {
        ticks++;
        while (!queue.isEmpty() && budgetLeft()) {
            long k = queue.poll();
            Boolean force = queued.remove(k);
            int cx = cx(k), cz = cz(k);
            try {
                if (!world.isChunkLoaded(cx, cz)) continue; // read on its next load if it is still not on the map
                if (!Boolean.TRUE.equals(force) && !dirty.contains(k) && store.present(cx, cz)) continue; // the scan was faster
                snapshot(world.getChunkAt(cx, cz));
            } finally {
                if (held.remove(k)) world.removePluginChunkTicket(cx, cz, plugin);
            }
        }
        if (scan != null) scanTick();
        if (ticks % 100 == 0) requeueDirty();
        if (ticks % 1200 == 0) {
            aroundPlayers(1);
            store.flush();
            checkBorder();
        }
    }

    private void enqueue(long k, boolean force) {
        Boolean was = queued.get(k);
        if (was == null) {
            queued.put(k, force);
            queue.add(k);
        } else if (force && !was) {
            queued.put(k, true);
        }
    }

    /** Takes the chunk's snapshot (main thread, counted against the tick's budget) and works its columns out on a worker. */
    private void snapshot(Chunk c) {
        int cx = c.getX(), cz = c.getZ();
        dirty.remove(key(cx, cz));
        budgetLeft(); // starts this tick's count
        long t0 = System.nanoTime();
        ChunkSnapshot s = c.getChunkSnapshot(true, natural != null, false, false);
        budgetUsed += System.nanoTime() - t0;
        snapshots++;
        backlog.incrementAndGet();
        try {
            workers.execute(() -> {
                try {
                    store.put(cx, cz, ChunkColors.columns(s, minY, maxY, natural));
                    tiles.chunkChanged(cx, cz);
                } catch (RuntimeException e) {
                    log.log(Level.WARNING, "[map] chunk " + cx + "," + cz + " not read", e);
                } finally {
                    backlog.decrementAndGet();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            backlog.decrementAndGet(); // shutting down
        }
    }

    private void requeueDirty() {
        if (dirty.isEmpty()) return;
        List<Long> gone = new ArrayList<>();
        for (long k : dirty) {
            if (world.isChunkLoaded(cx(k), cz(k))) enqueue(k, true);
            else gone.add(k);
        }
        dirty.removeAll(gone);
    }

    /** Reads the chunks within {@code r} of every player in the overworld again. */
    private void aroundPlayers(int r) {
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            int pcx = l.getBlockX() >> 4, pcz = l.getBlockZ() >> 4;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    if (inside(cx, cz) && world.isChunkLoaded(cx, cz)) enqueue(key(cx, cz), true);
                }
            }
        }
    }

    /**
     * Bakes the cartography (map.cartography, on by default) for the current spawn and square on a worker, then draws
     * every tile over it. Main thread; the bake reads only the loaded content (no chunk, block or biome) and takes about
     * a second off the main thread.
     */
    private void bake() {
        if (!plugin.getConfig().getBoolean("map.cartography", true)) {
            cartography = "унтраалттай (map.cartography: false)";
            return;
        }
        org.bukkit.Location sp = world.getSpawnLocation();
        bakedSpawnX = sp.getBlockX() + 0.5;
        bakedSpawnZ = sp.getBlockZ() + 0.5;
        MapGeometry g = tiles.geometry();
        mn.suld.api.map.Cartography.Layout layout = CartoContent.layout(mn.suld.plugin.content.Content.pack(), bakedSpawnX, bakedSpawnZ);
        cartography = "зурж байна…";
        // the bake's row bands run in parallel streams; in a task of this pool they stay in it (map.workers threads)
        // instead of taking every core of the common pool for about a second
        java.util.concurrent.ForkJoinPool bakePool = new java.util.concurrent.ForkJoinPool(Math.max(1, plugin.getConfig().getInt("map.workers", 2)),
                pool -> {
                    var t = java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    t.setName("suld-map-bake-" + t.getPoolIndex());
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }, null, false);
        CompletableFuture.supplyAsync(() -> mn.suld.api.map.Cartography.bake(layout, g), bakePool).whenComplete((b, err) -> {
            bakePool.shutdown();
            if (err != null) {
                cartography = "алдаа: " + err;
                log.log(Level.WARNING, "[map] the cartography was not baked; the map shows the real blocks only", err);
                return;
            }
            if (!g.equals(tiles.geometry())) return; // the border moved meanwhile: its own bake follows
            tiles.baked(b);
            cartography = String.format(Locale.ROOT, "%d ms, %.1f MB", b.bakeMillis(), b.bytes() / 1048576.0);
            log.info("[map] cartography baked in " + b.bakeMillis() + " ms (" + b.bytes() / 1024 + " KB)");
        });
    }

    /** A moved or resized border gives a new square (the stored columns stay; only the presence bits start over). */
    private void checkBorder() {
        org.bukkit.Location sp = world.getSpawnLocation();
        if (tiles.baked() != null && (sp.getBlockX() + 0.5 != bakedSpawnX || sp.getBlockZ() + 0.5 != bakedSpawnZ)) bake(); // the city moved the spawn
        MapGeometry g = borderGeometry();
        if (g.equals(tiles.geometry())) return;
        tiles.geometry(g);
        bounds(g);
        int side = Math.max(maxCx - minCx, maxCz - minCz) + 1, mx = minCx, mz = minCz;
        io.execute(() -> store.presenceSquare(mx, mz, side));
        log.info("[map] the border changed: the map is now " + g.width() + " x " + g.depth() + " blocks");
        bake();
    }

    // ------------------------------------------------------------------ live updates

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!ready || e.getWorld() != world) return;
        Chunk c = e.getChunk();
        int cx = c.getX(), cz = c.getZ();
        if (!inside(cx, cz)) return;
        long k = key(cx, cz);
        if (scan != null && scan.requested.contains(k)) return; // the scan reads it when its load completes
        if (!e.isNewChunk() && store.present(cx, cz)) return;
        if (budgetLeft()) {
            snapshot(c); // now, while it is surely loaded
            return;
        }
        // later: hold it loaded until then (a pre-generator or a teleport lets hundreds go within the tick)
        if (!held.contains(k) && queued.get(k) == null) {
            c.addPluginChunkTicket(plugin);
            held.add(k);
        }
        enqueue(k, e.isNewChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent e) {
        if (!ready || e.getWorld() != world) return;
        Chunk c = e.getChunk();
        if (dirty.contains(key(c.getX(), c.getZ()))) snapshot(c); // the last chance to see the change
    }

    /** A block changed: its chunk is read again, unless the change is deeper than the map ever looks. */
    private void touched(Block b) {
        if (!ready || b.getWorld() != world) return;
        int x = b.getX(), z = b.getZ(), cx = x >> 4, cz = z >> 4;
        if (!inside(cx, cz)) return;
        if (b.getY() < world.getHighestBlockYAt(x, z) - ChunkColors.LOOK_DOWN) return;
        dirty.add(key(cx, cz));
    }

    private void touched(List<Block> blocks) {
        for (Block b : blocks) touched(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        touched(e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        touched(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        touched(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityForm(EntityBlockFormEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        touched(e.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTree(StructureGrowEvent e) {
        for (BlockState s : e.getBlocks()) touched(s.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        for (BlockState s : e.getBlocks()) touched(s.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonOut(BlockPistonExtendEvent e) {
        touched(e.getBlock());
        touched(e.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonIn(BlockPistonRetractEvent e) {
        touched(e.getBlock());
        touched(e.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        touched(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent e) {
        for (BlockState s : e.getBlocks()) touched(s.getBlock());
    }

    // ------------------------------------------------------------------ the scan of the world on disk

    /** Starts the scan: every chunk the region files hold inside the border ({@code full}: even those on the map). */
    public boolean startScan(boolean full, CommandSender reportTo) {
        if (!ready || scan != null) return false;
        Scan s = new Scan(full, reportTo);
        scan = s;
        File regionDir = new File(world.getWorldFolder(), "region");
        int[] b = {minCx, minCz, maxCx, maxCz};
        MapGeometry g = tiles.geometry();
        double ccx = g.centreX() / 16, ccz = g.centreZ() / 16;
        CompletableFuture.supplyAsync(() -> listChunks(regionDir, b, ccx, ccz, full), workers).whenComplete((list, err) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (scan != s) return; // stopped meanwhile
                if (err != null) {
                    scan = null;
                    log.log(Level.WARNING, "[map] the region files were not read", err);
                    return;
                }
                s.chunks = list;
                log.info("[map] scan: " + list.length + " chunks on disk to read" + (full ? " (full)" : ""));
            });
        });
        return true;
    }

    public boolean stopScan() {
        if (scan == null) return false;
        lastScan = "зогсоосон, " + describeCounts(scan);
        scan = null;
        return true;
    }

    /** The chunks the region files of {@code dir} hold inside the chunk bounds {@code b}, nearest region first. */
    private long[] listChunks(File dir, int[] b, double ccx, double ccz, boolean full) {
        File[] files = dir.listFiles((d, n) -> REGION_FILE.matcher(n).matches());
        if (files == null) return new long[0];
        record RegionFile(int rx, int rz, File file) {
        }
        List<RegionFile> regions = new ArrayList<>();
        for (File f : files) {
            Matcher m = REGION_FILE.matcher(f.getName());
            if (!m.matches()) continue;
            int rx = Integer.parseInt(m.group(1)), rz = Integer.parseInt(m.group(2));
            // skip a region wholly outside the bounds
            if (rx * 32 + 31 < b[0] || rx * 32 > b[2] || rz * 32 + 31 < b[1] || rz * 32 > b[3]) continue;
            regions.add(new RegionFile(rx, rz, f));
        }
        regions.sort(Comparator.comparingDouble(r -> Math.hypot(r.rx() * 32 + 16 - ccx, r.rz() * 32 + 16 - ccz)));
        List<Long> out = new ArrayList<>();
        byte[] header = new byte[4096];
        for (RegionFile r : regions) {
            try (RandomAccessFile raf = new RandomAccessFile(r.file(), "r")) {
                if (raf.length() < 4096) continue;
                raf.readFully(header);
            } catch (IOException e) {
                log.warning("[map] " + r.file().getName() + " not read: " + e.getMessage());
                continue;
            }
            for (int i = 0; i < 1024; i++) {
                int o = i * 4;
                if ((header[o] | header[o + 1] | header[o + 2] | header[o + 3]) == 0) continue; // no such chunk
                int cx = r.rx() * 32 + (i & 31), cz = r.rz() * 32 + (i >> 5);
                if (cx < b[0] || cx > b[2] || cz < b[1] || cz > b[3]) continue;
                if (!full && (store.present(cx, cz) || store.unfinished(cx, cz))) continue;
                out.add(key(cx, cz));
            }
        }
        long[] a = new long[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    private void scanTick() {
        Scan s = scan;
        if (s.chunks == null) return;
        var c = plugin.getConfig();
        double mspt = recentMspt();
        int target = c.getInt("map.scan.target-mspt", 30), pauseAt = c.getInt("map.scan.pause-mspt", 45);
        int max = Math.max(1, c.getInt("map.scan.max-in-flight", 8));
        if (mspt > pauseAt) {
            s.allowance = 0;
            s.calm = 0;
        } else if (mspt > target) {
            s.allowance = Math.max(1, s.allowance / 2);
            s.calm = 0;
        } else if (mspt < target - 10) {
            if (++s.calm >= 10) { // ten calm ticks in a row: one more in flight
                s.allowance = Math.min(max, s.allowance + 1);
                s.calm = 0;
            }
        } else {
            s.allowance = Math.max(1, s.allowance);
            s.calm = 0;
        }
        int perTick = Math.max(1, c.getInt("map.scan.max-per-tick", 6));
        for (int sent = 0; sent < perTick && s.inFlight < s.allowance && s.next < s.chunks.length; ) {
            long k = s.chunks[s.next++];
            int cx = cx(k), cz = cz(k);
            if (!s.full && (store.present(cx, cz) || store.unfinished(cx, cz))) {
                s.skipped++;
                continue;
            }
            if (world.isChunkLoaded(cx, cz)) { // already loaded (a player is there): read it now
                snapshot(world.getChunkAt(cx, cz));
                s.loaded++;
                s.windowCount++;
                sent++;
                continue;
            }
            s.inFlight++;
            s.requested.add(k);
            sent++;
            world.getChunkAtAsync(cx, cz, false).whenComplete((chunk, err) -> {
                if (Bukkit.isPrimaryThread()) scanned(s, k, chunk, err);
                else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> scanned(s, k, chunk, err));
            });
        }
        long now = System.currentTimeMillis();
        if (now - s.windowStart >= 5000) {
            double r = s.windowCount * 1000.0 / (now - s.windowStart);
            s.rate = s.rate == 0 ? r : s.rate * 0.6 + r * 0.4;
            s.windowStart = now;
            s.windowCount = 0;
        }
        if (s.next >= s.chunks.length && s.inFlight == 0) finishScan(s);
    }

    private void scanned(Scan s, long k, Chunk chunk, Throwable err) {
        s.inFlight--;
        s.requested.remove(k);
        s.windowCount++;
        if (err != null) {
            s.failed++;
            if (s.failed <= 5) log.warning("[map] chunk " + cx(k) + "," + cz(k) + " not loaded: " + err.getMessage());
            return;
        }
        if (chunk == null) { // on disk but never finished generating: nothing to draw, and not asked again by later scans
            s.empty++;
            store.markUnfinished(cx(k), cz(k));
            return;
        }
        if (ready) snapshot(chunk);
        s.loaded++;
        world.unloadChunkRequest(cx(k), cz(k)); // let it go again unless a player (or anything else) holds it
    }

    private void finishScan(Scan s) {
        scan = null;
        long secs = (System.currentTimeMillis() - s.startedAt) / 1000;
        lastScan = "дууссан, " + describeCounts(s) + ", " + secs + " с";
        store.flush();
        log.info("[map] scan done: " + s.loaded + " read, " + s.empty + " never finished generating, " + s.failed + " failed, "
                + s.skipped + " already on the map, " + secs + " s");
        if (s.reportTo != null && (!(s.reportTo instanceof Player p) || p.isOnline())) {
            s.reportTo.sendMessage(Messages.success("Газрын зургийн скан " + lastScan + "."));
        }
    }

    private static String describeCounts(Scan s) {
        return s.loaded + " chunk уншсан, " + s.empty + " дутуу үүссэн, " + s.failed + " алдаа";
    }

    private double recentMspt() {
        long[] times = Bukkit.getServer().getTickTimes();
        if (times == null || times.length == 0) return Bukkit.getServer().getAverageTickTime();
        int now = Bukkit.getCurrentTick();
        long sum = 0;
        int n = Math.min(20, times.length);
        for (int k = 1; k <= n; k++) sum += times[Math.floorMod(now - k, times.length)];
        return sum / (double) n / 1_000_000.0;
    }

    // ------------------------------------------------------------------ staff: /suld map

    /** {@code /suld map status|scan [full|stop]|export <zoom> [x z r]|pois|flush|refresh [r]} (suld.admin, checked by the caller). */
    public void command(CommandSender sender, String[] args) {
        String sub = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status().forEach(l -> sender.sendMessage(Messages.info(l)));
            case "scan" -> {
                String what = args.length < 3 ? "" : args[2].toLowerCase(Locale.ROOT);
                if (what.equals("stop")) {
                    sender.sendMessage(stopScan() ? Messages.success("Скан зогслоо.") : Messages.error("Скан явагдаагүй байна."));
                } else if (startScan(what.equals("full"), sender)) {
                    sender.sendMessage(Messages.success("Скан эхэллээ" + (what.equals("full") ? " (бүгдийг дахин)" : "") + ": /suld map status"));
                } else {
                    sender.sendMessage(Messages.error(ready ? "Скан аль хэдийн явж байна." : "Газрын зураг бэлэн болоогүй байна."));
                }
            }
            case "export" -> export(sender, args);
            case "pois" -> {
                List<MapPois.Poi> list = pois.get();
                Path file = root.resolve("export").resolve("pois.json");
                String json = MapPois.json(list);
                Map<MapPois.Kind, Integer> counts = new java.util.EnumMap<>(MapPois.Kind.class);
                for (MapPois.Poi p : list) counts.merge(p.kind(), 1, Integer::sum);
                CompletableFuture.runAsync(() -> {
                    try {
                        java.nio.file.Files.createDirectories(file.getParent());
                        java.nio.file.Files.writeString(file, json, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }, io).whenComplete((v, err) -> reply(sender, err == null
                        ? Messages.success(list.size() + " газар " + counts + ": " + plugin.getDataFolder().toPath().relativize(file))
                        : Messages.error("Бичилт амжилтгүй: " + err.getMessage())));
            }
            case "flush" -> store.flush().whenComplete((v, err) -> reply(sender, err == null
                    ? Messages.success("Газрын зургийн өгөгдөл бичигдлээ.") : Messages.error("Бичилт амжилтгүй: " + err.getMessage())));
            case "refresh" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Messages.error("Тоглогч л ашиглана."));
                    return;
                }
                int r = args.length > 2 ? parse(args[2], 4) : 4;
                r = Math.max(0, Math.min(16, r));
                int before = queue.size();
                Location l = p.getLocation();
                int pcx = l.getBlockX() >> 4, pcz = l.getBlockZ() >> 4;
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        int cx = pcx + dx, cz = pcz + dz;
                        if (l.getWorld() == world && inside(cx, cz) && world.isChunkLoaded(cx, cz)) enqueue(key(cx, cz), true);
                    }
                }
                sender.sendMessage(Messages.success((queue.size() - before) + " chunk дахин уншигдана."));
            }
            default -> sender.sendMessage(Messages.info("/suld map status|scan [full|stop]|export <zoom 0-4> [x z радиус]|pois|flush|refresh [r]"));
        }
    }

    public List<String> status() {
        List<String> out = new ArrayList<>();
        MapGeometry g = tiles.geometry();
        int side = Math.max(maxCx - minCx, maxCz - minCz) + 1;
        long total = (long) side * side;
        int present = store.presentCount();
        out.add("Газрын зураг: x " + g.minX() + ".." + g.maxX() + ", z " + g.minZ() + ".." + g.maxZ() + " (" + g.width() + " x " + g.depth() + " блок)"
                + (ready ? "" : " · бэлтгэж байна"));
        out.add(String.format(Locale.ROOT, "Зурагт буй chunk: %d / %d (%.1f%%) · region файл %d, санах ойд %d", present, total,
                100.0 * present / Math.max(1, total), store.regionsOnDisk().size(), store.regionsInMemory()));
        out.add("Дараалал " + queue.size() + " (барьсан " + held.size() + ") · өөрчлөгдсөн " + dirty.size() + " · тооцоолж буй " + backlog.get()
                + " · tile " + tiles.cachedCount() + " · нийт snapshot " + snapshots);
        out.add("Суурь зураглал: " + cartography);
        Scan s = scan;
        if (s == null) {
            out.add("Скан: идэвхгүй (сүүлийнх: " + lastScan + ")");
        } else if (s.chunks == null) {
            out.add("Скан: region файлуудыг уншиж байна…");
        } else {
            int n = s.chunks.length;
            String eta = s.rate > 0.1 ? ", ~" + Math.max(1, Math.round((n - s.next) / s.rate / 60)) + " мин үлдсэн" : "";
            out.add(String.format(Locale.ROOT, "Скан: %d / %d (%.1f%%) · %s · зэрэг %d/%d · %.1f chunk/s%s · tick %.1f ms", s.next, n,
                    100.0 * s.next / Math.max(1, n), describeCounts(s), s.inFlight, s.allowance, s.rate, eta, recentMspt()));
        }
        return out;
    }

    private void export(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.info("/suld map export <zoom 0-4> [x z радиус] — PNG (plugins/SULD/map/export)"));
            return;
        }
        int zoom = parse(args[2], -1);
        if (zoom < 0 || zoom >= MapGeometry.ZOOMS) {
            sender.sendMessage(Messages.error("Zoom 0-4 (1, 2, 4, 8, 16 блок нэг цэгт)."));
            return;
        }
        MapGeometry g = tiles.geometry();
        int x0 = g.minX(), z0 = g.minZ(), x1 = g.maxX(), z1 = g.maxZ();
        if (args.length >= 6) {
            int x = parse(args[3], Integer.MIN_VALUE), z = parse(args[4], Integer.MIN_VALUE), r = parse(args[5], -1);
            if (x == Integer.MIN_VALUE || z == Integer.MIN_VALUE || r <= 0) {
                sender.sendMessage(Messages.error("x z радиус — бүхэл тоо."));
                return;
            }
            x0 = Math.max(g.minX(), x - r);
            z0 = Math.max(g.minZ(), z - r);
            x1 = Math.min(g.maxX(), x + r);
            z1 = Math.min(g.maxZ(), z + r);
        }
        long px = (long) ((x1 - x0) >> zoom) * ((z1 - z0) >> zoom);
        if (px > 32L * 1024 * 1024) {
            sender.sendMessage(Messages.error("Хэт том зураг (" + px / 1_000_000 + " сая цэг): zoom-оо өсгө эсвэл радиус өг."));
            return;
        }
        Path file = root.resolve("export").resolve("map-z" + zoom + "_" + x0 + "_" + z0 + "_" + x1 + "_" + z1 + ".png");
        sender.sendMessage(Messages.info("Зураг бэлтгэж байна: " + ((x1 - x0) >> zoom) + " x " + ((z1 - z0) >> zoom) + " цэг…"));
        long t0 = System.currentTimeMillis();
        tiles.export(zoom, x0, z0, x1, z1, file).whenComplete((f, err) -> reply(sender, err == null
                ? Messages.success("Бэлэн (" + (System.currentTimeMillis() - t0) / 1000 + " с): " + plugin.getDataFolder().toPath().relativize(f))
                : Messages.error("Зураг гарсангүй: " + err.getMessage())));
    }

    private void reply(CommandSender sender, net.kyori.adventure.text.Component msg) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(sender instanceof Player p) || p.isOnline()) sender.sendMessage(msg);
        });
    }

    private static int parse(String s, int fallback) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
