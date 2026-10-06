package mn.suld.api.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatCalculatorTest {

    private final CombatCalculator calc = new CombatCalculator(50.0);

    @Test
    void noArmorNoCritIsRawDamage() {
        DamageResult r = calc.compute(100, 0.0, 2.0, 0, 0.99);
        assertEquals(100.0, r.finalDamage(), 1e-6);
        assertFalse(r.critical());
    }

    @Test
    void armorMitigatesWithDiminishingReturns() {
        // armor 50 with K=50 -> 50% mitigation
        assertEquals(0.5, calc.mitigation(50), 1e-9);
        DamageResult r = calc.compute(100, 0.0, 2.0, 50, 0.99);
        assertEquals(50.0, r.finalDamage(), 1e-6);
        assertEquals(50.0, r.mitigated(), 1e-6);
    }

    @Test
    void critAppliesMultiplierWhenRollBelowChance() {
        DamageResult r = calc.compute(100, 0.25, 2.0, 0, 0.10);
        assertTrue(r.critical());
        assertEquals(200.0, r.finalDamage(), 1e-6);
    }

    @Test
    void critBoundaryIsExclusive() {
        // roll == chance should NOT crit (roll < chance)
        assertFalse(calc.compute(100, 0.25, 2.0, 0, 0.25).critical());
    }
}
