package mn.suld.plugin.persistence;

import mn.suld.api.persistence.StyleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit 2026-10-11 SEC-003: a store retrying "/credits give" must never pay twice. Runs on H2 by default and on a real
 * PostgreSQL with SULD_TEST_PG_URL (see TestDb).
 */
class JdbcCreditGrantIT {

    private JdbcStyleRepository repo;
    private javax.sql.DataSource ds;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        ds = db.ds();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(ds, db.dialect()).migrate());
        repo = new JdbcStyleRepository(ds, db.dialect(), pool);
    }

    @Test
    void aRepeatedTransactionIdGrantsOnce() {
        UUID p = UUID.randomUUID();
        StyleRepository.CreditGrant first = repo.grantCredits(p, 600, "tebex-tx-1", "CONSOLE").join();
        assertTrue(first.applied());
        assertEquals(600, first.balance());
        StyleRepository.CreditGrant again = repo.grantCredits(p, 600, "tebex-tx-1", "CONSOLE").join();
        assertFalse(again.applied(), "a retry is a no-op");
        assertEquals(600, again.balance());
        assertTrue(repo.grantCredits(p, 250, "tebex-tx-2", "CONSOLE").join().applied());
        assertEquals(850, repo.addCredits(p, 0).join());
    }

    @Test
    void concurrentReplaysOfOneTransactionGrantOnce() {
        UUID p = UUID.randomUUID();
        List<CompletableFuture<StyleRepository.CreditGrant>> all = new ArrayList<>();
        for (int i = 0; i < 16; i++) all.add(repo.grantCredits(p, 100, "tx-race", "CONSOLE"));
        long applied = all.stream().map(CompletableFuture::join).filter(StyleRepository.CreditGrant::applied).count();
        assertEquals(1, applied, "exactly one of 16 concurrent replays applies");
        assertEquals(100, repo.addCredits(p, 0).join());
    }
}
