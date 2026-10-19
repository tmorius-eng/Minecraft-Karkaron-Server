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

    @Test
    void scoreBoardsCountMarksTotalsAndUnlocksPerPlayer() throws Exception {
        TestDb.Db testDb = TestDb.fresh();
        javax.sql.DataSource ds = testDb.ds();
        new SchemaMigrator(ds, testDb.dialect()).migrate();
        JdbcProfileRepository repo = new JdbcProfileRepository(ds, testDb.dialect(), Executors.newSingleThreadExecutor());
        UUID a = save(repo, "Anu", PlayerClass.BAATAR, 12, 300, 50);
        UUID b = save(repo, "Bat", PlayerClass.MERGEN, 12, 900, 10);
        UUID none = save(repo, "NoClass", null, 40, 0, 0);
        boolean pg = testDb.dialect() == SqlDialect.POSTGRESQL;
        try (java.sql.Connection c = ds.getConnection()) {
            for (Object[] m : new Object[][] {{a, "dungeon.a"}, {a, "dungeon.b"}, {b, "dungeon.a"}, {none, "dungeon.a"}}) {
                try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO suld_player_marks (player_uuid, kind, mark_key, mark_value, at) VALUES (?, 'DUNGEON_CLEAR', ?, 1, 1)")) {
                    if (pg) ps.setObject(1, m[0]); else ps.setString(1, m[0].toString());
                    ps.setString(2, (String) m[1]);
                    ps.executeUpdate();
                }
            }
            for (Object[] t : new Object[][] {{"2026-W41", a, 10L}, {"2026-W42", a, 5L}, {"2026-W42", b, 9L}}) {
                try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO suld_campaign_totals (cycle_id, player_uuid, player_name, units) VALUES (?, ?, 'x', ?)")) {
                    ps.setString(1, (String) t[0]);
                    if (pg) ps.setObject(2, t[1]); else ps.setString(2, t[1].toString());
                    ps.setLong(3, (Long) t[2]);
                    ps.executeUpdate();
                }
            }
            try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO suld_player_achievements (player_uuid, achievement_id, unlocked_at) VALUES (?, 'ach.x', 1)")) {
                if (pg) ps.setObject(1, b); else ps.setString(1, b.toString());
                ps.executeUpdate();
            }
        }
        var clears = repo.top(Leaderboard.DUNGEON_CLEARS, 10).join();
        assertEquals(List.of(a, b), clears.stream().map(Leaderboard.Entry::player).toList(), "two clears before one; a player without a class is not listed");
        assertEquals(2, clears.get(0).score());
        var week = repo.top(Leaderboard.KHURALDAI, 10).join();
        assertEquals(List.of(b, a), week.stream().map(Leaderboard.Entry::player).toList(), "the latest cycle only: 9 before 5, the old 10 does not count");
        assertEquals(9, week.get(0).score());
        assertEquals(List.of(b), repo.top(Leaderboard.ACHIEVEMENTS, 10).join().stream().map(Leaderboard.Entry::player).toList());
    }
}
