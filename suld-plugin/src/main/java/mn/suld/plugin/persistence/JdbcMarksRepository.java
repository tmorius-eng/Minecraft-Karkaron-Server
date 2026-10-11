package mn.suld.plugin.persistence;

import mn.suld.api.persistence.MarksRepository;
import mn.suld.api.persistence.RepositoryException;
import mn.suld.api.progress.Mark;
import mn.suld.api.progress.MarkKind;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** JDBC marks storage over the V17 {@code suld_player_marks} table. Every write is an upsert. */
public final class JdbcMarksRepository implements MarksRepository {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcMarksRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    private String upsert() {
        return switch (dialect) {
            case POSTGRESQL -> "INSERT INTO suld_player_marks (player_uuid, kind, mark_key, mark_value, at) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (player_uuid, kind, mark_key) DO UPDATE SET mark_value = EXCLUDED.mark_value, at = EXCLUDED.at";
            case MYSQL -> "INSERT INTO suld_player_marks (player_uuid, kind, mark_key, mark_value, at) VALUES (?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE mark_value = VALUES(mark_value), at = VALUES(at)";
        };
    }

    @Override
    public CompletableFuture<Void> put(Mark mark) {
        return putAll(List.of(mark));
    }

    @Override
    public CompletableFuture<Void> putAll(List<Mark> marks) {
        if (marks.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(upsert())) {
                for (Mark m : marks) {
                    bindUuid(ps, 1, m.player());
                    ps.setString(2, m.kind().name());
                    ps.setString(3, m.key());
                    ps.setLong(4, m.value());
                    ps.setLong(5, m.at());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to write " + marks.size() + " marks", ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<MarkKind, Map<String, Long>>> load(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            Map<MarkKind, Map<String, Long>> out = new EnumMap<>(MarkKind.class);
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(
                    "SELECT kind, mark_key, mark_value FROM suld_player_marks WHERE player_uuid = ?")) {
                bindUuid(ps, 1, player);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MarkKind kind;
                        try {
                            kind = MarkKind.valueOf(rs.getString(1));
                        } catch (IllegalArgumentException unknown) {
                            continue;
                        }
                        out.computeIfAbsent(kind, k -> new HashMap<>()).put(rs.getString(2), rs.getLong(3));
                    }
                }
                return out;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to load marks of " + player, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> count(MarkKind kind, String key) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM suld_player_marks WHERE kind = ? AND mark_key = ?")) {
                ps.setString(1, kind.name());
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to count marks " + kind + "/" + key, ex);
            }
        }, executor);
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid); else ps.setString(index, uuid.toString());
    }
}
