package mn.suld.plugin.persistence;

import mn.suld.api.mastery.WeaponMastery;
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

/** V25 weapon mastery: writes only add, so batches from any number of threads end at their exact sum. */
class JdbcWeaponMasteryIT {

    private JdbcWeaponMasteryRepository repo;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        repo = new JdbcWeaponMasteryRepository(db.ds(), db.dialect(), pool);
    }

    @AfterEach
    void stop() {
        pool.shutdownNow();
    }

    @Test
    void batchesAddUpPerTrack() {
        UUID p = UUID.randomUUID();
        assertTrue(repo.totals(p).join().isEmpty());
        repo.add(p, Map.of(WeaponMastery.Track.SCIMITAR, 12.5, WeaponMastery.Track.LANCE, 3.0), 1).join();
        repo.add(p, Map.of(WeaponMastery.Track.SCIMITAR, 7.5), 2).join();
        repo.add(p, Map.of(WeaponMastery.Track.COMPOSITE_BOW, -4.0, WeaponMastery.Track.HAMMER, 0.0), 3).join(); // nothing
        Map<WeaponMastery.Track, Double> t = repo.totals(p).join();
        assertEquals(20.0, t.get(WeaponMastery.Track.SCIMITAR), 1e-9);
        assertEquals(3.0, t.get(WeaponMastery.Track.LANCE), 1e-9);
        assertEquals(2, t.size(), "a negative or empty batch writes no row");
        repo.set(p, WeaponMastery.Track.LANCE, 1000, 4).join();
        assertEquals(1000.0, repo.totals(p).join().get(WeaponMastery.Track.LANCE), 1e-9);
    }

    @Test
    void concurrentBatchesEndAtTheirExactSum() {
        UUID p = UUID.randomUUID();
        List<CompletableFuture<Void>> all = new ArrayList<>();
        double expected = 0;
        for (int i = 0; i < 48; i++) {
            double pts = 1.25 + i; // every first write races the others for the new row
            expected += pts;
            all.add(repo.add(p, Map.of(WeaponMastery.Track.SHAMANIC_STAFF, pts, WeaponMastery.Track.SCIMITAR, 1.0), i));
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();
        Map<WeaponMastery.Track, Double> t = repo.totals(p).join();
        assertEquals(expected, t.get(WeaponMastery.Track.SHAMANIC_STAFF), 1e-6);
        assertEquals(48.0, t.get(WeaponMastery.Track.SCIMITAR), 1e-9);
    }
}
