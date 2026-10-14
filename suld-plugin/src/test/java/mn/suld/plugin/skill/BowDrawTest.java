package mn.suld.plugin.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WP-01/WP-02 (the Мэргэн's hold-to-draw force) and WP-06 (a held right button never types R-R-R). */
class BowDrawTest {

    @Test
    void aQuickTapShootsAtTheTapForce() {
        for (int t = 0; t < SkillService.QUICK_TAP_TICKS; t++) assertEquals(SkillService.TAP_FORCE, SkillService.drawForce(t), 1e-6);
    }

    @Test
    void theForceNeverFallsAsTheDrawIsHeldLonger() {
        float prev = 0;
        for (int t = 0; t <= SkillService.MAX_DRAW_TICKS; t++) {
            float f = SkillService.drawForce(t);
            assertTrue(f >= prev, "force fell at tick " + t);
            assertTrue(f >= SkillService.TAP_FORCE && f <= 1.0f, "force out of range at tick " + t);
            prev = f;
        }
    }

    @Test
    void aFullDrawArrivesWithThePackPulling2Model() {
        // bow.json shows pulling_2 from use_duration 0.9 s (scale 0.05 per tick) = 18 ticks
        assertEquals(18, SkillService.FULL_DRAW_TICKS);
        assertTrue(SkillService.drawForce(SkillService.FULL_DRAW_TICKS - 1) < 1.0f);
        assertEquals(1.0f, SkillService.drawForce(SkillService.FULL_DRAW_TICKS), 1e-6);
        // CombatFeel's release ring needs 0.9: only a draw well into pulling_1/2 earns it
        assertTrue(SkillService.drawForce(13) < 0.9f);
    }

    @Test
    void aFullDrawFitsTheOneSecondShotPace() {
        assertTrue(SkillService.FULL_DRAW_TICKS * 50L <= SkillService.SHOT_MS);
    }

    @Test
    void aHeldButtonsThirdRepeatIsDropped() {
        // hold: press, +200 ms, +200 ms, … — the second click counts, the third and later never do
        assertFalse(SkillService.heldRepeat(null, Long.MAX_VALUE)); // the press
        assertFalse(SkillService.heldRepeat(Long.MAX_VALUE, 200)); // first repeat: R-R
        assertTrue(SkillService.heldRepeat(200L, 200)); // second repeat: would have made R-R-R
        assertTrue(SkillService.heldRepeat(200L, 210));
    }

    @Test
    void deliberateClicksAtAnyOtherRhythmStillCount() {
        assertFalse(SkillService.heldRepeat(120L, 130)); // a fast R-R-R
        assertFalse(SkillService.heldRepeat(200L, 150)); // one 4-tick gap, then a quicker click
        assertFalse(SkillService.heldRepeat(250L, 200));
        assertFalse(SkillService.heldRepeat(300L, 300));
    }
}
