package mn.suld.api.identity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class KeyedSequencerTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    private CompletableFuture<String> delayed(List<String> log, String tag, long millis) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.add(tag);
            return tag;
        }, pool);
    }

    @Test
    void sameKeyRunsInSubmissionOrderEvenIfFirstIsSlow() throws Exception {
        KeyedSequencer<String> seq = new KeyedSequencer<>();
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<String> save = seq.submit("p", () -> delayed(log, "save", 150));
        CompletableFuture<String> load = seq.submit("p", () -> delayed(log, "load", 0));
        assertEquals("load", load.get(5, TimeUnit.SECONDS));
        assertEquals(List.of("save", "load"), log, "the load can never overtake the save");
        assertTrue(save.isDone());
    }

    @Test
    void differentKeysRunInParallel() throws Exception {
        KeyedSequencer<String> seq = new KeyedSequencer<>();
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<String> slow = seq.submit("a", () -> delayed(log, "a", 300));
        CompletableFuture<String> fast = seq.submit("b", () -> delayed(log, "b", 0));
        fast.get(5, TimeUnit.SECONDS);
        assertFalse(slow.isDone(), "b did not wait for a");
        slow.get(5, TimeUnit.SECONDS);
    }

    @Test
    void failureDoesNotBlockTheQueue() throws Exception {
        KeyedSequencer<String> seq = new KeyedSequencer<>();
        CompletableFuture<String> bad = seq.submit("p", () -> CompletableFuture.failedFuture(new IllegalStateException("db down")));
        CompletableFuture<String> thrown = seq.submit("p", () -> { throw new IllegalStateException("boom"); });
        CompletableFuture<String> good = seq.submit("p", () -> CompletableFuture.completedFuture("ok"));
        assertEquals("ok", good.get(5, TimeUnit.SECONDS));
        assertTrue(bad.isCompletedExceptionally());
        assertTrue(thrown.isCompletedExceptionally());
        ExecutionExceptionCheck.assertCause(bad, IllegalStateException.class);
    }

    @Test
    void synchronousCompletionDoesNotDeadlockOrLeak() throws Exception {
        KeyedSequencer<Integer> seq = new KeyedSequencer<>();
        for (int i = 0; i < 1000; i++) {
            int v = i;
            assertEquals(v, seq.submit(1, () -> CompletableFuture.completedFuture(v)).get(1, TimeUnit.SECONDS));
        }
        assertEquals(0, seq.inFlight(), "no stale tails left behind");
    }

    @Test
    void strictOrderingUnderContention() throws Exception {
        KeyedSequencer<String> seq = new KeyedSequencer<>();
        List<Integer> log = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Integer>> all = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            int v = i;
            all.add(seq.submit("p", () -> CompletableFuture.supplyAsync(() -> {
                log.add(v);
                return v;
            }, pool)));
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        for (int i = 0; i < 300; i++) {
            assertEquals(i, log.get(i));
        }
        assertEquals(0, seq.inFlight());
    }

    /** Small helper so the failure test asserts the unwrapped cause type. */
    static final class ExecutionExceptionCheck {
        static void assertCause(CompletableFuture<?> f, Class<? extends Throwable> type) {
            try {
                f.join();
                fail("expected failure");
            } catch (java.util.concurrent.CompletionException ex) {
                assertInstanceOf(type, ex.getCause());
            }
        }
    }
}
