package mn.suld.plugin.persistence;

import mn.suld.api.quest.SideQuestRecord;
import mn.suld.api.quest.SideQuestStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** V32 side quests on H2 (MySQL mode) or PostgreSQL: taken once, handed in once under concurrency, abandon and reset. */
class JdbcSideQuestIT {

    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @Test
    void aQuestStartsOnceAndIsHandedInOnce() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        JdbcSideQuestRepository repo = new JdbcSideQuestRepository(db.ds(), db.dialect(), pool);
        UUID p = UUID.randomUUID(), other = UUID.randomUUID();

        assertTrue(repo.start(p, "squest.urtuu_east", 1_000L).get());
        assertFalse(repo.start(p, "squest.urtuu_east", 2_000L).get(), "a side quest is taken once");
        assertTrue(repo.start(other, "squest.urtuu_east", 1_500L).get(), "another player takes the same quest");
        repo.progress(p, "squest.urtuu_east", 1).get();
        SideQuestRecord r = repo.load(p).get().get("squest.urtuu_east");
        assertEquals(SideQuestStatus.ACTIVE, r.status());
        assertEquals(1, r.progress());
        assertEquals(1_000L, r.startedAt());

        List<CompletableFuture<Boolean>> all = new ArrayList<>();
        for (int i = 0; i < 24; i++) all.add(repo.complete(p, "squest.urtuu_east", 1, 5_000L + i));
        assertEquals(1, all.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count(), "24 concurrent hand-ins pay once");
        r = repo.load(p).get().get("squest.urtuu_east");
        assertTrue(r.done());
        assertTrue(r.completedAt() >= 5_000L);
        repo.progress(p, "squest.urtuu_east", 0).get();
        assertEquals(1, repo.load(p).get().get("squest.urtuu_east").progress(), "a done row keeps its count");
        assertFalse(repo.abandon(p, "squest.urtuu_east").get(), "a done quest cannot be abandoned");
        assertFalse(repo.start(p, "squest.urtuu_east", 9_000L).get(), "nor taken again");

        assertTrue(repo.abandon(other, "squest.urtuu_east").get());
        assertTrue(repo.load(other).get().isEmpty());
        assertTrue(repo.start(other, "squest.urtuu_east", 9_000L).get(), "an abandoned quest can be taken again");

        assertTrue(repo.reset(p, "squest.urtuu_east").get(), "staff reset removes a done row");
        Map<String, SideQuestRecord> none = repo.load(p).get();
        assertTrue(none.isEmpty());
        assertFalse(repo.reset(p, "squest.urtuu_east").get());
    }
}
