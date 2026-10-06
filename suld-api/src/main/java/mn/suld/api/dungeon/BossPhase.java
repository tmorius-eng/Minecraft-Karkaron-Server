package mn.suld.api.dungeon;

/**
 * A single phase of a boss fight, triggered when the boss's health drops
 * below {@link #healthThresholdPct}.
 *
 * @param healthThresholdPct fraction [0, 1] — phase activates when HP falls below this
 * @param attackMultiplier   multiplied against the boss's base attack from this phase onward
 * @param phaseName          Mongolian short label shown in the HUD (e.g. "Уурласан")
 */
public record BossPhase(
        double healthThresholdPct,
        double attackMultiplier,
        String phaseName) {

    public BossPhase {
        if (healthThresholdPct < 0 || healthThresholdPct > 1)
            throw new IllegalArgumentException("healthThresholdPct must be in [0, 1]");
        if (attackMultiplier <= 0)
            throw new IllegalArgumentException("attackMultiplier must be > 0");
    }
}
