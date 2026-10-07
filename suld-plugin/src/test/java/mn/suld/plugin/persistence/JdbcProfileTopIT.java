package mn.suld.plugin.persistence;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.leaderboard.Leaderboard;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Real-database test of the leaderboard query. Opt-in: SULD_TEST_PG_URL / _USER / _PASS (throwaway database). */
class JdbcProfileTopIT {

    @Test
    void topOrdersByLevelAndCoinsAndSkipsPlayersWithoutAClass() throws Exception {
        TestDb.Db testDb = TestDb.fresh();
        javax.sql.DataSource ds = testDb.ds();
        new SchemaMigrator(ds, testDb.dialect()).migrate();
        JdbcProfileRepository repo = new JdbcProfileRepository(ds, testDb.dialect(), Executors.newSingleThreadExecutor());
        UUID a = save(repo, "Anu", PlayerClass.BAATAR, 12, 300, 50);
        UUID b = save(repo, "Bat", PlayerClass.MERGEN, 12, 900, 10);
        UUID c = save(repo, "Chimeg", PlayerClass.BOO, 4, 0, 5000);
        save(repo, "NoClass", null, 40, 0, 99999);
        assertEquals(List.of(b, a, c), repo.top(Leaderboard.LEVEL, 10).join().stream().map(Leaderboard.Entry::player).toList());
        assertEquals(List.of(c, a), repo.top(Leaderboard.COINS, 2).join().stream().map(Leaderboard.Entry::player).toList());
    }

    private static UUID save(JdbcProfileRepository repo, String name, PlayerClass clazz, int level, long exp, long coins) {
        UUID id = UUID.randomUUID();
        PlayerProfile p = PlayerProfile.createNew(id, name, Instant.now());
        if (clazz != null) p.selectClass(clazz);
        p.progression(new Progression(level, exp));
        p.currency(coins);
        repo.save(p).join();
        return id;
    }
}
