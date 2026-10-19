package mn.suld.api.worldevent;

import java.util.Set;

/**
 * A server-wide timed objective (e.g. "kill 30 raiding wolves in 10 minutes").
 *
 * @param targetMobIds     SÜLD mob ids that count toward the goal
 * @param minContribution  kills a player needs to qualify for rewards
 * @param clanExpPerKill   clan EXP credited per qualifying kill (social progression)
 * @param clanSuccessBonus clan EXP each participating clan gets when the event succeeds
 * @param automatic        drawn by the automatic rotation (every 45 minutes one of these starts); an event that is not
 *                         automatic starts by staff or when a campaign unlocks it (docs/KHURALDAI.md)
 */
public record WorldEventDefinition(
        String id,
        String displayName,
        String description,
        Set<String> targetMobIds,
        int targetKills,
        int durationSeconds,
        long rewardExp,
        long rewardCurrency,
        int minContribution,
        long clanExpPerKill,
        long clanSuccessBonus,
        boolean automatic) {

    /** Staff-started or campaign-unlocked only (not in the automatic rotation). */
    public WorldEventDefinition(String id, String displayName, String description, Set<String> targetMobIds, int targetKills,
                                int durationSeconds, long rewardExp, long rewardCurrency, int minContribution, long clanExpPerKill,
                                long clanSuccessBonus) {
        this(id, displayName, description, targetMobIds, targetKills, durationSeconds, rewardExp, rewardCurrency, minContribution,
                clanExpPerKill, clanSuccessBonus, false);
    }

    public WorldEventDefinition {
        targetMobIds = Set.copyOf(targetMobIds);
        if (targetKills <= 0 || durationSeconds <= 0 || minContribution <= 0) {
            throw new IllegalArgumentException("targetKills, durationSeconds and minContribution must be > 0");
        }
        if (rewardExp < 0 || rewardCurrency < 0 || clanExpPerKill < 0 || clanSuccessBonus < 0) {
            throw new IllegalArgumentException("rewards must be >= 0");
        }
    }
}
