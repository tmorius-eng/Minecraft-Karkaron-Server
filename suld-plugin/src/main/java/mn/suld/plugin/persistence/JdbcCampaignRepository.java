package mn.suld.plugin.persistence;

import mn.suld.api.campaign.CampaignSnapshot;
import mn.suld.api.campaign.CampaignState;
import mn.suld.api.campaign.Contribution;
import mn.suld.api.campaign.WorldFlag;
import mn.suld.api.campaign.vote.VoteCycle;
import mn.suld.api.persistence.CampaignRepository;
import mn.suld.api.persistence.RepositoryException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * JDBC storage of Их Хуралдай over the V18 tables. A ballot is one INSERT guarded by the primary key; closing a vote
 * and changing a campaign's state are {@code UPDATE … WHERE version = ?}; contributions are additive updates plus a
 * ledger row, all in one transaction per flush.
 */
public final class JdbcCampaignRepository implements CampaignRepository {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcCampaignRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    // ------------------------------------------------------------------ votes

    @Override
    public CompletableFuture<Optional<VoteCycle>> voteCycle(String cycleId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                return selectCycle(conn, cycleId);
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read vote cycle " + cycleId, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<VoteCycle> openVote(String cycleId, List<String> options, long opensAt, long closesAt) {
        return CompletableFuture.supplyAsync(() -> {
            String insert = switch (dialect) {
                case POSTGRESQL -> "INSERT INTO suld_khuraldai_cycles (cycle_id, options, opened_at, closes_at, tally, voters, version) "
                        + "VALUES (?, ?, ?, ?, '', 0, 0) ON CONFLICT (cycle_id) DO NOTHING";
                case MYSQL -> "INSERT INTO suld_khuraldai_cycles (cycle_id, options, opened_at, closes_at, tally, voters, version) "
                        + "VALUES (?, ?, ?, ?, '', 0, 0) ON DUPLICATE KEY UPDATE cycle_id = cycle_id";
            };
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setString(1, cycleId);
                    ps.setString(2, String.join(",", options));
                    ps.setLong(3, opensAt);
                    ps.setLong(4, closesAt);
                    ps.executeUpdate();
                }
                return selectCycle(conn, cycleId).orElseThrow(() -> new SQLException("vote cycle row missing " + cycleId));
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to open vote " + cycleId, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<CastResult> castVote(String cycleId, UUID player, String option, int level, long at) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                Optional<VoteCycle> c = selectCycle(conn, cycleId);
                if (c.isEmpty()) return CastResult.NO_CYCLE;
                if (c.get().closed()) return CastResult.CLOSED;
                if (!c.get().options().contains(option)) return CastResult.NO_SUCH_OPTION;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO suld_khuraldai_votes (cycle_id, player_uuid, option_id, player_level, at) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, cycleId);
                    bindUuid(ps, 2, player);
                    ps.setString(3, option);
                    ps.setInt(4, level);
                    ps.setLong(5, at);
                    ps.executeUpdate();
                    return CastResult.CAST;
                } catch (SQLIntegrityConstraintViolationException dup) {
                    return CastResult.ALREADY_VOTED;
                } catch (SQLException ex) {
                    if (ex.getSQLState() != null && ex.getSQLState().startsWith("23")) return CastResult.ALREADY_VOTED; // PostgreSQL unique_violation
                    throw ex;
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to cast vote in " + cycleId, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Integer>> tally(String cycleId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                return tally(conn, cycleId);
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to tally " + cycleId, ex);
            }
        }, executor);
    }

    private Map<String, Integer> tally(Connection conn, String cycleId) throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT option_id, COUNT(*) FROM suld_khuraldai_votes WHERE cycle_id = ? GROUP BY option_id")) {
            ps.setString(1, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
            }
        }
        return out;
    }

    @Override
    public CompletableFuture<Boolean> closeVote(String cycleId, String winner, Map<String, Integer> tally, long closedAt, long expectedVersion) {
        return CompletableFuture.supplyAsync(() -> {
            int voters = tally.values().stream().mapToInt(Integer::intValue).sum();
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(
                    "UPDATE suld_khuraldai_cycles SET closed_at = ?, winner = ?, tally = ?, voters = ?, version = version + 1 "
                            + "WHERE cycle_id = ? AND version = ? AND closed_at IS NULL")) {
                ps.setLong(1, closedAt);
                ps.setString(2, winner);
                ps.setString(3, encodeTally(tally));
                ps.setInt(4, voters);
                ps.setString(5, cycleId);
                ps.setLong(6, expectedVersion);
                return ps.executeUpdate() == 1;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to close vote " + cycleId, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<VoteCycle>> recentVotes(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(
                    "SELECT cycle_id, options, opened_at, closes_at, closed_at, winner, tally, voters, version FROM suld_khuraldai_cycles "
                            + "ORDER BY cycle_id DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, Math.min(100, limit)));
                List<VoteCycle> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(readCycle(rs));
                }
                return out;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read vote cycles", ex);
            }
        }, executor);
    }

    private Optional<VoteCycle> selectCycle(Connection conn, String cycleId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT cycle_id, options, opened_at, closes_at, closed_at, winner, tally, voters, version FROM suld_khuraldai_cycles WHERE cycle_id = ?")) {
            ps.setString(1, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readCycle(rs)) : Optional.empty();
            }
        }
    }

    private static VoteCycle readCycle(ResultSet rs) throws SQLException {
        long closedAt = rs.getLong("closed_at");
        Long closed = rs.wasNull() ? null : closedAt;
        String options = rs.getString("options");
        return new VoteCycle(rs.getString("cycle_id"), options == null || options.isBlank() ? List.of() : List.of(options.split(",")),
                rs.getLong("opened_at"), rs.getLong("closes_at"), closed, rs.getString("winner"), decodeTally(rs.getString("tally")),
                rs.getInt("voters"), rs.getLong("version"));
    }

    static String encodeTally(Map<String, Integer> tally) {
        StringBuilder sb = new StringBuilder();
        tally.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ",").append(k).append('=').append(v));
        return sb.toString();
    }

    static Map<String, Integer> decodeTally(String text) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return out;
        for (String part : text.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try {
                out.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1)));
            } catch (NumberFormatException ignored) {
                // a foreign row: skip the pair
            }
        }
        return out;
    }

    // -------------------------------------------------------------- campaigns

    private static final String CAMPAIGN_COLUMNS = "cycle_id, campaign_id, state, starts_at, ends_at, resolved_at, target, total, effect_applied_at, version";

    @Override
    public CompletableFuture<Optional<CampaignSnapshot>> campaign(String cycleId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                return selectCampaign(conn, cycleId);
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read campaign " + cycleId, ex);
            }
        }, executor);
    }

    private Optional<CampaignSnapshot> selectCampaign(Connection conn, String cycleId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + CAMPAIGN_COLUMNS + " FROM suld_campaigns WHERE cycle_id = ?")) {
            ps.setString(1, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readCampaign(rs)) : Optional.empty();
            }
        }
    }

    private static CampaignSnapshot readCampaign(ResultSet rs) throws SQLException {
        long resolved = rs.getLong("resolved_at");
        Long resolvedAt = rs.wasNull() ? null : resolved;
        long applied = rs.getLong("effect_applied_at");
        Long effectAt = rs.wasNull() ? null : applied;
        return new CampaignSnapshot(rs.getString("cycle_id"), rs.getString("campaign_id"), CampaignState.valueOf(rs.getString("state")),
                rs.getLong("starts_at"), rs.getLong("ends_at"), resolvedAt, rs.getLong("target"), rs.getLong("total"), effectAt, rs.getLong("version"));
    }

    @Override
    public CompletableFuture<Boolean> saveCampaign(CampaignSnapshot s, long expectedVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                if (expectedVersion < 0) {
                    String insert = switch (dialect) {
                        case POSTGRESQL -> "INSERT INTO suld_campaigns (" + CAMPAIGN_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0) ON CONFLICT (cycle_id) DO NOTHING";
                        case MYSQL -> "INSERT IGNORE INTO suld_campaigns (" + CAMPAIGN_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
                    };
                    try (PreparedStatement ps = conn.prepareStatement(insert)) {
                        ps.setString(1, s.cycleId());
                        ps.setString(2, s.campaignId());
                        ps.setString(3, s.state().name());
                        ps.setLong(4, s.startsAt());
                        ps.setLong(5, s.endsAt());
                        setNullableLong(ps, 6, s.resolvedAt());
                        ps.setLong(7, s.target());
                        ps.setLong(8, s.total());
                        setNullableLong(ps, 9, s.effectAppliedAt());
                        return ps.executeUpdate() == 1;
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE suld_campaigns SET state = ?, resolved_at = ?, effect_applied_at = ?, version = version + 1 WHERE cycle_id = ? AND version = ?")) {
                    ps.setString(1, s.state().name());
                    setNullableLong(ps, 2, s.resolvedAt());
                    setNullableLong(ps, 3, s.effectAppliedAt());
                    ps.setString(4, s.cycleId());
                    ps.setLong(5, expectedVersion);
                    return ps.executeUpdate() == 1;
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to save campaign " + s.cycleId(), ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> addContributions(String cycleId, List<Contribution> deltas) {
        if (deltas.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            String upsert = switch (dialect) {
                case POSTGRESQL -> "INSERT INTO suld_campaign_totals (cycle_id, player_uuid, player_name, clan_uuid, units) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (cycle_id, player_uuid) DO UPDATE SET units = suld_campaign_totals.units + EXCLUDED.units, "
                        + "player_name = EXCLUDED.player_name, clan_uuid = EXCLUDED.clan_uuid";
                case MYSQL -> "INSERT INTO suld_campaign_totals (cycle_id, player_uuid, player_name, clan_uuid, units) VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE units = units + VALUES(units), player_name = VALUES(player_name), clan_uuid = VALUES(clan_uuid)";
            };
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    long sum = 0, now = System.currentTimeMillis();
                    try (PreparedStatement ps = conn.prepareStatement(upsert);
                         PreparedStatement ledger = conn.prepareStatement("INSERT INTO suld_campaign_ledger (cycle_id, player_uuid, units, at) VALUES (?, ?, ?, ?)")) {
                        for (Contribution d : deltas) {
                            if (d.units() <= 0) continue;
                            ps.setString(1, cycleId);
                            bindUuid(ps, 2, d.player());
                            ps.setString(3, truncate(d.name(), 16));
                            bindNullableUuid(ps, 4, d.clan());
                            ps.setLong(5, d.units());
                            ps.addBatch();
                            ledger.setString(1, cycleId);
                            bindUuid(ledger, 2, d.player());
                            ledger.setLong(3, d.units());
                            ledger.setLong(4, now);
                            ledger.addBatch();
                            sum += d.units();
                        }
                        ps.executeBatch();
                        ledger.executeBatch();
                    }
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE suld_campaigns SET total = total + ? WHERE cycle_id = ?")) {
                        ps.setLong(1, sum);
                        ps.setString(2, cycleId);
                        ps.executeUpdate();
                    }
                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to add " + deltas.size() + " contributions to " + cycleId, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<Contribution>> contributions(String cycleId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(
                    "SELECT player_uuid, player_name, clan_uuid, units, reward_tier, reward_claimed_at FROM suld_campaign_totals "
                            + "WHERE cycle_id = ? ORDER BY units DESC, player_name ASC")) {
                ps.setString(1, cycleId);
                List<Contribution> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int tier = rs.getInt("reward_tier");
                        Integer rewardTier = rs.wasNull() ? null : tier;
                        long claimed = rs.getLong("reward_claimed_at");
                        Long claimedAt = rs.wasNull() ? null : claimed;
                        out.add(new Contribution(readUuid(rs, "player_uuid"), rs.getString("player_name"), readUuid(rs, "clan_uuid"),
                                rs.getLong("units"), rewardTier, claimedAt));
                    }
                }
                return out;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read contributions of " + cycleId, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> markReward(String cycleId, UUID player, int tier, Long claimedAt) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(
                    "UPDATE suld_campaign_totals SET reward_tier = ?, reward_claimed_at = ? WHERE cycle_id = ? AND player_uuid = ?")) {
                ps.setInt(1, tier);
                setNullableLong(ps, 2, claimedAt);
                ps.setString(3, cycleId);
                bindUuid(ps, 4, player);
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to mark reward of " + player + " in " + cycleId, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<CampaignSnapshot>> recentCampaigns(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(
                    "SELECT " + CAMPAIGN_COLUMNS + " FROM suld_campaigns ORDER BY cycle_id DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, Math.min(100, limit)));
                List<CampaignSnapshot> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(readCampaign(rs));
                }
                return out;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read campaigns", ex);
            }
        }, executor);
    }

    // ------------------------------------------------------------ world state

    @Override
    public CompletableFuture<Void> setFlag(WorldFlag flag) {
        return CompletableFuture.runAsync(() -> {
            String upsert = switch (dialect) {
                case POSTGRESQL -> "INSERT INTO suld_world_state (flag_key, flag_value, source_cycle, expires_cycle, set_at) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (flag_key) DO UPDATE SET flag_value = EXCLUDED.flag_value, source_cycle = EXCLUDED.source_cycle, "
                        + "expires_cycle = EXCLUDED.expires_cycle, set_at = EXCLUDED.set_at";
                case MYSQL -> "INSERT INTO suld_world_state (flag_key, flag_value, source_cycle, expires_cycle, set_at) VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value), source_cycle = VALUES(source_cycle), "
                        + "expires_cycle = VALUES(expires_cycle), set_at = VALUES(set_at)";
            };
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(upsert)) {
                ps.setString(1, flag.key());
                ps.setString(2, flag.value());
                ps.setString(3, flag.sourceCycle());
                if (flag.expiresCycle() == null) ps.setNull(4, Types.VARCHAR); else ps.setString(4, flag.expiresCycle());
                ps.setLong(5, flag.setAt());
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to set world flag " + flag.key(), ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> clearFlag(String key) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM suld_world_state WHERE flag_key = ?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to clear world flag " + key, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<WorldFlag>> flags() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(
                    "SELECT flag_key, flag_value, source_cycle, expires_cycle, set_at FROM suld_world_state ORDER BY set_at")) {
                List<WorldFlag> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new WorldFlag(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5)));
                }
                return out;
            } catch (SQLException ex) {
                throw new RepositoryException("Failed to read world flags", ex);
            }
        }, executor);
    }

    // ---------------------------------------------------------------- helpers

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

    static UUID readUuid(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        if (raw == null) return null;
        if (raw instanceof UUID u) return u;
        return UUID.fromString(raw.toString());
    }
}
