package mn.suld.api.config;

/**
 * Analytics configuration.
 *
 * @param enabled              whether analytics are recorded at all
 * @param flushIntervalSeconds how often buffered events are flushed
 * @param sink                 which sink implementation to use
 * @param retentionDays        the database sink deletes rows older than this (0 = keep forever)
 * @param skipTypes            event types the database sink does not store (high volume; session totals cover them)
 * @param queueLimit           most events buffered between flushes; beyond it the oldest are dropped (and counted)
 */
public record AnalyticsSettings(boolean enabled, int flushIntervalSeconds, Sink sink, int retentionDays,
                                java.util.Set<String> skipTypes, int queueLimit) {

    public enum Sink {
        /** Write events to the server log / a flat file. */
        LOG,
        /** Persist events into the analytics table. */
        DATABASE
    }

    public AnalyticsSettings {
        if (flushIntervalSeconds < 1) {
            throw new IllegalArgumentException("flushIntervalSeconds must be >= 1: " + flushIntervalSeconds);
        }
        retentionDays = Math.max(0, retentionDays);
        skipTypes = skipTypes == null ? java.util.Set.of() : java.util.Set.copyOf(skipTypes);
        queueLimit = Math.max(100, queueLimit);
    }

    public AnalyticsSettings(boolean enabled, int flushIntervalSeconds, Sink sink) {
        this(enabled, flushIntervalSeconds, sink, 90, java.util.Set.of("exp_gain"), 10_000);
    }

    public static AnalyticsSettings defaults() {
        return new AnalyticsSettings(true, 30, Sink.LOG);
    }
}
