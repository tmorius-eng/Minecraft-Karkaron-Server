package mn.suld.plugin.persistence;

import mn.suld.api.persistence.ReferralRepository;
import mn.suld.api.persistence.RepositoryException;
import mn.suld.api.social.Referral;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Referrals over the V31 table (docs/REFERRAL.md). The invitee's row is written once (the primary key refuses a second
 * inviter); the two payments are {@code UPDATE … WHERE … IS NULL}, so under any number of concurrent level-ups or
 * logins each side is paid exactly once.
 */
public final class JdbcReferralRepository implements ReferralRepository {

    private static final String COLUMNS = "invitee_uuid, inviter_uuid, created_at, rewarded_at, inviter_paid_at";

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcReferralRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Boolean> create(Referral r) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO suld_referrals (" + COLUMNS + ") VALUES (?, ?, ?, NULL, NULL)")) {
                bindUuid(ps, 1, r.invitee());
                bindUuid(ps, 2, r.inviter());
                ps.setLong(3, r.createdAt());
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                if (e.getSQLState() != null && e.getSQLState().startsWith("23")) return false; // the first invitation stands
                throw new RepositoryException("create referral of " + r.invitee(), e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Referral>> byInvitee(UUID invitee) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM suld_referrals WHERE invitee_uuid = ?")) {
                bindUuid(ps, 1, invitee);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new RepositoryException("referral of " + invitee, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<Referral>> byInviter(UUID inviter) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM suld_referrals WHERE inviter_uuid = ? ORDER BY created_at, invitee_uuid")) {
                bindUuid(ps, 1, inviter);
                List<Referral> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(read(rs));
                }
                return out;
            } catch (SQLException e) {
                throw new RepositoryException("referrals by " + inviter, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> markRewarded(UUID invitee, long at) {
        return update("UPDATE suld_referrals SET rewarded_at = ? WHERE invitee_uuid = ? AND rewarded_at IS NULL", invitee, at);
    }

    @Override
    public CompletableFuture<Boolean> markInviterPaid(UUID invitee, long at) {
        return update("UPDATE suld_referrals SET inviter_paid_at = ? WHERE invitee_uuid = ? AND rewarded_at IS NOT NULL AND inviter_paid_at IS NULL", invitee, at);
    }

    private CompletableFuture<Boolean> update(String sql, UUID invitee, long at) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, at);
                bindUuid(ps, 2, invitee);
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                throw new RepositoryException("referral payment of " + invitee, e);
            }
        }, executor);
    }

    private Referral read(ResultSet rs) throws SQLException {
        long rewarded = rs.getLong(4);
        Long rewardedAt = rs.wasNull() ? null : rewarded;
        long paid = rs.getLong(5);
        Long paidAt = rs.wasNull() ? null : paid;
        return new Referral(uuid(rs, 1), uuid(rs, 2), rs.getLong(3), rewardedAt, paidAt);
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid);
        else ps.setString(index, uuid.toString());
    }

    private UUID uuid(ResultSet rs, int col) throws SQLException {
        return dialect == SqlDialect.POSTGRESQL ? rs.getObject(col, UUID.class) : UUID.fromString(rs.getString(col));
    }
}
