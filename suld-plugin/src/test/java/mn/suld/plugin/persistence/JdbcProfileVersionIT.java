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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optimistic versioning of profile rows: a snapshot older than the stored row (a second writer, a late retry) never
 * overwrites it: the stale save is refused with an error on every dialect and the row is left unchanged.
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
        // refused on every dialect (MySQL/H2 report found rows, so the version is read back instead)
        assertThrows(CompletionException.class, () -> repo.save(stale).join());
        assertEquals(stored - 1, stale.version(), "a refused save never marks the copy persisted");
        PlayerProfile after = repo.find(id).join().orElseThrow();
        assertEquals(5000, after.currency());
        assertEquals(10, after.progression().level());

        // the live copy keeps saving normally
        live.currency(7000);
        repo.save(live).join();
        assertEquals(7000, repo.find(id).join().orElseThrow().currency());
    }

    @Test
    void anIdenticalResaveIsNotMistakenForStale() throws Exception {
        TestDb.Db db = TestDb.fresh();
        new SchemaMigrator(db.ds(), db.dialect()).migrate();
        JdbcProfileRepository repo = new JdbcProfileRepository(db.ds(), db.dialect(), Executors.newSingleThreadExecutor());
        PlayerProfile live = PlayerProfile.createNew(UUID.randomUUID(), "Anu", Instant.now());
        live.currency(42);
        repo.save(live).join();
        repo.save(live).join(); // nothing changed: the row already holds this version
        assertFalse(live.isDirty());
        assertEquals(42, repo.find(live.playerId()).join().orElseThrow().currency());
    }
}
