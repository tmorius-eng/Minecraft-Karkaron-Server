package mn.suld.plugin.combat;

import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.Ultimate;
import mn.suld.api.training.TrainingStats;

/**
 * What SÜLD damage is being dealt right now on the main thread: a spell, a passive or an ultimate (docs/TRAINING_GROUNDS.md).
 * The damage event such a hit raises is dispatched synchronously inside {@link #run}, so a listener reads the source of the
 * hit it is looking at with {@link #current()}. Basic hits, arrows and burning carry none: their event says what they are.
 * Like {@link CombatListener#asSpell}, the previous source is always restored in {@code finally}.
 */
public final class HitSource {

    private static TrainingStats.Source current;

    private HitSource() {
    }

    /** The source of the SÜLD hit being dealt, or null. */
    public static TrainingStats.Source current() {
        return current;
    }

    /** Runs {@code hit} with {@code source} as the current source ({@code null} runs it unlabelled). */
    public static void run(TrainingStats.Source source, Runnable hit) {
        TrainingStats.Source before = current;
        current = source;
        try {
            hit.run();
        } finally {
            current = before;
        }
    }

    public static TrainingStats.Source spell(Spell s) {
        return new TrainingStats.Source(TrainingStats.Kind.SPELL, s.displayName());
    }

    public static TrainingStats.Source passive(String name) {
        return new TrainingStats.Source(TrainingStats.Kind.PASSIVE, name);
    }

    public static TrainingStats.Source ultimate(Ultimate u) {
        return new TrainingStats.Source(TrainingStats.Kind.ULTIMATE, u.displayName());
    }
}
