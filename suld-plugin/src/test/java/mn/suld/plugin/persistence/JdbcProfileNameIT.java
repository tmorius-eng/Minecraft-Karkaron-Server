package mn.suld.plugin.persistence;

import mn.suld.api.identity.PlayerIdentity;
import mn.suld.api.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The name lookup the hybrid login gate uses (V30): H2 on every build, PostgreSQL when SULD_TEST_PG_URL is set. */
class JdbcProfileNameIT {

    @Test
    void aNameIsFoundWithItsCaseIgnoredAndTheOldestProfileOwnsIt() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        JdbcProfileRepository repo = new JdbcProfileRepository(db.ds(), db.dialect(), Executors.newSingleThreadExecutor());

        UUID dorj = PlayerIdentity.offlineUuid("Dorj");
        repo.save(PlayerProfile.createNew(dorj, "Dorj", Instant.ofEpochMilli(1_700_000_000_000L))).join();
        assertEquals(Optional.of(dorj), repo.ownerOfName("Dorj").join());
        assertEquals(Optional.of(dorj), repo.ownerOfName("dORJ").join(), "the case is ignored");
        assertEquals(Optional.empty(), repo.ownerOfName("Dorjo").join(), "a longer name is another name");
        assertEquals(Optional.empty(), repo.ownerOfName("Dor_").join(), "no pattern matching");

        // rows from before the check can differ only in case: the older profile keeps the name
        UUID twin = PlayerIdentity.offlineUuid("dorj");
        repo.save(PlayerProfile.createNew(twin, "dorj", Instant.ofEpochMilli(1_700_000_100_000L))).join();
        assertEquals(Optional.of(dorj), repo.ownerOfName("dorj").join());
    }
}
