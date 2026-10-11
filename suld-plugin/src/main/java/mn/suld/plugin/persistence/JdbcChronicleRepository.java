package mn.suld.plugin.persistence;

import mn.suld.api.chronicle.ChronicleEntry;
import mn.suld.api.chronicle.ChronicleKind;
import mn.suld.api.chronicle.ChronicleRules;
import mn.suld.api.persistence.ChronicleRepository;
import mn.suld.api.persistence.RepositoryException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * JDBC storage of the Хроник over the V19 tables. A first is an INSERT into {@code suld_chronicle_records} that either
 * succeeds or hits the primary key; a record is an {@code UPDATE … WHERE version = ?} after comparing; the log row is
 * written in the same transaction, so a first or record can never exist without its line.
 */
public final class JdbcChronicleRepository implements ChronicleRepository {

    private static final String COLUMNS = "id, kind, subject_key, player_uuid, player_name, party, clan_tag, entry_value, detail, cycle_id, at";

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcChronicleRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Optional<ChronicleEntry>> claimFirst(ChronicleEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            String claim = switch (dialect) {
                case POSTGRESQL -> "INSERT INTO suld_chronicle_records (kind, subject_key, entry_id, entry_value, version) VALUES (?, ?, ?, ?, 0) ON CONFLICT DO NOTHING";
                case MYSQL -> "INSERT IGNORE INTO suld_chronicle_records (kind, subject_key, entry_id, entry_value, version) VALUES (?, ?, ?, ?, 0)";
            };
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // a cheap pre-check keeps the log clean of rolled-back ids; the INSERT is still the real guard
                    if (holder(conn, entry.kind(), entry.subjectKey()).isPresent()) {
                        conn.rollback();
                        return Optional.empty();
                    }
                    ChronicleEntry written = insertLog(conn, entry);
                    int claimed;
                    try (PreparedStatement ps = conn.prepareStatement(claim)) {
                        ps.setString(1, entry.kind().name());
                        ps.setString(2, entry.subjectKey());
                        ps.setLong(3, written.id());
                        setNullableLong(ps, 4, entry.value());
                        claimed = ps.executeUpdate();
                    }
                    if (claimed != 1) {
                        conn.rollback(); // lost the race: the log row goes with it
                        return Optional.empty();
                    }
                    conn.commit();
                    return Optional.of(written);
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to claim first " + entry.kind() + " " + entry.subjectKey(), ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<ChronicleEntry>> recordIfBetter(ChronicleEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                // a lost compare-and-set means another record landed meanwhile: re-read and compare again, so the best
                // of several concurrent attempts always ends up holding the record
                for (int attempt = 0; attempt < 8; attempt++) {
                    try {
                        Optional<long[]> cur = recordRow(conn, entry.kind(), entry.subjectKey()); // {entry_id, value, version, hasValue}
                        Long current = cur.isEmpty() || cur.get()[3] == 0 ? null : cur.get()[1];
                        if (cur.isPresent() && !ChronicleRules.better(entry.kind(), current, entry.value())) {
                            conn.rollback();
                            return Optional.empty();
                        }
                        ChronicleEntry written = insertLog(conn, entry);
                        int changed;
                        if (cur.isEmpty()) {
                            String insert = switch (dialect) {
                                case POSTGRESQL -> "INSERT INTO suld_chronicle_records (kind, subject_key, entry_id, entry_value, version) VALUES (?, ?, ?, ?, 0) ON CONFLICT DO NOTHING";
                                case MYSQL -> "INSERT IGNORE INTO suld_chronicle_records (kind, subject_key, entry_id, entry_value, version) VALUES (?, ?, ?, ?, 0)";
                            };
                            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                                ps.setString(1, entry.kind().name());
                                ps.setString(2, entry.subjectKey());
                                ps.setLong(3, written.id());
                                setNullableLong(ps, 4, entry.value());
                                changed = ps.executeUpdate();
                            }
                        } else {
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "UPDATE suld_chronicle_records SET entry_id = ?, entry_value = ?, version = version + 1 WHERE kind = ? AND subject_key = ? AND version = ?")) {
                                ps.setLong(1, written.id());
                                setNullableLong(ps, 2, entry.value());
                                ps.setString(3, entry.kind().name());
                                ps.setString(4, entry.subjectKey());
                                ps.setLong(5, cur.get()[2]);
                                changed = ps.executeUpdate();
                            }
                        }
                        if (changed == 1) {
                            conn.commit();
                            return Optional.of(written);
                        }
                        conn.rollback(); // lost the race: the log row goes with it; compare again against the new holder
                    } catch (SQLException ex) {
                        conn.rollback();
                        if (ex.getSQLState() != null && ex.getSQLState().startsWith("40")) continue; // serialization failure: retry
                        throw ex;
                    }
                }
                return Optional.empty();
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to record " + entry.kind() + " " + entry.subjectKey(), ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<ChronicleEntry> append(ChronicleEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    ChronicleEntry written = insertLog(conn, entry);
                    conn.commit();
                    return written;
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to append chronicle " + entry.kind(), ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<ChronicleEntry>> latest(int limit) {
        return query("SELECT " + COLUMNS + " FROM suld_chronicle WHERE hidden = 0 ORDER BY id DESC LIMIT ?", ps -> ps.setInt(1, clamp(limit)));
    }

    @Override
    public CompletableFuture<List<ChronicleEntry>> byKind(ChronicleKind kind, int limit) {
        return query("SELECT " + COLUMNS + " FROM suld_chronicle WHERE hidden = 0 AND kind = ? ORDER BY id DESC LIMIT ?", ps -> {
            ps.setString(1, kind.name());
            ps.setInt(2, clamp(limit));
        });
    }

    @Override
    public CompletableFuture<List<ChronicleEntry>> byPlayer(UUID player, int limit) {
        return query("SELECT " + COLUMNS + " FROM suld_chronicle WHERE hidden = 0 AND player_uuid = ? ORDER BY id DESC LIMIT ?", ps -> {
            bindUuid(ps, 1, player);
            ps.setInt(2, clamp(limit));
        });
    }

    @Override
    public CompletableFuture<Optional<ChronicleEntry>> holder(ChronicleKind kind, String subjectKey) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                return holder(conn, kind, subjectKey);
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read holder " + kind + " " + subjectKey, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> hide(long id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE suld_chronicle SET hidden = 1 WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() == 1;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to hide chronicle " + id, ex);
            }
        }, executor);
    }

    // ---------------------------------------------------------------- helpers

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private CompletableFuture<List<ChronicleEntry>> query(String sql, Binder binder) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                binder.bind(ps);
                List<ChronicleEntry> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(read(rs));
                }
                return out;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read the chronicle", ex);
            }
        }, executor);
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(200, limit));
    }

    private Optional<ChronicleEntry> holder(Connection conn, ChronicleKind kind, String subjectKey) throws SQLException {
        String qualified = "c." + COLUMNS.replace(", ", ", c.");
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + qualified + " FROM suld_chronicle c JOIN suld_chronicle_records r ON r.entry_id = c.id "
                + "WHERE r.kind = ? AND r.subject_key = ?")) {
            ps.setString(1, kind.name());
            ps.setString(2, subjectKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    /** {entry_id, value, version, hasValue (1/0)} of the current record row. */
    private Optional<long[]> recordRow(Connection conn, ChronicleKind kind, String subjectKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT entry_id, entry_value, version FROM suld_chronicle_records WHERE kind = ? AND subject_key = ?")) {
            ps.setString(1, kind.name());
            ps.setString(2, subjectKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                long value = rs.getLong(2);
                boolean has = !rs.wasNull();
                return Optional.of(new long[]{rs.getLong(1), value, rs.getLong(3), has ? 1 : 0});
            }
        }
    }

    private ChronicleEntry insertLog(Connection conn, ChronicleEntry e) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO suld_chronicle (kind, subject_key, player_uuid, player_name, party, clan_tag, "
                + "entry_value, detail, cycle_id, at, hidden) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.kind().name());
            ps.setString(2, e.subjectKey());
            bindNullableUuid(ps, 3, e.player());
            ps.setString(4, truncate(e.playerName(), 16));
            ps.setString(5, String.join(",", e.party()));
            ps.setString(6, truncate(e.clanTag(), 8));
            setNullableLong(ps, 7, e.value());
            ps.setString(8, truncate(e.detail(), 255));
            ps.setString(9, truncate(e.cycleId(), 16));
            ps.setLong(10, e.at());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("no generated id for the chronicle row");
                return e.withId(keys.getLong(1));
            }
        }
    }

    private static ChronicleEntry read(ResultSet rs) throws SQLException {
        long value = rs.getLong("entry_value");
        Long v = rs.wasNull() ? null : value;
        String party = rs.getString("party");
        return new ChronicleEntry(rs.getLong("id"), ChronicleKind.valueOf(rs.getString("kind")), rs.getString("subject_key"),
                JdbcCampaignRepository.readUuid(rs, "player_uuid"), rs.getString("player_name"),
                party == null || party.isBlank() ? List.of() : List.of(party.split(",")), rs.getString("clan_tag"), v,
                rs.getString("detail"), rs.getString("cycle_id"), rs.getLong("at"));
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long v) throws SQLException {
        if (v == null) ps.setNull(index, Types.BIGINT); else ps.setLong(index, v);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid); else ps.setString(index, uuid.toString());
    }

    private void bindNullableUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (uuid == null) ps.setNull(index, dialect == SqlDialect.POSTGRESQL ? Types.OTHER : Types.CHAR);
        else bindUuid(ps, index, uuid);
    }
}
