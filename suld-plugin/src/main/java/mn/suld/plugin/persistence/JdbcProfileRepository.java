package mn.suld.plugin.persistence;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.persistence.ProfileRepository;
import mn.suld.api.persistence.RepositoryException;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.quest.QuestState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.sql.DataSource;

/**
 * JDBC-backed {@link ProfileRepository}. Every method runs its SQL on the
 * supplied {@link Executor} (never the server main thread) and completes the
 * returned future off-thread; callers hop back to the main thread before
 * touching Bukkit state.
 */
public final class JdbcProfileRepository implements ProfileRepository {

    private static final String SELECT =
            "SELECT name, class_id, level, exp_into_level, created_at, last_seen_at, version, "
                    + "currency, active_quest_id, quest_progress, quest_completed, skill_data, equipment_data, class_gear, active_minutes "
                    + "FROM suld_profiles WHERE player_uuid = ?";

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcProfileRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> find(UUID playerId) {
        return CompletableFuture.supplyAsync(mn.suld.plugin.perf.PerfProbe.timed("db.profile.find", () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT)) {
                bindUuid(ps, 1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<PlayerProfile>empty();
                    }
                    String classId = rs.getString("class_id");
                    // an unknown id must not load as "no class": the next save would erase the player's class
                    PlayerClass clazz = classId == null || classId.isEmpty() ? null : PlayerClass.byId(classId)
                            .orElseThrow(() -> new RepositoryException("Unknown class id '" + classId + "' for profile " + playerId));
                    Progression progression = new Progression(rs.getInt("level"), rs.getLong("exp_into_level"));
                    String questId = rs.getString("active_quest_id");
                    QuestState questState = (questId == null || questId.isEmpty())
                            ? QuestState.NONE
                            : new QuestState(questId, rs.getInt("quest_progress"), rs.getBoolean("quest_completed"));
                    PlayerProfile profile = PlayerProfile.restore(
                            playerId,
                            rs.getString("name"),
                            clazz,
                            progression,
                            Instant.ofEpochMilli(rs.getLong("created_at")),
                            Instant.ofEpochMilli(rs.getLong("last_seen_at")),
                            rs.getLong("version"),
                            rs.getLong("currency"),
                            questState,
                            readSkills(playerId, rs.getString("skill_data")),
                            readEquipment(playerId, rs.getString("equipment_data")),
                            readClassGear(playerId, rs.getString("class_gear")),
                            readActive(playerId, rs.getString("active_minutes")));
                    return Optional.of(profile);
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to load profile " + playerId, ex);
            }
        }), executor);
    }

    /** Unreadable skill data must stop the load: saving the profile afterwards would erase the player's build. */
    private static mn.suld.api.skill.tree.SkillState readSkills(UUID playerId, String json) {
        try {
            return mn.suld.api.skill.tree.SkillState.fromJson(json);
        } catch (IllegalArgumentException ex) {
            throw new RepositoryException("Skill data of profile " + playerId + " cannot be read: " + ex.getMessage(), ex);
        }
    }

    /** Unreadable equipment must stop the load too: saving afterwards would erase the accessories. */
    private static mn.suld.api.item.EquipmentState readEquipment(UUID playerId, String json) {
        try {
            return mn.suld.api.item.EquipmentState.fromJson(json);
        } catch (IllegalArgumentException ex) {
            throw new RepositoryException("Equipment data of profile " + playerId + " cannot be read: " + ex.getMessage(), ex);
        }
    }

    /** Unreadable class gear must stop the load: saving afterwards would erase the armour progress. */
    private static mn.suld.api.classgear.ClassGear readClassGear(UUID playerId, String json) {
        try {
            return mn.suld.api.classgear.ClassGear.fromJson(json);
        } catch (IllegalArgumentException ex) {
            throw new RepositoryException("Class gear of profile " + playerId + " cannot be read: " + ex.getMessage(), ex);
        }
    }

    private static mn.suld.api.activity.ActiveMinutes readActive(UUID playerId, String json) {
        try {
            return mn.suld.api.activity.ActiveMinutes.fromJson(json);
        } catch (IllegalArgumentException ex) {
            throw new RepositoryException("Active minutes of profile " + playerId + " cannot be read: " + ex.getMessage(), ex);
        }
    }

    @Override
    public CompletableFuture<java.util.List<mn.suld.api.leaderboard.Leaderboard.Entry>> top(
            mn.suld.api.leaderboard.Leaderboard board, int limit) {
        String order = switch (board) {
            case LEVEL -> "level DESC, exp_into_level DESC";
            case COINS -> "currency DESC";
        };
        String sql = "SELECT player_uuid, name, level, exp_into_level, currency FROM suld_profiles "
                + "WHERE class_id IS NOT NULL ORDER BY " + order + " LIMIT ?";
        return CompletableFuture.supplyAsync(() -> {
            java.util.List<mn.suld.api.leaderboard.Leaderboard.Entry> rows = new java.util.ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, Math.min(100, limit)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new mn.suld.api.leaderboard.Leaderboard.Entry(readUuid(rs, 1), rs.getString(2), rs.getInt(3),
                                rs.getLong(4), rs.getLong(5)));
                    }
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read leaderboard " + board, ex);
            }
            return rows;
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> exists(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT 1 FROM suld_profiles WHERE player_uuid = ?")) {
                bindUuid(ps, 1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed existence check for " + playerId, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<PlayerProfile> save(PlayerProfile profile) {
        return CompletableFuture.supplyAsync(mn.suld.plugin.perf.PerfProbe.timed("db.profile.save", () -> {
            // One consistent snapshot (all fields and the version under the profile's lock): the row and the
            // in-memory "persisted" marker agree, and a change made meanwhile stays dirty for the next save.
            PlayerProfile.Snapshot snap = profile.snapshot();
            long version = snap.version();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(dialect.profileUpsert())) {
                bindUuid(ps, 1, snap.playerId());
                ps.setString(2, snap.name());
                ps.setString(3, snap.playerClass() == null ? null : snap.playerClass().id());
                ps.setInt(4, snap.progression().level());
                ps.setLong(5, snap.progression().expIntoLevel());
                ps.setLong(6, snap.createdAt().toEpochMilli());
                ps.setLong(7, snap.lastSeenAt().toEpochMilli());
                ps.setLong(8, version);
                ps.setLong(9, snap.currency());
                QuestState q = snap.questState();
                ps.setString(10, q.questId().isEmpty() ? null : q.questId());
                ps.setInt(11, q.progress());
                ps.setBoolean(12, q.completed());
                ps.setString(13, snap.skillState().toJson());
                ps.setString(14, snap.equipment().isEmpty() ? null : snap.equipment().toJson());
                ps.setString(15, snap.classGear().isEmpty() ? null : snap.classGear().toJson());
                ps.setString(16, snap.activeMinutes().isEmpty() ? null : snap.activeMinutes().toJson());
                ps.executeUpdate();
                profile.markPersisted(version);
                return profile;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to save profile " + profile.playerId(), ex);
            }
        }), executor);
    }

    @Override
    public CompletableFuture<Void> delete(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM suld_profiles WHERE player_uuid = ?")) {
                bindUuid(ps, 1, playerId);
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to delete profile " + playerId, ex);
            }
        }, executor);
    }

    private static UUID readUuid(ResultSet rs, int column) throws SQLException {
        Object v = rs.getObject(column);
        return v instanceof UUID u ? u : UUID.fromString(String.valueOf(v));
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) {
            // Native uuid column: bind the UUID object so the driver maps it.
            ps.setObject(index, uuid);
        } else {
            ps.setString(index, uuid.toString());
        }
    }
}
