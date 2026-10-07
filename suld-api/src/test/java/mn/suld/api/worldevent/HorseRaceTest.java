package mn.suld.api.worldevent;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorseRaceTest {

    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), C = UUID.randomUUID(), D = UUID.randomUUID();

    private static void ride(HorseRace r, UUID who, long t0) {
        for (int g = 0; g < 8; g++) r.pass(who, g, t0 + g * 1000L);
    }

    @Test
    void aFullLoopStartsChecksEveryGateInOrderAndFinishes() {
        HorseRace r = new HorseRace(8);
        assertEquals(HorseRace.Kind.START, r.pass(A, 0, 1000).kind());
        assertEquals(HorseRace.Kind.NONE, r.pass(A, 2, 2000).kind()); // skipping a gate does not count
        HorseRace.Pass p = r.pass(A, 1, 3000);
        assertEquals(HorseRace.Kind.CHECKPOINT, p.kind());
        assertEquals(1, p.index());
        assertEquals(2000, p.millis());
        for (int g = 2; g < 8; g++) assertEquals(HorseRace.Kind.CHECKPOINT, r.pass(A, g, 3000 + g * 1000L).kind());
        HorseRace.Pass fin = r.pass(A, 0, 61_000);
        assertEquals(HorseRace.Kind.FINISH, fin.kind());
        assertEquals(1, fin.place());
        assertEquals(300, fin.prize());
        assertEquals(60_000, fin.millis());
        assertEquals(HorseRace.Kind.NONE, r.pass(A, 0, 70_000).kind()); // a finisher never scores twice
        assertTrue(r.finished(A));
    }

    @Test
    void placesAndPrizesGoToTheFirstThreeOnly() {
        HorseRace r = new HorseRace(8);
        for (UUID u : new UUID[]{A, B, C, D}) ride(r, u, 0);
        // the order of closing the loop decides, not the order of starting
        HorseRace.Pass b = r.pass(B, 0, 9000);
        assertEquals(1, b.place());
        assertEquals(300, b.prize());
        HorseRace.Pass a = r.pass(A, 0, 9500);
        assertEquals(2, a.place());
        assertEquals(200, a.prize());
        HorseRace.Pass c = r.pass(C, 0, 9600);
        assertEquals(3, c.place());
        assertEquals(100, c.prize());
        HorseRace.Pass d = r.pass(D, 0, 9700);
        assertEquals(4, d.place());
        assertEquals(0, d.prize());
        assertEquals(B, r.results().get(0).getKey());
    }

    @Test
    void aTeleportPutsAnUnfinishedRiderBackToTheStart() {
        HorseRace r = new HorseRace(8);
        r.pass(A, 0, 0);
        r.pass(A, 1, 1000);
        r.pass(A, 2, 2000);
        assertEquals(3, r.nextGate(A));
        assertTrue(r.reset(A));
        assertEquals(0, r.nextGate(A));
        assertEquals(HorseRace.Kind.NONE, r.pass(A, 3, 3000).kind());
        assertEquals(HorseRace.Kind.START, r.pass(A, 0, 4000).kind());
        assertFalse(r.reset(B)); // never started
        ride(r, A, 5000);
        r.pass(A, 0, 20_000);
        assertFalse(r.reset(A)); // a finisher keeps the result
        assertTrue(r.finished(A));
    }

    @Test
    void ridingListsOnlyUnfinishedStarters() {
        HorseRace r = new HorseRace(4);
        r.pass(A, 0, 0);
        r.pass(B, 0, 0);
        r.pass(B, 1, 10);
        assertEquals(2, r.riding().size());
        assertEquals(2, r.riding().get(B));
        for (int g = 1; g < 4; g++) r.pass(A, g, 100 + g);
        r.pass(A, 0, 200);
        assertEquals(1, r.riding().size());
        assertEquals(2, r.startedCount());
    }
}
