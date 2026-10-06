package mn.suld.api.worldbuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

/**
 * Compiles a {@link CitySpec} into a {@link CompiledCity}, deterministically and without a server:
 * <ol>
 *   <li>terrain columns from the {@link TerrainPlan} (aligned to the natural ground);</li>
 *   <li>each placement's module is built in local space, then mirrored/rotated/offset into city space
 *       (block states rewritten), with its palette (base → district → placement overrides);</li>
 *   <li>overlap rules: a higher {@link Layer} overrides a lower one; a lower one never clobbers a higher
 *       one; two placements writing different blocks on the same layer is an OVERLAP error unless the
 *       later placement sets {@code allowOverlap};</li>
 *   <li>fence / wall / pane / bars connections are recomputed from final neighbours (so rotated or
 *       adjoining modules join up correctly).</li>
 * </ol>
 */
public final class CityCompiler {

    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final int[][] D = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private CityCompiler() {
    }

    /** Compile against perfectly flat natural ground at y = 0 (previews, tests, validation). */
    public static CompiledCity compile(CitySpec spec, ModuleLibrary library) {
        return compile(spec, library, (x, z) -> 0);
    }

    public static CompiledCity compile(CitySpec spec, ModuleLibrary library, IntBinaryOperator natural) {
        List<Issue> issues = new ArrayList<>();
        Map<Long, Cell> cells = new HashMap<>(1 << 18);

        // 1. terrain
        List<TerrainPlan.Column> cols = spec.terrain().columns(natural);
        for (TerrainPlan.Column c : cols) {
            cells.put(BlockPos.pack(c.x(), c.top(), c.z()), new Cell(c.surface(), Pass.TERRAIN, Layer.TERRAIN, -1));
            if (c.natural() < c.top()) {
                for (int y = c.natural() + 1; y < c.top(); y++) {
                    cells.put(BlockPos.pack(c.x(), y, c.z()), new Cell("minecraft:dirt", Pass.TERRAIN, Layer.TERRAIN, -1));
                }
            } else {
                for (int y = c.top() - 2; y < c.top(); y++) {
                    cells.put(BlockPos.pack(c.x(), y, c.z()), new Cell("minecraft:dirt", Pass.TERRAIN, Layer.TERRAIN, -1));
                }
            }
        }

        // 2. placements
        List<CompiledCity.Instance> instances = new ArrayList<>();
        Map<String, Placement> landmarks = new HashMap<>();
        Set<String> ids = new HashSet<>();
        Map<String, int[]> conflicts = new LinkedHashMap<>(); // "a|b" -> {count, x, y, z}
        List<Placement> placements = spec.placements();
        for (int i = 0; i < placements.size(); i++) {
            Placement pl = placements.get(i);
            if (!ids.add(pl.id())) {
                issues.add(Issue.error("DUPLICATE_ID", "placement id used twice: " + pl.id(), pl.x(), pl.y(), pl.z()));
            }
            if (pl.landmark()) {
                Placement prev = landmarks.putIfAbsent(pl.module(), pl);
                if (prev != null) {
                    issues.add(Issue.error("DUPLICATE_LANDMARK", "landmark " + pl.module() + " placed by both "
                            + prev.id() + " and " + pl.id(), pl.x(), pl.y(), pl.z()));
                }
            }
            Module m = library.find(pl.module()).orElse(null);
            if (m == null) {
                issues.add(Issue.error("MISSING_MODULE", "placement " + pl.id() + " uses unknown module " + pl.module(),
                        pl.x(), pl.y(), pl.z()));
                instances.add(new CompiledCity.Instance(i, pl, Footprint.point(pl.x(), pl.y(), pl.z()), 0, List.of()));
                continue;
            }
            Palette palette = spec.palette();
            if (pl.district() != null && spec.districtPalettes().containsKey(pl.district())) {
                palette = palette.with(spec.districtPalettes().get(pl.district()));
            }
            if (!pl.palette().isEmpty()) palette = palette.with(pl.palette());
            ModuleContext ctx = new ModuleContext(pl.params(), palette, mix(spec.seed(), pl.seed()));
            ModuleCanvas canvas = new ModuleCanvas(palette, m.layer());
            try {
                m.build(canvas, ctx);
            } catch (RuntimeException e) {
                issues.add(Issue.error("MODULE_FAILED", pl.id() + " (" + pl.module() + "): " + e.getMessage(), pl.x(), pl.y(), pl.z()));
                instances.add(new CompiledCity.Instance(i, pl, Footprint.point(pl.x(), pl.y(), pl.z()), 0, List.of()));
                continue;
            }
            Transform t = pl.transform();
            Footprint fp = null;
            int written = 0;
            for (Map.Entry<Long, Cell> e : canvas.cells().entrySet()) {
                long lp = e.getKey();
                int[] xz = t.apply(BlockPos.x(lp), BlockPos.z(lp));
                int x = pl.x() + xz[0], y = pl.y() + BlockPos.y(lp), z = pl.z() + xz[1];
                Cell src = e.getValue();
                Cell next = new Cell(BlockStates.transform(src.block(), t), src.pass(), src.layer(), i);
                long key = BlockPos.pack(x, y, z);
                Cell prev = cells.get(key);
                fp = fp == null ? Footprint.point(x, y, z) : fp.include(x, y, z);
                if (prev == null || prev.owner() == i || prev.layer().priority < next.layer().priority) {
                    cells.put(key, next);
                    written++;
                    continue;
                }
                if (prev.layer().priority > next.layer().priority) continue; // never clobber a higher layer
                if (prev.block().equals(next.block()) || BlockKinds.isAir(prev.block()) && BlockKinds.isAir(next.block())) continue;
                if (pl.allowOverlap()) {
                    cells.put(key, next);
                    written++;
                    continue;
                }
                String pair = placements.get(prev.owner()).id() + " ↔ " + pl.id();
                int[] c = conflicts.computeIfAbsent(pair, k -> new int[]{0, x, y, z});
                c[0]++;
            }
            List<Connector> cons = new ArrayList<>();
            for (Connector c : m.connectors(ctx)) {
                int[] xz = t.apply(c.x(), c.z());
                cons.add(new Connector(c.name(), pl.x() + xz[0], pl.y() + c.y(), pl.z() + xz[1],
                        c.facing() == null ? null : t.apply(c.facing()), c.walkable()));
            }
            instances.add(new CompiledCity.Instance(i, pl, fp == null ? Footprint.point(pl.x(), pl.y(), pl.z()) : fp, written, cons));
        }
        conflicts.forEach((pair, c) -> issues.add(Issue.error("OVERLAP",
                pair + ": " + c[0] + " block(s) on the same layer", c[1], c[2], c[3])));

        // 3. connections
        resolveConnections(cells, CompiledCity.index(cols));

        return new CompiledCity(spec, cells, CompiledCity.index(cols), instances, issues);
    }

