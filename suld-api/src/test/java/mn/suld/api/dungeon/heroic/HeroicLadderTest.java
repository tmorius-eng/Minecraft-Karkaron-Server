package mn.suld.api.dungeon.heroic;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroicLadderTest {

    @Test
    void everyKeyLevelAddsTenPercentHealthAndEightPercentDamage() {
        assertEquals(1.5, HeroicLadder.healthMultiplier(5), 1e-9);
        assertEquals(1.4, HeroicLadder.damageMultiplier(5), 1e-9);
        assertEquals(1.1, HeroicLadder.healthMultiplier(0), 1e-9, "below +1 is +1");
        assertEquals(3.0, HeroicLadder.healthMultiplier(99), 1e-9, "above +20 is +20");
    }

    @Test
    void affixesComeOneByOneAndRotateWeekly() {
        int[] keys = {1, 2, 3, 4, 6, 7, 9, 10, 20};
        int[] counts = {0, 1, 1, 2, 2, 3, 3, 4, 4};
        for (int i = 0; i < keys.length; i++) assertEquals(counts[i], HeroicLadder.affixCount(keys[i]), "+" + keys[i]);
        List<SteppeAffix> a = HeroicLadder.affixes(7, "2026-W42");
        assertEquals(3, a.size());
        assertEquals(3, new HashSet<>(a).size(), "no affix twice");
        assertEquals(a, HeroicLadder.affixes(7, "2026-W42"), "the same week, the same affixes");
        boolean rotates = false;
        for (int w = 1; w <= 8; w++) rotates |= !HeroicLadder.affixes(2, "2026-W" + w).equals(HeroicLadder.affixes(2, "2026-W" + (w + 1)));
        assertTrue(rotates, "the weekly order changes");
        assertEquals(List.of(SteppeAffix.values()).size(), new HashSet<>(HeroicLadder.affixes(10, "2026-W42")).size(), "+10 has all four");
    }

    @Test
    void theClockCountsDeathsAndJudgesTheRun() {
        int limit = HeroicLadder.timeLimitSeconds(2, 180);
        assertEquals(450, limit);
        assertEquals(HeroicLadder.Outcome.TIMED, HeroicLadder.outcome(true, 400, 3, limit), "400 + 45 ≤ 450");
        assertEquals(HeroicLadder.Outcome.OVERTIME, HeroicLadder.outcome(true, 400, 4, limit), "400 + 60 > 450");
        assertEquals(HeroicLadder.Outcome.FAILED, HeroicLadder.outcome(false, 100, 0, limit));
    }

    @Test
    void aTimedRunOpensTheNextKeysAndNothingEverFalls() {
        int limit = 450;
        assertEquals(7, HeroicLadder.openAfter(5, 5, HeroicLadder.Outcome.TIMED, 300, 0, limit), "within 80 %: two keys up");
        assertEquals(6, HeroicLadder.openAfter(5, 5, HeroicLadder.Outcome.TIMED, 440, 0, limit), "timed: one up");
        assertEquals(6, HeroicLadder.openAfter(6, 3, HeroicLadder.Outcome.TIMED, 100, 0, limit), "a low key never lowers what is open");
        assertEquals(5, HeroicLadder.openAfter(5, 5, HeroicLadder.Outcome.OVERTIME, 500, 0, limit));
        assertEquals(5, HeroicLadder.openAfter(5, 5, HeroicLadder.Outcome.FAILED, 100, 0, limit), "a failed key does not fall");
        assertEquals(20, HeroicLadder.openAfter(20, 20, HeroicLadder.Outcome.TIMED, 100, 0, limit), "never above +20");
        assertEquals(1, HeroicLadder.openAfter(0, 0, HeroicLadder.Outcome.FAILED, 100, 0, limit), "+1 is always open");
        assertTrue(HeroicLadder.mayOpen(3, 5));
        assertTrue(HeroicLadder.mayOpen(5, 5));
        assertFalse(HeroicLadder.mayOpen(6, 5));
        assertFalse(HeroicLadder.mayOpen(0, 5));
    }

    @Test
    void onlyATimedRunPaysJade() {
        assertEquals(1, HeroicLadder.jade(1, HeroicLadder.Outcome.TIMED));
        assertEquals(2, HeroicLadder.jade(4, HeroicLadder.Outcome.TIMED));
        assertEquals(6, HeroicLadder.jade(20, HeroicLadder.Outcome.TIMED));
        assertEquals(0, HeroicLadder.jade(20, HeroicLadder.Outcome.OVERTIME));
        assertEquals(0, HeroicLadder.jade(20, HeroicLadder.Outcome.FAILED));
        assertNotEquals(0, SteppeAffix.packMultiplier(1) - 1.0);
        assertEquals(1.6, SteppeAffix.packMultiplier(9), 1e-9, "at most four neighbours count");
    }
}
