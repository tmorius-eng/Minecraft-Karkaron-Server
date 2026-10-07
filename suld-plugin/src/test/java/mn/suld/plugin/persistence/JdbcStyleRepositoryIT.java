package mn.suld.plugin.persistence;

import mn.suld.api.style.PlayerStyle;
import mn.suld.api.style.Rank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-database test of the style row (V5) and discovered regions (V6). Opt-in like the other ITs:
 * SULD_TEST_PG_URL / _USER / _PASS pointing at a THROWAWAY database (its public schema is dropped).
 */
class JdbcStyleRepositoryIT {

    private JdbcStyleRepository repo;
    private final ExecutorService single = Executors.newSingleThreadExecutor();

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db testDb = TestDb.fresh();
        javax.sql.DataSource ds = testDb.ds();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(ds, testDb.dialect()).migrate());
        repo = new JdbcStyleRepository(ds, testDb.dialect(), single);
    }

    @Test
    void styleAndDiscoveriesRoundTrip() {
        UUID id = UUID.randomUUID();
        PlayerStyle s = new PlayerStyle(id);
        s.rank(Rank.ARAVT);
        assertTrue(s.claimLevel(5));
        assertTrue(s.claimDaily(20_000, 3));
        s.taskProgress(20_000, "4,0,9");
        assertTrue(s.grant("aura.altan"));
        assertTrue(s.equip(mn.suld.api.style.Cosmetic.Category.AURA, "aura.altan"));
        assertTrue(s.discover(1));
        assertTrue(s.discover(3));
        assertFalse(s.discover(3), "a region is discovered once");
        repo.save(s.snapshotAndClean()).join();

        PlayerStyle back = PlayerStyle.restore(repo.load(id).join().orElseThrow());
        assertEquals(Rank.ARAVT, back.rank());
        assertEquals(0b1010, back.discovered());
        assertEquals(20_000, back.dailyDay());
        assertEquals(3, back.dailyStreak());
        assertEquals("aura.altan", back.equipped(mn.suld.api.style.Cosmetic.Category.AURA).orElseThrow().id());
        assertEquals("4,0,9", back.taskProgress(20_000));
        assertEquals("", back.taskProgress(20_001), "progress belongs to its day");
        assertFalse(back.claimDaily(20_000, 4), "one claim per day");
        assertFalse(back.discover(1));
        assertTrue(back.discover(0));
        repo.save(back.snapshotAndClean()).join();
        assertEquals(0b1011, repo.load(id).join().orElseThrow().discovered());

        assertEquals(40, repo.addCredits(id, 40).join());
        assertEquals(-1, repo.addCredits(id, -41).join(), "credits never go negative");
        assertEquals(0b1011, repo.load(id).join().orElseThrow().discovered(), "credit updates leave discoveries alone");
    }
}
