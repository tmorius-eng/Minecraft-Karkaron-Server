package mn.suld.api.death;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OvooRitualTest {

    /** Walks {@code turns} clockwise rounds at radius {@code r}, 10° per step, {@code msPerStep} apart, from {@code t0}. */
    private static OvooRitual.Step walk(OvooRitual rit, double turns, double r, long t0, long msPerStep, long[] clock) {
        OvooRitual.Step last = OvooRitual.Step.IDLE;
        int steps = (int) Math.round(turns * 36);
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(i * 10);
            // clockwise seen from above with x east, z south: bearing grows as we go (Navigation.bearing)
            double dx = Math.sin(a) * r, dz = -Math.cos(a) * r;
            clock[0] = t0 + i * msPerStep;
            last = rit.step(dx, dz, clock[0]);
            if (last == OvooRitual.Step.DONE) return last;
        }
        return last;
    }

    @Test
    void threeSunwiseRoundsAndNinetySecondsBringTheSoulBack() {
        OvooRitual rit = new OvooRitual(90);
        long[] clock = {0};
        // three rounds in 108 steps × 1 s = 108 s: done on the step that closes the third round
        OvooRitual.Step s = walk(rit, 3.05, 5, 0, 1000, clock);
        assertEquals(OvooRitual.Step.DONE, s);
        assertTrue(rit.done());
        assertTrue(rit.elapsedSeconds(clock[0]) >= 90);
    }

    @Test
    void fastRoundsWaitForTheOffering() {
        OvooRitual rit = new OvooRitual(90);
        long[] clock = {0};
        // three rounds in 30 s: the rounds are done, the offering is not
        OvooRitual.Step s = walk(rit, 3.05, 5, 0, 270, clock);
        assertEquals(OvooRitual.Step.OFFERING, s);
        assertEquals(3, rit.rounds(), 1e-9);
        // standing at the cairn (closer than the ring) during the offering is allowed
        assertEquals(OvooRitual.Step.OFFERING, rit.step(0.5, 0.5, 60_000));
        assertEquals(OvooRitual.Step.DONE, rit.step(1, 1, 90_000));
    }

    @Test
    void leavingTheRingBreaksItAndItStartsOver() {
        OvooRitual rit = new OvooRitual(90);
        long[] clock = {0};
        walk(rit, 2, 5, 0, 1000, clock);
        assertTrue(rit.started());
        assertEquals(OvooRitual.Step.BROKEN, rit.step(20, 0, clock[0] + 1000), "walked away");
        assertFalse(rit.started());
        assertEquals(0, rit.rounds(), 1e-9);
        assertEquals(OvooRitual.Step.IDLE, rit.step(20, 0, clock[0] + 2000), "still away: nothing to break");
    }

    @Test
    void walkingTheWrongWayDoesNotCount() {
        OvooRitual rit = new OvooRitual(0);
        OvooRitual.Step last = OvooRitual.Step.IDLE;
        for (int i = 0; i <= 120; i++) {
            double a = Math.toRadians(-i * 10);
            last = rit.step(Math.sin(a) * 5, -Math.cos(a) * 5, i * 1000L);
        }
        assertEquals(OvooRitual.Step.WALKING, last, "counter-clockwise rounds never finish it");
        assertEquals(0, rit.rounds(), 1e-9);
    }
}
