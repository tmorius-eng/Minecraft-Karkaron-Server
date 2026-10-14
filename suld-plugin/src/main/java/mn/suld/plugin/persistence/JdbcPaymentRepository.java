package mn.suld.plugin.persistence;

import mn.suld.api.payment.PaymentEvent;
import mn.suld.api.payment.PaymentIntent;
import mn.suld.api.persistence.PaymentRepository;
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
 * The payment ledger over the V29 tables (docs/PAYMENT_ARCHITECTURE.md §Data). An intent moves only by
 * {@code UPDATE … WHERE version = ?}; a confirmed provider transaction id already another intent's breaks the unique
 * key, and that write is refused like a lost compare-and-set. The event log is append-only.
 */
public final class JdbcPaymentRepository implements PaymentRepository {

    private static final String COLUMNS = "intent_id, player_uuid, product, credits, amount, currency, provider, state, created_at, "
            + "expires_at, invoice_id, provider_txn_id, confirmed_at, fulfilled_at, version";

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;

    public JdbcPaymentRepository(DataSource dataSource, SqlDialect dialect, Executor executor) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> create(PaymentIntent i) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO suld_payment_intents (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bindUuid(ps, 1, i.intentId());
                bindUuid(ps, 2, i.player());
                ps.setString(3, i.product());
                ps.setLong(4, i.credits());
                ps.setLong(5, i.amountMinor());
                ps.setString(6, i.currency());
                ps.setString(7, i.provider());
                ps.setString(8, i.state().name());
                ps.setLong(9, i.createdAt());
                ps.setLong(10, i.expiresAt());
                ps.setString(11, i.invoiceId());
                ps.setString(12, i.providerTxnId());
                setLong(ps, 13, i.confirmedAt());
                setLong(ps, 14, i.fulfilledAt());
                ps.setInt(15, i.version());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("payment intent " + i.intentId(), e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<PaymentIntent>> find(UUID intentId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM suld_payment_intents WHERE intent_id = ?")) {
                bindUuid(ps, 1, intentId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.<PaymentIntent>empty();
                }
            } catch (SQLException e) {
                throw new RepositoryException("payment intent " + intentId, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> replace(PaymentIntent n, int expectedVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE suld_payment_intents SET state = ?, invoice_id = ?, provider_txn_id = ?, "
                         + "confirmed_at = ?, fulfilled_at = ?, expires_at = ?, version = ? WHERE intent_id = ? AND version = ?")) {
                ps.setString(1, n.state().name());
                ps.setString(2, n.invoiceId());
                ps.setString(3, n.providerTxnId());
                setLong(ps, 4, n.confirmedAt());
                setLong(ps, 5, n.fulfilledAt());
                ps.setLong(6, n.expiresAt());
                ps.setInt(7, n.version());
                bindUuid(ps, 8, n.intentId());
                ps.setInt(9, expectedVersion);
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                if (uniqueViolation(e)) return false; // the provider id is another intent's: refused, like a lost CAS
                throw new RepositoryException("payment intent " + n.intentId(), e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PaymentIntent>> owed() {
        return list("SELECT " + COLUMNS + " FROM suld_payment_intents WHERE state = 'CONFIRMED' AND fulfilled_at IS NULL", ps -> {
        });
    }

    @Override
    public CompletableFuture<List<PaymentIntent>> expiredBy(long now) {
        return list("SELECT " + COLUMNS + " FROM suld_payment_intents WHERE state = 'PENDING' AND expires_at <= ?", ps -> ps.setLong(1, now));
    }

    @Override
    public CompletableFuture<List<PaymentIntent>> open(UUID player, long now) {
        return list("SELECT " + COLUMNS + " FROM suld_payment_intents WHERE player_uuid = ? AND state = 'PENDING' AND expires_at > ?", ps -> {
            bindUuid(ps, 1, player);
            ps.setLong(2, now);
        });
    }

    @Override
    public CompletableFuture<List<PaymentIntent>> recent(UUID player, int limit) {
        return list("SELECT " + COLUMNS + " FROM suld_payment_intents WHERE player_uuid = ? ORDER BY created_at DESC", ps -> {
            bindUuid(ps, 1, player);
            ps.setMaxRows(Math.max(1, limit));
        });
    }

    @Override
    public CompletableFuture<Void> log(PaymentEvent e) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO suld_payment_events (received_at, provider, body_sha256, signature_ok, "
                         + "reference, invoice_id, status, outcome) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, e.receivedAt());
                ps.setString(2, e.provider());
                ps.setString(3, e.bodySha256());
                ps.setBoolean(4, e.signatureOk());
                ps.setString(5, e.reference());
                ps.setString(6, e.invoiceId());
                ps.setString(7, e.status());
                ps.setString(8, e.outcome());
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new RepositoryException("payment event", ex);
            }
        }, executor);
    }

    /** The log, newest first (tests and staff). */
    public CompletableFuture<List<PaymentEvent>> events(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT received_at, provider, body_sha256, signature_ok, reference, invoice_id, status, "
                         + "outcome FROM suld_payment_events ORDER BY id DESC")) {
                ps.setMaxRows(Math.max(1, limit));
                List<PaymentEvent> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new PaymentEvent(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getString(5),
                                rs.getString(6), rs.getString(7), rs.getString(8)));
                    }
                }
                return out;
            } catch (SQLException e) {
                throw new RepositoryException("payment events", e);
            }
        }, executor);
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private CompletableFuture<List<PaymentIntent>> list(String sql, Binder binder) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                binder.bind(ps);
                List<PaymentIntent> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(read(rs));
                }
                return out;
            } catch (SQLException e) {
                throw new RepositoryException("payment intents", e);
            }
        }, executor);
    }

    private static PaymentIntent read(ResultSet rs) throws SQLException {
        return new PaymentIntent(uuid(rs, "intent_id"), uuid(rs, "player_uuid"),
                rs.getString("product"), rs.getLong("credits"), rs.getLong("amount"), rs.getString("currency"), rs.getString("provider"),
                PaymentIntent.State.valueOf(rs.getString("state")), rs.getLong("created_at"), rs.getLong("expires_at"),
                rs.getString("invoice_id"), rs.getString("provider_txn_id"), nullableLong(rs, "confirmed_at"), nullableLong(rs, "fulfilled_at"),
                rs.getInt("version"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        return raw instanceof UUID u ? u : UUID.fromString(String.valueOf(raw).trim());
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static void setLong(PreparedStatement ps, int index, Long v) throws SQLException {
        if (v == null) ps.setNull(index, Types.BIGINT);
        else ps.setLong(index, v);
    }

    /** SQLSTATE 23505 (PostgreSQL, H2) or MySQL's 1062: a unique key would be broken. */
    static boolean uniqueViolation(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && ("23505".equals(s.getSQLState()) || s.getErrorCode() == 1062)) return true;
        }
        return false;
    }

    private void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (dialect == SqlDialect.POSTGRESQL) ps.setObject(index, uuid);
        else ps.setString(index, uuid.toString());
    }
}
