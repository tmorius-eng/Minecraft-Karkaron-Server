package mn.suld.api.config;

/**
 * Hardcore-PvE death-system tuning. Every lever is data-driven so operators can
 * dial the harshness without code changes.
 *
 * <p>Deliberately contains no real-money mechanics: resurrection is either a
 * free in-game recovery path or an administrative action (see GAME_DESIGN.md).
 *
 * @param enabled               master switch for the custom death system
 * @param soulStateSeconds      duration of the temporary soul/death state
 * @param expLossFraction       fraction of current-level progress lost on death [0,1]
 * @param durabilityDamageFraction gear durability damage applied on death [0,1]
 * @param dropLoot              whether non-soulbound items drop on death
 * @param lootLossFraction      fraction of droppable items actually lost [0,1]
 * @param allowFreeRevive       whether free in-game revive paths are enabled
 */
public record DeathSettings(
        boolean enabled,
        int soulStateSeconds,
        double expLossFraction,
        double durabilityDamageFraction,
        boolean dropLoot,
        double lootLossFraction,
        boolean allowFreeRevive) {

    public DeathSettings {
        expLossFraction = clamp01(expLossFraction);
        durabilityDamageFraction = clamp01(durabilityDamageFraction);
        lootLossFraction = clamp01(lootLossFraction);
        if (soulStateSeconds < 0) {
            throw new IllegalArgumentException("soulStateSeconds must be >= 0: " + soulStateSeconds);
        }
    }

    public static DeathSettings defaults() {
        return new DeathSettings(true, 30, 0.10, 0.25, true, 0.5, true);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
