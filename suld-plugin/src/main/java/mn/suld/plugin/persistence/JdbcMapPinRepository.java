package mn.suld.plugin.persistence;

import mn.suld.api.map.MapPin;
import mn.suld.api.persistence.MapPinRepository;
import mn.suld.api.persistence.RepositoryException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Map pins over the V26 table (docs/WORLD_MAP.md). The limit is kept by the insert itself ({@code INSERT … SELECT …
 * WHERE (SELECT COUNT(*) …) < max}), so two adds racing on the shared io pool cannot both pass a count read before.
 */
public final class JdbcMapPinRepository implements MapPinRepository {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcMapPinRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<List<MapPin>> list(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT pin_id, name, x, y, z, colour, created_at FROM suld_map_pins "
                         + "WHERE player_uuid = ? ORDER BY created_at, pin_id")) {
                bindUuid(ps, 1, owner);
                List<MapPin> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            out.add(new MapPin(uuid(rs, 1), owner, rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6), rs.getLong(7)));
                        } catch (IllegalArgumentException bad) {
                            // a row this build cannot show (a colour of a later build): kept, not read
                        }
                    }
                }
                return out;
            } catch (SQLException e) {
                throw new RepositoryException("map pins of " + owner, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> add(MapPin pin, int max) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO suld_map_pins (pin_id, player_uuid, name, x, y, z, colour, created_at) "
                    + "SELECT ?, ?, ?, ?, ?, ?, ?, ? " + (dialect == SqlDialect.MYSQL ? "FROM DUAL " : "")
                    + "WHERE (SELECT COUNT(*) FROM suld_map_pins WHERE player_uuid = ?) < ?";
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                bindUuid(ps, 1, pin.id());
                bindUuid(ps, 2, pin.owner());
                ps.setString(3, pin.name());
                ps.setInt(4, pin.x());
                ps.setInt(5, pin.y());
                ps.setInt(6, pin.z());
                ps.setString(7, pin.colour());
                ps.setLong(8, pin.createdAt());
                bindUuid(ps, 9, pin.owner());
                ps.setInt(10, max);
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                throw new RepositoryException("add map pin of " + pin.owner(), e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> remove(UUID owner, UUID pin) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM suld_map_pins WHERE pin_id = ? AND player_uuid = ?")) {
                bindUuid(ps, 1, pin);
                bindUuid(ps, 2, owner);
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                throw new RepositoryException("remove map pin of " + owner, e);
            }
        }, executor);
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid);
        else ps.setString(index, uuid.toString());
    }

    private UUID uuid(ResultSet rs, int col) throws SQLException {
        return dialect == SqlDialect.POSTGRESQL ? rs.getObject(col, UUID.class) : UUID.fromString(rs.getString(col));
    }
}
