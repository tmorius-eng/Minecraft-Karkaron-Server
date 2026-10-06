package mn.suld.api.analytics;

import java.util.concurrent.CompletableFuture;

/**
 * Destination for {@link AnalyticsEvent}s.
 *
 * <p>This is the single seam between gameplay code and whatever analytics
 * backend is configured. Phase 1 ships a log-based and a database-backed sink;
 * a future web dashboard is just another implementation. {@link #record} must
 * be cheap and non-blocking (buffer and flush asynchronously); callers may emit
 * from the main thread.
 */
public interface AnalyticsSink {

    /** Buffer an event. Must not block or throw on the hot path. */
    void record(AnalyticsEvent event);

    /** Flush buffered events to the backend. */
    CompletableFuture<Void> flush();

    /** A sink that discards everything. */
    AnalyticsSink NOOP = new AnalyticsSink() {
        @Override
        public void record(AnalyticsEvent event) {
        }

        @Override
        public CompletableFuture<Void> flush() {
            return CompletableFuture.completedFuture(null);
        }
    };
}
