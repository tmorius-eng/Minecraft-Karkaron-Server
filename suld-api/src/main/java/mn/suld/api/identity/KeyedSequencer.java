package mn.suld.api.identity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Runs asynchronous operations for the same key strictly one after another, while different
 * keys proceed in parallel. SÜLD uses it per player UUID so a profile load can never overtake
 * the save from the previous session (the classic quit→instant-rejoin stale-read / rollback).
 * A failed operation does not block the ones queued behind it.
 */
public final class KeyedSequencer<K> {

    private final Map<K, CompletableFuture<?>> tails = new ConcurrentHashMap<>();

    public <T> CompletableFuture<T> submit(K key, Supplier<CompletableFuture<T>> operation) {
        // Only swap in our placeholder tail under the map lock; never run user code inside
        // compute() (a synchronously-completing op would otherwise re-enter the map).
        CompletableFuture<Void> myTail = new CompletableFuture<>();
        CompletableFuture<?>[] previous = new CompletableFuture<?>[1];
        tails.compute(key, (k, prev) -> {
            previous[0] = prev;
            return myTail;
        });
        CompletableFuture<?> start = previous[0] == null ? CompletableFuture.completedFuture(null) : previous[0];
        CompletableFuture<T> result = new CompletableFuture<>();
        start.handle((ignored, err) -> null)
                .thenCompose(ignored -> {
                    try {
                        CompletableFuture<T> f = operation.get();
                        return f != null ? f : CompletableFuture.<T>completedFuture(null);
                    } catch (RuntimeException ex) {
                        return CompletableFuture.<T>failedFuture(ex);
                    }
                })
                .whenComplete((value, err) -> {
                    tails.remove(key, myTail); // no-op if someone queued behind us
                    myTail.complete(null);     // release the next operation for this key
                    if (err != null) {
                        result.completeExceptionally(err instanceof java.util.concurrent.CompletionException
                                && err.getCause() != null ? err.getCause() : err);
                    } else {
                        result.complete(value);
                    }
                });
        return result;
    }

    /** Keys with operations in flight (diagnostics/tests). */
    public int inFlight() {
        return tails.size();
    }
}
