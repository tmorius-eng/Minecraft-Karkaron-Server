package mn.suld.api.balance;

import mn.suld.api.progression.LevelCurve;
import mn.suld.api.progression.PolynomialLevelCurve;

/**
 * Progression v2 (docs/PROGRESSION_BALANCE_SPEC.md): the curve every other v2 rule is measured against. EXP to go
 * from L to L+1 = round(315 · L^2.2), cap 60, 46,943,870 EXP in all: about 200 active hours of efficient play.
 * The simulation ({@code ProposedRules}) and the game both read the numbers from this package.
 */
public final class Balance {

    public static final double CURVE_BASE = 315, CURVE_EXP = 2.2;
    public static final int MAX_LEVEL = 60;

    private Balance() {
    }

    public static LevelCurve curve() {
        return new PolynomialLevelCurve(CURVE_BASE, CURVE_EXP, MAX_LEVEL);
    }

    /** EXP of level {@code level} on {@code c}, clamped to 1..59 (level 60 is priced as 59). */
    public static long need(LevelCurve c, int level) {
        return c.expForLevel(Math.max(1, Math.min(c.maxLevel() - 1, level)));
    }
}
