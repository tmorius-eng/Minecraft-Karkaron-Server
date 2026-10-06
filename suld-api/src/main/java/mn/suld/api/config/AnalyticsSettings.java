package mn.suld.api.config;

/**
 * Analytics configuration.
 *
 * @param enabled              whether analytics are recorded at all
 * @param flushIntervalSeconds how often buffered events are flushed
 * @param sink                 which sink implementation to use
 */
public record AnalyticsSettings(boolean enabled, int flushIntervalSeconds, Sink sink) {

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
    }

    public static AnalyticsSettings defaults() {
        return new AnalyticsSettings(true, 30, Sink.LOG);
    }
}
