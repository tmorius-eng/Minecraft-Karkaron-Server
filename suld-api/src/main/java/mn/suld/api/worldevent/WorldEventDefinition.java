package mn.suld.api.worldevent;

import java.util.Set;

/**
 * A server-wide timed objective (e.g. "kill 30 raiding wolves in 10 minutes").
 *
 * @param targetMobIds     SÜLD mob ids that count toward the goal
 * @param minContribution  kills a player needs to qualify for rewards
 * @param clanExpPerKill   clan EXP credited per qualifying kill (social progression)
 * @param clanSuccessBonus clan EXP each participating clan gets when the event succeeds
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
        long clanSuccessBonus) {

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
