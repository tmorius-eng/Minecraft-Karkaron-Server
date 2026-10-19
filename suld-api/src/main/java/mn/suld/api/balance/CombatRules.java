package mn.suld.api.balance;

import mn.suld.api.clazz.PlayerClass;

/** Player-side combat numbers of progression v2: armour mitigation, level suppression, class health. */
public final class CombatRules {

    public static final double MITIGATION_CAP = 0.75;

    /**
     * PVP-01 (owner decision 2026-10-19): a consented fight between players (a duel, a fighting ground) runs the SÜLD
     * hit formula, spells included, at this share of the amount a mob would take, so a duel lasts longer than one
     * combo: players have 80–130 health, a mob of their level several hundred. Tuned on the dev server.
     */
    public static final double PVP_SCALAR = 0.6;

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

    /**
     * Damage a new player takes from mobs: ×0.5 at level 1, +0.1 per level, full from level 6. A fresh character has
     * ~20 HP and no armour, while the mob curve is fitted to a par player of 51 HP / 8 armour (audit 2026-10-11,
     * PROG-001), so the first fights would otherwise kill.
     */
    public static double newbieTaken(int playerLevel) {
        return Math.min(1.0, 0.5 + 0.1 * (Math.max(1, playerLevel) - 1));
    }

    /** Class base health grows 4 % per level (Баатар 40 → 134 at 60). */
    public static double classHealth(PlayerClass c, int level) {
        return c.baseHealth() * (1 + 0.04 * (Math.max(1, level) - 1));
    }

    /**
     * The class's own spell power on top of the SPELL_DAMAGE stat: Бөө, the caster, adds 40 % («Тэнгэрийн хүч»; 25 % until 2026-10-12); the
     * others 0. Live in SkillTreeService.spellDamageMultiplier, measured by the simulator.
     */
    public static double spellPassive(PlayerClass c) {
        return c == PlayerClass.BOO ? 0.40 : 0.0;
    }
}
