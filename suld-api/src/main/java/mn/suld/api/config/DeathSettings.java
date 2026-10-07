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
 * @param allowFreeRevive       whether the lock ends by itself (false: only an admin ends it)
 * @param lockCurve             how the real-time death lock grows with level (docs/DEATH_AND_RECOVERY.md)
 * @param lockMinMinutes        lock at level 1
 * @param lockMaxMinutes        lock at level 60 and in Ascension
 * @param woundPerDeath         effective-stat penalty of the class gear per death [0,1]
 * @param woundMax              cap of the wound [0,1]
 * @param woundHealMinutes      active minutes that heal one wound step
 */
public record DeathSettings(
        boolean enabled,
        int soulStateSeconds,
        double expLossFraction,
        double durabilityDamageFraction,
        boolean dropLoot,
        double lootLossFraction,
        boolean allowFreeRevive,
        mn.suld.api.death.DeathLock.Curve lockCurve,
        double lockMinMinutes,
        double lockMaxMinutes,
        double woundPerDeath,
        double woundMax,
        int woundHealMinutes) {

    public DeathSettings {
        expLossFraction = clamp01(expLossFraction);
        durabilityDamageFraction = clamp01(durabilityDamageFraction);
        lootLossFraction = clamp01(lootLossFraction);
        woundPerDeath = clamp01(woundPerDeath);
        woundMax = clamp01(woundMax);
        if (lockCurve == null) lockCurve = mn.suld.api.death.DeathLock.Curve.GEOMETRIC;
        if (soulStateSeconds < 0) {
            throw new IllegalArgumentException("soulStateSeconds must be >= 0: " + soulStateSeconds);
        }
        if (lockMinMinutes < 0 || lockMaxMinutes < lockMinMinutes) {
            throw new IllegalArgumentException("need 0 <= lock-min <= lock-max: " + lockMinMinutes + " / " + lockMaxMinutes);
        }
        if (woundHealMinutes < 1) {
            throw new IllegalArgumentException("woundHealMinutes must be >= 1: " + woundHealMinutes);
        }
    }

    /** The settings without the lock/wound levers (the death costs only); lock and wound at their defaults. */
    public DeathSettings(boolean enabled, int soulStateSeconds, double expLossFraction, double durabilityDamageFraction,
                         boolean dropLoot, double lootLossFraction, boolean allowFreeRevive) {
        this(enabled, soulStateSeconds, expLossFraction, durabilityDamageFraction, dropLoot, lootLossFraction, allowFreeRevive,
                mn.suld.api.death.DeathLock.Curve.GEOMETRIC, 5, 1440, 0.05, 0.15, 180);
    }

    /** The approved balance (docs/DEATH_AND_RECOVERY.md): geometric 5 min → 24 h lock, −5 % wound per death (max −15 %). */
    public static DeathSettings defaults() {
        return new DeathSettings(true, 30, 0.05, 0.25, true, 0.25, true,
                mn.suld.api.death.DeathLock.Curve.GEOMETRIC, 5, 1440, 0.05, 0.15, 180);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
