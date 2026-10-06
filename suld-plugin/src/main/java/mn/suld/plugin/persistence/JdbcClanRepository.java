package mn.suld.plugin.persistence;

import mn.suld.api.clan.Clan;
import mn.suld.api.clan.ClanMember;
import mn.suld.api.clan.ClanRank;
import mn.suld.api.clan.ClanSnapshot;
import mn.suld.api.persistence.ClanRepository;
import mn.suld.api.persistence.RepositoryException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * JDBC clan storage. Pass a <b>single-threaded</b> executor: writes must land in submission
 * order so membership moves never collide on the one-clan-per-player primary key.
 */
public final class JdbcClanRepository implements ClanRepository {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;
    private final Logger logger;

    public JdbcClanRepository(DataSource dataSource, SqlDialect dialect, Executor executor, Logger logger) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<List<Clan>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                record Row(UUID id, String name, String tag, long createdAt, long exp, long version) {
                }
                Map<UUID, Row> rows = new LinkedHashMap<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT clan_id, name, tag, created_at, exp, version FROM suld_clans ORDER BY created_at");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = readUuid(rs, "clan_id");
                        rows.put(id, new Row(id, rs.getString("name"), rs.getString("tag"),
                                rs.getLong("created_at"), rs.getLong("exp"), rs.getLong("version")));
                    }
                }
                Map<UUID, List<ClanMember>> members = new HashMap<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT player_uuid, clan_id, last_name, clan_rank, contribution, joined_at "
                                + "FROM suld_clan_members ORDER BY joined_at");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        members.computeIfAbsent(readUuid(rs, "clan_id"), k -> new ArrayList<>()).add(new ClanMember(
                                readUuid(rs, "player_uuid"), rs.getString("last_name"),
                                ClanRank.byId(rs.getString("clan_rank")), rs.getLong("contribution"),
                                Instant.ofEpochMilli(rs.getLong("joined_at"))));
                    }
                }
                List<Clan> clans = new ArrayList<>(rows.size());
                for (Row r : rows.values()) {
                    try {
                        clans.add(Clan.restore(r.id(), r.name(), r.tag(), Instant.ofEpochMilli(r.createdAt()),
                                r.exp(), r.version(), members.getOrDefault(r.id(), List.of())));
                    } catch (IllegalStateException ex) {
                        // Never let one corrupt clan take the whole server down; make it loud instead.
                        logger.severe("Skipping corrupt clan " + r.id() + " [" + r.tag() + "]: " + ex.getMessage());
                    }
                }
                return clans;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to load clans", ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(ClanSnapshot clan) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                boolean auto = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement(dialect.clanUpsert())) {
                        bindUuid(ps, 1, clan.id());
                        ps.setString(2, clan.name());
                        ps.setString(3, clan.name().toLowerCase(Locale.ROOT));
                        ps.setString(4, clan.tag());
                        ps.setLong(5, clan.createdAt().toEpochMilli());
                        ps.setLong(6, clan.exp());
                        ps.setLong(7, clan.version());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM suld_clan_members WHERE clan_id = ?")) {
                        bindUuid(ps, 1, clan.id());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO suld_clan_members (player_uuid, clan_id, last_name, clan_rank, contribution, joined_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)")) {
                        for (ClanMember m : clan.members()) {
                            bindUuid(ps, 1, m.playerId());
                            bindUuid(ps, 2, clan.id());
                            ps.setString(3, m.name());
                            ps.setString(4, m.rank().name());
                            ps.setLong(5, m.contribution());
                            ps.setLong(6, m.joinedAt().toEpochMilli());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(auto);
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to save clan " + clan.tag(), ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> delete(UUID clanId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM suld_clans WHERE clan_id = ?")) {
                bindUuid(ps, 1, clanId);
                ps.executeUpdate(); // members go via ON DELETE CASCADE
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to delete clan " + clanId, ex);
            }
        }, executor);
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) {
            ps.setObject(index, uuid);
        } else {
            ps.setString(index, uuid.toString());
        }
    }

    private UUID readUuid(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        return raw instanceof UUID u ? u : UUID.fromString(raw.toString());
    }
}
