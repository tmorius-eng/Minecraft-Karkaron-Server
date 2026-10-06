package mn.suld.api.analytics;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A single analytics data point.
 *
 * <p>Events are intentionally schema-light: a {@code type} plus a free-form
 * attribute map. This lets new metrics be added without touching the sink
 * contract, and lets a downstream dashboard evolve independently.
 *
 * @param type       event type key (see {@link AnalyticsEventType})
 * @param player     the player the event relates to, or {@code null} for
 *                   server-wide events
 * @param at         when the event occurred
 * @param attributes additional key/value context; never {@code null}
 */
public record AnalyticsEvent(String type, @Nullable UUID player, Instant at, Map<String, Object> attributes) {

    public AnalyticsEvent {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(at, "at");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AnalyticsEvent of(String type, @Nullable UUID player) {
        return new AnalyticsEvent(type, player, Instant.now(), Map.of());
    }

    public static AnalyticsEvent of(String type, @Nullable UUID player, Map<String, Object> attributes) {
        return new AnalyticsEvent(type, player, Instant.now(), attributes);
    }
}
