package mn.suld.api.campaign;

import java.util.List;

/**
 * One campaign the Хуралдай can choose (content/campaigns.json). A week-long shared goal with three personal tiers,
 * podium multipliers for the top three, clan EXP, and the world effect its success leaves behind.
 *
 * @param tiers            personal units for the bronze / silver / gold tier (ascending; gold ≤ 5 % of the target)
 * @param tierRewards      EXP and coins paid for reaching each tier (index 0 = bronze)
 * @param podium           multipliers for the three biggest contributors (applied to their tier reward)
 * @param clanExpPerUnit   clan EXP per unit a member contributes
 * @param clanSuccessBonus clan EXP once per clan with a tier-1+ member when the campaign succeeds
 * @param effect           the world change on success, or null for none
 * @param cooldownCycles   weeks this campaign stays out of the ballot after winning
 */
public record CampaignDefinition(String id, String name, String description, CampaignGoal goal, int[] tiers,
                                 List<TierReward> tierRewards, double[] podium, long clanExpPerUnit, long clanSuccessBonus,
                                 WorldEffect effect, int cooldownCycles) {

    public record TierReward(long exp, long coins) {
        public TierReward {
            if (exp < 0 || coins < 0) throw new IllegalArgumentException("rewards must be >= 0");
        }
    }

    public static final int TIERS = 3;
    /** Gold is at most this share of the server goal, so a 2–3 session player can reach it (catch-up). */
    public static final double GOLD_MAX_SHARE = 0.05;

    public CampaignDefinition {
        if (id == null || !id.matches("campaign\\.[a-z0-9_]+")) throw new IllegalArgumentException("campaign id " + id);
        if (name == null || name.isBlank()) throw new IllegalArgumentException(id + ": name");
        description = description == null ? "" : description;
        if (goal == null) throw new IllegalArgumentException(id + ": goal");
        if (tiers == null || tiers.length != TIERS) throw new IllegalArgumentException(id + ": tiers needs " + TIERS + " values");
        for (int i = 0; i < TIERS; i++) {
            if (tiers[i] <= 0 || (i > 0 && tiers[i] <= tiers[i - 1])) throw new IllegalArgumentException(id + ": tiers must be positive and ascending");
        }
        if (tiers[TIERS - 1] > goal.target() * GOLD_MAX_SHARE) throw new IllegalArgumentException(id + ": the gold tier is more than 5 % of the target");
        if (tierRewards == null || tierRewards.size() != TIERS) throw new IllegalArgumentException(id + ": tierRewards needs " + TIERS + " values");
        if (podium == null || podium.length != 3) throw new IllegalArgumentException(id + ": podium needs 3 multipliers");
        for (double m : podium) if (m < 1 || m > 3) throw new IllegalArgumentException(id + ": podium multipliers are 1..3");
        if (clanExpPerUnit < 0 || clanSuccessBonus < 0) throw new IllegalArgumentException(id + ": clan EXP must be >= 0");
        if (cooldownCycles < 0 || cooldownCycles > 12) throw new IllegalArgumentException(id + ": cooldownCycles 0..12");
        tiers = tiers.clone();
        podium = podium.clone();
        tierRewards = List.copyOf(tierRewards);
    }

    /** The tier (1..3) a personal contribution reaches, 0 for none. */
    public int tierOf(long units) {
        int tier = 0;
        for (int i = 0; i < TIERS; i++) if (units >= tiers[i]) tier = i + 1;
        return tier;
    }

    /** The tier's reward (tier 1..3). */
    public TierReward reward(int tier) {
        return tierRewards.get(Math.max(1, Math.min(TIERS, tier)) - 1);
    }

    /** The podium multiplier of a 1-based rank; 1.0 past third place. */
    public double podiumMultiplier(int rank) {
        return rank >= 1 && rank <= podium.length ? podium[rank - 1] : 1.0;
    }
}
