package mn.suld.api.mob;

/**
 * Mob power tiers. Higher tiers multiply base stats and loot quality. Used by
 * the mob engine to scale a {@link MobDefinition} and by loot/analytics.
 *
 * <p>{@link #statMultiplier()} is the one multiplier hand-set mobs use for health and attack.
 * Progression v2 ({@link mn.suld.api.balance.MobScaling}) multiplies health and damage separately:
 * {@link #healthMultiplier()} and {@link #damageMultiplier()}.
 */
public enum MobTier {
    NORMAL(1.0, 1.0, 1.0, 1.0),
    ELITE(2.5, 1.8, 2.5, 1.3),
    CHAMPION(5.0, 3.0, 5.0, 1.6),
    MYTHIC(10.0, 5.0, 10.0, 2.0),
    BOSS(25.0, 10.0, 40.0, 1.27),
    WORLD_BOSS(80.0, 25.0, 200.0, 1.524);

    private final double statMultiplier;
    private final double rewardMultiplier;
    private final double healthMultiplier;
    private final double damageMultiplier;

    MobTier(double statMultiplier, double rewardMultiplier, double healthMultiplier, double damageMultiplier) {
        this.statMultiplier = statMultiplier;
        this.rewardMultiplier = rewardMultiplier;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
    }

    public double statMultiplier() {
        return statMultiplier;
    }

    public double rewardMultiplier() {
        return rewardMultiplier;
    }

    public double healthMultiplier() {
        return healthMultiplier;
    }

    public double damageMultiplier() {
        return damageMultiplier;
    }

    public boolean boss() {
        return this == BOSS || this == WORLD_BOSS;
    }
}
