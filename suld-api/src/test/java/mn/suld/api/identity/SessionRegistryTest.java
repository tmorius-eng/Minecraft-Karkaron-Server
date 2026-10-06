package mn.suld.api.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID p = UUID.randomUUID();

    @Test
    void singleSessionLifecycle() {
        SessionRegistry reg = new SessionRegistry();
        SessionRegistry.Start s = reg.begin(p, T0);
        assertFalse(s.duplicate());
        assertEquals(s.sessionId(), reg.activateNewest(p, T0));
        assertEquals(1, reg.activeCount(p));
        assertTrue(reg.end(p, s.sessionId()).last(), "last session ending => unload allowed");
        assertEquals(0, reg.liveSessions(p));
    }

    @Test
    void duplicateLoginOldQuitMustNotUnload() {
        SessionRegistry reg = new SessionRegistry();
        long oldS = reg.begin(p, T0).sessionId();
        reg.activateNewest(p, T0);
        SessionRegistry.Start newS = reg.begin(p, T0.plusSeconds(60));   // same account logs in again
        assertTrue(newS.duplicate());
        // Paper kicks the old connection before the new one joins:
        assertFalse(reg.end(p, oldS).last(), "old session leaving must NOT unload the shared profile");
        assertEquals(newS.sessionId(), reg.activateNewest(p, T0.plusSeconds(61)));
        assertTrue(reg.end(p, newS.sessionId()).last());
    }

    @Test
    void joinWithoutPreLoginIsRejected() {
        assertEquals(-1, new SessionRegistry().activateNewest(p, T0));
    }

    @Test
    void abandonedPendingSessionsExpire() {
        SessionRegistry reg = new SessionRegistry();
        long s = reg.begin(p, T0).sessionId();   // client vanished during configuration
        assertTrue(reg.expirePending(T0.plusSeconds(59), Duration.ofSeconds(60)).isEmpty());
        List<SessionRegistry.Ended> expired = reg.expirePending(T0.plusSeconds(60), Duration.ofSeconds(60));
        assertEquals(1, expired.size());
        assertEquals(s, expired.get(0).sessionId());
        assertTrue(expired.get(0).last());
    }

    @Test
    void activeSessionsNeverExpire() {
        SessionRegistry reg = new SessionRegistry();
        reg.begin(p, T0);
        reg.activateNewest(p, T0);
        assertTrue(reg.expirePending(T0.plus(Duration.ofDays(1)), Duration.ofSeconds(60)).isEmpty());
        assertEquals(1, reg.activeCount(p));
    }

    @Test
    void pendingExpiryNextToActiveSessionIsNotLast() {
        SessionRegistry reg = new SessionRegistry();
        reg.begin(p, T0);
        reg.activateNewest(p, T0);
        reg.begin(p, T0.plusSeconds(1));         // second connection never completes
        List<SessionRegistry.Ended> expired = reg.expirePending(T0.plusSeconds(120), Duration.ofSeconds(60));
        assertEquals(1, expired.size());
        assertFalse(expired.get(0).last(), "active session still owns the profile");
    }

    @Test
    void endingUnknownOrTwiceIsHarmless() {
        SessionRegistry reg = new SessionRegistry();
        long s = reg.begin(p, T0).sessionId();
        assertTrue(reg.end(p, s).last());
        assertTrue(reg.end(p, s).last(), "idempotent");
    }

    @Test
    void concurrentPreLoginsAreSafe() throws Exception {
        SessionRegistry reg = new SessionRegistry();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger duplicates = new AtomicInteger();
        for (int i = 0; i < 200; i++) {
            pool.submit(() -> {
                go.await();
                if (reg.begin(p, T0).duplicate()) duplicates.incrementAndGet();
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(200, reg.liveSessions(p));
        assertEquals(199, duplicates.get(), "exactly one first session, never two");
    }
}
