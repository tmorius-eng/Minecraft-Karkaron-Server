package mn.suld.plugin.persistence;

import mn.suld.api.persistence.RepositoryException;
import mn.suld.api.persistence.StyleRepository;
import mn.suld.api.style.PlayerStyle;
import mn.suld.api.style.Rank;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * JDBC storage of player style (V5 schema). Owned cosmetics only ever grow, so a save upserts the style row and
 * inserts the owned ids that are new, in one transaction.
 */
public final class JdbcStyleRepository implements StyleRepository {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcStyleRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Optional<PlayerStyle.Snapshot>> load(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                Set<String> owned = new LinkedHashSet<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT cosmetic_id FROM suld_player_cosmetics WHERE player_uuid = ? ORDER BY obtained_at")) {
                    bindUuid(ps, 1, player);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) owned.add(rs.getString(1));
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT rank_id, tag_id, name_color_id, chat_color_id, "
                        + "join_message_id, claimed_levels, credits, discovered_regions, daily_day, daily_streak, task_day, task_progress, aura_id, trail_id, kill_effect_id FROM suld_player_style WHERE player_uuid = ?")) {
                    bindUuid(ps, 1, player);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return owned.isEmpty() ? Optional.<PlayerStyle.Snapshot>empty()
                                    : Optional.of(new PlayerStyle.Snapshot(player, Rank.ARD, owned, null, null, null, null, 0, 0, 0, 0, 0, 0, "", null, null, null));
                        }
                        return Optional.of(new PlayerStyle.Snapshot(player, Rank.byId(rs.getString(1)), owned, rs.getString(2),
                                rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getLong(7), rs.getLong(8),
                                rs.getLong(9), rs.getInt(10), rs.getLong(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15)));
                    }
                }
            } catch (SQLException e) {
                throw new RepositoryException("load style " + player, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(PlayerStyle.Snapshot s) {
        return CompletableFuture.runAsync(() -> {
            long now = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement(upsert())) {
                        bindUuid(ps, 1, s.player());
                        ps.setString(2, s.rank().id());
                        ps.setString(3, s.tag());
                        ps.setString(4, s.nameColor());
                        ps.setString(5, s.chatColor());
                        ps.setString(6, s.joinMessage());
                        ps.setLong(7, s.claimedLevels());
                        ps.setLong(8, s.discovered());
                        ps.setLong(9, s.dailyDay());
                        ps.setInt(10, s.dailyStreak());
                        ps.setLong(11, s.taskDay());
                        ps.setString(12, s.taskProgress());
                        ps.setString(13, s.aura());
                        ps.setString(14, s.trail());
                        ps.setString(15, s.killEffect());
                        ps.setLong(16, now);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(insertOwned())) {
                        for (String id : s.owned()) {
                            bindUuid(ps, 1, s.player());
                            ps.setString(2, id);
                            ps.setLong(3, now);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RepositoryException("save style " + s.player(), e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> addCredits(UUID player, long delta) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // make sure the row exists (credits 0), then one conditional UPDATE does the arithmetic
                    try (PreparedStatement ps = conn.prepareStatement(switch (dialect) {
                        case MYSQL -> "INSERT IGNORE INTO suld_player_style (player_uuid, updated_at) VALUES (?, ?)";
                        case POSTGRESQL -> "INSERT INTO suld_player_style (player_uuid, updated_at) VALUES (?, ?) ON CONFLICT (player_uuid) DO NOTHING";
                    })) {
                        bindUuid(ps, 1, player);
                        ps.setLong(2, System.currentTimeMillis());
                        ps.executeUpdate();
                    }
                    int rows;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE suld_player_style SET credits = credits + ? WHERE player_uuid = ? AND credits + ? >= 0")) {
                        ps.setLong(1, delta);
                        bindUuid(ps, 2, player);
                        ps.setLong(3, delta);
                        rows = ps.executeUpdate();
                    }
                    long balance = -1;
                    if (rows == 1) {
                        try (PreparedStatement ps = conn.prepareStatement("SELECT credits FROM suld_player_style WHERE player_uuid = ?")) {
                            bindUuid(ps, 1, player);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) balance = rs.getLong(1);
                            }
                        }
                    }
                    conn.commit();
                    return balance;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RepositoryException("credits " + player, e);
            }
        }, executor);
    }

    private String upsert() {
        String cols = "(player_uuid, rank_id, tag_id, name_color_id, chat_color_id, join_message_id, claimed_levels, discovered_regions, daily_day, daily_streak, task_day, task_progress, aura_id, trail_id, kill_effect_id, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ";
        return switch (dialect) {
            case MYSQL -> "INSERT INTO suld_player_style " + cols + "ON DUPLICATE KEY UPDATE rank_id = VALUES(rank_id), "
                    + "tag_id = VALUES(tag_id), name_color_id = VALUES(name_color_id), chat_color_id = VALUES(chat_color_id), "
                    + "join_message_id = VALUES(join_message_id), claimed_levels = VALUES(claimed_levels), discovered_regions = VALUES(discovered_regions), daily_day = VALUES(daily_day), daily_streak = VALUES(daily_streak), task_day = VALUES(task_day), task_progress = VALUES(task_progress), aura_id = VALUES(aura_id), trail_id = VALUES(trail_id), kill_effect_id = VALUES(kill_effect_id), updated_at = VALUES(updated_at)";
            case POSTGRESQL -> "INSERT INTO suld_player_style " + cols + "ON CONFLICT (player_uuid) DO UPDATE SET rank_id = EXCLUDED.rank_id, "
                    + "tag_id = EXCLUDED.tag_id, name_color_id = EXCLUDED.name_color_id, chat_color_id = EXCLUDED.chat_color_id, "
                    + "join_message_id = EXCLUDED.join_message_id, claimed_levels = EXCLUDED.claimed_levels, discovered_regions = EXCLUDED.discovered_regions, daily_day = EXCLUDED.daily_day, daily_streak = EXCLUDED.daily_streak, task_day = EXCLUDED.task_day, task_progress = EXCLUDED.task_progress, aura_id = EXCLUDED.aura_id, trail_id = EXCLUDED.trail_id, kill_effect_id = EXCLUDED.kill_effect_id, updated_at = EXCLUDED.updated_at";
        };
    }

    private String insertOwned() {
        return switch (dialect) {
            case MYSQL -> "INSERT IGNORE INTO suld_player_cosmetics (player_uuid, cosmetic_id, obtained_at) VALUES (?, ?, ?)";
            case POSTGRESQL -> "INSERT INTO suld_player_cosmetics (player_uuid, cosmetic_id, obtained_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT (player_uuid, cosmetic_id) DO NOTHING";
        };
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid);
        else ps.setString(index, uuid.toString());
    }
}
