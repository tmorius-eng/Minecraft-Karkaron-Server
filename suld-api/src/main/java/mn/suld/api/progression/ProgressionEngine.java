package mn.suld.api.progression;

/**
 * Pure, stateless calculator for experience and levelling, driven by a
 * {@link LevelCurve}.
 *
 * <p>This class owns all levelling arithmetic so the rules live in exactly one
 * place and can be unit-tested without a server. It holds no per-player state;
 * callers pass in a {@link Progression} snapshot and receive a new one. It is
 * therefore immutable and safe to share across threads.
 */
public final class ProgressionEngine {

    private final LevelCurve curve;

    public ProgressionEngine(LevelCurve curve) {
        this.curve = java.util.Objects.requireNonNull(curve, "curve");
    }

    public LevelCurve curve() {
        return curve;
    }

    /**
     * Grant experience, rolling the character up through as many levels as the
     * amount allows (capped at {@link LevelCurve#maxLevel()}).
     *
     * @param current the starting progression
     * @param amount  experience to add; must be {@code >= 0}
     * @return the result, including the new progression and how many levels were
     *         gained
     * @throws IllegalArgumentException if {@code amount < 0}
     */
    public ExpGainResult grant(Progression current, long amount) {
        java.util.Objects.requireNonNull(current, "current");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }

        int maxLevel = curve.maxLevel();

        if (current.level() >= maxLevel) {
            // Already capped: nothing accrues.
            return new ExpGainResult(current, current, 0, true, amount);
        }

        int level = current.level();
        long into = current.expIntoLevel() + amount;
        int gained = 0;

        long needed = curve.expForLevel(level);
        while (level < maxLevel && needed > 0 && into >= needed) {
            into -= needed;
            level++;
            gained++;
            needed = curve.expForLevel(level);
        }

        boolean reachedMax = level >= maxLevel;
        long wasted = 0L;
        if (reachedMax) {
            // No progress bar beyond the cap; discard the remainder.
            wasted = into;
            into = 0L;
        }

        Progression after = new Progression(level, into);
        return new ExpGainResult(current, after, gained, reachedMax, wasted);
    }

    /**
     * Experience still required to reach the next level, or {@code 0} if the
     * character is at the cap.
     */
    public long expToNextLevel(Progression progression) {
        java.util.Objects.requireNonNull(progression, "progression");
        if (progression.level() >= curve.maxLevel()) {
            return 0L;
        }
        long needed = curve.expForLevel(progression.level());
        return Math.max(0L, needed - progression.expIntoLevel());
    }

    /**
     * Progress toward the next level as a fraction in {@code [0.0, 1.0]}. Returns
     * {@code 1.0} at the level cap. Useful for rendering XP bars.
     */
    public double progressFraction(Progression progression) {
        java.util.Objects.requireNonNull(progression, "progression");
        if (progression.level() >= curve.maxLevel()) {
            return 1.0;
        }
        long needed = curve.expForLevel(progression.level());
        if (needed <= 0) {
            return 1.0;
        }
        double fraction = (double) progression.expIntoLevel() / (double) needed;
        return Math.max(0.0, Math.min(1.0, fraction));
    }
}
