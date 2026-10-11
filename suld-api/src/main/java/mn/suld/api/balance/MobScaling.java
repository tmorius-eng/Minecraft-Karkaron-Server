package mn.suld.api.balance;

import mn.suld.api.mob.MobTier;

/**
 * Designed mob stats from the level alone (docs/DIFFICULTY_CURVE.md). They are fitted to the median ("par") player
 * of the simulation, so a fight at par always reads the same: a NORMAL mob dies in about 2.5 s and one fight costs
 * about 15 % of max health. Tiers multiply health and damage separately.
 */
public final class MobScaling {

    static final double TTK_NORMAL = 2.5, DANGER_NORMAL = 0.15, SHARE = 0.5, ENGAGED = 1.2, INTERVAL = 1.5;

    private MobScaling() {
    }

    /** Base EXP of a NORMAL mob; tiers multiply by {@link MobTier#rewardMultiplier()}. */
    public static long baseExp(int level) {
        return Math.round(30 + 10.0 * level + 0.05 * level * level);
    }

    public static long exp(int level, MobTier tier) {
        return Math.round(baseExp(level) * tier.rewardMultiplier());
    }

    /** Coins a kill drops: (1 + 0.4·L), × the reward multiplier above NORMAL. */
    public static long coins(int level, MobTier tier) {
        return Math.round((1 + 0.4 * level) * (tier == MobTier.NORMAL ? 1 : tier.rewardMultiplier()));
    }

    /** Par damage per second at a level (fit of the median player: 10 + 2.75·L^1.36). */
    public static double parDps(int level) {
        return 10 + 2.75 * Math.pow(level, 1.36);
    }

    /** Par max health (46.5 + 4.5·L + 0.113·L²). */
    public static double parHp(int level) {
        return 46.5 + 4.5 * level + 0.113 * level * level;
    }

    /** Par armour (7 + 0.97·L). */
    public static double parArmor(int level) {
        return 7 + 0.97 * level;
    }

    public static double parRegen(int level) {
        return 0.3 + 0.05 * level;
    }

    /** Health of a NORMAL mob. */
    public static double baseHealth(int level) {
        return Math.round(TTK_NORMAL * parDps(level));
    }

    /** Damage per hit of a NORMAL mob, from the danger target at par. */
    public static double baseDamage(int level) {
        double inc = (DANGER_NORMAL * parHp(level) / TTK_NORMAL + parRegen(level)) / ENGAGED;
        double mit = CombatRules.mitigation(parArmor(level), level);
        return Math.round(inc * INTERVAL / ((1 - mit) * SHARE) * 10) / 10.0;
    }

    public static double health(int level, MobTier tier) {
        return baseHealth(level) * tier.healthMultiplier();
    }

    public static double damage(int level, MobTier tier) {
        return baseDamage(level) * tier.damageMultiplier();
    }

    /**
     * Seconds between a vanilla host's hits (difficulty hard): melee mobs swing about once a second, a stray's bow
     * every 2 s, a pillager's crossbow every 2.5 s.
     */
    public static double hostInterval(String entityType) {
        return switch (entityType == null ? "" : entityType) {
            case "STRAY", "SKELETON" -> 2.0;
            case "PILLAGER" -> 2.5;
            case "RAVAGER" -> 1.5;
            default -> 1.0;
        };
    }

    public static boolean rangedHost(String entityType) {
        return "STRAY".equals(entityType) || "SKELETON".equals(entityType) || "PILLAGER".equals(entityType);
    }

    /**
     * The designed damage per hit assumes a hit every 1.5 s (2 s ranged, 2 s for bosses). Scaling each real hit by
     * host interval / design interval keeps the damage per second the design's whatever the host's swing speed.
     */
    public static double hitScale(String entityType, boolean boss) {
        double design = boss || rangedHost(entityType) ? 2.0 : INTERVAL;
        return hostInterval(entityType) / design;
    }

    /** Boss health is sized for the dungeon's recommended party: ×0.44 for a solo dungeon … ×1.0 for four. */
    public static double bossPartyScale(int recommendedParty) {
        return 0.25 + 0.75 * Math.max(1, Math.min(4, recommendedParty)) / 4.0;
    }
}
