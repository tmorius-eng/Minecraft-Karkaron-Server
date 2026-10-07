package mn.suld.plugin.persistence;

import mn.suld.api.clan.Clan;
import mn.suld.api.clan.ClanRank;
import mn.suld.api.clan.ClanRegistry;
import mn.suld.api.persistence.RepositoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-database test of the V1-V3 migrations and clan persistence. Opt-in: set
 * SULD_TEST_PG_URL (e.g. jdbc:postgresql://127.0.0.1:5432/suld_it), SULD_TEST_PG_USER and
 * SULD_TEST_PG_PASS to a THROWAWAY database — the test drops all suld_ tables first.
 */
class JdbcClanRepositoryIT {

    private javax.sql.DataSource ds;
    private TestDb.Db testDb;
    private JdbcClanRepository repo;
    private final ExecutorService single = Executors.newSingleThreadExecutor();

    @BeforeEach
    void freshSchema() throws Exception {
        testDb = TestDb.fresh();
        ds = testDb.ds();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(ds, testDb.dialect()).migrate(), "all migrations applied to a fresh schema");
        assertEquals(0, new SchemaMigrator(ds, testDb.dialect()).migrate(), "idempotent");
        repo = new JdbcClanRepository(ds, testDb.dialect(), single, Logger.getLogger("it"));
    }

    @Test
    void saveLoadMoveAndCascade() {
        ClanRegistry reg = new ClanRegistry(Clock.systemUTC(), Duration.ofMinutes(5));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Clan wolves = reg.create(a, "Alpha", "Хүрэн Чоно", "ХЧ").clan();
        reg.invite(a, b);
        reg.accept(b, "Bravo");
        reg.promote(a, b);
        reg.contribute(b, 777);
        Clan eagles = reg.create(c, "Charlie", "Бүргэд", "БРГ").clan();
        repo.save(wolves.snapshot());
        repo.save(eagles.snapshot()).join();

        List<Clan> loaded = repo.loadAll().join();
        assertEquals(2, loaded.size());
        Clan back = loaded.stream().filter(x -> x.tag().equals("ХЧ")).findFirst().orElseThrow();
        assertEquals("Хүрэн Чоно", back.name(), "Cyrillic round-trips");
        assertEquals(777, back.exp());
        assertEquals(ClanRank.OFFICER, back.member(b).orElseThrow().rank());
        assertEquals(777, back.member(b).orElseThrow().contribution());

        // b moves from wolves to eagles: ordered saves keep the one-clan-per-player PK happy.
        reg.kick(a, b);
        reg.invite(c, b);
        reg.accept(b, "Bravo");
        repo.save(wolves.snapshot());
        repo.save(eagles.snapshot()).join();
        Clan eaglesBack = repo.loadAll().join().stream().filter(x -> x.tag().equals("БРГ")).findFirst().orElseThrow();
        assertTrue(eaglesBack.contains(b));

        repo.delete(wolves.id()).join();
        List<Clan> after = repo.loadAll().join();
        assertEquals(1, after.size(), "cascade removed the clan and its members");
    }

    @Test
    void databaseRejectsDuplicateTagsAndDoubleMembership() {
        ClanRegistry r1 = new ClanRegistry(Clock.systemUTC(), Duration.ofMinutes(5));
        ClanRegistry r2 = new ClanRegistry(Clock.systemUTC(), Duration.ofMinutes(5));
        UUID p = UUID.randomUUID();
        Clan first = r1.create(p, "P", "First", "DUP").clan();
        repo.save(first.snapshot()).join();
        // A second registry (e.g. a buggy second server on the same DB) cannot bypass the schema.
        Clan clash = r2.create(UUID.randomUUID(), "Q", "Second", "DUP").clan();
        CompletionException tag = assertThrows(CompletionException.class, () -> repo.save(clash.snapshot()).join());
        assertInstanceOf(RepositoryException.class, tag.getCause());
        Clan stealsMember = r2.create(p, "P", "Third", "THR").clan();
        assertThrows(CompletionException.class, () -> repo.save(stealsMember.snapshot()).join(),
                "same player in two clans is impossible at the storage level");
    }
}
