package mn.suld.plugin.persistence;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.quest.QuestState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Optimistic versioning of profile rows: a snapshot older than the stored row (a second writer, a late retry) never
 * overwrites it. On PostgreSQL the stale save is refused with an error; on MySQL/H2 the row is left unchanged.
 */
class JdbcProfileVersionIT {

    @Test
    void aStaleSnapshotNeverRollsBackANewerRow() throws Exception {
        TestDb.Db db = TestDb.fresh();
        new SchemaMigrator(db.ds(), db.dialect()).migrate();
        JdbcProfileRepository repo = new JdbcProfileRepository(db.ds(), db.dialect(), Executors.newSingleThreadExecutor());

        UUID id = UUID.randomUUID();
        PlayerProfile live = PlayerProfile.createNew(id, "Anu", Instant.now());
        live.progression(new Progression(10, 0));
        live.currency(5000);
        repo.save(live).join();
        long stored = repo.find(id).join().orElseThrow().version();

        // a copy loaded before those changes, saved late: its version is behind the row
        PlayerProfile stale = PlayerProfile.restore(id, "Anu", null, new Progression(1, 0), Instant.now(), Instant.now(),
                stored - 1, 1, new QuestState("", 0, false), null);
        if (db.dialect() == SqlDialect.POSTGRESQL) {
            assertThrows(CompletionException.class, () -> repo.save(stale).join());
        } else {
            repo.save(stale).join();
        }
        PlayerProfile after = repo.find(id).join().orElseThrow();
        assertEquals(5000, after.currency());
        assertEquals(10, after.progression().level());

        // the live copy keeps saving normally
        live.currency(7000);
        repo.save(live).join();
        assertEquals(7000, repo.find(id).join().orElseThrow().currency());
    }
}