    static long mix(long citySeed, long placementSeed) {
        long z = citySeed * 0x9E3779B97F4A7C15L + placementSeed;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static String at(Map<Long, Cell> cells, Map<Long, TerrainPlan.Column> cols, int x, int y, int z) {
        Cell c = cells.get(BlockPos.pack(x, y, z));
        if (c != null) return c.block();
        TerrainPlan.Column col = cols.get(BlockPos.pack(x, 0, z));
        return y <= (col != null ? col.top() : 0) ? "minecraft:stone" : "minecraft:air";
    }

    static void resolveConnections(Map<Long, Cell> cells, Map<Long, TerrainPlan.Column> cols) {
        List<Map.Entry<Long, Cell>> updates = new ArrayList<>();
        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            String fam = BlockKinds.connectionFamily(e.getValue().block());
            if (fam == null) continue;
            long p = e.getKey();
            int x = BlockPos.x(p), y = BlockPos.y(p), z = BlockPos.z(p);
            Map<String, String> props = BlockStates.props(e.getValue().block());
            boolean[] con = new boolean[4];
            for (int d = 0; d < 4; d++) {
                con[d] = connects(fam, at(cells, cols, x + D[d][0], y, z + D[d][1]));
            }
            if (fam.equals("wall")) {
                boolean tallAbove = BlockKinds.isFullCube(at(cells, cols, x, y + 1, z));
                for (int d = 0; d < 4; d++) props.put(DIRS[d], con[d] ? (tallAbove ? "tall" : "low") : "none");
                boolean straightNS = con[0] && con[2] && !con[1] && !con[3];
                boolean straightEW = con[1] && con[3] && !con[0] && !con[2];
                props.put("up", String.valueOf(!(straightNS || straightEW) || tallAbove));
            } else {
                for (int d = 0; d < 4; d++) props.put(DIRS[d], String.valueOf(con[d]));
            }
            String id = BlockStates.id(e.getValue().block());
            Cell c = e.getValue();
            updates.add(Map.entry(p, new Cell(BlockStates.build(id, props), c.pass(), c.layer(), c.owner())));
        }
        for (Map.Entry<Long, Cell> u : updates) cells.put(u.getKey(), u.getValue());
    }

    private static boolean connects(String family, String neighbour) {
        String nf = BlockKinds.connectionFamily(neighbour);
        if (BlockStates.id(neighbour).endsWith("_fence_gate")) return !family.equals("pane");
        if (nf == null) return BlockKinds.isFullCube(neighbour);
        return switch (family) {
            case "fence" -> nf.equals("fence");
            case "nether_fence" -> nf.equals("nether_fence");
            case "wall", "pane" -> nf.equals("wall") || nf.equals("pane");
            default -> false;
        };
    }
}
