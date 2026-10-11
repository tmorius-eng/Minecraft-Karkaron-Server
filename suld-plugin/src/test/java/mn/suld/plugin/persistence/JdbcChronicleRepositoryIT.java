package mn.suld.plugin.persistence;

import mn.suld.api.chronicle.ChronicleEntry;
import mn.suld.api.chronicle.ChronicleKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V19: one first per subject under contention; records converge to the best; hidden rows stay. */
class JdbcChronicleRepositoryIT {

    private JdbcChronicleRepository repo;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        repo = new JdbcChronicleRepository(db.ds(), db.dialect(), pool);
    }

    private static ChronicleEntry first(UUID p, String name) {
        return ChronicleEntry.of(ChronicleKind.FIRST_DUNGEON_CLEAR, "dungeon.khasar_den", p, name, List.of(name, "Бөртэ"), "ОВГ", 245L, "", "2026-W41", 1000);
    }

    private static ChronicleEntry record(UUID p, String name, long seconds) {
        return ChronicleEntry.of(ChronicleKind.FASTEST_DUNGEON_CLEAR, "dungeon.khasar_den", p, name, List.of(name), "", seconds, "", "2026-W41", 1000);
    }

    @Test
    void exactlyOneFirstUnderContention() {
        List<CompletableFuture<Optional<ChronicleEntry>>> all = new ArrayList<>();
        for (int i = 0; i < 24; i++) all.add(repo.claimFirst(first(UUID.randomUUID(), "P" + i)));
        long claimed = all.stream().map(CompletableFuture::join).filter(Optional::isPresent).count();
        assertEquals(1, claimed, "one of 24 concurrent claims is the first");
        assertEquals(1, repo.latest(50).join().size(), "losers leave no line behind");
        ChronicleEntry e = repo.holder(ChronicleKind.FIRST_DUNGEON_CLEAR, "dungeon.khasar_den").join().orElseThrow();
        assertEquals(2, e.party().size());
        assertEquals("ОВГ", e.clanTag());
        assertEquals(245L, e.value());
        assertTrue(repo.claimFirst(first(UUID.randomUUID(), "late")).join().isEmpty());
        assertTrue(repo.holder(ChronicleKind.FIRST_DUNGEON_CLEAR, "dungeon.govi_bulsh").join().isEmpty());
    }

    @Test
    void recordsConvergeToTheBest() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(repo.recordIfBetter(record(a, "A", 300)).join().isPresent());
        assertTrue(repo.recordIfBetter(record(b, "B", 400)).join().isEmpty(), "slower does not replace");
        List<CompletableFuture<Optional<ChronicleEntry>>> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) all.add(repo.recordIfBetter(record(b, "B", 200 - i)));
        all.forEach(CompletableFuture::join);
        ChronicleEntry best = repo.holder(ChronicleKind.FASTEST_DUNGEON_CLEAR, "dungeon.khasar_den").join().orElseThrow();
        assertEquals(189L, best.value(), "the fastest of the concurrent attempts holds the record");
        assertEquals("B", best.playerName());
        assertEquals(best.id(), repo.byKind(ChronicleKind.FASTEST_DUNGEON_CLEAR, 1).join().get(0).id(), "the newest line is the record");
        assertTrue(repo.byPlayer(a, 10).join().stream().allMatch(e -> a.equals(e.player())));
        assertFalse(repo.byPlayer(a, 10).join().isEmpty());
    }

    @Test
    void appendAndHide() {
        ChronicleEntry e = repo.append(ChronicleEntry.of(ChronicleKind.KHURALDAI_RESULT, "2026-W41", null, "", List.of(), "", null,
                "«Чонын Их Аян» 12 саналаар сонгогдов", "2026-W41", 5)).join();
        assertTrue(e.id() > 0);
        assertEquals(1, repo.latest(10).join().size());
        assertTrue(repo.hide(e.id()).join());
        assertFalse(repo.hide(e.id() + 1000).join());
        assertTrue(repo.latest(10).join().isEmpty(), "hidden rows leave every listing");
        ChronicleEntry again = repo.append(ChronicleEntry.of(ChronicleKind.RELIC_CLAIMED, "relic.khukh_suld", UUID.randomUUID(), "Тэмүүжин", List.of(), "", null, "", "2026-W41", 6)).join();
        assertTrue(again.id() > e.id(), "ids only grow: nothing is deleted");
    }
}
