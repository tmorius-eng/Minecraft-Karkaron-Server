package mn.suld.api.service;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.analytics.AnalyticsSink;
import mn.suld.api.event.EventDispatcher;
import mn.suld.api.event.LevelUpEvent;
import mn.suld.api.event.SuldEvent;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.progression.PolynomialLevelCurve;
import mn.suld.api.progression.ProgressionEngine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultProgressionServiceTest {

    private static final class RecordingDispatcher implements EventDispatcher {
        final List<SuldEvent> events = new ArrayList<>();

        @Override
        public void dispatch(SuldEvent event) {
            events.add(event);
        }
    }

    private static final class RecordingSink implements AnalyticsSink {
        final List<AnalyticsEvent> events = new ArrayList<>();

        @Override
        public void record(AnalyticsEvent event) {
            events.add(event);
        }

        @Override
        public CompletableFuture<Void> flush() {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void grantAppliesProgressionAndEmitsEvents() {
        ProgressionEngine engine = new ProgressionEngine(new PolynomialLevelCurve(100.0, 1.0, 10));
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingSink sink = new RecordingSink();
        DefaultProgressionService service = new DefaultProgressionService(engine, dispatcher, sink);

        PlayerProfile profile = PlayerProfile.createNew(UUID.randomUUID(), "Temujin", Instant.now());

        service.grantExp(profile, 100, ExpSource.QUEST); // level 1 -> 2

        assertEquals(2, profile.progression().level());
        assertTrue(dispatcher.events.stream().anyMatch(e -> e instanceof LevelUpEvent));
        assertTrue(sink.events.stream().anyMatch(e -> e.type().equals(AnalyticsEventType.LEVEL_UP)));
        assertTrue(sink.events.stream().anyMatch(e -> e.type().equals(AnalyticsEventType.EXP_GAIN)));
    }

    @Test
    void grantWithoutLevelUpEmitsNoLevelUpEvent() {
        ProgressionEngine engine = new ProgressionEngine(new PolynomialLevelCurve(100.0, 1.0, 10));
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        DefaultProgressionService service =
                new DefaultProgressionService(engine, dispatcher, AnalyticsSink.NOOP);

        PlayerProfile profile = PlayerProfile.createNew(UUID.randomUUID(), "Subotai", Instant.now());
        service.grantExp(profile, 30, ExpSource.MOB_KILL);

        assertEquals(1, profile.progression().level());
        assertEquals(30, profile.progression().expIntoLevel());
        assertTrue(dispatcher.events.stream().noneMatch(e -> e instanceof LevelUpEvent));
    }
}
