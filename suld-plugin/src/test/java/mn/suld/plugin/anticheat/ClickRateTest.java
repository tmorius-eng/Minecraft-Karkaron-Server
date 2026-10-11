package mn.suld.plugin.anticheat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A whole-menu click burst in one tick is cut to the burst size; a person's pace always passes. */
class ClickRateTest {

    @Test
    void aOneTickBurstIsCut() {
        ClickRate r = new ClickRate(10, 10);
        UUID p = UUID.randomUUID();
        int passed = 0;
        for (int i = 0; i < 54; i++) if (r.allow(p, 1_000)) passed++;
        assertEquals(10, passed);
        assertEquals(44, r.takeDropped(p));
        assertEquals(0, r.takeDropped(p));
    }

    @Test
    void humanClickingNeverDrops() {
        ClickRate r = new ClickRate(10, 10);
        UUID p = UUID.randomUUID();
        for (int i = 0; i < 600; i++) assertTrue(r.allow(p, 1_000 + i * 125L), "click " + i); // 8 clicks a second for 75 s
    }

    @Test
    void theBucketRefills() {
        ClickRate r = new ClickRate(10, 10);
        UUID p = UUID.randomUUID();
        for (int i = 0; i < 10; i++) r.allow(p, 0);
        assertTrue(!r.allow(p, 0));
        assertTrue(r.allow(p, 1_000)); // a second later: 10 more
    }
}
