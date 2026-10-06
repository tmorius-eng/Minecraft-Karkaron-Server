package mn.suld.api.progression;

/**
 * Immutable snapshot of a character's level and progress toward the next level.
 *
 * <p>The canonical, persisted state is {@code (level, expIntoLevel)} rather than
 * a single cumulative total. Storing the level explicitly means a later
 * rebalance of the {@link LevelCurve} never silently demotes a player: their
 * level is preserved and only the remaining progress bar is reinterpreted.
 *
 * <p>Being a record, instances are immutable; progression changes produce a new
 * value via {@link ProgressionEngine}.
 *
 * @param level       current level, {@code >= 1}
 * @param expIntoLevel experience accumulated toward the next level, {@code >= 0}
 */
public record Progression(int level, long expIntoLevel) {

    public Progression {
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1: " + level);
        }
        if (expIntoLevel < 0) {
            throw new IllegalArgumentException("expIntoLevel must be >= 0: " + expIntoLevel);
        }
    }

    /** A fresh character at level 1 with no experience. */
    public static Progression initial() {
        return new Progression(1, 0L);
    }
}
