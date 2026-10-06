package mn.suld.api.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuldConfigFactoryTest {

    @Test
    void emptyViewYieldsDefaults() {
        SuldConfig cfg = SuldConfigFactory.load(new MapConfigView(Map.of()));
        assertEquals(SuldConfig.defaults(), cfg);
    }

    @Test
    void nullViewYieldsDefaults() {
        assertEquals(SuldConfig.defaults(), SuldConfigFactory.load(null));
    }

    @Test
    void overridesAreApplied() {
        Map<String, Object> raw = Map.of(
                "locale", "en",
                "progression", Map.of(
                        "max-level", 80,
                        "curve", Map.of("base", 150.0, "exponent", 2.0)),
                "database", Map.of(
                        "type", "postgresql",
                        "host", "db.example.com",
                        "port", 5432,
                        "pool-size", 20),
                "death", Map.of(
                        "enabled", false,
                        "exp-loss-fraction", 0.5),
                "analytics", Map.of("sink", "database"));

        SuldConfig cfg = SuldConfigFactory.load(new MapConfigView(raw));

        assertEquals("en", cfg.locale());
        assertEquals(80, cfg.progression().maxLevel());
        assertEquals(2.0, cfg.progression().exponent());
        assertEquals(StorageType.POSTGRESQL, cfg.database().type());
        assertEquals("db.example.com", cfg.database().host());
        assertEquals(5432, cfg.database().port());
        assertEquals(20, cfg.database().poolSize());
        assertEquals(false, cfg.death().enabled());
        assertEquals(0.5, cfg.death().expLossFraction());
        assertEquals(AnalyticsSettings.Sink.DATABASE, cfg.analytics().sink());
    }

    @Test
    void unknownEnumValuesFallBackToDefaults() {
        Map<String, Object> raw = Map.of(
                "database", Map.of("type", "mongodb"),
                "analytics", Map.of("sink", "carrier-pigeon"));
        SuldConfig cfg = SuldConfigFactory.load(new MapConfigView(raw));
        assertEquals(StorageType.MEMORY, cfg.database().type());
        assertEquals(AnalyticsSettings.Sink.LOG, cfg.analytics().sink());
    }

    @Test
    void socialSettingsLoadAndClamp() {
        SuldConfig defaults = SuldConfigFactory.load(new MapConfigView(Map.of()));
        assertEquals(500, defaults.social().clanCreateCost());
        assertTrue(defaults.social().worldEventsEnabled());

        SuldConfig cfg = SuldConfigFactory.load(new MapConfigView(Map.of(
                "clans", Map.of("create-cost", 1200),
                "world-events", Map.of("enabled", false, "interval-minutes", 1, "min-players", 0))));
        assertEquals(1200, cfg.social().clanCreateCost());
        assertFalse(cfg.social().worldEventsEnabled());
        assertEquals(5, cfg.social().worldEventIntervalMin(), "clamped to >= 5 minutes");
        assertEquals(1, cfg.social().worldEventMinPlayers(), "clamped to >= 1");
    }

    @Test
    void relicSettingsLoadAndClamp() {
        SuldConfig d = SuldConfigFactory.load(new MapConfigView(Map.of()));
        assertTrue(d.relics().enabled());
        assertEquals(72, d.relics().offlineReturnHours());
        SuldConfig cfg = SuldConfigFactory.load(new MapConfigView(Map.of("relics", Map.of(
                "auto-place-min-radius", 10, "auto-place-max-radius", 20, "ritual-seconds", 999))));
        assertEquals(64, cfg.relics().autoPlaceMinRadius(), "never right at spawn");
        assertEquals(96, cfg.relics().autoPlaceMaxRadius(), "max > min");
        assertEquals(60, cfg.relics().ritualSeconds());
    }
}
