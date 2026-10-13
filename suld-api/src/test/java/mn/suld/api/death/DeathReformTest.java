package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The death reform of 2026-10-13: beginners lose nothing, a soul has no timer, beacon, ritual, shard. */
class DeathReformTest {

    private final DeathSettings s = DeathSettings.defaults();

    @Test
    void theShippedSettingsAreTheReform() {
        assertEquals(10, s.beginnerLevels());
        assertTrue(s.untilRecovery(), "no timer: the soul comes back by acting");
        assertEquals(0.10, s.expLossFraction(), 1e-12);
        assertEquals(10, s.beaconMinutes(), 1e-12);
        assertEquals(0.70, s.beaconRefund(), 1e-12);
        assertEquals(90, s.ritualSeconds());
    }

    @Test
    void theFirstTenLevelsAreFullyProtected() {
        assertTrue(DeathRules.protectedDeath(1, 0, s));
        assertTrue(DeathRules.protectedDeath(10, 0, s));
        assertFalse(DeathRules.protectedDeath(11, 0, s), "from 11 a death costs");
        assertFalse(DeathRules.protectedDeath(5, 1, s), "an ascended character is never a beginner");
        DeathSettings off = new DeathSettings(false, 30, 0.1, 0.25, true, 0.25, true);
        assertFalse(DeathRules.protectedDeath(1, 0, off), "the system off: nothing to protect from");
        // the older constructors keep the old rules (no protection, a timed lock)
        DeathSettings old = new DeathSettings(true, 30, 0.05, 0.25, true, 0.25, true);
        assertFalse(DeathRules.protectedDeath(1, 0, old));
        assertFalse(old.untilRecovery());
    }

    @Test
    void aLevelElevenDeathCostsTenPercentOfTheBar() {
        mn.suld.api.progression.Progression after = DeathRules.afterDeath(new mn.suld.api.progression.Progression(11, 1000), s);
        assertEquals(11, after.level());
        assertEquals(900, after.expIntoLevel());
    }

    @Test
    void aSoulWithoutATimerIsMarkedSo() {
        DeathRecord r = new DeathRecord(UUID.randomUUID(), UUID.randomUUID(), 1, 1_000, DeathRecord.UNTIL_RECOVERY, "world", 0, 0, 0,
                "X", 20, 0, 0, DeathRecord.State.LOCKED, null, 1);
        assertTrue(r.untilRecovery());
        assertTrue(r.remaining(2_000) > 1_000_000_000L, "never runs out by itself");
        DeathRecord timed = new DeathRecord(UUID.randomUUID(), UUID.randomUUID(), 1, 1_000, 61_000, "world", 0, 0, 0,
                "X", 20, 0, 0, DeathRecord.State.LOCKED, null, 1);
        assertFalse(timed.untilRecovery());
    }
}
