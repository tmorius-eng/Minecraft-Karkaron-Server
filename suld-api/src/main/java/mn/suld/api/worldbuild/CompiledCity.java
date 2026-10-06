package mn.suld.api.worldbuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The deterministic result of compiling a {@link CitySpec}: every block with its pass, layer and
 * owner, the terrain columns, per-placement footprints and connectors (in city coordinates), and the
 * issues found. Output is ordered for placement: by pass, then chunk, then bottom-up.
 */
public final class CompiledCity implements BlockLookup {

    /** A compiled placement. */
    public record Instance(int index, Placement placement, Footprint footprint, int blocks, List<Connector> connectors) {
    }

    private final CitySpec spec;
    private final Map<Long, Cell> cells;
    private final Map<Long, TerrainPlan.Column> columns;
    private final List<Instance> instances;
    private final List<Issue> issues;

    CompiledCity(CitySpec spec, Map<Long, Cell> cells, Map<Long, TerrainPlan.Column> columns,
                 List<Instance> instances, List<Issue> issues) {
        this.spec = spec;
        this.cells = cells;
        this.columns = columns;
        this.instances = List.copyOf(instances);
        this.issues = new ArrayList<>(issues);
    }

    public CitySpec spec() { return spec; }
    public Map<Long, Cell> cells() { return Collections.unmodifiableMap(cells); }
    public List<Instance> instances() { return instances; }
    public List<Issue> issues() { return Collections.unmodifiableList(issues); }
    public Columns columns() { return new Columns(columns); }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == Issue.Severity.ERROR);
    }

    public Cell cell(int x, int y, int z) {
        return cells.get(BlockPos.pack(x, y, z));
    }

    public TerrainPlan.Column column(int x, int z) {
        return columns.get(BlockPos.pack(x, 0, z));
    }

    /** What stands at a position after the build (compiled block, else terrain, else air). */
    @Override
    public String blockAt(int x, int y, int z) {
        Cell c = cells.get(BlockPos.pack(x, y, z));
        if (c != null) return c.block();
        TerrainPlan.Column col = columns.get(BlockPos.pack(x, 0, z));
        int top = col != null ? col.top() : 0;
        return y <= top ? "minecraft:stone" : "minecraft:air";
    }

    public int blockCount() { return cells.size(); }

    public long count(String blockPrefix) {
        return cells.values().stream().filter(c -> c.block().startsWith(blockPrefix)).count();
    }

    /** Chunk keys (chunkX, chunkZ packed) touched by blocks or terrain, in build order. */
    public List<long[]> chunks() {
        TreeSet<Long> keys = new TreeSet<>(CHUNK_ORDER);
        for (long p : cells.keySet()) keys.add(chunkKey(BlockPos.x(p) >> 4, BlockPos.z(p) >> 4));
        for (TerrainPlan.Column c : columns.values()) keys.add(chunkKey(c.x() >> 4, c.z() >> 4));
        List<long[]> out = new ArrayList<>(keys.size());
        for (long k : keys) out.add(new long[]{chunkX(k), chunkZ(k)});
        return out;
    }

    /** Blocks of one pass inside one chunk (city-local chunk coordinates), bottom-up. */
    public List<BlockSpec> blocks(Pass pass, int chunkX, int chunkZ) {
        List<BlockSpec> out = new ArrayList<>();
        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            long p = e.getKey();
            if (e.getValue().pass() != pass || BlockPos.x(p) >> 4 != chunkX || BlockPos.z(p) >> 4 != chunkZ) continue;
            out.add(new BlockSpec(BlockPos.x(p), BlockPos.y(p), BlockPos.z(p), e.getValue().block()));
        }
        out.sort(Comparator.comparingInt(BlockSpec::y).thenComparingInt(BlockSpec::x).thenComparingInt(BlockSpec::z));
        return out;
    }

    /** All blocks grouped by pass → chunk key → ordered blocks (one pass over the cells). */
    public Map<Pass, Map<Long, List<BlockSpec>>> plan() {
        Map<Pass, Map<Long, List<BlockSpec>>> out = new java.util.EnumMap<>(Pass.class);
        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            long p = e.getKey();
            int x = BlockPos.x(p), y = BlockPos.y(p), z = BlockPos.z(p);
            out.computeIfAbsent(e.getValue().pass(), k -> new java.util.TreeMap<>(CHUNK_ORDER))
                    .computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new ArrayList<>())
                    .add(new BlockSpec(x, y, z, e.getValue().block()));
        }
        Comparator<BlockSpec> order = Comparator.comparingInt(BlockSpec::y).thenComparingInt(BlockSpec::x).thenComparingInt(BlockSpec::z);
        out.values().forEach(m -> m.values().forEach(l -> l.sort(order)));
        return out;
    }

    /** Stable content hash of the compiled result (blocks and terrain). */
    public String fingerprint() {
        List<Long> keys = new ArrayList<>(cells.keySet());
        Collections.sort(keys);
        long h = 1125899906842597L;
        for (long k : keys) {
            Cell c = cells.get(k);
            h = 31 * h + k;
            h = 31 * h + c.block().hashCode();
            h = 31 * h + c.pass().ordinal();
        }
        List<Long> ck = new ArrayList<>(columns.keySet());
        Collections.sort(ck);
        for (long k : ck) h = 31 * h + k * 7 + columns.get(k).top();
        return String.format("%016x", h);
    }

    void addIssues(List<Issue> more) {
        issues.addAll(more);
    }

    public static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int chunkX(long key) { return (int) (key >> 32); }
    public static int chunkZ(long key) { return (int) key; }

    /** Row-major (z, then x): neighbouring chunks are built one after another. */
    public static final Comparator<Long> CHUNK_ORDER = Comparator.<Long>comparingInt(CompiledCity::chunkZ).thenComparingInt(CompiledCity::chunkX);

    /** Read-only view of terrain columns. */
    public static final class Columns {
        private final Map<Long, TerrainPlan.Column> columns;

        Columns(Map<Long, TerrainPlan.Column> columns) {
            this.columns = columns;
        }

        public int size() { return columns.size(); }

        public java.util.Collection<TerrainPlan.Column> all() { return Collections.unmodifiableCollection(columns.values()); }

        public Map<Long, List<TerrainPlan.Column>> byChunk() {
            Map<Long, List<TerrainPlan.Column>> out = new java.util.TreeMap<>(CHUNK_ORDER);
            for (TerrainPlan.Column c : columns.values()) {
                out.computeIfAbsent(chunkKey(c.x() >> 4, c.z() >> 4), k -> new ArrayList<>()).add(c);
            }
            return out;
        }
    }

    static Map<Long, TerrainPlan.Column> index(List<TerrainPlan.Column> cols) {
        Map<Long, TerrainPlan.Column> m = new HashMap<>(cols.size() * 2);
        for (TerrainPlan.Column c : cols) m.put(BlockPos.pack(c.x(), 0, c.z()), c);
        return m;
    }
}
