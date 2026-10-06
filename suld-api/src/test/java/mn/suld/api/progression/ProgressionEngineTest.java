package mn.suld.api.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionEngineTest {

    // Linear curve: level l -> l+1 costs 100*l. maxLevel 5.
    private final ProgressionEngine engine =
            new ProgressionEngine(new PolynomialLevelCurve(100.0, 1.0, 5));

    @Test
    void grantingExactThresholdLevelsUpOnce() {
        ExpGainResult r = engine.grant(Progression.initial(), 100);
        assertEquals(2, r.after().level());
        assertEquals(0, r.after().expIntoLevel());
        assertEquals(1, r.levelsGained());
        assertTrue(r.leveledUp());
        assertFalse(r.reachedMax());
    }

    @Test
    void partialGrantAccumulatesWithoutLevelling() {
        ExpGainResult r = engine.grant(Progression.initial(), 40);
        assertEquals(1, r.after().level());
        assertEquals(40, r.after().expIntoLevel());
        assertEquals(0, r.levelsGained());
    }

    @Test
    void largeGrantRollsThroughMultipleLevels() {
        // From L2 with 50 banked, +250 = 300. L2->3 costs 200 (leaves 100),
        // L3->4 costs 300 (100 < 300, stop). Result: L3 with 100.
        ExpGainResult r = engine.grant(new Progression(2, 50), 250);
        assertEquals(3, r.after().level());
        assertEquals(100, r.after().expIntoLevel());
        assertEquals(1, r.levelsGained());
    }

    @Test
    void reachingCapDiscardsOverflow() {
        ExpGainResult r = engine.grant(new Progression(4, 0), 100_000);
        assertEquals(5, r.after().level());
        assertEquals(0, r.after().expIntoLevel());
        assertTrue(r.reachedMax());
        assertTrue(r.wastedExp() > 0);
    }

    @Test
    void grantingAtCapIsNoProgress() {
        ExpGainResult r = engine.grant(new Progression(5, 0), 500);
        assertEquals(5, r.after().level());
        assertEquals(0, r.levelsGained());
        assertTrue(r.reachedMax());
        assertEquals(500, r.wastedExp());
    }

    @Test
    void expToNextAndProgressFraction() {
        Progression p = new Progression(1, 25); // needs 100 at level 1
        assertEquals(75, engine.expToNextLevel(p));
        assertEquals(0.25, engine.progressFraction(p), 1e-9);

        Progression capped = new Progression(5, 0);
        assertEquals(0, engine.expToNextLevel(capped));
        assertEquals(1.0, engine.progressFraction(capped), 1e-9);
    }

    @Test
    void negativeGrantRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine.grant(Progression.initial(), -1));
    }
}
