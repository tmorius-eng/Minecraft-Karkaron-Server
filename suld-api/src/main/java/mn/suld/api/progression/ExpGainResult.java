package mn.suld.api.progression;

/**
 * Outcome of granting experience, produced by {@link ProgressionEngine}.
 *
 * @param before        progression before the grant
 * @param after         progression after the grant
 * @param levelsGained  number of levels crossed ({@code >= 0})
 * @param reachedMax    whether this grant pushed the character to the level cap
 * @param wastedExp     experience discarded because the cap was already (or
 *                      became) reached; {@code 0} in the normal case
 */
public record ExpGainResult(
        Progression before,
        Progression after,
        int levelsGained,
        boolean reachedMax,
        long wastedExp) {

    public boolean leveledUp() {
        return levelsGained > 0;
    }
}
