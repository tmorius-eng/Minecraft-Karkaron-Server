package mn.suld.plugin.persistence;

import mn.suld.api.persistence.AchievementRepository;
import mn.suld.api.persistence.RepositoryException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Deeds, achievements and the pass over the V28 tables (docs/ACHIEVEMENTS_AND_PASS.md). Every write commutes: counts
 * and points add, best values keep the greater ({@code GREATEST}), unlocks, claims and an opened heroic track are
 * inserted where absent. A batch is one transaction.
 */
public final class JdbcAchievementRepository implements AchievementRepository {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcAchievementRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Stored> load(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                Map<String, Long> deeds = new HashMap<>(), unlocked = new HashMap<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT deed_key, value FROM suld_player_deeds WHERE player_uuid = ?")) {
                    bindUuid(ps, 1, player);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) deeds.put(rs.getString(1), rs.getLong(2));
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT achievement_id, unlocked_at FROM suld_player_achievements WHERE player_uuid = ?")) {
                    bindUuid(ps, 1, player);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) unlocked.put(rs.getString(1), rs.getLong(2));
                    }
                }
                return new Stored(deeds, unlocked);
            } catch (SQLException e) {
                throw new RepositoryException("deeds of " + player, e);
            }
        }, executor);
    }

    private String countSql() {
        return switch (dialect) {
            case POSTGRESQL -> "INSERT INTO suld_player_deeds (player_uuid, deed_key, value, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (player_uuid, deed_key) DO UPDATE SET value = suld_player_deeds.value + EXCLUDED.value, updated_at = EXCLUDED.updated_at";
            case MYSQL -> "INSERT INTO suld_player_deeds (player_uuid, deed_key, value, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE value = value + VALUES(value), updated_at = VALUES(updated_at)";
        };
    }

    private String bestSql() {
        return switch (dialect) {
            case POSTGRESQL -> "INSERT INTO suld_player_deeds (player_uuid, deed_key, value, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (player_uuid, deed_key) DO UPDATE SET value = GREATEST(suld_player_deeds.value, EXCLUDED.value), updated_at = EXCLUDED.updated_at";
            case MYSQL -> "INSERT INTO suld_player_deeds (player_uuid, deed_key, value, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE value = GREATEST(value, VALUES(value)), updated_at = VALUES(updated_at)";
        };
    }

    /** Inserts a row unless its key is there: {@code ON CONFLICT DO NOTHING} / {@code INSERT IGNORE}. */
    private String insertOnce(String table, String columns, int values) {
        String marks = String.join(", ", java.util.Collections.nCopies(values, "?"));
        return switch (dialect) {
            case POSTGRESQL -> "INSERT INTO " + table + " (" + columns + ") VALUES (" + marks + ") ON CONFLICT DO NOTHING";
            case MYSQL -> "INSERT IGNORE INTO " + table + " (" + columns + ") VALUES (" + marks + ")";
        };
    }

    @Override
    public CompletableFuture<Void> write(UUID player, Map<String, Long> counts, Map<String, Long> bests, Map<String, Long> unlocks, long now) {
        Map<String, Long> add = positive(counts), best = positive(bests), once = new HashMap<>(unlocks);
        if (add.isEmpty() && best.isEmpty() && once.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            for (int attempt = 1; ; attempt++) {
                try (Connection c = dataSource.getConnection()) {
                    boolean auto = c.getAutoCommit();
                    c.setAutoCommit(false);
                    try {
                        batch(c, countSql(), player, add, now);
                        batch(c, bestSql(), player, best, now);
                        if (!once.isEmpty()) {
                            try (PreparedStatement ps = c.prepareStatement(insertOnce("suld_player_achievements", "player_uuid, achievement_id, unlocked_at", 3))) {
                                for (Map.Entry<String, Long> en : once.entrySet()) {
                                    bindUuid(ps, 1, player);
                                    ps.setString(2, en.getKey());
                                    ps.setLong(3, en.getValue());
                                    ps.addBatch();
                                }
                                ps.executeBatch();
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
                    // two first writes of a row racing (23…), or a deadlock between batches (40…): the whole batch was
                    // rolled back, so writing it again counts it once
                    String state = e.getSQLState();
                    boolean retry = state != null && (state.startsWith("23") || state.startsWith("40"));
                    if (!retry || attempt >= 5) throw new RepositoryException("deeds of " + player, e);
                }
            }
        }, executor);
    }

    private void batch(Connection c, String sql, UUID player, Map<String, Long> values, long now) throws SQLException {
        if (values.isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, Long> en : values.entrySet()) {
                bindUuid(ps, 1, player);
                ps.setString(2, en.getKey());
                ps.setLong(3, en.getValue());
                ps.setLong(4, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static Map<String, Long> positive(Map<String, Long> m) {
        Map<String, Long> out = new HashMap<>();
        m.forEach((k, v) -> {
            if (v != null && v > 0) out.put(k, v);
        });
        return out;
    }

    @Override
    public CompletableFuture<Pass> pass(UUID player, String season) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                long points = 0;
                boolean heroic;
                Set<String> claimed = new HashSet<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT points FROM suld_pass_points WHERE player_uuid = ? AND season_id = ?")) {
                    bindUuid(ps, 1, player);
                    ps.setString(2, season);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) points = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM suld_pass_heroic WHERE player_uuid = ? AND season_id = ?")) {
                    bindUuid(ps, 1, player);
                    ps.setString(2, season);
                    try (ResultSet rs = ps.executeQuery()) {
                        heroic = rs.next();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT reward_key FROM suld_pass_claims WHERE player_uuid = ? AND season_id = ?")) {
                    bindUuid(ps, 1, player);
                    ps.setString(2, season);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) claimed.add(rs.getString(1));
                    }
                }
                return new Pass(points, heroic, claimed);
            } catch (SQLException e) {
                throw new RepositoryException("pass of " + player, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> addPoints(UUID player, String season, long points, long now) {
        if (points <= 0) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            String sql = switch (dialect) {
                case POSTGRESQL -> "INSERT INTO suld_pass_points (player_uuid, season_id, points, updated_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (player_uuid, season_id) DO UPDATE SET points = suld_pass_points.points + EXCLUDED.points, updated_at = EXCLUDED.updated_at";
                case MYSQL -> "INSERT INTO suld_pass_points (player_uuid, season_id, points, updated_at) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE points = points + VALUES(points), updated_at = VALUES(updated_at)";
            };
            for (int attempt = 1; ; attempt++) {
                try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                    bindUuid(ps, 1, player);
                    ps.setString(2, season);
                    ps.setLong(3, points);
                    ps.setLong(4, now);
                    ps.executeUpdate();
                    return;
                } catch (SQLException e) {
                    String state = e.getSQLState();
                    boolean retry = state != null && (state.startsWith("23") || state.startsWith("40"));
                    if (!retry || attempt >= 5) throw new RepositoryException("pass points of " + player, e);
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> claim(UUID player, String season, String rewardKey, long now) {
        return insertOnceAsync("suld_pass_claims", "player_uuid, season_id, reward_key, claimed_at", player, season, rewardKey, now);
    }

    @Override
    public CompletableFuture<Boolean> openHeroic(UUID player, String season, String source, long now) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(insertOnce("suld_pass_heroic", "player_uuid, season_id, opened_at, source", 4))) {
                bindUuid(ps, 1, player);
                ps.setString(2, season);
                ps.setLong(3, now);
                ps.setString(4, source);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RepositoryException("heroic pass of " + player, e);
            }
        }, executor);
    }

    private CompletableFuture<Boolean> insertOnceAsync(String table, String columns, UUID player, String season, String key, long now) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(insertOnce(table, columns, 4))) {
                bindUuid(ps, 1, player);
                ps.setString(2, season);
                ps.setString(3, key);
                ps.setLong(4, now);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RepositoryException(table + " of " + player, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> reset(UUID player) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                boolean auto = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    for (String t : new String[]{"suld_player_deeds", "suld_player_achievements", "suld_pass_points", "suld_pass_claims", "suld_pass_heroic"}) {
                        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t + " WHERE player_uuid = ?")) {
                            bindUuid(ps, 1, player);
                            ps.executeUpdate();
                        }
                    }
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(auto);
                }
            } catch (SQLException e) {
                throw new RepositoryException("reset deeds of " + player, e);
            }
        }, executor);
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid);
        else ps.setString(index, uuid.toString());
    }
}
