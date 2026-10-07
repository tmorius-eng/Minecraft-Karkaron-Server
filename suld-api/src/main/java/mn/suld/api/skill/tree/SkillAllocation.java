package mn.suld.api.skill.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A player's ranks on one {@link SkillTree} (immutable). The root always has rank 1 and is never stored.
 * All unlock/refund rules live here so the GUI, commands, admin tools and tests share one implementation.
 * A build is <em>consistent</em> when every learned node reaches the root through learned nodes, meets its
 * extra requirements, and no two exclusive nodes are both learned.
 */
public final class SkillAllocation {

    public enum Why {
        OK, ROOT, MAXED, NOT_CONNECTED, LEVEL, POINTS, EXCLUSIVE, REQUIRES, NOT_UNLOCKED, WOULD_BREAK
    }

    /** The answer to "may I change this node?"; {@code other} is the blocking node (rival, missing requirement, orphan). */
    public record Check(Why why, SkillNode node, SkillNode other, int amount) {
        public boolean ok() { return why == Why.OK; }
    }

    /** The result of decoding a stored build: the allocation plus node ids that no longer exist. */
    public record Decoded(SkillAllocation allocation, List<String> dropped) {
    }

    private final SkillTree tree;
    private final int[] ranks;

    public SkillAllocation(SkillTree tree, int[] ranks) {
        this.tree = tree;
        this.ranks = new int[tree.nodes().size()];
        for (int i = 1; i < this.ranks.length && i < ranks.length; i++) {
            this.ranks[i] = Math.max(0, Math.min(tree.node(i).maxRank(), ranks[i]));
        }
    }

    public static SkillAllocation empty(SkillTree tree) {
        return new SkillAllocation(tree, new int[0]);
    }

    public SkillTree tree() { return tree; }

    public int rank(int index) { return index == 0 ? 1 : ranks[index]; }

    public int rank(SkillNode n) { return rank(n.index()); }

    public boolean unlocked(SkillNode n) { return rank(n.index()) > 0; }

    public boolean maxed(SkillNode n) { return rank(n.index()) >= n.maxRank(); }

    public int spent() {
        int s = 0;
        for (SkillNode n : tree.nodes()) if (n.index() > 0) s += ranks[n.index()] * n.cost();
        return s;
    }

