package mn.suld.api.progression;

/**
 * Maps character levels to the experience required to advance.
 *
 * <p>Implementations are pure functions of level and must be deterministic and
 * thread-safe (effectively immutable). The curve is <em>data-driven</em>:
 * concrete parameters come from configuration, never from hard-coded constants
 * scattered through gameplay code.
 */
public interface LevelCurve {

    /** The highest reachable level (inclusive). Must be {@code >= 1}. */
    int maxLevel();

    /**
     * Experience required to advance from {@code level} to {@code level + 1}.
     *
     * @param level the current level, in {@code [1, maxLevel()]}
     * @return a positive amount, or {@code 0} when {@code level >= maxLevel()}
     *         (no further advancement is possible)
     * @throws IllegalArgumentException if {@code level < 1}
     */
    long expForLevel(int level);
}
