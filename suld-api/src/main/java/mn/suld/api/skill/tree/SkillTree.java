package mn.suld.api.skill.tree;

import mn.suld.api.clazz.PlayerClass;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A class's ability map: nodes on a grid, edges between neighbouring nodes (a path must reach a node through an
 * unlocked neighbour), and exclusive links (red lines): two nodes joined by one cannot both be unlocked.
 * Validated on construction, so a content mistake fails a unit test instead of a player.
 */
public final class SkillTree {

    public static final int MAX_NODES = 255;

    private final PlayerClass clazz;
    private final List<SkillNode> nodes;
    private final Map<String, SkillNode> byId = new HashMap<>();
    private final List<Set<Integer>> neighbours = new ArrayList<>();
    private final List<Set<Integer>> exclusive = new ArrayList<>();
    private final Set<Long> edgeKeys = new HashSet<>();

    public SkillTree(PlayerClass clazz, List<SkillNode> nodes, List<String[]> edges, List<String[]> exclusives) {
        this.clazz = clazz;
        this.nodes = List.copyOf(nodes);
        if (nodes.isEmpty() || nodes.size() > MAX_NODES) throw new IllegalArgumentException("node count " + nodes.size());
        Set<String> coords = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            SkillNode n = nodes.get(i);
            if (n.index() != i) throw new IllegalArgumentException(n.id() + ": index " + n.index() + " != position " + i);
            if (byId.put(n.id(), n) != null) throw new IllegalArgumentException("duplicate id " + n.id());
            if (n.satellite() && !byId.containsKey(n.orbit())) throw new IllegalArgumentException(n.id() + ": orbits unknown or later node " + n.orbit());
            if (!n.satellite() && !coords.add(n.x() + "," + n.y())) throw new IllegalArgumentException("two nodes at " + n.x() + "," + n.y());
            if (i == 0 && (n.cost() != 0 || n.maxRank() != 1)) throw new IllegalArgumentException("the root is free");
            if (i > 0 && n.cost() < 1) throw new IllegalArgumentException(n.id() + ": cost");
            if (n.maxRank() < 1 || n.maxRank() > 5) throw new IllegalArgumentException(n.id() + ": maxRank " + n.maxRank());
            if (n.maxRank() > 1 && n.effects().stream().anyMatch(e -> !(e instanceof Effect.Stat || e instanceof Effect.SpellMod)))
                throw new IllegalArgumentException(n.id() + ": only stat and spell-modifier nodes can have several ranks");
            neighbours.add(new HashSet<>());
            exclusive.add(new HashSet<>());
        }
        Set<String> diagonals = new HashSet<>();
        for (String[] e : edges) {
            SkillNode a = node(e[0]), b = node(e[1]);
            int dx = Math.abs(a.x() - b.x()), dy = Math.abs(a.y() - b.y());
            if (a.satellite() || b.satellite()) {
                // a satellite links only to its hub or to a sibling of the same hub (it has no place on the grid)
                String ha = a.satellite() ? a.orbit() : a.id(), hb = b.satellite() ? b.orbit() : b.id();
                if (!ha.equals(hb)) throw new IllegalArgumentException("edge " + a.id() + "-" + b.id() + " leaves its hub's orbit");
                if (!edgeKeys.add(key(a.index(), b.index()))) throw new IllegalArgumentException("duplicate edge " + a.id() + "-" + b.id());
                neighbours.get(a.index()).add(b.index());
                neighbours.get(b.index()).add(a.index());
                continue;
            }
            if (dx > 1 || dy > 1 || dx + dy == 0) throw new IllegalArgumentException("edge " + a.id() + "-" + b.id() + " is not between neighbours");
            if (dx == 1 && dy == 1) {
                // two diagonals through one cell would be drawn on top of each other
                String cell = Math.min(a.x(), b.x()) + "," + Math.min(a.y(), b.y());
                if (!diagonals.add(cell)) throw new IllegalArgumentException("crossing diagonals at " + cell);
            }
            if (!edgeKeys.add(key(a.index(), b.index()))) throw new IllegalArgumentException("duplicate edge " + a.id() + "-" + b.id());
            neighbours.get(a.index()).add(b.index());
            neighbours.get(b.index()).add(a.index());
        }
        for (String[] e : exclusives) {
            SkillNode a = node(e[0]), b = node(e[1]);
            if (a == b) throw new IllegalArgumentException("exclusive with itself " + a.id());
            if (edgeKeys.contains(key(a.index(), b.index()))) throw new IllegalArgumentException(a.id() + "-" + b.id() + " is a path and an exclusive link");
            exclusive.get(a.index()).add(b.index());
            exclusive.get(b.index()).add(a.index());
        }
        for (SkillNode n : nodes) {
            for (SkillNode.Req r : n.requires()) {
                SkillNode need = byId.get(r.node());
                if (need == null) throw new IllegalArgumentException(n.id() + ": requires unknown node " + r.node());
                if (need == n) throw new IllegalArgumentException(n.id() + ": requires itself");
                if (r.rank() < 1 || r.rank() > need.maxRank()) throw new IllegalArgumentException(n.id() + ": requires rank " + r.rank() + " of " + need.id());
                if (exclusive.get(n.index()).contains(need.index())) throw new IllegalArgumentException(n.id() + " requires its own exclusive rival " + need.id());
            }
        }
        // every node must be reachable from the root through edges
        boolean[] seen = new boolean[nodes.size()];
        ArrayDeque<Integer> q = new ArrayDeque<>(List.of(0));
        seen[0] = true;
        int count = 1;
        while (!q.isEmpty()) {
            for (int nb : neighbours.get(q.poll())) {
                if (!seen[nb]) {
                    seen[nb] = true;
                    count++;
                    q.add(nb);
                }
            }
        }
        if (count != nodes.size()) throw new IllegalArgumentException("unreachable nodes in " + clazz);
    }

    private static long key(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    public PlayerClass clazz() { return clazz; }
    public List<SkillNode> nodes() { return nodes; }
    public SkillNode root() { return nodes.get(0); }
    public SkillNode node(int index) { return nodes.get(index); }

    public SkillNode node(String id) {
        SkillNode n = byId.get(id);
        if (n == null) throw new IllegalArgumentException("unknown node " + id);
        return n;
    }

    public Optional<SkillNode> find(String id) { return Optional.ofNullable(byId.get(id)); }

    public Optional<SkillNode> at(int x, int y) {
        for (SkillNode n : nodes) if (!n.satellite() && n.x() == x && n.y() == y) return Optional.of(n);
        return Optional.empty();
    }

    public Set<Integer> neighbours(int index) { return neighbours.get(index); }
    public Set<Integer> exclusives(int index) { return exclusive.get(index); }

    public boolean linked(int a, int b) { return edgeKeys.contains(key(a, b)); }

    public boolean exclusiveLink(int a, int b) { return exclusive.get(a).contains(b); }

    public int maxY() {
        int m = 0;
        for (SkillNode n : nodes) if (!n.satellite()) m = Math.max(m, n.y());
        return m;
    }

    public int maxX() {
        int m = 0;
        for (SkillNode n : nodes) if (!n.satellite()) m = Math.max(m, n.x());
        return m;
    }

    /** Total cost of every rank of every node (what unlocking the whole map would take, ignoring exclusive links). */
    public int totalCost() {
        int c = 0;
        for (SkillNode n : nodes) c += n.cost() * n.maxRank();
        return c;
    }
}
