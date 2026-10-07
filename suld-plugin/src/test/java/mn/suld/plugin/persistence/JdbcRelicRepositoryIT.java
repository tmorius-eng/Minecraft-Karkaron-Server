package mn.suld.plugin.persistence;

import mn.suld.api.relic.CasResult;
import mn.suld.api.relic.RelicEvent;
import mn.suld.api.relic.RelicRecord;
import mn.suld.api.relic.RelicState;
import mn.suld.api.relic.RelicTransition;
import mn.suld.api.relic.ShrineLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-database proof of relic uniqueness (opt-in, see JdbcClanRepositoryIT for the env vars;
 * DROPS all suld_ tables in the target database first).
 */
class JdbcRelicRepositoryIT {

    private javax.sql.DataSource ds;
    private TestDb.Db testDb;
    private final ExecutorService pool = Executors.newFixedThreadPool(24); // real parallel connections
    private JdbcRelicRepository repo;

    @BeforeEach
    void freshSchema() throws Exception {
        testDb = TestDb.fresh();
        ds = testDb.ds();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(ds, testDb.dialect()).migrate(), "all migrations applied to a fresh schema");
        repo = new JdbcRelicRepository(ds, testDb.dialect(), pool);
    }

    @AfterEach
    void stop() {
        pool.shutdownNow();
    }

    @Test
    void ensureMintsExactlyOnce() {
        UUID first = UUID.randomUUID();
        RelicRecord a = repo.ensure("relic.khukh_suld", first).join();
        RelicRecord b = repo.ensure("relic.khukh_suld", UUID.randomUUID()).join();
        assertEquals(first, a.itemUuid());
        assertEquals(first, b.itemUuid(), "restart keeps the original identity");
        assertEquals(1, repo.loadAll().join().size());
        assertThrows(CompletionException.class, () -> repo.ensure("relic.other", first).join(),
                "the same item UUID can never be minted for a second relic");
    }

    @Test
    void twentyFourConcurrentClaimsOneWinner() throws Exception {
        RelicRecord r = repo.ensure("relic.khukh_suld", UUID.randomUUID()).join();
        r = repo.setShrine(r.key(), new ShrineLocation("world", 100, 70, -200), "admin").join();
        RelicRecord base = r;
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<CasResult>> attempts = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            UUID p = UUID.randomUUID();
            attempts.add(CompletableFuture.supplyAsync(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return repo.apply(RelicTransition.claim(base, p, "P", RelicEvent.DISCOVERED, p.toString(), "")).join();
            }, Executors.newVirtualThreadPerTaskExecutor()));
        }
        go.countDown();
        long winners = 0;
        for (CompletableFuture<CasResult> a : attempts) {
            if (a.get(30, TimeUnit.SECONDS).success()) winners++;
        }
        assertEquals(1, winners, "exactly one bearer, enforced by the database");
        RelicRecord now = repo.loadAll().join().get(0);
        assertEquals(RelicState.OWNED, now.state());
        assertEquals(1, now.version());
        assertNotNull(now.shrine());
        assertEquals(3, repo.history(now.key(), 10).join().size(), "CREATED, SHRINE_SET, one DISCOVERED");
    }

    @Test
    void databaseRejectsASecondRelicForTheSameBearer() {
        UUID bearer = UUID.randomUUID();
        RelicRecord a = repo.ensure("relic.khukh_suld", UUID.randomUUID()).join();
        RelicRecord b = repo.ensure("relic.altan_gerege", UUID.randomUUID()).join();
        assertTrue(repo.apply(RelicTransition.claim(a, bearer, "B", RelicEvent.DISCOVERED, "b", "")).join().success());
        assertThrows(CompletionException.class,
                () -> repo.apply(RelicTransition.claim(b, bearer, "B", RelicEvent.DISCOVERED, "b", "")).join(),
                "UNIQUE(owner_uuid) holds even if the service check were bypassed");
        assertEquals(RelicState.UNCLAIMED,
                repo.loadAll().join().stream().filter(x -> x.key().equals(b.key())).findFirst().orElseThrow().state());
    }

    @Test
    void seizeReleaseAndRegenerateKeepGenerationsMonotonic() {
        UUID p = UUID.randomUUID(), q = UUID.randomUUID();
        RelicRecord r = repo.ensure("relic.khukh_suld", UUID.randomUUID()).join();
        RelicRecord owned = repo.apply(RelicTransition.claim(r, p, "P", RelicEvent.DISCOVERED, "p", "")).join().current();
        RelicRecord seized = repo.apply(RelicTransition.claim(owned, q, "Q", RelicEvent.SEIZED, "q", "killed P")).join().current();
        assertEquals(q, seized.owner());
        assertEquals("Q", seized.ownerName());
        RelicRecord regen = repo.apply(RelicTransition.regenerate(seized, RelicEvent.RECOVERED, "admin", "")).join().current();
        assertEquals(seized.acquiredAt(), regen.acquiredAt(), "same bearer keeps acquisition time");
        assertEquals(seized.version() + 1, regen.version(), "old physical copies are now stale");
        CasResult stale = repo.apply(RelicTransition.release(seized, RelicEvent.RETURNED, "x", "")).join();
        assertFalse(stale.success(), "decision from an old generation rejected");
        RelicRecord home = repo.apply(RelicTransition.release(regen, RelicEvent.RETURNED, "server", "")).join().current();
        assertEquals(RelicState.UNCLAIMED, home.state());
        assertNull(home.owner());
        assertNull(home.acquiredAt());
        assertEquals(RelicEvent.RETURNED, repo.history(home.key(), 1).join().get(0).event());
    }
}
