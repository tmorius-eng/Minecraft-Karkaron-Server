package mn.suld.plugin.persistence;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shutdown drain returns within its budget however stuck a pool is, and says what it gave up on. */
class PoolShutdownTest {

    /** A pool that never finishes: every await sleeps the whole timeout, and three tasks are always "queued". */
    static final class StuckPool extends AbstractExecutorService {
        final AtomicInteger shutdownNowCalls = new AtomicInteger();
        volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownNowCalls.incrementAndGet();
            return List.of(() -> { }, () -> { }, () -> { });
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            Thread.sleep(unit.toMillis(timeout));
            return false;
        }

        @Override
        public void execute(Runnable command) {
        }
    }

    @Test
    void stuckPoolsAreCutAtTheDeadlineAndReported() {
        StuckPool a = new StuckPool(), b = new StuckPool();
        ExecutorService fine = Executors.newSingleThreadExecutor();
        LinkedHashMap<String, ExecutorService> pools = new LinkedHashMap<>();
        pools.put("a", a);
        pools.put("fine", fine);
        pools.put("b", b);
        long start = System.nanoTime();
        PoolShutdown.Report r = PoolShutdown.drain(pools, start + TimeUnit.MILLISECONDS.toNanos(500), Logger.getAnonymousLogger());
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(tookMs < 1500, "returned within the budget, took " + tookMs + " ms");
        assertEquals(1, a.shutdownNowCalls.get());
        assertEquals(1, b.shutdownNowCalls.get());
        assertTrue(a.shutdown && b.shutdown && fine.isShutdown(), "every pool was told to stop first");
        assertEquals(3, r.abandonedQueued().get("a"));
        assertEquals(3, r.abandonedQueued().get("b"));
        assertEquals(List.of("a", "b"), r.interrupted());
        assertFalse(r.clean());
    }

    @Test
    void idlePoolsDrainCleanly() {
        LinkedHashMap<String, ExecutorService> pools = new LinkedHashMap<>();
        pools.put("x", Executors.newSingleThreadExecutor());
        pools.put("y", Executors.newFixedThreadPool(2));
        PoolShutdown.Report r = PoolShutdown.drain(pools, System.nanoTime() + TimeUnit.SECONDS.toNanos(5), Logger.getAnonymousLogger());
        assertTrue(r.clean());
        assertTrue(pools.values().stream().allMatch(ExecutorService::isTerminated));
    }
}
