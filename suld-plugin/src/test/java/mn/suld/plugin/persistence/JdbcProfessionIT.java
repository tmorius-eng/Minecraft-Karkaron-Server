package mn.suld.plugin.persistence;

import mn.suld.api.mastery.Profession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V27 crafts: writes only add, so batches from any number of threads end at their exact sum. */
class JdbcProfessionIT {

    private JdbcProfessionRepository repo;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        repo = new JdbcProfessionRepository(db.ds(), db.dialect(), pool);
    }

    @AfterEach
    void stop() {
        pool.shutdownNow();
    }

    @Test
    void batchesAddUpPerCraft() {
        UUID p = UUID.randomUUID();
        assertTrue(repo.totals(p).join().isEmpty());
        repo.add(p, Map.of(Profession.TOMRIIN_DARKHAN, 30L, Profession.ESGIICH, 10L), 1).join();
        repo.add(p, Map.of(Profession.TOMRIIN_DARKHAN, 20L), 2).join();
        repo.add(p, Map.of(Profession.ADUUCHIN, -4L, Profession.EMIIN_BARIACH, 0L), 3).join(); // nothing
        Map<Profession, Long> t = repo.totals(p).join();
        assertEquals(50L, t.get(Profession.TOMRIIN_DARKHAN));
        assertEquals(10L, t.get(Profession.ESGIICH));
        assertEquals(2, t.size(), "a negative or empty batch writes no row");
        repo.set(p, Profession.ESGIICH, 1000, 4).join();
        assertEquals(1000L, repo.totals(p).join().get(Profession.ESGIICH));
    }

    @Test
    void concurrentBatchesEndAtTheirExactSum() {
        UUID p = UUID.randomUUID();
        List<CompletableFuture<Void>> all = new ArrayList<>();
        long expected = 0;
        for (int i = 0; i < 48; i++) {
            long pts = 10L + i; // every first write races the others for the new row
            expected += pts;
            all.add(repo.add(p, Map.of(Profession.EMIIN_BARIACH, pts, Profession.ADUUCHIN, 1L), i));
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();
        Map<Profession, Long> t = repo.totals(p).join();
        assertEquals(expected, t.get(Profession.EMIIN_BARIACH));
        assertEquals(48L, t.get(Profession.ADUUCHIN));
    }
}
