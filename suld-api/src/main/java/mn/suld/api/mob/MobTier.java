package mn.suld.api.mob;

/**
 * Mob power tiers. Higher tiers multiply base stats and loot quality. Used by
 * the mob engine to scale a {@link MobDefinition} and by loot/analytics.
 */
public enum MobTier {
    NORMAL(1.0, 1.0),
    ELITE(2.5, 1.8),
    CHAMPION(5.0, 3.0),
    MYTHIC(10.0, 5.0),
    BOSS(25.0, 10.0),
    WORLD_BOSS(80.0, 25.0);

    private final double statMultiplier;
    private final double rewardMultiplier;

    MobTier(double statMultiplier, double rewardMultiplier) {
        this.statMultiplier = statMultiplier;
        this.rewardMultiplier = rewardMultiplier;
    }

    public double statMultiplier() {
        return statMultiplier;
    }

    public double rewardMultiplier() {
        return rewardMultiplier;
    }
}
