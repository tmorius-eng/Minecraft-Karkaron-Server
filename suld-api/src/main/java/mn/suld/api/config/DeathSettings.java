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
 * @param newbieLevels          up to this level the lock is at most {@code newbieLockMinutes} (0 = off)
 * @param newbieLockMinutes     the new-player lock cap (audit 2026-10-11 ONB-002: a 5-minute lock at level 1 is a quit point)
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
        int woundHealMinutes,
        int newbieLevels,
        double newbieLockMinutes) {

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
        if (newbieLevels < 0 || newbieLockMinutes < 0) {
            throw new IllegalArgumentException("newbie lock levels/minutes must be >= 0: " + newbieLevels + " / " + newbieLockMinutes);
        }
        if (woundHealMinutes < 1) {
            throw new IllegalArgumentException("woundHealMinutes must be >= 1: " + woundHealMinutes);
        }
    }

    /** Without the new-player lock levers: defaults {@link #NEWBIE_LEVELS} / {@link #NEWBIE_LOCK_MINUTES}. */
    public DeathSettings(boolean enabled, int soulStateSeconds, double expLossFraction, double durabilityDamageFraction,
                         boolean dropLoot, double lootLossFraction, boolean allowFreeRevive,
                         mn.suld.api.death.DeathLock.Curve lockCurve, double lockMinMinutes, double lockMaxMinutes,
                         double woundPerDeath, double woundMax, int woundHealMinutes) {
        this(enabled, soulStateSeconds, expLossFraction, durabilityDamageFraction, dropLoot, lootLossFraction, allowFreeRevive,
                lockCurve, lockMinMinutes, lockMaxMinutes, woundPerDeath, woundMax, woundHealMinutes, NEWBIE_LEVELS, NEWBIE_LOCK_MINUTES);
    }

    /** Levels 1–5 are locked at most one minute (audit roadmap P1 #4). */
    public static final int NEWBIE_LEVELS = 5;
    public static final double NEWBIE_LOCK_MINUTES = 1.0;

    /** The settings without the lock/wound levers (the death costs only); lock and wound at their defaults. */
    public DeathSettings(boolean enabled, int soulStateSeconds, double expLossFraction, double durabilityDamageFraction,
                         boolean dropLoot, double lootLossFraction, boolean allowFreeRevive) {
        this(enabled, soulStateSeconds, expLossFraction, durabilityDamageFraction, dropLoot, lootLossFraction, allowFreeRevive,
                mn.suld.api.death.DeathLock.Curve.GEOMETRIC, 5, 30, 0.05, 0.15, 180);
    }

    /** The approved balance (docs/DEATH_AND_RECOVERY.md): geometric 5 min → 24 h lock, −5 % wound per death (max −15 %). */
    public static DeathSettings defaults() {
        return new DeathSettings(true, 30, 0.05, 0.25, true, 0.25, true,
                mn.suld.api.death.DeathLock.Curve.GEOMETRIC, 5, 30, 0.05, 0.15, 180, NEWBIE_LEVELS, NEWBIE_LOCK_MINUTES);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
