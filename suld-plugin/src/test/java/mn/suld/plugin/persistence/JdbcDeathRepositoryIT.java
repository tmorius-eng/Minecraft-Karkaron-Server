package mn.suld.plugin.persistence;

import mn.suld.api.death.DeathRecord;
import mn.suld.api.death.Wound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-database test of the persistent death state (V12). Opt-in like the other ITs: SULD_TEST_PG_URL / _USER / _PASS
 * pointing at a THROWAWAY database (its public schema is dropped).
 */
class JdbcDeathRepositoryIT {

    private javax.sql.DataSource ds;
    private TestDb.Db testDb;
    private JdbcDeathRepository repo;
    private final ExecutorService single = Executors.newSingleThreadExecutor();

    @BeforeEach
    void freshSchema() throws Exception {
        testDb = TestDb.fresh();
        ds = testDb.ds();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(ds, testDb.dialect()).migrate());
        repo = new JdbcDeathRepository(ds, testDb.dialect(), single);
    }

    static DeathRecord locked(UUID p, long at, long until) {
        return new DeathRecord(UUID.randomUUID(), p, 0, at, until, "world", 10, 64, -5, "ENTITY_ATTACK:WOLF", 23, 0, 0,
                DeathRecord.State.LOCKED, null, 0);
    }

    @Test
    void deathsPersistWithSequenceAndOneOpenLock() {
        UUID p = UUID.randomUUID();
        DeathRecord a = repo.insert(locked(p, 1_000, 61_000)).join();
        assertEquals(1, a.seq());
        assertEquals(61_000, repo.openLock(p).join().orElseThrow().lockedUntil(), "the lock survives (re-read from the database)");

        // the database refuses a second open lock, whatever the service believes
        assertThrows(CompletionException.class, () -> repo.insert(locked(p, 2_000, 62_000)).join());

        DeathRecord closed = a.closed(DeathRecord.State.ADMIN_REVIVED, 30_000, 1);
        assertTrue(repo.update(closed).join());
        assertFalse(repo.update(closed).join(), "stale version: no double recovery");
        assertTrue(repo.openLock(p).join().isEmpty());

        DeathRecord b = repo.insert(locked(p, 100_000, 160_000)).join();
        assertEquals(2, b.seq());
        var recent = repo.recent(p, 5).join();
        assertEquals(2, recent.size());
        assertEquals(b.deathId(), recent.get(0).deathId());
        assertEquals(DeathRecord.State.ADMIN_REVIVED, recent.get(1).state());
        assertEquals(30_000L, recent.get(1).recoveredAt());
        assertEquals(1, recent.get(1).woundAfter());

        // another player is independent
        UUID q = UUID.randomUUID();
        assertEquals(1, repo.insert(locked(q, 5, 10)).join().seq());
    }

    @Test
    void woundUpsert() {
        UUID p = UUID.randomUUID();
        assertEquals(Wound.NONE, repo.wound(p).join());
        repo.saveWound(p, new Wound(2, 37.5)).join();
        assertEquals(new Wound(2, 37.5), repo.wound(p).join());
        repo.saveWound(p, Wound.NONE).join();
        assertEquals(Wound.NONE, repo.wound(p).join());
    }
}
