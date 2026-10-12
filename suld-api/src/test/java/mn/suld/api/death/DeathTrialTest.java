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
    }

    @Test
    void cooldownCountsDown() {
        assertEquals(300, t.waitSeconds(0));
        assertEquals(1, t.waitSeconds(299.5));
        assertEquals(0, t.waitSeconds(301));
    }

    @Test
    void wolvesGrowWithLevelAndNoLockOutlastsHalfAnHour() {
        assertTrue(DeathTrial.wolfHealth(60) > DeathTrial.wolfHealth(1));
        assertTrue(DeathTrial.wolfBite(60) > DeathTrial.wolfBite(1));
        DeathSettings s = DeathSettings.defaults();
        for (int level = 1; level <= 60; level++) assertTrue(DeathLock.minutes(s, level, 0) <= 30, "level " + level);
        assertEquals(30, DeathLock.minutes(s, 30, 3), 1e-9, "Ascension = the top: 30 min");
    }
}
