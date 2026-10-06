package mn.suld.plugin.persistence;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.persistence.ProfileRepository;
import mn.suld.api.persistence.RepositoryException;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;

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
            "SELECT name, class_id, level, exp_into_level, created_at, last_seen_at, version "
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
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT)) {
                bindUuid(ps, 1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<PlayerProfile>empty();
                    }
                    PlayerClass clazz = PlayerClass.byId(rs.getString("class_id")).orElse(null);
                    Progression progression = new Progression(rs.getInt("level"), rs.getLong("exp_into_level"));
                    PlayerProfile profile = PlayerProfile.restore(
                            playerId,
                            rs.getString("name"),
                            clazz,
                            progression,
                            Instant.ofEpochMilli(rs.getLong("created_at")),
                            Instant.ofEpochMilli(rs.getLong("last_seen_at")),
                            rs.getLong("version"));
                    return Optional.of(profile);
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to load profile " + playerId, ex);
            }
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
        return CompletableFuture.supplyAsync(() -> {
            // Snapshot the version under the profile's lock so the row and the
            // in-memory "persisted" marker agree.
            long version = profile.version();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(dialect.profileUpsert())) {
                bindUuid(ps, 1, profile.playerId());
                ps.setString(2, profile.name());
                ps.setString(3, profile.playerClass().map(PlayerClass::id).orElse(null));
                ps.setInt(4, profile.progression().level());
                ps.setLong(5, profile.progression().expIntoLevel());
                ps.setLong(6, profile.createdAt().toEpochMilli());
                ps.setLong(7, profile.lastSeenAt().toEpochMilli());
                ps.setLong(8, version);
                ps.executeUpdate();
                profile.markPersisted(version);
                return profile;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to save profile " + profile.playerId(), ex);
            }
        }, executor);
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

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) {
            // Native uuid column: bind the UUID object so the driver maps it.
            ps.setObject(index, uuid);
        } else {
            ps.setString(index, uuid.toString());
        }
    }
}
