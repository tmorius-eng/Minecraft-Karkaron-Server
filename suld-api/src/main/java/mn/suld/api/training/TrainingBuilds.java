package mn.suld.api.training;

import mn.suld.api.balance.Ascension;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.SkillAllocation;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.SkillPoints;
import mn.suld.api.skill.tree.SkillTree;

import java.util.ArrayList;
import java.util.List;

/**
 * The two ready builds of the training grounds (docs/TRAINING_GROUNDS.md). A finished level-60 character has
 * {@link #maxPoints()} points; build A spends them on the class's first keystone, build B on its second, each with one
 * ultimate (B takes another one than A when it can), and the rest goes to the passives and attack nodes a new player most
 * needs to see. Every step follows the ordinary unlock rules ({@link SkillAllocation#pathTo}, {@link
 * SkillAllocation#canUnlock}), so a build is always one a player could click together. Pure and deterministic: the same
 * tree always gives the same build.
 */
public final class TrainingBuilds {

    /** The level of the training character. */
    public static final int LEVEL = 60;
    /** Build A and build B. */
    public static final int VARIANTS = 2;

    private TrainingBuilds() {
    }

    /** Every point a finished character has: 59 from levels, the whole story, every region, Тэнгэрийн Зэрэг III. */
    public static int maxPoints() {
        return SkillPoints.total(LEVEL, 3 * 14, 2 * 4, 0) + Ascension.MAX_RANK;
    }

    /**
     * One ready build.
     *
     * @param keystone the keystone it is built around (null for a tree without one)
     * @param ultimate the ultimate it learns (null when none can be reached)
     */
    public record Build(int variant, SkillNode keystone, SkillNode ultimate, SkillAllocation allocation) {

        /** «A» or «B». */
        public String letter() {
            return variant == 0 ? "A" : "B";
        }
    }

    /** Build {@code variant} (0 = A, 1 = B) with {@link #maxPoints()}. */
    public static Build of(SkillTree tree, int variant) {
        return of(tree, variant, maxPoints());
    }

    /** Build {@code variant} (0 = A, 1 = B) with {@code points} points. */
    public static Build of(SkillTree tree, int variant, int points) {
        int v = Math.floorMod(variant, VARIANTS);
        SkillNode avoid = v == 0 ? null : build(tree, 0, points, null).ultimate();
        return build(tree, v, points, avoid);
    }

    private static Build build(SkillTree tree, int variant, int points, SkillNode avoidUltimate) {
        List<SkillNode> keys = tree.nodes().stream().filter(SkillNode::keystone).toList();
        SkillNode key = keys.isEmpty() ? null : keys.get(Math.min(variant, keys.size() - 1));
        SkillAllocation a = SkillAllocation.empty(tree);
        if (key != null) {
            SkillAllocation with = acquire(a, key, points, 0);
            if (with != null) a = with;
            else key = null;
        }
        SkillNode ult = null;
        SkillAllocation best = null;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (SkillNode n : tree.nodes()) {
                if (!n.unlocksUltimate() || pass == 0 && n.equals(avoidUltimate)) continue;
                SkillAllocation with = acquire(a, n, points, 0);
                if (with != null && (best == null || with.spent() < best.spent())) {
                    best = with;
                    ult = n;
                }
            }
        }
        if (best != null) a = best;
        return new Build(variant, key, ult, fill(a, points));
    }

    /** Spends what is left, one best-value step at a time (a rank-up, or the chain to a node not learned yet). */
    private static SkillAllocation fill(SkillAllocation a, int points) {
        SkillTree tree = a.tree();
        int guard = 1000;
        while (guard-- > 0) {
            int left = points - a.spent();
            if (left <= 0) break;
            List<SkillNode> bestChain = null;
            double bestScore = 0;
            for (SkillNode n : tree.nodes()) {
                if (n.root() || a.maxed(n) || n.keystone() || n.unlocksUltimate()) continue;
                List<SkillNode> chain;
                if (a.unlocked(n)) {
                    if (!a.canUnlock(n, LEVEL, left).ok()) continue;
                    chain = List.of(n);
                } else {
                    if (a.missing(n) != null) continue;
                    chain = a.pathTo(n, LEVEL);
                    if (chain == null) continue;
                }
                int cost = SkillAllocation.cost(chain);
                if (cost <= 0 || cost > left || chain.stream().anyMatch(c -> c.keystone() || c.unlocksUltimate())) continue;
                double value = 0;
                for (SkillNode c : chain) value += value(c);
                double score = value / cost;
                if (bestChain == null || score > bestScore + 1e-9) {
                    bestChain = chain;
                    bestScore = score;
                }
            }
            if (bestChain == null) break;
            for (SkillNode c : bestChain) a = a.plusRank(c);
        }
        return a;
    }

    /**
     * {@code a} with {@code n} learned (its extra requirements first, then the cheapest chain to it), or null when that
     * breaks a rule or costs more than {@code points}.
     */
    private static SkillAllocation acquire(SkillAllocation a, SkillNode n, int points, int depth) {
        if (a.unlocked(n)) return a;
        if (depth > 8) return null;
        SkillTree tree = a.tree();
        for (SkillNode.Req r : n.requires()) {
            SkillNode need = tree.node(r.node());
            int guard = 10;
            while (a != null && a.rank(need) < r.rank() && guard-- > 0) {
                if (!a.unlocked(need)) {
                    a = acquire(a, need, points, depth + 1);
                } else if (a.canUnlock(need, LEVEL, points - a.spent()).ok()) {
                    a = a.plusRank(need);
                } else {
                    a = null;
                }
            }
            if (a == null || a.rank(need) < r.rank()) return null;
        }
        List<SkillNode> chain = a.pathTo(n, LEVEL);
        if (chain == null || a.spent() + SkillAllocation.cost(chain) > points) return null;
        SkillAllocation out = a;
        for (SkillNode c : chain) out = out.plusRank(c);
        return out;
    }

    /**
     * What a rank of a node is worth to a new player trying the class: a passive (it shows in the training numbers) most,
     * then spell modifiers and the attack stats, then everything else. Never zero, so no point is left unspent.
     */
    static double value(SkillNode n) {
        double v = 0;
        for (Effect e : n.effects()) {
            v += switch (e) {
                case Effect.Proc p -> 6;
                case Effect.SpellMod m -> 3;
                case Effect.Stat s -> switch (s.key()) {
                    case ATTACK_PCT, CRIT_CHANCE, CRIT_DAMAGE, SPELL_DAMAGE -> 3;
                    default -> 1.5;
                };
                case Effect.Keystone k -> 0;
                case Effect.UnlockUltimate u -> 0;
            };
        }
        return Math.max(0.5, v);
    }

    /** The passive nodes of a build, in map order (what the training grounds explain and count). */
    public static List<SkillNode> passives(SkillAllocation a) {
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode n : a.unlockedNodes()) if (n.hasProc()) out.add(n);
        return out;
    }
}
