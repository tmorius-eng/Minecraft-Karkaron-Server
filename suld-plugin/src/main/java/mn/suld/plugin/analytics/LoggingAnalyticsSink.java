package mn.suld.plugin.analytics;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsSink;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * An {@link AnalyticsSink} that buffers events and writes them to the server
 * log on flush. It is the default phase-1 sink and a reference implementation:
 * swapping in a database or HTTP sink requires no changes to gameplay code.
 */
public final class LoggingAnalyticsSink implements AnalyticsSink {

    private final Logger logger;
    private final Queue<AnalyticsEvent> buffer = new ConcurrentLinkedQueue<>();

    public LoggingAnalyticsSink(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void record(AnalyticsEvent event) {
        buffer.add(event);
    }

    @Override
    public CompletableFuture<Void> flush() {
        AnalyticsEvent event;
        int count = 0;
        while ((event = buffer.poll()) != null) {
            AnalyticsEvent current = event;
            logger.info(() -> "[analytics] " + format(current));
            count++;
        }
        if (count > 0) {
            logger.fine("[analytics] flushed " + count + " event(s)");
        }
        return CompletableFuture.completedFuture(null);
    }

    private static String format(AnalyticsEvent e) {
        return e.type()
                + " player=" + (e.player() == null ? "-" : e.player())
                + " at=" + e.at()
                + " attrs=" + e.attributes();
    }
}
