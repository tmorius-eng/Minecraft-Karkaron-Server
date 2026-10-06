package mn.suld.plugin.worldbuild;

import mn.suld.api.config.WorldSettings;
import mn.suld.api.worldbuild.BlockKinds;
import mn.suld.api.worldbuild.BlockPos;
import mn.suld.api.worldbuild.BlockSpec;
import mn.suld.api.worldbuild.Cell;
import mn.suld.api.worldbuild.CityCompiler;
import mn.suld.api.worldbuild.CitySpec;
import mn.suld.api.worldbuild.CityValidator;
import mn.suld.api.worldbuild.CompiledCity;
import mn.suld.api.worldbuild.Footprint;
import mn.suld.api.worldbuild.Issue;
import mn.suld.api.worldbuild.ModuleLibrary;
import mn.suld.api.worldbuild.Palette;
import mn.suld.api.worldbuild.Pass;
import mn.suld.api.worldbuild.SliceLoader;
import mn.suld.api.worldbuild.TerrainPlan;
import mn.suld.api.worldbuild.ValidationReport;
import mn.suld.api.worldbuild.WorldPoint;
import mn.suld.api.worldbuild.kharkhorum.Kharkhorum;
import mn.suld.api.worldbuild.schematic.SchematicLibrary;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Builds a city slice (Kharkhorum slice 1) into the real world, server-side and without any
 * world-editing plugin.
 * <ol>
 *   <li><b>Plan</b>: anchor the city origin on a chunk corner at the world spawn, sample the natural
 *       ground over the slice (+ feather band), save that height map, compile and validate the slice
 *       (pure {@link CityCompiler} / {@link CityValidator}). Errors stop the build.</li>
 *   <li><b>Build</b>: pass by pass (terrain first), chunk by chunk, under a per-tick time and block
 *       budget. Every changed position's original block is appended to a rollback log first.
 *       Progress (pass, chunk) is saved, so a restart resumes where it stopped; re-placing a chunk is
 *       idempotent.</li>
 *   <li><b>Finish</b>: set the world spawn on the plaza, validate the real blocks in-world (points
 *       walkable and reachable, compiled-vs-world diff) and record metrics.</li>
 * </ol>
 * Approved/locked builds are never rebuilt or overwritten without an explicit unlock.
 */
public final class WorldBuildService implements Listener, mn.suld.api.zone.CityZone {

    private static final String SLICE = "slice-1";
    private static final long TICK_BUDGET_NANOS = 25_000_000L;

    private final JavaPlugin plugin;
    private final WorldSettings settings;
    private final Path dir;
    private final Path stateFile, heightsFile, rollbackFile, reportFile;
    private final NamespacedKey visitedKey;

    private CitySpec spec;
    private int specVersion = 1;
    private ModuleLibrary library;
    private CompiledCity city;
    private BuildState state;
    private List<Unit> units = List.of();
    private BukkitTask task;
    private PrintWriter rollback;
    private final Set<Long> recorded = new HashSet<>();
    private final Map<String, BlockData> dataCache = new HashMap<>();
    private final Set<String> badBlocks = new LinkedHashSet<>();
    private int[] heights;   // natural ground per column of the sampled area (row-major), city-local y
    private Footprint area;  // sampled area (bounds + feather), x/z only

    /** One unit of placement work: one pass inside one chunk. */
    private record Unit(Pass pass, long chunk, List<BlockSpec> blocks, List<TerrainPlan.Column> columns) {
    }

    public WorldBuildService(JavaPlugin plugin, WorldSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.dir = plugin.getDataFolder().toPath().resolve("worldbuild");
        this.stateFile = dir.resolve(SLICE + ".json");
        this.heightsFile = dir.resolve(SLICE + ".heights.bin");
        this.rollbackFile = dir.resolve(SLICE + ".rollback.gz");
        this.reportFile = dir.resolve(SLICE + ".validation.txt");
        this.visitedKey = new NamespacedKey(plugin, "kharkhorum_arrived");
    }

    // ------------------------------------------------------------------ lifecycle

    /** Called on enable: resume an interrupted build, or plan a new one when auto-build is on. */
    public void start() {
        try {
            loadSpec();
        } catch (Exception e) {
            plugin.getLogger().severe("[worldbuild] cannot load the slice spec: " + e.getMessage());
            return;
        }
        if (Files.exists(stateFile)) {
            try {
                state = BuildState.load(stateFile);
            } catch (IOException e) {
                plugin.getLogger().severe("[worldbuild] unreadable state " + stateFile + ": " + e.getMessage());
                return;
            }
            boolean outdated = state.sliceVersion != specVersion;
            if (outdated && state.status != BuildState.Status.APPROVED && state.status != BuildState.Status.LOCKED
                    && state.status != BuildState.Status.ROLLED_BACK) {
                plugin.getLogger().info("[worldbuild] slice plan v" + state.sliceVersion + " → v" + specVersion
                        + ": rolling back the old build and rebuilding the new plan at the same anchor");
                int[] anchor = {state.anchorX, state.anchorY, state.anchorZ};
                Bukkit.getScheduler().runTaskLater(plugin, () -> rollback(null, () -> plan(null, anchor)), 40L);
                return;
            }
            if (outdated) {
                plugin.getLogger().warning("[worldbuild] slice plan changed (v" + state.sliceVersion + " → v" + specVersion
                        + ") but the build is " + state.status + "; not rebuilding automatically (/worldbuild unlock, rollback, build)");
            }
            switch (state.status) {
                case BUILDING -> Bukkit.getScheduler().runTaskLater(plugin, () -> resume(null), 40L);
                case BUILT, APPROVED, LOCKED -> {
                    Bukkit.getScheduler().runTask(plugin, this::applySpawn);
                    // Self-heal: anything that changed the city while unprotected (explosions, old builds)
                    // is put back from the plan. Idempotent; only differing blocks are written.
                    Bukkit.getScheduler().runTaskLater(plugin, () -> repair(null), 60L);
                }
                default -> { }
            }
            return;
        }
        if (settings.autoBuildKharkhorum()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> plan(null), 40L);
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        if (state != null && repairReturn != null) { // an unfinished repair re-runs on the next start anyway
            state.status = repairReturn;
            state.blocksPlaced = repairStart[0];
            state.blocksCleared = repairStart[1];
            repairReturn = null;
            saveState();
        }
        if (state != null && state.status == BuildState.Status.BUILDING) saveState();
        closeRollback();
    }

