package mn.suld.api.web;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void aClientBurstsThenRefillsAtItsRate() {
        RateLimiter r = new RateLimiter(5, 2, 100);
        for (int i = 0; i < 5; i++) assertTrue(r.allow("a", 1000), "burst " + i);
        assertFalse(r.allow("a", 1000), "the bucket is empty");
        assertTrue(r.allow("b", 1000), "another client has its own bucket");
        assertTrue(r.allow("a", 1500), "half a second later: one token back (2/s)");
        assertFalse(r.allow("a", 1500));
        for (int i = 0; i < 5; i++) assertTrue(r.allow("a", 10_000), "long idle: full burst again " + i);
    }

    @Test
    void theClientMapIsBoundedAndIdleClientsAreForgotten() {
        RateLimiter r = new RateLimiter(2, 1, 3);
        assertTrue(r.allow("a", 0));
        assertTrue(r.allow("b", 0));
        assertTrue(r.allow("c", 0));
        assertFalse(r.allow("d", 0), "three busy clients: a fourth waits");
        assertTrue(r.allow("d", 5_000), "once the others have refilled they are forgotten and d gets in");
        assertTrue(r.clients() <= 3);
    }

    @Test
    void aClientIsToldHowLongToWait() {
        RateLimiter r = new RateLimiter(2, 2, 100); // a token every 500 ms
        assertEquals(0, r.retryAfterMs("a", 0), "an unknown client waits for nothing");
        assertTrue(r.allow("a", 0));
        assertTrue(r.allow("a", 0));
        assertEquals(500, r.retryAfterMs("a", 0));
        assertEquals(200, r.retryAfterMs("a", 300));
        assertEquals(0, r.retryAfterMs("a", 500));
    }

    @Test
    void concurrentRequestsNeverTakeMoreThanTheBurst() throws Exception {
        RateLimiter r = new RateLimiter(50, 0.001, 10);
        AtomicInteger ok = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 400; i++) pool.submit(() -> { if (r.allow("same", 1000)) ok.incrementAndGet(); });
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(50, ok.get());
    }
}
