package mn.suld.api.skill.tree;

import mn.suld.api.skill.Spell;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything an allocation adds up to, ready for the combat engine (precomputed once per change). Immutable. */
public final class SkillBuild {

    public static final SkillBuild EMPTY = new SkillBuild(new EnumMap<>(StatKey.class), new EnumMap<>(Spell.class),
            new EnumMap<>(TriggerEvent.class), null, EnumSet.noneOf(KeystoneKind.class), List.of());

    private final Map<StatKey, Double> stats;
    private final Map<Spell, Map<ModKey, Double>> mods;
    private final Map<TriggerEvent, List<IndexedProc>> procs;
    private final Ultimate ultimate;
    private final Set<KeystoneKind> keystones;
    private final List<SkillNode> nodes;
    /** Whether something besides the tree (equipment) contributes. */
    private final boolean extra;

    /** A proc with the node it came from (the cooldown is tracked per node). */
    public record IndexedProc(int node, Effect.Proc proc) {
    }

    private SkillBuild(Map<StatKey, Double> stats, Map<Spell, Map<ModKey, Double>> mods,
                       Map<TriggerEvent, List<IndexedProc>> procs, Ultimate ultimate, Set<KeystoneKind> keystones,
                       List<SkillNode> nodes) {
        this(stats, mods, procs, ultimate, keystones, nodes, false);
    }

    private SkillBuild(Map<StatKey, Double> stats, Map<Spell, Map<ModKey, Double>> mods,
                       Map<TriggerEvent, List<IndexedProc>> procs, Ultimate ultimate, Set<KeystoneKind> keystones,
                       List<SkillNode> nodes, boolean extra) {
        this.extra = extra;
        this.stats = stats;
        this.mods = mods;
        this.procs = procs;
        this.ultimate = ultimate;
        this.keystones = keystones;
        this.nodes = nodes;
    }

    static SkillBuild of(SkillAllocation a) {
        Map<StatKey, Double> stats = new EnumMap<>(StatKey.class);
        Map<Spell, Map<ModKey, Double>> mods = new EnumMap<>(Spell.class);
        Map<TriggerEvent, List<IndexedProc>> procs = new EnumMap<>(TriggerEvent.class);
        Set<KeystoneKind> keystones = EnumSet.noneOf(KeystoneKind.class);
        Ultimate ult = null;
        List<SkillNode> nodes = a.unlockedNodes();
        for (SkillNode n : nodes) {
            int rank = a.rank(n);
            for (Effect e : n.effects()) {
                switch (e) {
                    case Effect.Stat s -> stats.merge(s.key(), s.value() * rank, Double::sum);
                    case Effect.SpellMod m -> mods.computeIfAbsent(m.spell(), k -> new EnumMap<>(ModKey.class)).merge(m.key(), m.value() * rank, Double::sum);
                    case Effect.Proc p -> procs.computeIfAbsent(p.event(), k -> new ArrayList<>()).add(new IndexedProc(n.index(), p));
                    case Effect.UnlockUltimate u -> ult = u.ultimate();
                    case Effect.Keystone k -> keystones.add(k.kind());
                }
            }
        }
        return new SkillBuild(stats, mods, procs, ult, keystones, nodes);
    }

    /**
     * This build plus what the player's equipment adds: stats, spell modifiers and passive spells (item procs get the
     * indices {@code procIndexBase + i}, so their cooldowns never collide with the tree's nodes). Returns this build when
     * nothing is added.
     */
    public SkillBuild plus(Map<StatKey, Double> extraStats, Map<Spell, Map<ModKey, Double>> extraMods, List<Effect.Proc> extraProcs,
                           int procIndexBase) {
        boolean none = (extraStats == null || extraStats.isEmpty()) && (extraMods == null || extraMods.isEmpty())
                && (extraProcs == null || extraProcs.isEmpty());
        if (none) return this;
        Map<StatKey, Double> st = new EnumMap<>(StatKey.class);
        st.putAll(stats);
        if (extraStats != null) extraStats.forEach((k, v) -> st.merge(k, v, Double::sum));
        Map<Spell, Map<ModKey, Double>> md = new EnumMap<>(Spell.class);
        mods.forEach((s, m) -> md.put(s, new EnumMap<>(m)));
        if (extraMods != null) {
            extraMods.forEach((s, m) -> m.forEach((k, v) -> md.computeIfAbsent(s, x -> new EnumMap<>(ModKey.class)).merge(k, v, Double::sum)));
        }
        Map<TriggerEvent, List<IndexedProc>> pr = new EnumMap<>(TriggerEvent.class);
        procs.forEach((e, l) -> pr.put(e, new ArrayList<>(l)));
        if (extraProcs != null) {
            for (int i = 0; i < extraProcs.size(); i++) {
                Effect.Proc p = extraProcs.get(i);
                pr.computeIfAbsent(p.event(), x -> new ArrayList<>()).add(new IndexedProc(procIndexBase + i, p));
            }
        }
        return new SkillBuild(st, md, pr, ultimate, keystones, nodes, true);
    }

    public double stat(StatKey key) { return stats.getOrDefault(key, 0.0); }

    public double mod(Spell spell, ModKey key) {
        Map<ModKey, Double> m = mods.get(spell);
        return m == null ? 0 : m.getOrDefault(key, 0.0);
    }

    public List<IndexedProc> procs(TriggerEvent event) { return procs.getOrDefault(event, List.of()); }

    public Ultimate ultimate() { return ultimate; }

    public boolean has(KeystoneKind k) { return keystones.contains(k); }

    public Set<KeystoneKind> keystones() { return keystones; }

    public List<SkillNode> nodes() { return nodes; }

    /** Nothing learned and nothing equipped that adds to it. */
    public boolean isEmpty() { return nodes.size() <= 1 && !extra; }
}
