package mn.suld.plugin.persistence;

import mn.suld.api.persistence.RelicRepository;
import mn.suld.api.persistence.RepositoryException;
import mn.suld.api.relic.CasResult;
import mn.suld.api.relic.RelicEvent;
import mn.suld.api.relic.RelicHistoryEntry;
import mn.suld.api.relic.RelicRecord;
import mn.suld.api.relic.RelicState;
import mn.suld.api.relic.RelicTransition;
import mn.suld.api.relic.ShrineLocation;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * JDBC relic storage over the V1/V4 {@code suld_world_unique_*} tables.
 *
 * <p>{@link #apply} is a single {@code UPDATE … WHERE item_key = ? AND version = ?} inside a
 * transaction together with its history row: if anyone changed the relic since the caller
 * read it, zero rows match and nothing is written. Combined with the schema's CHECK and
 * UNIQUE constraints this makes a duplicate bearer impossible even across crashes, restarts
 * or two servers sharing one database.
 */
public final class JdbcRelicRepository implements RelicRepository {

    private static final String COLUMNS = "item_key, item_uuid, state, owner_uuid, owner_name, acquired_at, version, "
            + "shrine_world, shrine_x, shrine_y, shrine_z";

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcRelicRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<RelicRecord> ensure(String key, UUID freshItemUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String insert = switch (dialect) {
                case POSTGRESQL -> "INSERT INTO suld_world_unique_items (item_key, item_uuid, state, version) "
                        + "VALUES (?, ?, 'UNCLAIMED', 0) ON CONFLICT (item_key) DO NOTHING";
                case MYSQL -> "INSERT INTO suld_world_unique_items (item_key, item_uuid, state, version) "
                        + "VALUES (?, ?, 'UNCLAIMED', 0) ON DUPLICATE KEY UPDATE item_key = item_key";
            };
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    int inserted;
                    try (PreparedStatement ps = conn.prepareStatement(insert)) {
                        ps.setString(1, key);
                        bindUuid(ps, 2, freshItemUuid);
                        inserted = ps.executeUpdate();
                    }
                    if (inserted == 1) {
                        history(conn, key, RelicEvent.CREATED, null, "server", "");
                    }
                    RelicRecord record = select(conn, key).orElseThrow(() -> new SQLException("relic row missing: " + key));
                    conn.commit();
                    return record;
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to ensure relic " + key, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<RelicRecord>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT " + COLUMNS + " FROM suld_world_unique_items");
                 ResultSet rs = ps.executeQuery()) {
                List<RelicRecord> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(read(rs));
                }
                return out;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to load relics", ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<CasResult> apply(RelicTransition t) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    RelicRecord cur = select(conn, t.key()).orElseThrow(() -> new SQLException("unknown relic " + t.key()));
                    if (cur.version() != t.expectedVersion()) {
                        conn.rollback();
                        return new CasResult(false, cur);
                    }
                    boolean ownerChanged = t.newOwner() == null ? cur.owner() != null : !t.newOwner().equals(cur.owner());
                    Long acquiredAt = t.newState() == RelicState.OWNED
                            ? (ownerChanged ? Long.valueOf(Instant.now().toEpochMilli())
                            : (cur.acquiredAt() == null ? null : cur.acquiredAt().toEpochMilli()))
                            : null;
                    int updated;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE suld_world_unique_items SET state = ?, owner_uuid = ?, owner_name = ?, acquired_at = ?, "
                                    + "version = version + 1 WHERE item_key = ? AND version = ?")) {
                        ps.setString(1, t.newState().name());
                        bindNullableUuid(ps, 2, t.newOwner());
                        ps.setString(3, t.newOwnerName());
                        if (acquiredAt == null) ps.setNull(4, Types.BIGINT); else ps.setLong(4, acquiredAt);
                        ps.setString(5, t.key());
                        ps.setLong(6, t.expectedVersion());
                        updated = ps.executeUpdate();
                    }
                    if (updated != 1) {
                        conn.rollback(); // lost the race between our read and our write
                        return new CasResult(false, select(conn, t.key()).orElse(cur));
                    }
                    history(conn, t.key(), t.event(), t.newOwner(), t.actor(), t.detail());
                    RelicRecord next = select(conn, t.key()).orElseThrow();
                    conn.commit();
                    return new CasResult(true, next);
                } catch (SQLException ex) {
                    conn.rollback(); // e.g. UNIQUE(owner_uuid): that player already bears a relic
                    throw ex;
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Relic transition rejected for " + t.key() + " (" + ex.getSQLState() + ")", ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<RelicRecord> setShrine(String key, ShrineLocation s, String actor) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE suld_world_unique_items SET shrine_world = ?, "
                            + "shrine_x = ?, shrine_y = ?, shrine_z = ? WHERE item_key = ?")) {
                        ps.setString(1, s.world());
                        ps.setInt(2, s.x());
                        ps.setInt(3, s.y());
                        ps.setInt(4, s.z());
                        ps.setString(5, key);
                        if (ps.executeUpdate() != 1) throw new SQLException("unknown relic " + key);
                    }
                    history(conn, key, RelicEvent.SHRINE_SET, null, actor, s.world() + " " + s.x() + " " + s.y() + " " + s.z());
                    RelicRecord r = select(conn, key).orElseThrow();
                    conn.commit();
                    return r;
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to set shrine for " + key, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<RelicHistoryEntry>> history(String key, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT at, event, owner_uuid, actor, detail "
                         + "FROM suld_world_unique_history WHERE item_key = ? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, key);
                ps.setInt(2, Math.max(1, Math.min(100, limit)));
                List<RelicHistoryEntry> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        RelicEvent event;
                        try {
                            event = RelicEvent.valueOf(rs.getString("event"));
                        } catch (IllegalArgumentException unknown) {
                            continue; // pre-V4 or foreign event types: skip, don't fail
                        }
                        out.add(new RelicHistoryEntry(Instant.ofEpochMilli(rs.getLong("at")), event,
                                readUuid(rs, "owner_uuid"), rs.getString("actor"), rs.getString("detail")));
                    }
                }
                return out;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read relic history " + key, ex);
            }
        }, executor);
    }

    // ---------------------------------------------------------------- helpers

    private Optional<RelicRecord> select(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + COLUMNS + " FROM suld_world_unique_items WHERE item_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    private RelicRecord read(ResultSet rs) throws SQLException {
        String world = rs.getString("shrine_world");
        ShrineLocation shrine = world == null ? null
                : new ShrineLocation(world, rs.getInt("shrine_x"), rs.getInt("shrine_y"), rs.getInt("shrine_z"));
        long acquired = rs.getLong("acquired_at");
        Instant acquiredAt = rs.wasNull() ? null : Instant.ofEpochMilli(acquired);
        return new RelicRecord(rs.getString("item_key"), readUuid(rs, "item_uuid"), RelicState.valueOf(rs.getString("state")),
                readUuid(rs, "owner_uuid"), rs.getString("owner_name"), acquiredAt, rs.getLong("version"), shrine);
    }

    private void history(Connection conn, String key, RelicEvent event, UUID owner, String actor, String detail)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO suld_world_unique_history "
                + "(item_key, owner_uuid, event, at, actor, detail) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, key);
            bindNullableUuid(ps, 2, owner);
            ps.setString(3, event.name());
            ps.setLong(4, Instant.now().toEpochMilli());
            ps.setString(5, truncate(actor, 64));
            ps.setString(6, truncate(detail, 255));
            ps.executeUpdate();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String clean = s.replaceAll("\\p{Cntrl}", "?");
        return clean.length() > max ? clean.substring(0, max) : clean;
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid); else ps.setString(index, uuid.toString());
    }

    private void bindNullableUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (uuid == null) {
            ps.setNull(index, dialect == SqlDialect.POSTGRESQL ? Types.OTHER : Types.CHAR);
        } else {
            bindUuid(ps, index, uuid);
        }
    }

    private static UUID readUuid(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        if (raw == null) return null;
        return raw instanceof UUID u ? u : UUID.fromString(raw.toString());
    }
}
