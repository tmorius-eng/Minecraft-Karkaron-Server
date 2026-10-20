package mn.suld.plugin.persistence;

import mn.suld.api.persistence.RepositoryException;
import mn.suld.api.persistence.SideQuestRepository;
import mn.suld.api.quest.SideQuestRecord;
import mn.suld.api.quest.SideQuestStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Side quests over the V32 table (docs/SIDE_QUESTS.md). A take is an INSERT the primary key refuses twice; the hand-in
 * is {@code UPDATE … SET state = 'DONE' WHERE state = 'ACTIVE'}, and the payment claims the reward with
 * {@code UPDATE … SET paid_at = ? WHERE paid_at = 0}, so under any number of concurrent clicks a reward is paid once,
 * and a player who left before it was paid is paid at the next load. Every call runs on the executor, never on the
 * server thread.
 */
public final class JdbcSideQuestRepository implements SideQuestRepository {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcSideQuestRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Map<String, SideQuestRecord>> load(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT quest_id, state, progress, started_at, completed_at, paid_at FROM suld_side_quests"
                         + " WHERE player_uuid = ? ORDER BY started_at, quest_id")) {
                bindUuid(ps, 1, player);
                Map<String, SideQuestRecord> out = new LinkedHashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        SideQuestStatus st;
                        try {
                            st = SideQuestStatus.valueOf(rs.getString(2));
                        } catch (IllegalArgumentException unknown) {
                            continue; // a state this build does not know (MySQL keeps no CHECK): never guessed
                        }
                        out.put(rs.getString(1), new SideQuestRecord(rs.getString(1), st, rs.getInt(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)));
                    }
                }
                return out;
            } catch (SQLException e) {
                throw new RepositoryException("side quests of " + player, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> start(UUID player, String questId, long now) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO suld_side_quests (player_uuid, quest_id, state, progress, started_at, completed_at, paid_at)"
                         + " VALUES (?, ?, 'ACTIVE', 0, ?, 0, 0)")) {
                bindUuid(ps, 1, player);
                ps.setString(2, questId);
                ps.setLong(3, now);
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                if (e.getSQLState() != null && e.getSQLState().startsWith("23")) return false; // taken before
                throw new RepositoryException("start " + questId + " for " + player, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> progress(UUID player, String questId, int progress) {
        return CompletableFuture.runAsync(() -> update("UPDATE suld_side_quests SET progress = ? WHERE player_uuid = ? AND quest_id = ? AND state = 'ACTIVE'",
                ps -> {
                    ps.setInt(1, Math.max(0, progress));
                    bindUuid(ps, 2, player);
                    ps.setString(3, questId);
                }, "progress of " + questId), executor);
    }

    @Override
    public CompletableFuture<Boolean> complete(UUID player, String questId, int progress, long now) {
        return CompletableFuture.supplyAsync(() -> update("UPDATE suld_side_quests SET state = 'DONE', progress = ?, completed_at = ?, paid_at = 0"
                + " WHERE player_uuid = ? AND quest_id = ? AND state = 'ACTIVE'", ps -> {
            ps.setInt(1, Math.max(0, progress));
            ps.setLong(2, now);
            bindUuid(ps, 3, player);
            ps.setString(4, questId);
        }, "complete " + questId) == 1, executor);
    }

    @Override
    public CompletableFuture<Boolean> claimReward(UUID player, String questId, long now) {
        return CompletableFuture.supplyAsync(() -> update("UPDATE suld_side_quests SET paid_at = ?"
                + " WHERE player_uuid = ? AND quest_id = ? AND state = 'DONE' AND paid_at = 0", ps -> {
            ps.setLong(1, Math.max(1, now));
            bindUuid(ps, 2, player);
            ps.setString(3, questId);
        }, "claim the reward of " + questId) == 1, executor);
    }

    @Override
    public CompletableFuture<Void> unclaimReward(UUID player, String questId, long claimedAt) {
        return CompletableFuture.runAsync(() -> update("UPDATE suld_side_quests SET paid_at = 0"
                + " WHERE player_uuid = ? AND quest_id = ? AND state = 'DONE' AND paid_at = ?", ps -> {
            bindUuid(ps, 1, player);
            ps.setString(2, questId);
            ps.setLong(3, claimedAt);
        }, "unclaim the reward of " + questId), executor);
    }

    @Override
    public CompletableFuture<Boolean> abandon(UUID player, String questId) {
        return CompletableFuture.supplyAsync(() -> update("DELETE FROM suld_side_quests WHERE player_uuid = ? AND quest_id = ? AND state = 'ACTIVE'", ps -> {
            bindUuid(ps, 1, player);
            ps.setString(2, questId);
        }, "abandon " + questId) == 1, executor);
    }

    @Override
    public CompletableFuture<Boolean> reset(UUID player, String questId) {
        return CompletableFuture.supplyAsync(() -> update("DELETE FROM suld_side_quests WHERE player_uuid = ? AND quest_id = ?", ps -> {
            bindUuid(ps, 1, player);
            ps.setString(2, questId);
        }, "reset " + questId) == 1, executor);
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private int update(String sql, Binder binder, String what) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(what, e);
        }
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid);
        else ps.setString(index, uuid.toString());
    }
}