    private void loadSpec() throws IOException {
        String slice = resource("world/kharkhorum/slices/" + SLICE + ".json");
        Object v = mn.suld.api.json.Json.object(mn.suld.api.json.Json.parse(slice)).get("version");
        specVersion = v instanceof Number n ? n.intValue() : 1;
        String points = resource("world/kharkhorum/points.json");
        spec = SliceLoader.load(slice, points, Palette.KHARKHORUM);
        library = Kharkhorum.library();
        String catalogue = resourceOrNull("world/schematics/schematics.json");
        if (catalogue != null) {
            SchematicLibrary.register(library, SchematicLibrary.catalogue(catalogue),
                    f -> plugin.getResource("world/schematics/" + f));
        }
    }

    private String resource(String path) throws IOException {
        String s = resourceOrNull(path);
        if (s == null) throw new IOException("missing bundled resource " + path);
        return s;
    }

    private String resourceOrNull(String path) throws IOException {
        try (InputStream in = plugin.getResource(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ plan

    /** Plan and start a fresh build at the world spawn. */
    public void plan(Consumer<String> feedback) {
        plan(feedback, null);
    }

    /** @param anchor reuse this anchor {x, y, z} (rebuilding an upgraded plan in place), or null */
    public void plan(Consumer<String> feedback, int[] anchor) {
        Consumer<String> say = msg(feedback);
        if (state != null && (state.status == BuildState.Status.APPROVED || state.status == BuildState.Status.LOCKED)) {
            say.accept("Барилга баталгаажсан/түгжигдсэн тул дахин барихгүй (/worldbuild unlock).");
            return;
        }
        if (task != null) {
            say.accept("Барилга аль хэдийн явагдаж байна.");
            return;
        }
        World world = Bukkit.getWorlds().get(0);
        Location sp = world.getSpawnLocation();
        int ax = anchor != null ? anchor[0] : Math.floorDiv(sp.getBlockX(), 16) * 16;
        int az = anchor != null ? anchor[2] : Math.floorDiv(sp.getBlockZ(), 16) * 16;
        Footprint b = spec.bounds();
        int f = spec.terrain().feather() + 1;
        area = new Footprint(b.minX() - f, 0, b.minZ() - f, b.maxX() + f, 0, b.maxZ() + f);
        say.accept("Газрын өндрийг хэмжиж байна (" + ((area.maxX() - area.minX()) / 16 + 1) * ((area.maxZ() - area.minZ()) / 16 + 1) + " chunk)…");
        loadChunks(world, ax, az).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            int[] raw = sampleHeights(world, ax, az);
            int anchorY = anchor != null ? anchor[1] : medianPlaza(raw);
            heights = new int[raw.length];
            for (int i = 0; i < raw.length; i++) heights[i] = raw[i] - anchorY;
            state = new BuildState();
            state.slice = SLICE;
            state.world = world.getName();
            state.anchorX = ax;
            state.anchorY = anchorY;
            state.anchorZ = az;
            state.originalSpawn = new int[]{sp.getBlockX(), sp.getBlockY(), sp.getBlockZ()};
            state.sliceVersion = specVersion;
            try {
                Files.createDirectories(dir);
                writeHeights();
            } catch (IOException e) {
                say.accept("Өндрийн зураг хадгалж чадсангүй: " + e.getMessage());
                return;
            }
            say.accept("Анхны цэг " + ax + " " + anchorY + " " + az + ". Хотыг эмхэтгэж байна…");
            compileAsync(say, true);
        }));
    }

