package mn.suld.api.progression;

/**
 * Moves a character from one {@link LevelCurve} to another without taking anything away: the level is kept and the
 * bar keeps the same fraction of the (new) level's need. Nobody gains a level from a migration either.
 */
public final class CurveMigration {

    private CurveMigration() {
    }

    public static Progression rescale(Progression p, LevelCurve from, LevelCurve to) {
        int level = Math.min(p.level(), to.maxLevel());
        long oldNeed = from.expForLevel(Math.min(p.level(), from.maxLevel()));
        long newNeed = to.expForLevel(level);
        if (oldNeed <= 0 || newNeed <= 0) return new Progression(level, 0);
        double fraction = Math.max(0, Math.min(1, p.expIntoLevel() / (double) oldNeed));
        long into = (long) Math.floor(fraction * newNeed);
        return new Progression(level, Math.min(into, newNeed - 1));
    }

    /** Total EXP to go from level 1 to {@code level} on a curve. */
    public static long totalTo(LevelCurve c, int level) {
        long sum = 0;
        for (int l = 1; l < Math.min(level, c.maxLevel() + 1); l++) sum += c.expForLevel(l);
        return sum;
    }
}
