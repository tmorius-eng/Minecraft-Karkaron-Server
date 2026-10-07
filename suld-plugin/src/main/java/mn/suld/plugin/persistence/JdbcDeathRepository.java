package mn.suld.plugin.persistence;

import mn.suld.api.death.DeathRecord;
import mn.suld.api.death.Wound;
import mn.suld.api.persistence.DeathRepository;
import mn.suld.api.persistence.RepositoryException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * JDBC storage of deaths and wounds (V12). The sequence number is assigned inside the insert's transaction; the
 * one-open-lock-per-player rule is a database constraint (PostgreSQL partial index, MySQL generated column), so a
 * second LOCKED insert fails no matter what the service believes. State changes are version compare-and-set.
 */
public final class JdbcDeathRepository implements DeathRepository {

    private static final String COLUMNS = "death_id, player_uuid, death_seq, died_at, locked_until, world, x, y, z, cause, "
            + "level, ascension, wound_after, revive_state, recovered_at, version";

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcDeathRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<DeathRecord> insert(DeathRecord d) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    int seq;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COALESCE(MAX(death_seq), 0) FROM suld_death_state WHERE player_uuid = ?")) {
                        bindUuid(ps, 1, d.player());
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            seq = rs.getInt(1) + 1;
                        }
                    }
                    DeathRecord stored = new DeathRecord(d.deathId(), d.player(), seq, d.diedAt(), d.lockedUntil(), d.world(), d.x(),
                            d.y(), d.z(), d.cause(), d.level(), d.ascension(), d.woundAfter(), d.state(), d.recoveredAt(), 1);
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO suld_death_state (" + COLUMNS
                            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        bind(ps, stored);
                        ps.executeUpdate();
                    }
                    conn.commit();
                    return stored;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RepositoryException("insert death " + d.player(), e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> update(DeathRecord r) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE suld_death_state SET revive_state = ?, recovered_at = ?, "
                         + "wound_after = ?, locked_until = ?, version = ? WHERE death_id = ? AND version = ?")) {
                ps.setString(1, r.state().name());
                if (r.recoveredAt() == null) ps.setNull(2, Types.BIGINT);
                else ps.setLong(2, r.recoveredAt());
                ps.setInt(3, r.woundAfter());
                ps.setLong(4, r.lockedUntil());
                ps.setInt(5, r.version());
                bindUuid(ps, 6, r.deathId());
                ps.setInt(7, r.version() - 1);
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                throw new RepositoryException("update death " + r.deathId(), e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<DeathRecord>> openLock(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            List<DeathRecord> rows = query("SELECT " + COLUMNS + " FROM suld_death_state WHERE player_uuid = ? AND revive_state = 'LOCKED'",
                    player, 1);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }, executor);
    }

    @Override
    public CompletableFuture<List<DeathRecord>> recent(UUID player, int limit) {
        return CompletableFuture.supplyAsync(() -> query("SELECT " + COLUMNS + " FROM suld_death_state WHERE player_uuid = ? "
                + "ORDER BY death_seq DESC", player, Math.max(1, limit)), executor);
    }

    @Override
    public CompletableFuture<Wound> wound(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT wound_stacks, heal_minutes FROM suld_death_recovery WHERE player_uuid = ?")) {
                bindUuid(ps, 1, player);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new Wound(rs.getInt(1), rs.getDouble(2)) : Wound.NONE;
                }
            } catch (SQLException e) {
                throw new RepositoryException("load wound " + player, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveWound(UUID player, Wound wound) {
        return CompletableFuture.runAsync(() -> {
            String sql = switch (dialect) {
                case POSTGRESQL -> "INSERT INTO suld_death_recovery (player_uuid, wound_stacks, heal_minutes, updated_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (player_uuid) DO UPDATE SET wound_stacks = EXCLUDED.wound_stacks, heal_minutes = EXCLUDED.heal_minutes, "
                        + "updated_at = EXCLUDED.updated_at";
                case MYSQL -> "INSERT INTO suld_death_recovery (player_uuid, wound_stacks, heal_minutes, updated_at) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE wound_stacks = VALUES(wound_stacks), heal_minutes = VALUES(heal_minutes), "
                        + "updated_at = VALUES(updated_at)";
            };
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                bindUuid(ps, 1, player);
                ps.setInt(2, wound.stacks());
                ps.setDouble(3, wound.healMinutes());
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("save wound " + player, e);
            }
        }, executor);
    }

    private List<DeathRecord> query(String sql, UUID player, int limit) {
        List<DeathRecord> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindUuid(ps, 1, player);
            ps.setMaxRows(limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && out.size() < limit) out.add(read(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("query deaths " + player, e);
        }
        return out;
    }

    private DeathRecord read(ResultSet rs) throws SQLException {
        long rec = rs.getLong(15);
        Long recovered = rs.wasNull() ? null : rec;
        return new DeathRecord(uuid(rs, 1), uuid(rs, 2), rs.getInt(3), rs.getLong(4), rs.getLong(5), rs.getString(6), rs.getInt(7),
                rs.getInt(8), rs.getInt(9), rs.getString(10), rs.getInt(11), rs.getInt(12), rs.getInt(13),
                DeathRecord.State.valueOf(rs.getString(14)), recovered, rs.getInt(16));
    }

    private void bind(PreparedStatement ps, DeathRecord d) throws SQLException {
        bindUuid(ps, 1, d.deathId());
        bindUuid(ps, 2, d.player());
        ps.setInt(3, d.seq());
        ps.setLong(4, d.diedAt());
        ps.setLong(5, d.lockedUntil());
        ps.setString(6, d.world());
        ps.setInt(7, d.x());
        ps.setInt(8, d.y());
        ps.setInt(9, d.z());
        ps.setString(10, d.cause());
        ps.setInt(11, d.level());
        ps.setInt(12, d.ascension());
        ps.setInt(13, d.woundAfter());
        ps.setString(14, d.state().name());
        if (d.recoveredAt() == null) ps.setNull(15, Types.BIGINT);
        else ps.setLong(15, d.recoveredAt());
        ps.setInt(16, d.version());
    }

    private UUID uuid(ResultSet rs, int col) throws SQLException {
        Object o = rs.getObject(col);
        return o instanceof UUID u ? u : UUID.fromString(String.valueOf(o));
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid);
        else ps.setString(index, uuid.toString());
    }
}
