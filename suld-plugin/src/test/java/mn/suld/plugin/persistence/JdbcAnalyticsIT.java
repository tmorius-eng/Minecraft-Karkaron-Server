package mn.suld.plugin.persistence;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.analytics.SessionTotals;
import mn.suld.plugin.analytics.JdbcAnalyticsSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The database analytics sink (V1 events + V14 sessions) on PostgreSQL. Opt-in: SULD_TEST_PG_URL / _USER / _PASS. */
@EnabledIfEnvironmentVariable(named = "SULD_TEST_PG_URL", matches = "jdbc:postgresql:.+")
class JdbcAnalyticsIT {

    @Test
    void batchesEventsWritesSessionRowsSkipsNoisyTypesBoundsTheQueueAndPurges() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(System.getenv("SULD_TEST_PG_URL"));
        ds.setUser(System.getenv("SULD_TEST_PG_USER"));
        ds.setPassword(System.getenv("SULD_TEST_PG_PASS"));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");
        }
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(ds, SqlDialect.POSTGRESQL).migrate());
        JdbcAnalyticsSink sink = new JdbcAnalyticsSink(ds, SqlDialect.POSTGRESQL, Executors.newSingleThreadExecutor(),
                Logger.getLogger("test"), Set.of("exp_gain"), 1_000);
        UUID p = UUID.randomUUID();
        for (int i = 0; i < 1_200; i++) sink.record(AnalyticsEvent.of(AnalyticsEventType.LEVEL_UP, p, Map.of("to", i)));
        assertEquals(200, sink.dropped(), "bounded: the oldest beyond 1000 are dropped");
        sink.record(AnalyticsEvent.of(AnalyticsEventType.EXP_GAIN, p, Map.of("amount", 5)));
        SessionTotals t = new SessionTotals(System.currentTimeMillis() - 3_600_000);
        t.activeMinutes = 42;
        t.mobsDefeated = 310;
        t.exp = 12_345;
        t.deaths = 2;
        t.masteryXp = 77.5;
        sink.record(new AnalyticsEvent(AnalyticsEventType.SESSION_END, p, Instant.now(), t.attributes(System.currentTimeMillis())));
        assertEquals(201, sink.dropped(), "the session_end pushed one more old event out");
        sink.flush().join();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT count(*) FROM suld_analytics_events");
            rs.next();
            assertEquals(1_000, rs.getInt(1), "999 level_up + 1 session_end (queue limit 1000), no exp_gain");
            rs = st.executeQuery("SELECT duration_s, active_min, mobs, exp, deaths, totals FROM suld_sessions");
            assertTrue(rs.next());
            assertTrue(rs.getLong(1) >= 3_599);
            assertEquals(42, rs.getLong(2));
            assertEquals(310, rs.getLong(3));
            assertEquals(12_345, rs.getLong(4));
            assertEquals(2, rs.getInt(5));
            assertTrue(rs.getString(6).contains("\"mastery_xp\":77.5"), rs.getString(6));
            st.execute("UPDATE suld_analytics_events SET at = 0");
            st.execute("UPDATE suld_sessions SET ended_at = 0");
        }
        assertEquals(1_001, sink.purge(90));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT (SELECT count(*) FROM suld_analytics_events) + (SELECT count(*) FROM suld_sessions)");
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }
}
