package mn.suld.plugin.persistence;

import mn.suld.api.mastery.WeaponMastery;
import mn.suld.api.persistence.RepositoryException;
import mn.suld.api.persistence.WeaponMasteryRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Weapon mastery over the V25 table (docs/MASTERY_AND_PROFESSIONS.md). {@link #add} is an additive upsert per track
 * ({@code points = points + ?}), all tracks of one batch in one transaction: batches commute, so the shared io pool may
 * run them in any order.
 */
public final class JdbcWeaponMasteryRepository implements WeaponMasteryRepository {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcWeaponMasteryRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Map<WeaponMastery.Track, Double>> totals(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT track, points FROM suld_weapon_mastery WHERE player_uuid = ?")) {
                bindUuid(ps, 1, player);
                Map<WeaponMastery.Track, Double> out = new EnumMap<>(WeaponMastery.Track.class);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            out.put(WeaponMastery.Track.valueOf(rs.getString(1)), rs.getDouble(2));
                        } catch (IllegalArgumentException unknown) {
                            // a track this build does not know (a later build's): kept in the table, not read
                        }
                    }
                }
                return out;
            } catch (SQLException e) {
                throw new RepositoryException("weapon mastery of " + player, e);
            }
        }, executor);
    }

    private String addSql() {
        return switch (dialect) {
            case POSTGRESQL -> "INSERT INTO suld_weapon_mastery (player_uuid, track, points, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (player_uuid, track) DO UPDATE SET points = suld_weapon_mastery.points + EXCLUDED.points, "
                    + "updated_at = EXCLUDED.updated_at";
            case MYSQL -> "INSERT INTO suld_weapon_mastery (player_uuid, track, points, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE points = points + VALUES(points), updated_at = VALUES(updated_at)";
        };
    }

    @Override
    public CompletableFuture<Void> add(UUID player, Map<WeaponMastery.Track, Double> points, long now) {
        Map<WeaponMastery.Track, Double> batch = new EnumMap<>(WeaponMastery.Track.class);
        points.forEach((t, p) -> {
            if (p != null && p > 0) batch.put(t, p);
        });
        if (batch.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            for (int attempt = 1; ; attempt++) {
                try (Connection c = dataSource.getConnection()) {
                    boolean auto = c.getAutoCommit();
                    c.setAutoCommit(false);
                    try (PreparedStatement ps = c.prepareStatement(addSql())) {
                        for (Map.Entry<WeaponMastery.Track, Double> en : batch.entrySet()) {
                            bindUuid(ps, 1, player);
                            ps.setString(2, en.getKey().name());
                            ps.setDouble(3, en.getValue());
                            ps.setLong(4, now);
                            ps.addBatch();
                        }
                        ps.executeBatch();
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
                    // rolled back, so adding it again counts it once
                    String state = e.getSQLState();
                    boolean retry = state != null && (state.startsWith("23") || state.startsWith("40"));
                    if (!retry || attempt >= 5) throw new RepositoryException("add weapon mastery of " + player, e);
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> set(UUID player, WeaponMastery.Track track, double total, long now) {
        return CompletableFuture.runAsync(() -> {
            String sql = switch (dialect) {
                case POSTGRESQL -> "INSERT INTO suld_weapon_mastery (player_uuid, track, points, updated_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (player_uuid, track) DO UPDATE SET points = EXCLUDED.points, updated_at = EXCLUDED.updated_at";
                case MYSQL -> "INSERT INTO suld_weapon_mastery (player_uuid, track, points, updated_at) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE points = VALUES(points), updated_at = VALUES(updated_at)";
            };
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                bindUuid(ps, 1, player);
                ps.setString(2, track.name());
                ps.setDouble(3, Math.max(0, total));
                ps.setLong(4, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("set weapon mastery of " + player, e);
            }
        }, executor);
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid);
        else ps.setString(index, uuid.toString());
    }
}
