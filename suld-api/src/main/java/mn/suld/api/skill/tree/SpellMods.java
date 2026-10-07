package mn.suld.api.skill.tree;

import mn.suld.api.skill.Spell;

/** Which {@link ModKey}s each spell understands (content is validated against this; the engine implements it). */
public final class SpellMods {

    private SpellMods() {
    }

    /** Spells that affect enemies around without damaging them (they still carry the riders). */
    private static boolean affectsEnemies(Spell s) {
        return s.damageMultiplier() > 0 || s == Spell.DAINY_KHASHGIRAAN || s == Spell.CHONYN_NUD || s == Spell.UKHRAKH_USRELT;
    }

    public static boolean supports(Spell s, ModKey k) {
        return switch (k) {
            case COST_PCT, SHIELD, HASTE -> true;
            case DAMAGE_PCT -> s.damageMultiplier() > 0 || s == Spell.SUNSNII_ZALBIRAL;
            case ECHO_PCT -> s.damageMultiplier() > 0 && s != Spell.OLON_SUM;
            case RADIUS_PCT -> switch (s) {
                case TENGERIIN_SUM, ZHADNY_SHIDELT, OLON_SUM, ONGONY_DUUDLAGA -> false;
                default -> affectsEnemies(s) || s == Spell.SUNSNII_ZALBIRAL;
            };
            case BURN, SLOW, WEAKEN, VULN, HEAL_ON_HIT, KNOCKUP, REFUND, PULL -> affectsEnemies(s);
        };
    }
}
