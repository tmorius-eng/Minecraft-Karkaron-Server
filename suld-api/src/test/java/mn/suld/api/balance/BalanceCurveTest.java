package mn.suld.api.balance;

import mn.suld.api.progression.PolynomialLevelCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PROG-002: rewards are priced on the configured curve, the one levelling uses. */
class BalanceCurveTest {

    @Test
    void theConfiguredCurvePricesTheRewards() {
        try {
            long design = Rewards.chapterExp(Balance.curve(), 20);
            Balance.configure(new PolynomialLevelCurve(Balance.CURVE_BASE * 2, Balance.CURVE_EXP, Balance.MAX_LEVEL));
            assertTrue(Rewards.chapterExp(Balance.curve(), 20) > design, "a steeper configured curve pays more per chapter");
            assertThrows(IllegalArgumentException.class, () -> Balance.configure(null));
        } finally {
            Balance.configure(Balance.designCurve());
        }
        assertEquals(Balance.designCurve().expForLevel(30), Balance.curve().expForLevel(30));
    }
}
