package mn.suld.plugin.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Stops a set of thread pools against one shared deadline. Every pool is told to stop first, so they all drain at
 * the same time; then each is awaited for whatever is left of the budget; whatever has not finished is interrupted
 * and its queued work counted, so the log says exactly what the shutdown abandoned. Pure Java (no Bukkit), so it is
 * unit-tested with pools that never finish.
 */
public final class PoolShutdown {

    /** What the drain gave up on: queued tasks dropped per pool, and the pools whose running tasks were interrupted. */
    public record Report(Map<String, Integer> abandonedQueued, List<String> interrupted) {
        public boolean clean() {
            return abandonedQueued.isEmpty() && interrupted.isEmpty();
        }
    }

    private PoolShutdown() {
    }

    /**
     * @param pools         name → pool, in the order the names should be logged
     * @param deadlineNanos {@link System#nanoTime()} by which every pool must be gone
     */
    public static Report drain(LinkedHashMap<String, ExecutorService> pools, long deadlineNanos, Logger log) {
        Map<String, Integer> abandoned = new LinkedHashMap<>();
        List<String> interrupted = new ArrayList<>();
        for (ExecutorService pool : pools.values()) pool.shutdown(); // all draining at once
        boolean cut = false;
        for (Map.Entry<String, ExecutorService> e : pools.entrySet()) {
            ExecutorService pool = e.getValue();
            boolean done = pool.isTerminated();
            if (!done && !cut) {
                long left = deadlineNanos - System.nanoTime();
                try {
                    done = left > 0 && pool.awaitTermination(left, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    cut = true; // stop waiting for everything else too
                }
            }
            if (done) continue;
            int queued = pool.shutdownNow().size();
            if (queued > 0) abandoned.put(e.getKey(), queued);
            interrupted.add(e.getKey());
            log.warning("[shutdown] pool " + e.getKey() + " not drained in time: " + queued + " queued task(s) abandoned, running task(s) interrupted");
        }
        return new Report(abandoned, interrupted);
    }
}
