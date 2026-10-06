package mn.suld.api.worldbuild;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Validates a compiled city slice before anything is placed (and, through {@link #points}, a real
 * world after placement):
 * <ul>
 *   <li>compile issues: overlaps, missing modules, duplicate landmarks or ids, failed modules;</li>
 *   <li>bounds: no placement block outside the slice bounds;</li>
 *   <li>floating blocks: every non-air block is connected (6-neighbour) to the ground;</li>
 *   <li>gameplay points: each point of this slice is walkable and reachable on foot from spawn
 *       (no swimming: canals must be crossed by bridges);</li>
 *   <li>walkable connectors (doors, road ends) are reachable.</li>
 * </ul>
 */
public final class CityValidator {

    /** Extra margin around the bounds the walker may use (the road outside the gate). */
    public static final int WALK_MARGIN = 16;
    private static final int MAX_NODES = 4_000_000;

    private CityValidator() {
    }

    public static ValidationReport validate(CompiledCity city) {
        List<Issue> issues = new ArrayList<>(city.issues());
        Map<String, Object> stats = new LinkedHashMap<>();
        CitySpec spec = city.spec();
        Footprint b = spec.bounds();

        // bounds
        Map<Integer, int[]> outside = new TreeMap<>();
        for (Map.Entry<Long, Cell> e : city.cells().entrySet()) {
            Cell c = e.getValue();
            if (c.owner() < 0) continue;
            long p = e.getKey();
            int x = BlockPos.x(p), z = BlockPos.z(p);
            if (x < b.minX() || x > b.maxX() || z < b.minZ() || z > b.maxZ()) {
                outside.computeIfAbsent(c.owner(), k -> new int[]{0, x, BlockPos.y(p), z})[0]++;
            }
        }
        outside.forEach((owner, c) -> issues.add(Issue.error("OUT_OF_BOUNDS",
                spec.placements().get(owner).id() + ": " + c[0] + " block(s) outside the slice bounds", c[1], c[2], c[3])));

        // floating
        Map<Integer, int[]> floating = floating(city);
        floating.forEach((owner, c) -> issues.add(Issue.error("FLOATING",
                spec.placements().get(owner).id() + ": " + c[0] + " unsupported block(s)", c[1], c[2], c[3])));
        stats.put("floating_blocks", floating.values().stream().mapToInt(c -> c[0]).sum());

        // points + reachability
        PointCheck pc = points(city, spec.points(), spec.slice(), b, issues);
        stats.putAll(pc.stats());

        // walkable connectors
        Set<Long> reach = pc.reachable();
        int unreachableConnectors = 0;
        for (CompiledCity.Instance in : city.instances()) {
            for (Connector c : in.connectors()) {
                if (!c.walkable()) continue;
                if (!reach.contains(BlockPos.pack(c.x(), c.y(), c.z()))) {
                    unreachableConnectors++;
                    issues.add(Issue.error("CONNECTOR_UNREACHABLE", in.placement().id() + "." + c.name()
                            + " is not reachable on foot from spawn", c.x(), c.y(), c.z()));
                }
            }
        }
        stats.put("unreachable_connectors", unreachableConnectors);
        stats.put("blocks", city.blockCount());
        stats.put("placements", city.instances().size());
        stats.put("chunks", city.chunks().size());
        stats.put("terrain_columns", city.columns().size());
        stats.put("fingerprint", city.fingerprint());
        return new ValidationReport(issues, stats);
    }

    /** Point-check outcome: stats and the set of reachable feet positions. */
    public record PointCheck(Map<String, Object> stats, Set<Long> reachable) {
    }

    /**
     * Checks that every point of {@code slice} is walkable and reachable from the spawn point.
     * Works on any {@link BlockLookup}: a compiled city or a snapshot of the real world.
     */
    public static PointCheck points(BlockLookup w, List<WorldPoint> points, String slice, Footprint bounds,
                                             List<Issue> issues) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<WorldPoint> mine = points.stream().filter(p -> slice.equals(p.slice())).toList();
        WorldPoint spawn = mine.stream().filter(p -> p.type().equals("spawn")).findFirst().orElse(null);
        if (spawn == null) {
            issues.add(Issue.error("NO_SPAWN", "slice " + slice + " has no spawn point", 0, 0, 0));
            return new PointCheck(stats, Set.of());
        }
        Footprint area = new Footprint(bounds.minX() - WALK_MARGIN, -64, bounds.minZ() - WALK_MARGIN,
                bounds.maxX() + WALK_MARGIN, 300, bounds.maxZ() + WALK_MARGIN);
        Set<Long> reach = Walkability.flood(w, spawn.x(), spawn.y(), spawn.z(), area, MAX_NODES);
        stats.put("reachable_cells", reach.size());
        int ok = 0;
        for (WorldPoint p : mine) {
            if (!Walkability.walkable(w, p.x(), p.y(), p.z())) {
                issues.add(Issue.error("POINT_BLOCKED", p.id() + " is not a walkable position (feet " + w.blockAt(p.x(), p.y(), p.z())
                        + ", floor " + w.blockAt(p.x(), p.y() - 1, p.z()) + ")", p.x(), p.y(), p.z()));
            } else if (!reach.contains(BlockPos.pack(p.x(), p.y(), p.z()))) {
                issues.add(p.required()
                        ? Issue.error("POINT_UNREACHABLE", p.id() + " cannot be reached on foot from spawn", p.x(), p.y(), p.z())
                        : Issue.warning("POINT_UNREACHABLE", p.id() + " cannot be reached on foot from spawn", p.x(), p.y(), p.z()));
            } else {
                ok++;
            }
        }
        stats.put("points_ok", ok + "/" + mine.size());
        return new PointCheck(stats, reach);
    }

    /** owner → {count, x, y, z of one example} for blocks not connected to the ground. */
    static Map<Integer, int[]> floating(CompiledCity city) {
        Map<Long, Cell> cells = city.cells();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            if (BlockKinds.isAir(e.getValue().block())) continue;
            long p = e.getKey();
            int x = BlockPos.x(p), y = BlockPos.y(p), z = BlockPos.z(p);
            TerrainPlan.Column col = city.column(x, z);
            int ground = col != null ? col.top() : 0;
            boolean grounded = e.getValue().owner() < 0 || y - 1 <= ground && !isCarvedBelow(cells, x, y, z);
            if (grounded && seen.add(p)) queue.add(p);
        }
        int[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long p = queue.poll();
            int x = BlockPos.x(p), y = BlockPos.y(p), z = BlockPos.z(p);
            for (int[] d : n) {
                long q = BlockPos.pack(x + d[0], y + d[1], z + d[2]);
                Cell c = cells.get(q);
                if (c == null || BlockKinds.isAir(c.block()) || !seen.add(q)) continue;
                queue.add(q);
            }
        }
        Map<Integer, int[]> out = new TreeMap<>();
        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            Cell c = e.getValue();
            if (c.owner() < 0 || BlockKinds.isAir(c.block()) || seen.contains(e.getKey())) continue;
            long p = e.getKey();
            out.computeIfAbsent(c.owner(), k -> new int[]{0, BlockPos.x(p), BlockPos.y(p), BlockPos.z(p)})[0]++;
        }
        return out;
    }

    /** True when the block directly below was explicitly carved to air (e.g. over a canal). */
    private static boolean isCarvedBelow(Map<Long, Cell> cells, int x, int y, int z) {
        Cell below = cells.get(BlockPos.pack(x, y - 1, z));
        return below != null && BlockKinds.isAir(below.block());
    }
}