    public List<SkillNode> unlockedNodes() {
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode n : tree.nodes()) if (unlocked(n)) out.add(n);
        return out;
    }

    // ------------------------------------------------------------------ codec (by node id, never by position)

    /** {@code id=rank;id=rank} for learned nodes in map order; empty when nothing is learned. */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (SkillNode n : tree.nodes()) {
            if (n.index() == 0 || ranks[n.index()] == 0) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(n.id()).append('=').append(ranks[n.index()]);
        }
        return sb.toString();
    }

    public static Decoded decode(SkillTree tree, String text) {
        int[] r = new int[tree.nodes().size()];
        List<String> dropped = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    dropped.add(part);
                    continue;
                }
                String id = part.substring(0, eq).trim();
                int rank;
                try {
                    rank = Integer.parseInt(part.substring(eq + 1).trim());
                } catch (NumberFormatException e) {
                    dropped.add(part);
                    continue;
                }
                SkillNode n = tree.find(id).orElse(null);
                if (n == null || n.root() || rank < 1) dropped.add(id);
                else r[n.index()] = Math.min(rank, n.maxRank());
            }
        }
        return new Decoded(new SkillAllocation(tree, r), dropped);
    }

    // ------------------------------------------------------------------ rules

    /** A node you could learn next: not learned yet, next to a learned node. (Rank-ups of learned nodes are always adjacent.) */
    public boolean reachable(SkillNode n) {
        if (unlocked(n)) return true;
        for (int nb : tree.neighbours(n.index())) if (rank(nb) > 0) return true;
        return false;
    }

    public SkillNode rival(SkillNode n) {
        for (int ex : tree.exclusives(n.index())) if (rank(ex) > 0) return tree.node(ex);
        return null;
    }

    /** The first unmet extra requirement of a node, or null. */
    public SkillNode missing(SkillNode n) {
        for (SkillNode.Req r : n.requires()) if (rank(tree.node(r.node()).index()) < r.rank()) return tree.node(r.node());
        return null;
    }

    /** Whether a node is shown on the map: not hidden, or already reachable / learned. */
    public boolean visible(SkillNode n) {
        return !n.hidden() || unlocked(n) || reachable(n);
    }

    /** What a node looks like on the map for this player. */
    public enum NodeState {
        /** Learned (some ranks). */
        UNLOCKED,
        /** Learned at its top rank. */
        MAXED,
        /** Could be bought right now. */
        AVAILABLE,
        /** Next to the build, but the level is too low. */
        LEVEL_LOCKED,
        /** Next to the build and allowed, but not enough points. */
        NEEDS_POINTS,
        /** Next to the build, but an extra required node is missing. */
        PREREQUISITE_MISSING,
        /** A rival (red link) is learned. */
        EXCLUDED,
        /** Not connected to the build yet. */
        LOCKED
    }

    public NodeState state(SkillNode n, int level, int availablePoints) {
        if (n.root() || maxed(n)) return NodeState.MAXED;
        if (unlocked(n)) return NodeState.UNLOCKED;
        if (!reachable(n)) return NodeState.LOCKED;
        if (rival(n) != null) return NodeState.EXCLUDED;
        if (missing(n) != null) return NodeState.PREREQUISITE_MISSING;
        if (level < n.effectiveLevel()) return NodeState.LEVEL_LOCKED;
        if (availablePoints < n.cost()) return NodeState.NEEDS_POINTS;
        return NodeState.AVAILABLE;
    }

    public Check canUnlock(SkillNode n, int level, int availablePoints) {
        if (n.root()) return new Check(Why.ROOT, n, null, 0);
        if (maxed(n)) return new Check(Why.MAXED, n, null, 0);
        if (!reachable(n)) return new Check(Why.NOT_CONNECTED, n, null, 0);
        SkillNode rival = rival(n);
        if (rival != null) return new Check(Why.EXCLUSIVE, n, rival, 0);
        SkillNode miss = missing(n);
        if (miss != null) return new Check(Why.REQUIRES, n, miss, 0);
        if (level < n.effectiveLevel()) return new Check(Why.LEVEL, n, null, n.effectiveLevel());
        if (availablePoints < n.cost()) return new Check(Why.POINTS, n, null, n.cost());
        return new Check(Why.OK, n, null, 0);
    }

    public Check canRefund(SkillNode n) {
        if (n.root()) return new Check(Why.ROOT, n, null, 0);
        if (!unlocked(n)) return new Check(Why.NOT_UNLOCKED, n, null, 0);
        SkillAllocation after = withRank(n, rank(n) - 1);
        SkillNode broken = after.firstInconsistent();
        if (broken != null) return new Check(Why.WOULD_BREAK, n, broken, 0);
        return new Check(Why.OK, n, null, 0);
    }

    public SkillAllocation withRank(SkillNode n, int rank) {
        int[] copy = ranks.clone();
        copy[n.index()] = rank;
        return new SkillAllocation(tree, copy);
    }

    /** One more rank (no checks: callers use {@link #canUnlock} first). */
    public SkillAllocation plusRank(SkillNode n) { return withRank(n, rank(n) + 1); }

    public SkillAllocation minusRank(SkillNode n) { return withRank(n, rank(n) - 1); }

    /** A learned node that no longer reaches the root, misses a requirement, or clashes with a rival; null if consistent. */
    public SkillNode firstInconsistent() {
        boolean[] seen = new boolean[ranks.length];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(0);
        seen[0] = true;
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (int nb : tree.neighbours(cur)) {
                if (!seen[nb] && rank(nb) > 0) {
                    seen[nb] = true;
                    q.add(nb);
                }
            }
        }
        for (SkillNode n : tree.nodes()) {
            if (!unlocked(n) || n.root()) continue;
            if (!seen[n.index()]) return n;
            if (missing(n) != null) return n;
            if (rival(n) != null) return n;
        }
        return null;
    }

    public boolean consistent() { return firstInconsistent() == null; }

    /** Whether the build is affordable and allowed at this level (every node's level and the point total). */
    public boolean fits(int level, int totalPoints) {
        if (spent() > totalPoints) return false;
        for (SkillNode n : unlockedNodes()) if (!n.root() && level < n.effectiveLevel()) return false;
        return true;
    }

    /** Remove nodes (deepest first) until the build is consistent and fits: used after losing levels or editing data. */
    public SkillAllocation trimmed(int level, int totalPoints) {
        SkillAllocation cur = this;
        int guard = 10_000;
        while (guard-- > 0) {
            SkillNode bad = cur.firstInconsistent();
            if (bad == null && cur.fits(level, totalPoints)) return cur;
            SkillNode victim = bad;
            if (victim == null) {
                List<SkillNode> order = new ArrayList<>(cur.unlockedNodes());
                order.removeIf(SkillNode::root);
                order.sort((a, b) -> b.y() != a.y() ? Integer.compare(b.y(), a.y()) : Integer.compare(b.index(), a.index()));
                for (SkillNode n : order) {
                    if (level < n.effectiveLevel() || cur.spent() > totalPoints) {
                        victim = n;
                        break;
                    }
                }
            }
            if (victim == null) return cur;
            cur = cur.withRank(victim, 0);
        }
        return cur;
    }

    /** Everything of one category refunded, plus whatever then loses its connection. */
    public SkillAllocation withoutCategory(SkillCategory category) {
        SkillAllocation cur = this;
        for (SkillNode n : tree.nodes()) if (!n.root() && n.category() == category) cur = cur.withRank(n, 0);
        return cur.trimmed(1000, Integer.MAX_VALUE);
    }

    public Ultimate ultimate() {
        for (SkillNode n : unlockedNodes()) {
            for (Effect e : n.effects()) if (e instanceof Effect.UnlockUltimate u) return u.ultimate();
        }
        return null;
    }

    public SkillBuild build() { return SkillBuild.of(this); }

    /** Rank per node id for display/debug. */
    public Map<String, Integer> asMap() {
        Map<String, Integer> m = new TreeMap<>();
        for (SkillNode n : tree.nodes()) if (n.index() > 0 && ranks[n.index()] > 0) m.put(n.id(), ranks[n.index()]);
        return m;
    }
}
