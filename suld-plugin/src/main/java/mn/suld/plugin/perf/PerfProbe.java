package mn.suld.plugin.perf;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Wall-clock cost of named SÜLD code paths (combat hit, skill cast, loot roll, menu click, database calls, ...).
 * Recording is a {@code System.nanoTime()} pair and a few atomic writes (thread-safe: database calls run on the
 * storage pool). Percentiles come from the most recent {@link #WINDOW} samples of each probe. Measurement only: it
 * never changes what the code does.
 *
 * <pre>long t = PerfProbe.start(); try { ... } finally { PerfProbe.stop("combat.hit", t); }</pre>
 */
public final class PerfProbe {

    public static final int WINDOW = 8192;

    private PerfProbe() {
    }

    /** One probe: lifetime count/total/max and a ring of the latest samples. */
    public static final class Timer {
        final LongAdder count = new LongAdder();
        final LongAdder totalNs = new LongAdder();
        final AtomicLong maxNs = new AtomicLong();
        final long[] ring = new long[WINDOW];
        final AtomicInteger next = new AtomicInteger();

        void record(long ns) {
            count.increment();
            totalNs.add(ns);
            maxNs.accumulateAndGet(ns, Math::max);
            ring[Math.floorMod(next.getAndIncrement(), WINDOW)] = ns;
        }

        public Stats stats() {
            long n = count.sum();
            int filled = (int) Math.min(n, WINDOW);
            long[] s = Arrays.copyOf(ring, filled);
            Arrays.sort(s);
            return new Stats(n, n == 0 ? 0 : totalNs.sum() / (double) n / 1000.0, pct(s, 0.50), pct(s, 0.95), pct(s, 0.99),
                    maxNs.get() / 1000.0);
        }
    }

    /** Microseconds. */
    public record Stats(long count, double meanUs, double p50Us, double p95Us, double p99Us, double maxUs) {
    }

    private static double pct(long[] sorted, double q) {
        if (sorted.length == 0) return 0;
        int i = (int) Math.min(sorted.length - 1, Math.ceil(q * sorted.length) - 1);
        return sorted[Math.max(0, i)] / 1000.0;
    }

    private static final Map<String, Timer> TIMERS = new ConcurrentHashMap<>();

    public static long start() {
        return System.nanoTime();
    }

    public static void stop(String name, long startNs) {
        TIMERS.computeIfAbsent(name, k -> new Timer()).record(System.nanoTime() - startNs);
    }

    /** A supplier that records its own run time under {@code name} (for work handed to an executor). */
    public static <T> java.util.function.Supplier<T> timed(String name, java.util.function.Supplier<T> body) {
        return () -> {
            long t = start();
            try {
                return body.get();
            } finally {
                stop(name, t);
            }
        };
    }

    public static Timer timer(String name) {
        return TIMERS.computeIfAbsent(name, k -> new Timer());
    }

    /** Every probe's statistics, sorted by name. */
    public static Map<String, Stats> snapshot() {
        Map<String, Stats> out = new LinkedHashMap<>();
        TIMERS.keySet().stream().sorted().forEach(k -> out.put(k, TIMERS.get(k).stats()));
        return out;
    }

    public static void reset() {
        TIMERS.clear();
    }
}
