package mn.suld.api.activity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The window classifier with synthetic signal streams (docs/ACTIVE_PLAYTIME_SPEC.md "Tests"). */
class ActivityTrackerTest {

    @Test
    void idleAndMenuOnlyMinutesDoNotCount() {
        ActivityTracker t = new ActivityTracker();
        for (int i = 0; i < 14; i++) assertEquals(ActivityTracker.Reason.IDLE, t.closeMinute().reason());
        assertFalse(t.away());
        t.closeMinute();
        assertTrue(t.away(), "15 silent minutes: away");
        t.signal(ActivitySignal.KILL);
        assertTrue(t.closeMinute().active());
        assertFalse(t.away());
    }

    @Test
    void oneStrongSignalOrTwoFamilies() {
        ActivityTracker t = new ActivityTracker();
        t.signal(ActivitySignal.INVENTORY);
        assertEquals(ActivityTracker.Reason.TOO_WEAK, t.closeMinute().reason(), "inventory shuffling alone");
        t.signal(ActivitySignal.INVENTORY);
        t.signal(ActivitySignal.BLOCK);
        assertFalse(t.closeMinute().active(), "two signals of one family");
        t.signal(ActivitySignal.CRAFT);
        t.signal(ActivitySignal.INVENTORY);
        ActivityTracker.Verdict v = t.closeMinute();
        assertTrue(v.active());
        assertEquals(ActivityCategory.CRAFTING, v.category());
        t.signal(ActivitySignal.DUNGEON);
        t.signal(ActivitySignal.KILL);
        assertEquals(ActivityCategory.DUNGEON, t.closeMinute().category());
    }

    @Test
    void ownMovementCountsButAWaterCurrentOrACartDoesNot() {
        ActivityTracker t = new ActivityTracker();
        for (int s = 0; s < 12; s++) t.move(s * 2.0, 0, false, s * 5_000L); // walked 22 blocks
        t.signal(ActivitySignal.INVENTORY);
        assertTrue(t.closeMinute().active(), "movement + interaction = two families");
        long base = 100_000;
        for (int s = 0; s < 12; s++) t.move(s * 8.0, 0, true, base + s * 5_000L);
        t.signal(ActivitySignal.INVENTORY);
        assertFalse(t.closeMinute().active(), "carried by a current / rail");
        for (int s = 0; s < 12; s++) t.move(0.3 * (s % 2), 0, false, 200_000 + s * 5_000L);
        t.signal(ActivitySignal.INVENTORY);
        assertFalse(t.closeMinute().active(), "jiggling in place");
        // more than one sample per 5 s is ignored
        t.move(0, 0, false, 300_000);
        t.move(50, 0, false, 300_100);
        t.signal(ActivitySignal.INVENTORY);
        assertFalse(t.closeMinute().active());
    }

    @Test
    void anAutoClickerIsNotActive() {
        ActivityTracker t = new ActivityTracker();
        long now = 0;
        for (int i = 0; i < 150; i++) t.attack(now += 400); // exactly 400 ms apart
        assertEquals(ActivityTracker.Reason.MACRO, t.closeMinute().reason());
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 150; i++) t.attack(now += 250 + r.nextInt(400)); // a human
        assertTrue(t.closeMinute().active());
    }

    @Test
    void anAfkMobFarmStopsCountingInTheThirdMinute() {
        ActivityTracker t = new ActivityTracker();
        t.signal(ActivitySignal.DAMAGE_TAKEN);
        assertTrue(t.closeMinute().active(), "a hit while walking is fine");
        t.signal(ActivitySignal.DAMAGE_TAKEN);
        assertTrue(t.closeMinute().active());
        t.signal(ActivitySignal.DAMAGE_TAKEN);
        assertEquals(ActivityTracker.Reason.DAMAGE_IN, t.closeMinute().reason());
        t.signal(ActivitySignal.DAMAGE_TAKEN);
        t.attack(1);
        assertTrue(t.closeMinute().active(), "fighting back resets it");
    }

    @Test
    void soulMinutesNeverCount() {
        ActivityTracker t = new ActivityTracker();
        t.signal(ActivitySignal.KILL);
        t.exclude();
        assertEquals(ActivityTracker.Reason.SOUL, t.closeMinute().reason());
    }

    @Test
    void farmingCurveIsSoftAndHasAFloor() {
        assertEquals(1.0, ActivityTracker.factor(0));
        assertEquals(1.0, ActivityTracker.factor(40));
        assertEquals(0.5, ActivityTracker.factor(100), 1e-9);
        assertEquals(1 / 3.0, ActivityTracker.factor(160), 1e-9);
        assertEquals(0.25, ActivityTracker.factor(220), 1e-9);
        assertEquals(0.25, ActivityTracker.factor(10_000), 1e-9, "never zero: some reward stays");
    }

    @Test
    void farmingOneSpotLowersTheRewardAndMovingOnRestoresIt() {
        ActivityTracker t = new ActivityTracker();
        double last = 1;
        for (int k = 0; k < 200; k++) {
            last = t.kill("mob.zombie", 10 + k % 20, 10);
            if (k % 10 == 9) t.closeMinute(); // ~10 kills per active minute
        }
        assertTrue(last < 0.4, "200 kills of one mob in one spot: " + last);
        assertTrue(t.areaFactor() < 0.5);
        assertEquals(1.0, t.kill("mob.zombie", 500, 500), "another area is a fresh count");
        assertEquals(1.0, t.farmFactor("mob.wolf", 900, 900));
    }

    @Test
    void otherMobTypesCountHalf() {
        ActivityTracker a = new ActivityTracker();
        for (int k = 0; k < 100; k++) a.kill(k % 2 == 0 ? "mob.a" : "mob.b", 5, 5);
        ActivityTracker b = new ActivityTracker();
        for (int k = 0; k < 100; k++) b.kill("mob.a", 5, 5);
        assertTrue(a.farmFactor("mob.a", 5, 5) > b.farmFactor("mob.a", 5, 5), "variety is farmed less");
    }

    @Test
    void killsExpireByActiveMinutesNotByIdleTime() {
        ActivityTracker t = new ActivityTracker();
        for (int k = 0; k < 160; k++) t.kill("mob.zombie", 1, 1);
        double tired = t.farmFactor("mob.zombie", 1, 1);
        for (int m = 0; m < 120; m++) t.closeMinute(); // two idle hours: nothing expires
        assertEquals(tired, t.farmFactor("mob.zombie", 1, 1), 1e-9);
        for (int m = 0; m < 31; m++) {
            t.signal(ActivitySignal.QUEST);
            t.closeMinute(); // 31 active minutes elsewhere
        }
        assertEquals(1.0, t.farmFactor("mob.zombie", 1, 1), 1e-9);
    }

    @Test
    void activeMinutesRoundTrip() {
        ActiveMinutes a = ActiveMinutes.NONE.plus(ActivityCategory.COMBAT, 3).plus(ActivityCategory.QUEST, 1).plus(ActivityCategory.COMBAT, 2);
        assertEquals(5, a.of(ActivityCategory.COMBAT));
        assertEquals(6, a.total());
        assertEquals(a, ActiveMinutes.fromJson(a.toJson()));
        assertEquals(ActiveMinutes.NONE, ActiveMinutes.fromJson(""));
        assertThrows(IllegalArgumentException.class, () -> ActiveMinutes.fromJson("{\"v\":2}"));
    }
}
