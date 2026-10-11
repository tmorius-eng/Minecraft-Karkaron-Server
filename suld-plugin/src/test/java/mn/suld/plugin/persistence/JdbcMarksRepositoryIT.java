package mn.suld.plugin.persistence;

import mn.suld.api.progress.Mark;
import mn.suld.api.progress.MarkKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V17 progress marks: upserts are idempotent, a backfill may run twice, counts are per distinct player. */
class JdbcMarksRepositoryIT {

    private JdbcMarksRepository repo;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        repo = new JdbcMarksRepository(db.ds(), db.dialect(), pool);
    }

    @Test
    void upsertIsIdempotentAndLoadGroupsByKind() {
        UUID p = UUID.randomUUID();
        repo.put(new Mark(p, MarkKind.DUNGEON_CLEAR, "dungeon.khasar_den", 1000, 1000)).join();
        repo.put(new Mark(p, MarkKind.DUNGEON_CLEAR, "dungeon.khasar_den", 1000, 1000)).join(); // a repeated backfill
        repo.putAll(List.of(new Mark(p, MarkKind.SITE_SEEN, "site.aurag", 2000, 2000),
                new Mark(p, MarkKind.OVOO_DAY, "area.kherlen_steppe", 20000, 2000))).join();
        repo.put(new Mark(p, MarkKind.OVOO_DAY, "area.kherlen_steppe", 20001, 3000)).join(); // the next day replaces
        Map<MarkKind, Map<String, Long>> all = repo.load(p).join();
        assertEquals(Map.of("dungeon.khasar_den", 1000L), all.get(MarkKind.DUNGEON_CLEAR));
        assertEquals(Map.of("site.aurag", 2000L), all.get(MarkKind.SITE_SEEN));
        assertEquals(Map.of("area.kherlen_steppe", 20001L), all.get(MarkKind.OVOO_DAY));
        assertTrue(repo.load(UUID.randomUUID()).join().isEmpty());
    }

    @Test
    void countIsPerDistinctPlayer() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        repo.put(new Mark(a, MarkKind.DUNGEON_CLEAR, "dungeon.govi_bulsh", 1, 1)).join();
        repo.put(new Mark(a, MarkKind.DUNGEON_CLEAR, "dungeon.govi_bulsh", 2, 2)).join();
        repo.put(new Mark(b, MarkKind.DUNGEON_CLEAR, "dungeon.govi_bulsh", 3, 3)).join();
        assertEquals(2, repo.count(MarkKind.DUNGEON_CLEAR, "dungeon.govi_bulsh").join());
        assertEquals(0, repo.count(MarkKind.SITE_SEEN, "dungeon.govi_bulsh").join());
    }
}
