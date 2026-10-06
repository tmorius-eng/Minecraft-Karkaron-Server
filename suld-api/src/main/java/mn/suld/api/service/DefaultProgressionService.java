package mn.suld.api.service;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.analytics.AnalyticsSink;
import mn.suld.api.event.EventDispatcher;
import mn.suld.api.event.ExpGainedEvent;
import mn.suld.api.event.LevelUpEvent;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.progression.Progression;
import mn.suld.api.progression.ProgressionEngine;

import java.util.Map;
import java.util.Objects;

/**
 * Default, server-agnostic {@link ProgressionService}.
 *
 * <p>All levelling arithmetic is delegated to the {@link ProgressionEngine};
 * this class only orchestrates: apply the new progression to the profile, emit
 * events, and record analytics. Being free of any Bukkit dependency, it is
 * fully unit-tested in {@code suld-api}.
 */
public final class DefaultProgressionService implements ProgressionService {

    private final ProgressionEngine engine;
    private final EventDispatcher events;
    private final AnalyticsSink analytics;

    public DefaultProgressionService(ProgressionEngine engine, EventDispatcher events, AnalyticsSink analytics) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.events = Objects.requireNonNull(events, "events");
        this.analytics = Objects.requireNonNull(analytics, "analytics");
    }

    @Override
    public ExpGainResult grantExp(PlayerProfile profile, long amount, ExpSource source) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(source, "source");

        Progression before = profile.progression();
        ExpGainResult result = engine.grant(before, amount);
        profile.progression(result.after());

        events.dispatch(new ExpGainedEvent(profile.playerId(), amount, source, result));
        analytics.record(AnalyticsEvent.of(AnalyticsEventType.EXP_GAIN, profile.playerId(),
                Map.of("amount", amount, "source", source.name())));

        if (result.leveledUp()) {
            int from = before.level();
            int to = result.after().level();
            events.dispatch(new LevelUpEvent(profile.playerId(), from, to));
            analytics.record(AnalyticsEvent.of(AnalyticsEventType.LEVEL_UP, profile.playerId(),
                    Map.of("from", from, "to", to)));
        }
        return result;
    }

    @Override
    public ProgressionEngine engine() {
        return engine;
    }
}
