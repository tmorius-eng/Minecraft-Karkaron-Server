package mn.suld.api.balance;

import mn.suld.api.progression.LevelCurve;
import mn.suld.api.progression.PolynomialLevelCurve;

/**
 * Progression v2 (docs/PROGRESSION_BALANCE_SPEC.md): the curve every other v2 rule is measured against. EXP to go
 * from L to L+1 = round(base · L^2.2), cap 60. The full design ({@code ProposedRules}: 42 chapters, band raids, a world
 * boss, 12 landmarks per region) uses base 315 (46,943,870 EXP, ≈ 200 h). The live game has less content today (18
 * chapters, no world boss), so its base is tuned by the simulation ({@code simTune --args="--tune --live"}) to the same
 * ≈ 200 efficient hours: 190 (28,315,352 EXP to 60). Raise it again as Act II and the world boss land.
 */
public final class Balance {

    public static final double CURVE_BASE = 190, CURVE_EXP = 2.2;
    /** The full design's base (ProposedRules). */
    public static final double DESIGN_BASE = 315;
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
