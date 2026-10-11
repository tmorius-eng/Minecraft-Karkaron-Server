package mn.suld.api.campaign;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A player's standing in one campaign ({@code suld_campaign_totals}), or a delta to add to it.
 *
 * @param clan        the clan at the time of the last contribution, or null
 * @param rewardTier  the tier paid or owed at resolution (0 none), null before resolution
 * @param claimedAt   when the reward was handed over, null while still owed
 */
public record Contribution(UUID player, String name, @Nullable UUID clan, long units, @Nullable Integer rewardTier, @Nullable Long claimedAt) {

    public static Contribution delta(UUID player, String name, @Nullable UUID clan, long units) {
        return new Contribution(player, name, clan, units, null, null);
    }

    public Contribution plus(long more) {
        return new Contribution(player, name, clan, units + more, rewardTier, claimedAt);
    }
}
