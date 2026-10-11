package mn.suld.api.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void newbieDeathLockIsConfigurable() {
        assertEquals(5, SuldConfig.defaults().death().newbieLevels());
        assertEquals(1.0, SuldConfig.defaults().death().newbieLockMinutes());
        SuldConfig cfg = SuldConfigFactory.load(new MapConfigView(Map.of(
                "death", Map.of("lock", Map.of("newbie-levels", 0, "newbie-minutes", 2.5)))));
        assertEquals(0, cfg.death().newbieLevels(), "0 turns the cap off");
        assertEquals(2.5, cfg.death().newbieLockMinutes());
    }

    @Test
    void unknownAnalyticsSinkFallsBackButAnUnknownDatabaseStopsStartup() {
        SuldConfig cfg = SuldConfigFactory.load(new MapConfigView(Map.of("analytics", Map.of("sink", "carrier-pigeon"))));
        assertEquals(AnalyticsSettings.Sink.LOG, cfg.analytics().sink());
        // silently falling back to volatile memory would lose every save, so a typo must fail loudly
        assertThrows(IllegalArgumentException.class,
                () -> SuldConfigFactory.load(new MapConfigView(Map.of("database", Map.of("type", "mongodb")))));
        assertEquals(StorageType.POSTGRESQL, SuldConfigFactory.load(new MapConfigView(Map.of("database", Map.of("type", "postgres")))).database().type());
        assertEquals(StorageType.MYSQL, SuldConfigFactory.load(new MapConfigView(Map.of("database", Map.of("type", "MariaDB")))).database().type());
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

    @Test
    void pvpIsConsentOnlyByDefault() {
        SuldConfig d = SuldConfigFactory.load(new MapConfigView(Map.of()));
        assertFalse(d.pvp().wildEnabled(), "SÜLD is PvE: the steppe is peaceful unless the owner opens it");
        assertTrue(d.pvp().duelEnabled());
        assertEquals(60, d.pvp().duelRequestSeconds());
        assertTrue(d.pvp().arenas().isEmpty());
        SuldConfig cfg = SuldConfigFactory.load(new MapConfigView(Map.of("pvp", Map.of(
                "wild-enabled", true, "duel-request-seconds", 1, "duel-minutes", 999,
                "arenas", java.util.List.of("world 10 10 -10 -10", "nonsense")))));
        assertTrue(cfg.pvp().wildEnabled());
        assertEquals(10, cfg.pvp().duelRequestSeconds(), "clamped to >= 10 s");
        assertEquals(60, cfg.pvp().duelMinutes(), "clamped to <= 60 min");
        assertEquals(1, cfg.pvp().arenas().size(), "malformed arena lines are skipped");
        assertTrue(cfg.pvp().inArena("world", 0, 0));
    }

    @Test
    void cityAutoRepairIsOffUnlessAsked() {
        assertFalse(SuldConfigFactory.load(new MapConfigView(Map.of())).world().autoRepairKharkhorum(),
                "a server whose config predates the key keeps hand-built spawn changes");
        assertTrue(SuldConfigFactory.load(new MapConfigView(Map.of("world", Map.of("kharkhorum-auto-repair", true))))
                .world().autoRepairKharkhorum());
    }
}
