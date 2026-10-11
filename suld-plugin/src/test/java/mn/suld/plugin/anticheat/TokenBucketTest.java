package mn.suld.plugin.anticheat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A whole-menu click burst in one tick is cut to the burst size; a person's pace always passes. */
class TokenBucketTest {

    @Test
    void aOneTickBurstIsCut() {
        TokenBucket r = new TokenBucket(10, 10);
        UUID p = UUID.randomUUID();
        int passed = 0;
        for (int i = 0; i < 54; i++) if (r.allow(p, 1_000)) passed++;
        assertEquals(10, passed);
        assertEquals(44, r.takeDropped(p));
        assertEquals(0, r.takeDropped(p));
    }

    @Test
    void humanClickingNeverDrops() {
        TokenBucket r = new TokenBucket(10, 10);
        UUID p = UUID.randomUUID();
        for (int i = 0; i < 600; i++) assertTrue(r.allow(p, 1_000 + i * 125L), "click " + i); // 8 clicks a second for 75 s
    }

    @Test
    void theBucketRefills() {
        TokenBucket r = new TokenBucket(10, 10);
        UUID p = UUID.randomUUID();
        for (int i = 0; i < 10; i++) r.allow(p, 0);
        assertTrue(!r.allow(p, 0));
        assertTrue(r.allow(p, 1_000)); // a second later: 10 more
    }

    @Test
    void aCheatHidingInBackToBackWindowsRunsDry() {
        TokenBucket budget = new TokenBucket(60, 6); // GrimBridge's default
        UUID p = UUID.randomUUID();
        int forgiven = 0;
        for (int tick = 0; tick < 200; tick++) if (budget.allow(p, tick * 50L)) forgiven++; // a flag every tick for 10 s
        assertTrue(forgiven <= 60 + 60 + 1, "forgiven " + forgiven + " of 200");
        assertTrue(budget.takeDropped(p) >= 79, "Grim judged the rest");
    }

    @Test
    void anHonestDashIsForgivenWhole() {
        TokenBucket budget = new TokenBucket(60, 6);
        UUID p = UUID.randomUUID();
        for (int dash = 0; dash < 10; dash++) {          // a dash every 5 s, each costing up to 25 flags
            for (int f = 0; f < 25; f++) assertTrue(budget.allow(p, dash * 5_000L + f * 50L), "dash " + dash + " flag " + f);
        }
    }
}