    private CompletableFuture<Void> loadChunks(World world, int ax, int az) {
        List<CompletableFuture<Chunk>> all = new ArrayList<>();
        for (int cx = Math.floorDiv(ax + area.minX(), 16); cx <= Math.floorDiv(ax + area.maxX(), 16); cx++)
            for (int cz = Math.floorDiv(az + area.minZ(), 16); cz <= Math.floorDiv(az + area.maxZ(), 16); cz++)
                all.add(world.getChunkAtAsync(cx, cz, true));
        return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new));
    }

    private int idx(int lx, int lz) {
        return (lz - area.minZ()) * (area.maxX() - area.minX() + 1) + (lx - area.minX());
    }

    /** Natural ground: the top non-plant, non-tree, non-fluid-surface block (world y). */
    private int[] sampleHeights(World world, int ax, int az) {
        int w = area.maxX() - area.minX() + 1, l = area.maxZ() - area.minZ() + 1;
        int[] out = new int[w * l];
        for (int lz = area.minZ(); lz <= area.maxZ(); lz++)
            for (int lx = area.minX(); lx <= area.maxX(); lx++) {
                int x = ax + lx, z = az + lz;
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                while (y > world.getMinHeight()) {
                    Material m = world.getBlockAt(x, y, z).getType();
                    String n = m.getKey().getKey();
                    if (m.isAir() || n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_leaves") || !m.isSolid()
                            || n.contains("mushroom_block") || n.equals("bamboo") || n.equals("cactus")) {
                        y--;
                        continue;
                    }
                    break;
                }
                out[idx(lx, lz)] = y;
            }
        return out;
    }

    private int medianPlaza(int[] raw) {
        List<Integer> v = new ArrayList<>();
        for (int lz = -30; lz <= 30; lz++)
            for (int lx = -30; lx <= 30; lx++)
                if (lx * lx + lz * lz <= 900) v.add(raw[idx(lx, lz)]);
        v.sort(Integer::compare);
        return v.get(v.size() / 2);
    }

    private int natural(int lx, int lz) {
        if (lx < area.minX() || lx > area.maxX() || lz < area.minZ() || lz > area.maxZ()) return 0;
        return heights[idx(lx, lz)];
    }

    private void writeHeights() throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(heightsFile)))) {
            out.writeInt(area.minX());
            out.writeInt(area.minZ());
            out.writeInt(area.maxX());
            out.writeInt(area.maxZ());
            out.writeInt(heights.length);
            for (int h : heights) out.writeInt(h);
        }
    }

    private void readHeights() throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(heightsFile)))) {
            int x1 = in.readInt(), z1 = in.readInt(), x2 = in.readInt(), z2 = in.readInt();
            area = new Footprint(x1, 0, z1, x2, 0, z2);
            heights = new int[in.readInt()];
            for (int i = 0; i < heights.length; i++) heights[i] = in.readInt();
        }
    }

    private void compileAsync(Consumer<String> say, boolean fresh) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long t0 = System.nanoTime();
            CompiledCity compiled = CityCompiler.compile(spec, library, this::natural);
            ValidationReport report = CityValidator.validate(compiled);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            Bukkit.getScheduler().runTask(plugin, () -> {
                city = compiled;
                writeReport("compile-time validation (" + ms + " ms)", report);
                plugin.getLogger().info("[worldbuild] compiled " + compiled.blockCount() + " blocks, " + compiled.chunks().size()
                        + " chunks, fingerprint " + compiled.fingerprint() + " — " + (report.passed() ? "PASS" : "FAIL"));
                if (!report.passed()) {
                    state.status = BuildState.Status.FAILED;
                    state.note = "compile-time validation failed: " + report.errors() + " error(s)";
                    saveState();
                    report.issues().stream().filter(i -> i.severity() == Issue.Severity.ERROR).limit(8)
                            .forEach(i -> say.accept(i.toString()));
                    say.accept("Шалгалт амжилтгүй — барихгүй. Дэлгэрэнгүй: " + reportFile);
                    return;
                }
                if (!fresh && !compiled.fingerprint().equals(state.fingerprint)) {
                    say.accept("Анхааруулга: хотын төлөвлөгөө өөрчлөгдсөн (" + state.fingerprint + " → " + compiled.fingerprint()
                            + "). Шинэ төлөвлөгөөгөөр үргэлжлүүлнэ.");
                }
                state.fingerprint = compiled.fingerprint();
                if (fresh) {
                    state.status = BuildState.Status.BUILDING;
                    state.startedAt = System.currentTimeMillis();
                    state.passIndex = 0;
                    state.chunkIndex = 0;
                }
                saveState();
                units = units(compiled);
                say.accept("Барилга эхэлж байна: " + compiled.blockCount() + " блок, " + units.size() + " ажил.");
                openRollback();
                runJob(say);
            });
        });
    }

    // ------------------------------------------------------------------ build

    private List<Unit> units(CompiledCity c) {
        Map<Pass, Map<Long, List<BlockSpec>>> plan = c.plan();
        Map<Long, List<TerrainPlan.Column>> cols = c.columns().byChunk();
        List<Unit> out = new ArrayList<>();
        for (Pass p : Pass.values()) {
            Map<Long, List<BlockSpec>> byChunk = plan.getOrDefault(p, Map.of());
            TreeSet<Long> keys = new TreeSet<>(CompiledCity.CHUNK_ORDER);
            keys.addAll(byChunk.keySet());
            if (p == Pass.TERRAIN) keys.addAll(cols.keySet());
            for (long k : keys) {
                out.add(new Unit(p, k, byChunk.getOrDefault(k, List.of()), p == Pass.TERRAIN ? cols.getOrDefault(k, List.of()) : List.of()));
            }
        }
        return out;
    }

    /** Resume an interrupted build (after a restart or pause). */
    public void resume(Consumer<String> feedback) {
        Consumer<String> say = msg(feedback);
        if (state == null || (state.status != BuildState.Status.BUILDING && state.status != BuildState.Status.PAUSED)) {
            say.accept("Үргэлжлүүлэх барилга алга.");
            return;
        }
        if (task != null) return;
        try {
            readHeights();
            loadRecorded();
        } catch (IOException e) {
            say.accept("Хадгалсан төлөв уншиж чадсангүй: " + e.getMessage());
            return;
        }
        state.status = BuildState.Status.BUILDING;
        say.accept("Барилгыг үргэлжлүүлж байна (өмнө нь " + state.chunkIndex + " ажил хийгдсэн)…");
        // After a crash the world on disk can be older than the saved cursor (unsaved chunks are lost),
        // so a resume replays the whole plan; placement is idempotent and skips blocks already in place.
        state.chunkIndex = 0;
        compileAsync(say, false);
    }

    /** Status to return to after a repair pass (null when no repair is running). */
    private BuildState.Status repairReturn;

    /**
     * Put the built city back exactly as planned: replays the plan against the saved height map and
     * rewrites only blocks that differ (creeper holes, fire, anything changed). The status (BUILT,
     * APPROVED, LOCKED) is kept. Original terrain stays in the rollback log (only first writes are logged).
     */
    public void repair(Consumer<String> feedback) {
        Consumer<String> say = msg(feedback);
        if (!isBuilt()) {
            say.accept("Засах хот алга (баригдаагүй).");
            return;
        }
        if (task != null || repairReturn != null) {
            say.accept("Барилга/засвар аль хэдийн явагдаж байна.");
            return;
        }
        try {
            readHeights();
            loadRecorded();
        } catch (IOException e) {
            say.accept("Хадгалсан төлөв уншиж чадсангүй: " + e.getMessage());
            return;
        }
        repairReturn = state.status;
        long placedBefore = state.blocksPlaced, clearedBefore = state.blocksCleared;
        state.status = BuildState.Status.BUILDING;
        state.chunkIndex = 0;
        repairStart = new long[]{placedBefore, clearedBefore};
        say.accept("Хархорумыг төлөвлөгөөний дагуу засаж байна…");
        compileAsync(say, false);
    }

    private long[] repairStart;

    public void pause(Consumer<String> feedback) {
        if (task == null) {
            msg(feedback).accept("Явагдаж буй барилга алга.");
            return;
        }
        task.cancel();
        task = null;
        state.status = BuildState.Status.PAUSED;
        saveState();
        closeRollback();
        msg(feedback).accept("Түр зогсоолоо. /worldbuild resume");
    }

    private int unitIndex() {
        // passIndex/chunkIndex are stored as one running unit index (chunkIndex) for simplicity
        return state.chunkIndex;
    }

    private void runJob(Consumer<String> say) {
        World world = Bukkit.getWorld(state.world);
        if (world == null) {
            say.accept("Ертөнц " + state.world + " олдсонгүй.");
            return;
        }
        Set<Long> touched = new HashSet<>();
        long[] lastReport = {System.currentTimeMillis()};
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long t0 = System.nanoTime();
            int placed = 0;
            double mspt = Bukkit.getServer().getAverageTickTime();
            state.worstMspt = Math.max(state.worstMspt, mspt);
            state.mstpSum += mspt;
            state.msptSamples++;
            while (unitIndex() < units.size() && System.nanoTime() - t0 < TICK_BUDGET_NANOS && placed < settings.buildBlocksPerTick()) {
                Unit u = units.get(unitIndex());
                int cx = CompiledCity.chunkX(u.chunk()), cz = CompiledCity.chunkZ(u.chunk());
                int wcx = Math.floorDiv(state.anchorX, 16) + cx, wcz = Math.floorDiv(state.anchorZ, 16) + cz;
                world.getChunkAt(wcx, wcz); // loads synchronously if needed
                if (touched.add(u.chunk())) state.chunksTouched = Math.max(state.chunksTouched, touched.size());
                placed += place(world, u);
                flushRollback(); // the log must be ahead of the world: flush every unit
                state.chunkIndex++;
                state.passIndex = u.pass().number;
                if (state.chunkIndex % 4 == 0) saveState();
            }
            if (System.currentTimeMillis() - lastReport[0] > 10_000) {
                lastReport[0] = System.currentTimeMillis();
                plugin.getLogger().info(String.format("[worldbuild] %d/%d units (pass %d), %d blocks placed, %d cleared, MSPT %.1f",
                        state.chunkIndex, units.size(), state.passIndex, state.blocksPlaced, state.blocksCleared, mspt));
            }
            if (unitIndex() >= units.size()) finish(say);
        }, 1L, 1L);
    }

    /** Place one unit; returns the number of block writes. */
    private int place(World world, Unit u) {
        int n = 0;
        int ax = state.anchorX, ay = state.anchorY, az = state.anchorZ;
        if (u.pass() == Pass.TERRAIN) {
            for (TerrainPlan.Column col : u.columns()) {
                int top = Math.max(col.clearTo(), col.inside() ? col.natural() + 12 : col.natural());
                for (int y = col.top() + 1; y <= top; y++) {
                    if (city.cell(col.x(), y, col.z()) != null) continue;
                    Block b = world.getBlockAt(ax + col.x(), ay + y, az + col.z());
                    if (b.getType().isAir()) continue;
                    record(b);
                    b.setType(Material.AIR, false);
                    state.blocksCleared++;
                    n++;
                }
            }
        }
        for (BlockSpec s : u.blocks()) {
            BlockData data = data(s.block());
            if (data == null) {
                state.blocksSkipped++;
                continue;
            }
            Block b = world.getBlockAt(ax + s.x(), ay + s.y(), az + s.z());
            if (b.getBlockData().matches(data)) continue;
            record(b);
            b.setBlockData(data, false);
            state.blocksPlaced++;
            n++;
        }
        return n;
    }

    private BlockData data(String block) {
        if (dataCache.containsKey(block)) return dataCache.get(block);
        BlockData d = null;
        for (String candidate : candidates(block)) {
            try {
                d = Bukkit.createBlockData(candidate);
                break;
            } catch (IllegalArgumentException ignored) {
                // try the next spelling
            }
        }
        if (d == null && badBlocks.add(block) && badBlocks.size() <= 30) {
            plugin.getLogger().warning("[worldbuild] unknown block data, skipped: " + block);
        }
        dataCache.put(block, d);
        return d;
    }

    /** Known renames between versions, then the bare id as a last resort. */
    private static List<String> candidates(String block) {
        List<String> out = new ArrayList<>();
        out.add(block);
        String id = mn.suld.api.worldbuild.BlockStates.id(block);
        String rest = block.substring(id.length());
        Map<String, String> renames = Map.of(
                "minecraft:chain", "minecraft:iron_chain",
                "minecraft:grass", "minecraft:short_grass",
                "minecraft:grass_path", "minecraft:dirt_path");
        if (renames.containsKey(id)) out.add(renames.get(id) + rest);
        out.add(id);
        if (renames.containsKey(id)) out.add(renames.get(id));
        return out;
    }

    private void finish(Consumer<String> say) {
        task.cancel();
        task = null;
        closeRollback();
        if (repairReturn != null) {
            state.status = repairReturn;
            repairReturn = null;
            long fixed = state.blocksPlaced - repairStart[0], cleared = state.blocksCleared - repairStart[1];
            // keep the original build's totals: a repair is not a build
            state.blocksPlaced = repairStart[0];
            state.blocksCleared = repairStart[1];
            saveState();
            String m = "Хархорум засагдлаа: " + fixed + " блок сэргээж, " + cleared + " илүү блок цэвэрлэв.";
            plugin.getLogger().info("[worldbuild] repair: " + fixed + " blocks restored, " + cleared + " cleared");
            say.accept(m);
            return;
        }
        state.status = BuildState.Status.BUILT;
        state.finishedAt = System.currentTimeMillis();
        state.buildMillis = state.finishedAt - state.startedAt;
        saveState();
        applySpawn();
        String m = String.format("Хархорум (slice 1) баригдлаа: %d блок тавьж, %d цэвэрлэж, %d алгассан; %d chunk; %.1f с; хамгийн муу MSPT %.1f.",
                state.blocksPlaced, state.blocksCleared, state.blocksSkipped, state.chunksTouched, state.buildMillis / 1000.0, state.worstMspt);
        say.accept(m);
        validateInWorld(null);
    }

    // ------------------------------------------------------------------ rollback log

    /** Rollback log parts: one gzip file per build run, so a crash can only tear the tail of its own part. */
    private List<Path> rollbackParts() {
        List<Path> parts = new ArrayList<>();
        if (Files.exists(rollbackFile)) parts.add(rollbackFile); // legacy single-file log
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().matches(java.util.regex.Pattern.quote(SLICE) + "\\.rollback\\.\\d+\\.gz"))
                    .sorted().forEach(parts::add);
        } catch (IOException ignored) {
            // no directory yet
        }
        return parts;
    }

    private void openRollback() {
        try {
            Files.createDirectories(dir);
            Path part = dir.resolve(String.format("%s.rollback.%04d.gz", SLICE, rollbackParts().size() + 1));
            rollback = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(part, StandardOpenOption.CREATE_NEW), true),
                    StandardCharsets.UTF_8)));
        } catch (IOException e) {
            plugin.getLogger().severe("[worldbuild] cannot open rollback log: " + e.getMessage());
        }
    }

    /** Every recorded original block, oldest first; a torn part keeps everything before the tear. */
    private List<String[]> readRollback() {
        List<String[]> lines = new ArrayList<>();
        for (Path part : rollbackParts()) {
            int before = lines.size();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(part)), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split(" ", 4);
                    if (p.length == 4) lines.add(p);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[worldbuild] rollback part " + part.getFileName() + " is torn after "
                        + (lines.size() - before) + " entries (" + e.getMessage() + "); using what was recovered");
                if (!lines.isEmpty() && lines.get(lines.size() - 1).length < 4) lines.remove(lines.size() - 1);
            }
        }
        return lines;
    }

    private void record(Block b) {
        long k = BlockPos.pack(b.getX() - state.anchorX, b.getY() - state.anchorY, b.getZ() - state.anchorZ);
        if (!recorded.add(k) || rollback == null) return;
        rollback.println(b.getX() + " " + b.getY() + " " + b.getZ() + " " + b.getBlockData().getAsString());
    }

    private void flushRollback() {
        if (rollback != null) rollback.flush();
    }

    private void closeRollback() {
        if (rollback != null) {
            rollback.close();
            rollback = null;
        }
    }

    private void loadRecorded() throws IOException {
        recorded.clear();
        for (String[] p : readRollback()) {
            try {
                recorded.add(BlockPos.pack(Integer.parseInt(p[0]) - state.anchorX, Integer.parseInt(p[1]) - state.anchorY,
                        Integer.parseInt(p[2]) - state.anchorZ));
            } catch (NumberFormatException ignored) {
                // torn line
            }
        }
    }

    /** Restore every recorded original block (reverse order). Refused for approved/locked builds. */
    public void rollback(Consumer<String> feedback) {
        rollback(feedback, null);
    }

    /** Restore every recorded original block; then run {@code after} (e.g. rebuild an upgraded plan). */
    public void rollback(Consumer<String> feedback, Runnable after) {
        Consumer<String> say = msg(feedback);
        if (state != null && rollbackParts().isEmpty() && after != null
                && state.status != BuildState.Status.APPROVED && state.status != BuildState.Status.LOCKED) {
            // nothing was placed (e.g. a refused plan): just forget the old state and continue
            try {
                Files.deleteIfExists(stateFile);
            } catch (IOException e) {
                plugin.getLogger().warning("[worldbuild] " + e.getMessage());
            }
            state = null;
            after.run();
            return;
        }
        if (state == null || rollbackParts().isEmpty()) {
            say.accept("Буцаах барилга алга.");
            return;
        }
        if (state.status == BuildState.Status.APPROVED || state.status == BuildState.Status.LOCKED) {
            say.accept("Баталгаажсан/түгжигдсэн барилгыг буцаахгүй (/worldbuild unlock).");
            return;
        }
        if (task != null) pause(feedback);
        closeRollback();
        List<String[]> lines = readRollback();
        World world = Bukkit.getWorld(state.world);
        int[] i = {lines.size() - 1};
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long t0 = System.nanoTime();
            while (i[0] >= 0 && System.nanoTime() - t0 < TICK_BUDGET_NANOS) {
                String[] p = lines.get(i[0]--);
                try {
                    world.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]))
                            .setBlockData(Bukkit.createBlockData(p[3]), false);
                } catch (IllegalArgumentException torn) {
                    // a line cut by a crash: skip it
                }
            }
            if (i[0] < 0) {
                task.cancel();
                task = null;
                state.status = BuildState.Status.ROLLED_BACK;
                if (state.originalSpawn != null) {
                    world.setSpawnLocation(state.originalSpawn[0], state.originalSpawn[1], state.originalSpawn[2]);
                }
                saveState();
                try {
                    Path applied = dir.resolve("applied-" + System.currentTimeMillis());
                    Files.createDirectories(applied);
                    for (Path part : rollbackParts()) Files.move(part, applied.resolve(part.getFileName()));
                    Files.deleteIfExists(stateFile);
                } catch (IOException e) {
                    plugin.getLogger().warning("[worldbuild] " + e.getMessage());
                }
                recorded.clear();
                state = null;
                say.accept("Буцаалт дууслаа: " + lines.size() + " блок сэргээв.");
                if (after != null) after.run();
            }
        }, 1L, 1L);
    }

    // ------------------------------------------------------------------ review status

    public void setStatus(BuildState.Status s, Consumer<String> feedback) {
        Consumer<String> say = msg(feedback);
        if (state == null) {
            say.accept("Барилга алга.");
            return;
        }
        boolean ok = switch (s) {
            case APPROVED -> state.status == BuildState.Status.BUILT;
            case LOCKED -> state.status == BuildState.Status.APPROVED;
            case BUILT -> state.status == BuildState.Status.LOCKED || state.status == BuildState.Status.APPROVED; // unlock
            default -> false;
        };
        if (!ok) {
            say.accept("Төлөв " + state.status + " → " + s + " шилжих боломжгүй.");
            return;
        }
        state.status = s;
        saveState();
        say.accept("Төлөв: " + s);
    }

    // ------------------------------------------------------------------ city zone

    private final Set<java.util.UUID> editors = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** True once a city stands in the world (built, approved or locked). */
    public boolean isBuilt() {
        return state != null && (state.status == BuildState.Status.BUILT || state.status == BuildState.Status.APPROVED
                || state.status == BuildState.Status.LOCKED);
    }

    @Override
    public boolean contains(String world, int x, int z) {
        return near(world, x, z, 0);
    }

    @Override
    public boolean near(String world, int x, int z, int margin) {
        BuildState s = state;
        if (s == null || spec == null || !isBuilt() || !s.world.equals(world)) return false;
        Footprint b = spec.bounds();
        int lx = x - s.anchorX, lz = z - s.anchorZ;
        return lx >= b.minX() - margin && lx <= b.maxX() + margin && lz >= b.minZ() - margin && lz <= b.maxZ() + margin;
    }

    /** Staff build mode: lets a {@code suld.admin.world} player change the protected city. */
    public boolean toggleEditor(java.util.UUID player) {
        if (!editors.remove(player)) {
            editors.add(player);
            return true;
        }
        return false;
    }

    public boolean isEditor(java.util.UUID player) {
        return editors.contains(player);
    }

    /** World location of a gameplay point of the built slice (null if unknown or not built). */
    public Location pointLocation(String id) {
        return isBuilt() ? point(id) : null;
    }

    /** All gameplay points of the built slice. */
    public List<WorldPoint> slicePoints() {
        return spec == null ? List.of() : spec.points().stream().filter(p -> "slice.1".equals(p.slice())).toList();
    }

    // ------------------------------------------------------------------ spawn

    private Location point(String id) {
        if (state == null) return null;
        World world = Bukkit.getWorld(state.world);
        for (WorldPoint p : spec.points()) {
            if (p.id().equals(id)) {
                return new Location(world, state.anchorX + p.x() + 0.5, state.anchorY + p.y(), state.anchorZ + p.z() + 0.5, p.yaw(), 0);
            }
        }
        return null;
    }

    @SuppressWarnings("removal")
    private void applySpawn() {
        Location spawn = point("spawn");
        if (spawn == null) return;
        World world = spawn.getWorld();
        world.setSpawnLocation(spawn);
        try {
            world.setGameRule(GameRule.SPAWN_RADIUS, 0); // renamed/data-driven in newer versions; best effort
        } catch (RuntimeException ignored) {
            // spawn radius stays at the server default
        }
    }

    /** First time a player joins after Kharkhorum is built, bring them to the plaza once. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (state == null || (state.status != BuildState.Status.BUILT && state.status != BuildState.Status.APPROVED
                && state.status != BuildState.Status.LOCKED)) return;
        Player p = e.getPlayer();
        if (p.getPersistentDataContainer().has(visitedKey, PersistentDataType.BYTE)) return;
        Location spawn = point("spawn");
        if (spawn == null) return;
        p.getPersistentDataContainer().set(visitedKey, PersistentDataType.BYTE, (byte) 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> p.teleport(spawn), 10L);
    }

    public boolean teleport(Player p, String id) {
        Location l = point(id);
        if (l == null) return false;
        p.teleport(l);
        return true;
    }

    public List<String> pointIds() {
        return spec == null ? List.of() : spec.points().stream().filter(p -> "slice.1".equals(p.slice())).map(WorldPoint::id).toList();
    }

    // ------------------------------------------------------------------ in-world validation and dump

    private Map<Long, ChunkSnapshot> snapshots(World world, int margin) {
        Map<Long, ChunkSnapshot> snaps = new HashMap<>();
        Footprint b = spec.bounds();
        for (int cx = Math.floorDiv(state.anchorX + b.minX() - margin, 16); cx <= Math.floorDiv(state.anchorX + b.maxX() + margin, 16); cx++)
            for (int cz = Math.floorDiv(state.anchorZ + b.minZ() - margin, 16); cz <= Math.floorDiv(state.anchorZ + b.maxZ() + margin, 16); cz++)
                snaps.put(CompiledCity.chunkKey(cx, cz), world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
        return snaps;
    }

    private static String at(Map<Long, ChunkSnapshot> snaps, World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return "minecraft:air";
        ChunkSnapshot s = snaps.get(CompiledCity.chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        if (s == null) return "minecraft:stone";
        return s.getBlockData(Math.floorMod(x, 16), y, Math.floorMod(z, 16)).getAsString();
    }

    /** Validate the real blocks: points walkable + reachable on foot, and compiled-vs-world diff. */
    public void validateInWorld(Consumer<String> feedback) {
        Consumer<String> say = msg(feedback);
        if (state == null || city == null) {
            say.accept("Шалгах барилга алга (эхлээд /worldbuild resume эсвэл build).");
            return;
        }
        World world = Bukkit.getWorld(state.world);
        Map<Long, ChunkSnapshot> snaps = snapshots(world, CityValidator.WALK_MARGIN + 2);
        int ax = state.anchorX, ay = state.anchorY, az = state.anchorZ;
        Map<String, String> normalized = new HashMap<>();
        dataCache.forEach((k, v) -> { if (v != null) normalized.put(k, v.getAsString()); });
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long t0 = System.nanoTime();
            List<Issue> issues = new ArrayList<>();
            CityValidator.PointCheck pc = CityValidator.points((x, y, z) -> at(snaps, world, ax + x, ay + y, az + z),
                    spec.points(), spec.slice(), spec.bounds(), issues);
            long mismatched = 0, checked = 0;
            List<String> examples = new ArrayList<>();
            for (Map.Entry<Long, Cell> e : city.cells().entrySet()) {
                long p = e.getKey();
                String want = normalized.getOrDefault(e.getValue().block(), e.getValue().block());
                String got = at(snaps, world, ax + BlockPos.x(p), ay + BlockPos.y(p), az + BlockPos.z(p));
                checked++;
                boolean same = got.equals(want) || BlockKinds.isAir(want) && BlockKinds.isAir(got)
                        || BlockKinds.connectionFamily(want) != null && mn.suld.api.worldbuild.BlockStates.id(got).equals(mn.suld.api.worldbuild.BlockStates.id(want));
                if (!same) {
                    mismatched++;
                    if (examples.size() < 10) examples.add(BlockPos.x(p) + " " + BlockPos.y(p) + " " + BlockPos.z(p) + " want " + want + " got " + got);
                }
            }
            if (mismatched > 0) {
                boolean serious = mismatched > Math.max(50, checked / 1000);
                issues.add((serious ? Issue.Severity.ERROR : Issue.Severity.WARNING) == Issue.Severity.ERROR
                        ? Issue.error("WORLD_DIFF", mismatched + " of " + checked + " compiled blocks differ in the world; e.g. "
                        + String.join(" | ", examples.subList(0, Math.min(3, examples.size()))), 0, 0, 0)
                        : Issue.warning("WORLD_DIFF", mismatched + " of " + checked + " compiled blocks differ in the world; e.g. "
                        + String.join(" | ", examples.subList(0, Math.min(3, examples.size()))), 0, 0, 0));
            }
            Map<String, Object> stats = new java.util.LinkedHashMap<>(pc.stats());
            stats.put("compiled_blocks_checked", checked);
            stats.put("world_mismatches", mismatched);
            stats.put("validate_ms", (System.nanoTime() - t0) / 1_000_000);
            ValidationReport rep = new ValidationReport(issues, stats);
            Bukkit.getScheduler().runTask(plugin, () -> {
                writeReport("in-world validation", rep);
                for (String line : rep.summary().split("\n")) {
                    if (!line.isBlank()) say.accept(line);
                }
            });
        });
    }

    /** Dump the real blocks of the slice to the renderer's text format (city-local coordinates). */
    public void dump(Consumer<String> feedback) {
        Consumer<String> say = msg(feedback);
        if (state == null) {
            say.accept("Барилга алга.");
            return;
        }
        World world = Bukkit.getWorld(state.world);
        Map<Long, ChunkSnapshot> snaps = snapshots(world, spec.terrain().feather());
        Footprint b = spec.bounds();
        int ax = state.anchorX, ay = state.anchorY, az = state.anchorZ, f = spec.terrain().feather();
        Path out = dir.resolve(SLICE + ".world-dump.txt");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long n = 0;
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
                w.println("# world dump " + SLICE + " anchor " + ax + " " + ay + " " + az + " (city-local coordinates)");
                for (int x = b.minX() - f; x <= b.maxX() + f; x++)
                    for (int z = b.minZ() - f; z <= b.maxZ() + f; z++)
                        for (int y = -6; y <= 48; y++) {
                            String s = at(snaps, world, ax + x, ay + y, az + z);
                            if (BlockKinds.isAir(s)) continue;
                            // only the top few layers of natural ground: keeps the dump small
                            if (y < 0 && !BlockKinds.isAir(at(snaps, world, ax + x, ay + y + 1, az + z))
                                    && !BlockKinds.isAir(at(snaps, world, ax + x, ay + y + 2, az + z)) && y < -2) continue;
                            w.println(x + " " + y + " " + z + " " + s);
                            n++;
                        }
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> say.accept("Dump амжилтгүй: " + e.getMessage()));
                return;
            }
            long count = n;
            Bukkit.getScheduler().runTask(plugin, () -> say.accept("Dump: " + count + " блок → " + out));
        });
    }

    public List<String> status() {
        List<String> out = new ArrayList<>();
        if (state == null) {
            out.add("Барилга төлөвлөөгүй. /worldbuild build");
            return out;
        }
        out.add("Slice: " + state.slice + "  төлөв: " + state.status + (task != null ? " (ажиллаж байна)" : ""));
        out.add("Анхны цэг: " + state.world + " " + state.anchorX + " " + state.anchorY + " " + state.anchorZ);
        out.add("Явц: " + state.chunkIndex + "/" + (units.isEmpty() ? "?" : units.size()) + " ажил, pass " + state.passIndex);
        out.add("Блок: " + state.blocksPlaced + " тавьсан, " + state.blocksCleared + " цэвэрлэсэн, " + state.blocksSkipped + " алгассан; chunk " + state.chunksTouched);
        out.add(String.format("MSPT: хамгийн муу %.1f, дундаж %.1f; хугацаа %.1f с", state.worstMspt,
                state.msptSamples == 0 ? 0.0 : state.mstpSum / state.msptSamples, state.buildMillis / 1000.0));
        out.add("Fingerprint: " + state.fingerprint + (state.note.isBlank() ? "" : "  тэмдэглэл: " + state.note));
        if (!badBlocks.isEmpty()) out.add("Танигдаагүй блок: " + badBlocks.size() + " (лог харна уу)");
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private void saveState() {
        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[worldbuild] cannot save state: " + e.getMessage());
        }
    }

    private void writeReport(String title, ValidationReport rep) {
        try {
            Files.createDirectories(dir);
            Files.writeString(reportFile, "# " + title + " — " + java.time.Instant.now() + "\n" + rep.summary() + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("[worldbuild] cannot write report: " + e.getMessage());
        }
    }

    private Consumer<String> msg(Consumer<String> feedback) {
        return s -> {
            plugin.getLogger().info("[worldbuild] " + s);
            if (feedback != null) feedback.accept(s);
        };
    }

    // exposed for /worldbuild unlock without switch gymnastics
    static final List<BuildState.Status> REVIEW = Arrays.asList(BuildState.Status.APPROVED, BuildState.Status.LOCKED);
}
