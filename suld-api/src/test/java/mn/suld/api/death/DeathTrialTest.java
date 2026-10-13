package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeathTrialTest {

    private final DeathTrial t = DeathTrial.DEFAULT;

    @Test
    void winningNeedsEveryWolfInTime() {
        assertEquals(DeathTrial.Outcome.RUNNING, t.outcome(2, 100, false, false));
        assertEquals(DeathTrial.Outcome.WON, t.outcome(3, 179, false, false));
        assertEquals(DeathTrial.Outcome.TIMED_OUT, t.outcome(2, 180, false, false));
        assertEquals(DeathTrial.Outcome.FALLEN, t.outcome(3, 10, true, false), "falling beats the last kill");
        assertEquals(DeathTrial.Outcome.LEFT, t.outcome(0, 1, false, true));
        // the lock ended mid-fight: stop at once, even on the tick the last wolf fell (the player is already alive)
        assertEquals(DeathTrial.Outcome.LOCK_ENDED, t.outcome(1, 30, false, false, false));
        assertEquals(DeathTrial.Outcome.LOCK_ENDED, t.outcome(3, 30, true, false, false));
        assertEquals(DeathTrial.Outcome.LEFT, t.outcome(0, 1, false, true, false), "a quit is still a quit");
        assertEquals(DeathTrial.Outcome.RUNNING, t.outcome(2, 100, false, false, true));
    }

    @Test
    void cooldownCountsDown() {
        // a configured wait counts down (the rule itself)
        DeathTrial waits = new DeathTrial(3, 180, 300);
        assertEquals(300, waits.waitSeconds(0));
        assertEquals(1, waits.waitSeconds(299.5));
        assertEquals(0, waits.waitSeconds(301));
    }

    @Test
    void theShippedTrialHasNoWaitAfterAFailure() {
        // 2026-10-13 death reform: a soul may try again at once (no passive waiting)
        assertEquals(0, t.cooldownSeconds());
        assertEquals(0, t.waitSeconds(0));
        assertEquals(SpiritTrialState.FAILED, SpiritTrialState.of(DeathTrial.Outcome.TIMED_OUT));
        assertTrue(SpiritTrialState.FAILED.mayStart());
        assertFalse(SpiritTrialState.FIGHTING.mayStart());
        assertEquals(SpiritTrialState.PASSED, SpiritTrialState.of(DeathTrial.Outcome.WON));
        assertEquals(SpiritTrialState.NOT_IN_TRIAL, SpiritTrialState.of(DeathTrial.Outcome.LOCK_ENDED));
        assertEquals(SpiritTrialState.FIGHTING, SpiritTrialState.of(DeathTrial.Outcome.RUNNING));
    }

    @Test
    void wolvesGrowWithLevelAndNoLockOutlastsHalfAnHour() {
        assertTrue(DeathTrial.wolfHealth(60) > DeathTrial.wolfHealth(1));
        // sized to the soul's attack: five plain hits, never below the level floor
        assertEquals(5 * 40.0, DeathTrial.wolfHealth(20, 40), 1e-9);
        assertEquals(DeathTrial.wolfHealth(20), DeathTrial.wolfHealth(20, 1), 1e-9);
        assertTrue(DeathTrial.wolfBite(60) > DeathTrial.wolfBite(1));
        DeathSettings s = DeathSettings.defaults();
        for (int level = 1; level <= 60; level++) assertTrue(DeathLock.minutes(s, level, 0) <= 30, "level " + level);
        assertEquals(30, DeathLock.minutes(s, 30, 3), 1e-9, "Ascension = the top: 30 min");
    }
}
