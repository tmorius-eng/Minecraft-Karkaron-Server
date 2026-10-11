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
    void eachKindHasItsOwnWindow() {
        AtomicLong now = new AtomicLong();
        MovementExemptions m = new MovementExemptions(now::get);
        UUID a = UUID.randomUUID();
        m.exempt(a, 40, "leap");
        m.exempt(a, 10, "lock-on", MovementExemptions.Kind.LOCKON);
        now.addAndGet(5 * TICK);
        assertEquals("leap", m.window(a, MovementExemptions.Kind.MOVE).reason());
        assertEquals("lock-on", m.window(a, MovementExemptions.Kind.LOCKON).reason()); // a leap does not hide the lock-on
        assertNull(m.window(a, MovementExemptions.Kind.MOUNT));
        now.addAndGet(10 * TICK);
        assertNull(m.window(a, MovementExemptions.Kind.LOCKON));
        assertEquals("leap", m.active(a));
    }

    @Test
    void theSeatHoldIsAMoveWindowOnly() {
        MovementExemptions m = new MovementExemptions(() -> 0L);
        UUID a = UUID.randomUUID();
        m.hold(a, "sky tree seat");
        assertEquals("sky tree seat", m.window(a, MovementExemptions.Kind.MOVE).reason());
        assertNull(m.window(a, MovementExemptions.Kind.LOCKON));
    }

    @Test
    void windowsGrowWithPingUpToTheCap() {
        assertEquals(40, MovementExemptions.withPing(40, 0));
        assertEquals(44, MovementExemptions.withPing(40, 100));   // two pings of 100 ms = 4 ticks
        assertEquals(80, MovementExemptions.withPing(40, 5_000)); // capped at +40
        assertEquals(10, MovementExemptions.withPing(10, -1));
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
