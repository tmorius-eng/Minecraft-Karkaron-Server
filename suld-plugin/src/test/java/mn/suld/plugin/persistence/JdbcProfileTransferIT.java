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

/** {@code saveTogether}: two profile rows in one transaction, both written or neither (the /pay and /trade coin leg). */
class JdbcProfileTransferIT {

    private static JdbcProfileRepository repo(TestDb.Db db) throws Exception {
        new SchemaMigrator(db.ds(), db.dialect()).migrate();
        return new JdbcProfileRepository(db.ds(), db.dialect(), Executors.newSingleThreadExecutor());
    }

    @Test
    void bothRowsAreWrittenAndBothProfilesAreClean() throws Exception {
        TestDb.Db db = TestDb.fresh();
        JdbcProfileRepository repo = repo(db);
        PlayerProfile a = PlayerProfile.createNew(UUID.randomUUID(), "Payer", Instant.now());
        PlayerProfile b = PlayerProfile.createNew(UUID.randomUUID(), "Payee", Instant.now());
        a.currency(100);
        repo.save(a).join();
        repo.save(b).join();

        a.addCurrency(-40);
        b.addCurrency(40);
        repo.saveTogether(a, b).join();

        assertEquals(60, repo.find(a.playerId()).join().orElseThrow().currency());
        assertEquals(40, repo.find(b.playerId()).join().orElseThrow().currency());
        assertFalse(a.isDirty());
        assertFalse(b.isDirty());
    }

    @Test
    void aStaleSecondProfileRollsTheFirstBack() throws Exception {
        TestDb.Db db = TestDb.fresh();
        JdbcProfileRepository repo = repo(db);
        PlayerProfile a = PlayerProfile.createNew(UUID.randomUUID(), "Payer", Instant.now());
        PlayerProfile b = PlayerProfile.createNew(UUID.randomUUID(), "Payee", Instant.now());
        a.currency(100);
        b.currency(10);
        repo.save(a).join();
        repo.save(b).join();
        long storedB = repo.find(b.playerId()).join().orElseThrow().version();
        // a copy of B that is behind the stored row
        PlayerProfile staleB = PlayerProfile.restore(b.playerId(), "Payee", null, new Progression(1, 0), Instant.now(), Instant.now(),
                storedB - 1, 50, new QuestState("", 0, false), null);

        a.addCurrency(-40);
        assertThrows(CompletionException.class, () -> repo.saveTogether(a, staleB).join());

        assertEquals(100, repo.find(a.playerId()).join().orElseThrow().currency(), "the first row was rolled back");
        assertEquals(10, repo.find(b.playerId()).join().orElseThrow().currency(), "the newer row was kept");
        assertTrue(a.isDirty(), "nothing was marked persisted");
        assertEquals(storedB - 1, staleB.version(), "the stale copy was not bumped to the row's version");
    }

    @Test
    void anSqlErrorOnTheSecondRowRollsTheFirstBack() throws Exception {
        TestDb.Db db = TestDb.fresh();
        JdbcProfileRepository repo = repo(db);
        PlayerProfile a = PlayerProfile.createNew(UUID.randomUUID(), "Payer", Instant.now());
        a.currency(100);
        repo.save(a).join();
        // name VARCHAR(16): a 17-character name fails the insert of the second row
        PlayerProfile bad = PlayerProfile.createNew(UUID.randomUUID(), "SeventeenLetters!", Instant.now());

        a.addCurrency(-40);
        assertThrows(CompletionException.class, () -> repo.saveTogether(a, bad).join());

        assertEquals(100, repo.find(a.playerId()).join().orElseThrow().currency());
        assertTrue(repo.find(bad.playerId()).join().isEmpty());
        assertTrue(a.isDirty());
    }
}
