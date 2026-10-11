package mn.suld.plugin.anticheat;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A SÜLD movement window lasts exactly as long as asked, never shrinks, and a seat hold lasts until released. */
class MovementExemptionsTest {

    private static final long TICK = 50_000_000L;

    @Test
    void aWindowClosesAfterItsTicks() {
        AtomicLong now = new AtomicLong(1_000);
        MovementExemptions m = new MovementExemptions(now::get);
        UUID a = UUID.randomUUID();
        assertNull(m.active(a));
        m.exempt(a, 40, "leap");
        now.addAndGet(39 * TICK);
        assertEquals("leap", m.active(a));
        now.addAndGet(TICK);
        assertNull(m.active(a));
    }

    @Test
    void aShorterWindowNeverCutsALongerOne() {
        AtomicLong now = new AtomicLong();
        MovementExemptions m = new MovementExemptions(now::get);
        UUID a = UUID.randomUUID();
        m.exempt(a, 40, "charge");
        m.exempt(a, 10, "lock-on");
        now.addAndGet(20 * TICK);
        assertEquals("charge", m.active(a));
    }

    @Test
    void aHoldLastsUntilReleasedAndOnlyByItsOwnReason() {
        AtomicLong now = new AtomicLong();
        MovementExemptions m = new MovementExemptions(now::get);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        m.hold(a, "sky tree seat");
        now.addAndGet(100_000 * TICK);
        assertEquals("sky tree seat", m.active(a));
        assertNull(m.active(b));
        m.release(a, "something else");
        assertEquals("sky tree seat", m.active(a));
        m.release(a, "sky tree seat");
        assertNull(m.active(a));
    }

    @Test
    void forgetDropsEverything() {
        MovementExemptions m = new MovementExemptions(() -> 0L);
        UUID a = UUID.randomUUID();
        m.exempt(a, 40, "dodge");
        m.hold(a, "sky tree seat");
        m.forget(a);
        assertNull(m.active(a));
    }
}
