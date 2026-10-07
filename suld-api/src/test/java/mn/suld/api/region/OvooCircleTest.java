package mn.suld.api.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OvooCircleTest {

    /** Walks {@code turns} around the cairn at radius r; clockwise on the map = bearing grows. */
    private static int walk(OvooCircle c, double turns, double r, boolean clockwise) {
        int done = 0;
        int steps = (int) (turns * 36);
        for (int i = 0; i <= steps; i++) {
            double bearing = Math.toRadians((clockwise ? 1 : -1) * i * 10);
            // bearing 0 = north (−z), 90 = east (+x)
            if (c.step(Math.sin(bearing) * r, -Math.cos(bearing) * r)) done++;
        }
        return done;
    }

    @Test
    void threeClockwiseTurnsComplete() {
        OvooCircle c = new OvooCircle();
        assertEquals(0, walk(c, 2.9, 4, true));
        OvooCircle d = new OvooCircle();
        assertEquals(1, walk(d, 3.05, 4, true));
    }

    @Test
    void theWrongWayNeverCompletes() {
        OvooCircle c = new OvooCircle();
        assertEquals(0, walk(c, 6, 4, false));
        assertTrue(c.turns() >= -1);
    }

    @Test
    void leavingTheRingStartsOver() {
        OvooCircle c = new OvooCircle();
        walk(c, 2.5, 4, true);
        assertTrue(c.turns() > 2);
        c.step(30, 0);
        assertEquals(0, c.turns());
    }
}
