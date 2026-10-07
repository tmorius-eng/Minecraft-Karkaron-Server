package mn.suld.api.worldbuild;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PregenPlanTest {

    @Test
    void spiralCoversTheSquareExactlyOnceFromTheCentreOutwards() {
        for (int r = 0; r <= 12; r++) {
            Set<Long> seen = new HashSet<>();
            long n = (2L * r + 1) * (2L * r + 1);
            int lastRing = 0;
            for (long i = 0; i < n; i++) {
                int[] c = PregenPlan.spiral(i);
                int ring = Math.max(Math.abs(c[0]), Math.abs(c[1]));
                assertTrue(ring <= r, "cell " + i + " leaves the square of radius " + r);
                assertTrue(ring >= lastRing, "spiral goes back inwards at " + i);
                lastRing = ring;
                assertTrue(seen.add(((long) c[0] << 32) ^ (c[1] & 0xffffffffL)), "cell repeated at " + i);
            }
            assertEquals(n, seen.size());
        }
        // large indices stay exact (floating point at ring boundaries)
        for (long k : new long[]{1000, 31_250, 312}) {
            long start = (2 * k - 1) * (2 * k - 1);
            int[] a = PregenPlan.spiral(start), b = PregenPlan.spiral(start - 1);
            assertEquals(k, Math.max(Math.abs(a[0]), Math.abs(a[1])));
            assertEquals(k - 1, Math.max(Math.abs(b[0]), Math.abs(b[1])));
        }
    }

    @Test
    void overlappingJobsNeverYieldAChunkTwice() {
        PregenPlan plan = new PregenPlan(List.of(
                PregenPlan.Job.corridor("road", PregenPlan.Priority.P1, 0, 0, 40, 10, 2),
                PregenPlan.Job.square("spawn", PregenPlan.Priority.P0, 0, 0, 6),
                PregenPlan.Job.square("camp", PregenPlan.Priority.P2, 38, 9, 4)), null);
        assertEquals("spawn", plan.jobs().get(0).id(), "priority order, not insertion order");
        Set<Long> seen = new HashSet<>();
        PregenPlan.Cursor c = PregenPlan.Cursor.START;
        int yielded = 0;
        while (true) {
            PregenPlan.Step s = plan.next(c, 64);
            if (s.done()) break;
            c = s.after();
            if (s.chunk() == null) continue;
            yielded++;
            assertTrue(seen.add(((long) s.chunk()[0] << 32) ^ (s.chunk()[1] & 0xffffffffL)), "duplicate " + s.chunk()[0] + "," + s.chunk()[1]);
        }
        assertEquals(plan.countUnique(), yielded);
        assertTrue(yielded < plan.upperBound());
        // the first 169 chunks are the spawn square
        PregenPlan.Cursor d = PregenPlan.Cursor.START;
        for (int i = 0; i < 169; i++) {
            PregenPlan.Step s = plan.next(d, 1000);
            assertTrue(Math.abs(s.chunk()[0]) <= 6 && Math.abs(s.chunk()[1]) <= 6);
            d = s.after();
        }
    }

    @Test
    void resumingFromASavedCursorContinuesWithoutRepeats() {
        PregenPlan plan = new PregenPlan(List.of(PregenPlan.Job.square("a", PregenPlan.Priority.P0, 3, -2, 5),
                PregenPlan.Job.square("b", PregenPlan.Priority.P3, 9, -2, 5)), null);
        Set<Long> seen = new HashSet<>();
        PregenPlan.Cursor c = PregenPlan.Cursor.START;
        for (int i = 0; i < 70; i++) {
            PregenPlan.Step s = plan.next(c, 1000);
            seen.add(((long) s.chunk()[0] << 32) ^ (s.chunk()[1] & 0xffffffffL));
            c = s.after();
        }
        PregenPlan.Cursor saved = new PregenPlan.Cursor(c.job(), c.center(), c.step()); // as written to disk
        PregenPlan again = new PregenPlan(List.of(PregenPlan.Job.square("a", PregenPlan.Priority.P0, 3, -2, 5),
                PregenPlan.Job.square("b", PregenPlan.Priority.P3, 9, -2, 5)), null);
        assertEquals(plan.fingerprint(), again.fingerprint());
        while (true) {
            PregenPlan.Step s = again.next(saved, 1000);
            if (s.done()) break;
            saved = s.after();
            if (s.chunk() != null) assertTrue(seen.add(((long) s.chunk()[0] << 32) ^ (s.chunk()[1] & 0xffffffffL)));
        }
        assertEquals(plan.countUnique(), seen.size());
    }

    @Test
    void filterAndSkipBudget() {
        PregenPlan plan = new PregenPlan(List.of(PregenPlan.Job.square("w", PregenPlan.Priority.P5, 0, 0, 20)),
                (x, z) -> x >= 15); // only a strip is inside the "border"
        PregenPlan.Step s = plan.next(PregenPlan.Cursor.START, 10);
        assertNull(s.chunk(), "skip budget spent before the first wanted chunk");
        assertNotNull(s.after());
        assertEquals(10, s.skipped());
        assertEquals(6L * 41, plan.countUnique());
    }

    @Test
    void borderSpec() {
        BorderSpec b = new BorderSpec(120, -40, BorderSpec.DEFAULT_DIAMETER);
        assertEquals(5000, b.radius());
        assertTrue(b.contains(5120, 4960));
        assertFalse(b.contains(5121, 0));
        assertArrayEquals(new double[]{5116, -40}, b.clampInside(9000, -40, 4));
        assertArrayEquals(new double[]{0, 7}, b.clampInside(0, 7, 4));
        assertArrayEquals(new double[]{12.5, -7}, BorderSpec.center("spawn", 12.5, -7));
        assertArrayEquals(new double[]{100, -3}, BorderSpec.center(" 100, -3 ", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> BorderSpec.center("north", 0, 0));
        assertFalse(b.differsFrom(120.2, -40, 10_000));
        assertTrue(b.differsFrom(0, 0, 10_000));
        assertTrue(b.differsFrom(120, -40, 59_999_968));
    }
}
