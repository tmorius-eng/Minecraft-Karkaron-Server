package mn.suld.api.achievement;

import java.util.List;

/**
 * What an achievement asks for: a measure of one {@link Deed} over some of its subjects, to reach {@code target}.
 *
 * @param deed     the deed counted
 * @param subjects the subjects that count; empty for any (or for a deed without subjects)
 * @param measure  how the subjects' values make one number
 * @param target   the number to reach (at least 1)
 */
public record Criterion(Deed deed, List<String> subjects, Measure measure, long target) {

    /** How the subjects' values make the number the target is set against. */
    public enum Measure {
        /** The sum of the subjects' values (kills of these mobs, clears of any dungeon). */
        TOTAL,
        /** How many of the subjects were done at all (these chapters, any 10 ovoos). */
        DISTINCT,
        /** The best value among the subjects (the level, a weapon track's level). */
        BEST,
        /** How many of the listed subjects reached the target each (all four crafts at 50); the target of the whole
         *  criterion is then that every one did. */
        EACH
    }

    public Criterion {
        subjects = List.copyOf(subjects);
        if (deed == null || measure == null) throw new IllegalArgumentException("a criterion needs a deed and a measure");
        if (target < 1) throw new IllegalArgumentException("target " + target);
        if (measure == Measure.EACH && subjects.isEmpty()) throw new IllegalArgumentException("EACH needs its subjects");
        if (measure == Measure.DISTINCT && !subjects.isEmpty() && target > subjects.size()) {
            throw new IllegalArgumentException("DISTINCT " + target + " of " + subjects.size() + " subjects");
        }
    }

    /** Whether {@code subject} is one this criterion counts. */
    public boolean counts(String subject) {
        return subjects.isEmpty() || subjects.contains(subject);
    }

    /** The goal shown in progress: the target, or for EACH the number of subjects. */
    public long goal() {
        return measure == Measure.EACH ? subjects.size() : target;
    }
}
