package mn.suld.api.balance;

import mn.suld.api.clazz.PlayerClass;

/** Player-side combat numbers of progression v2: armour mitigation, level suppression, class health. */
public final class CombatRules {

    public static final double MITIGATION_CAP = 0.75;

    private CombatRules() {
    }

    /** Armour constant: grows with the attacker's level, so old armour stops being enough. */
    public static double armorK(int attackerLevel) {
        return 10 + 2.5 * attackerLevel;
    }

    /** Share of a hit that armour stops: a / (a + 10 + 2.5·attacker level), at most 75 %. */
    public static double mitigation(double armor, int attackerLevel) {
        double a = Math.max(0, armor);
        return Math.min(MITIGATION_CAP, a / (a + armorK(attackerLevel)));
    }

    /** Damage you deal to a mob {@code gap} levels above you: −4 % per level, floor 40 %. */
    public static double gapDealt(int gap) {
        return gap <= 0 ? 1.0 : Math.max(0.4, 1.0 - 0.04 * gap);
    }

    /** Damage you take from a mob {@code gap} levels above you: +8 % per level. */
    public static double gapTaken(int gap) {
        return gap <= 0 ? 1.0 : 1.0 + 0.08 * gap;
    }

    /** Class base health grows 4 % per level (Баатар 40 → 134 at 60). */
    public static double classHealth(PlayerClass c, int level) {
        return c.baseHealth() * (1 + 0.04 * (Math.max(1, level) - 1));
    }
}
