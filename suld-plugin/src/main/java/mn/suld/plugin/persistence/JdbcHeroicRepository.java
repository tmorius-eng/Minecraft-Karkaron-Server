package mn.suld.plugin.persistence;

import mn.suld.api.dungeon.heroic.HeroicLadder;
import mn.suld.api.persistence.HeroicRepository;
import mn.suld.api.persistence.RepositoryException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Heroic keys over the V24 tables (docs/HEROIC_DUNGEONS.md). {@link #raise} only ever raises, in one transaction: an
 * upsert lifts {@code open_key} by GREATEST, then a conditional update writes {@code best_key} and {@code best_seconds}
 * together when the result is a new best (a higher key, or the same key timed faster). Every step is monotone, so
 * results written in any order, on any number of threads, end at their maximum.
 */
public final class JdbcHeroicRepository implements HeroicRepository {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcHeroicRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Map<String, Key>> keys(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT dungeon_id, open_key, best_key, best_seconds FROM suld_heroic_keys WHERE player_uuid = ?")) {
                bindUuid(ps, 1, player);
                Map<String, Key> out = new HashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String d = rs.getString(1);
                        out.put(d, new Key(d, rs.getInt(2), rs.getInt(3), rs.getInt(4)));
                    }
                }
                return out;
            } catch (SQLException e) {
                throw new RepositoryException("heroic keys of " + player, e);
            }
        }, executor);
    }

    /** The row, or the open key raised on it (GREATEST, so concurrent results commute). A new row gets the best too. */
    private String upsertOpen() {
        return switch (dialect) {
            case POSTGRESQL -> "INSERT INTO suld_heroic_keys (player_uuid, dungeon_id, open_key, best_key, best_seconds, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (player_uuid, dungeon_id) DO UPDATE SET "
                    + "open_key = GREATEST(suld_heroic_keys.open_key, EXCLUDED.open_key), updated_at = EXCLUDED.updated_at";
            case MYSQL -> "INSERT INTO suld_heroic_keys (player_uuid, dungeon_id, open_key, best_key, best_seconds, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                    + "open_key = GREATEST(open_key, VALUES(open_key)), updated_at = VALUES(updated_at)";
        };
    }

    /**
     * A timed key better than the stored best (a higher key, or the same key faster) writes both best columns from it.
     * A conditional update: the database checks the condition on the row it locks, so of two racing results the better
     * one is left, whatever their order. Plain SQL on every dialect (H2's MySQL mode cannot parse VALUES() inside CASE).
     */
    private static final String RAISE_BEST = "UPDATE suld_heroic_keys SET best_key = ?, best_seconds = ? "
            + "WHERE player_uuid = ? AND dungeon_id = ? AND (best_key < ? OR (best_key = ? AND (best_seconds = 0 OR best_seconds > ?)))";

    @Override
    public CompletableFuture<Void> raise(UUID player, String dungeonId, int open, int best, int bestSeconds, long now) {
        int key = Math.max(0, Math.min(HeroicLadder.MAX_KEY, best));
        int seconds = key == 0 ? 0 : Math.max(0, bestSeconds); // no best, no time (the in-memory rule)
        return CompletableFuture.runAsync(() -> {
            for (int attempt = 1; ; attempt++) {
                try (Connection c = dataSource.getConnection()) {
                    boolean auto = c.getAutoCommit();
                    c.setAutoCommit(false);
                    try {
                        try (PreparedStatement ps = c.prepareStatement(upsertOpen())) {
                            bindUuid(ps, 1, player);
                            ps.setString(2, dungeonId);
                            ps.setInt(3, HeroicLadder.clamp(open));
                            ps.setInt(4, key);
                            ps.setInt(5, seconds);
                            ps.setLong(6, now);
                            ps.executeUpdate();
                        }
                        if (key > 0) {
                            try (PreparedStatement ps = c.prepareStatement(RAISE_BEST)) {
                                ps.setInt(1, key);
                                ps.setInt(2, seconds);
                                bindUuid(ps, 3, player);
                                ps.setString(4, dungeonId);
                                ps.setInt(5, key);
                                ps.setInt(6, key);
                                ps.setInt(7, seconds);
                                ps.executeUpdate();
                            }
                        }
                        c.commit();
                        return;
                    } catch (SQLException e) {
                        c.rollback();
                        throw e;
                    } finally {
                        c.setAutoCommit(auto);
                    }
                } catch (SQLException e) {
                    // two first writes of the same row racing (23…: the loser's insert sees the winner's row next time), or
                    // a deadlock / serialization failure (40…) between them: the same writes again are safe, they only raise
                    String state = e.getSQLState();
                    boolean retry = state != null && (state.startsWith("23") || state.startsWith("40"));
                    if (!retry || attempt >= 5) throw new RepositoryException("raise heroic key of " + player + " in " + dungeonId, e);
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> logRun(Run r) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO suld_heroic_runs (run_id, dungeon_id, key_level, outcome, clock_seconds, deaths, affixes, members, week_id, finished_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bindUuid(ps, 1, r.runId());
                ps.setString(2, r.dungeonId());
                ps.setInt(3, r.key());
                ps.setString(4, r.outcome().name());
                ps.setInt(5, r.clockSeconds());
                ps.setInt(6, r.deaths());
                ps.setString(7, cut(r.affixes(), 128));
                ps.setString(8, cut(r.members(), 400));
                ps.setString(9, cut(r.weekId(), 16));
                ps.setLong(10, r.finishedAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("log heroic run " + r.runId(), e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<Run>> board(String dungeonId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                    "SELECT run_id, dungeon_id, key_level, outcome, clock_seconds, deaths, affixes, members, week_id, finished_at "
                            + "FROM suld_heroic_runs WHERE dungeon_id = ? AND outcome = 'TIMED' ORDER BY key_level DESC, clock_seconds ASC")) {
                ps.setString(1, dungeonId);
                ps.setMaxRows(Math.max(1, limit));
                List<Run> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Run(JdbcCampaignRepository.readUuid(rs, "run_id"), rs.getString("dungeon_id"), rs.getInt("key_level"),
                                HeroicLadder.Outcome.valueOf(rs.getString("outcome")), rs.getInt("clock_seconds"), rs.getInt("deaths"),
                                rs.getString("affixes"), rs.getString("members"), rs.getString("week_id"), rs.getLong("finished_at")));
                    }
                }
                return out;
            } catch (SQLException e) {
                throw new RepositoryException("heroic board of " + dungeonId, e);
            }
        }, executor);
    }

    private static String cut(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) : s;
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid);
        else ps.setString(index, uuid.toString());
    }
}
