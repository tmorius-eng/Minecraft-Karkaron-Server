package mn.suld.plugin.analytics;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.analytics.AnalyticsSink;
import mn.suld.api.json.Json;
import mn.suld.plugin.persistence.SqlDialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * The database analytics sink ({@code analytics.sink: database}, docs/ANALYTICS.md):
 * <ul>
 *   <li>{@link #record} only queues (never blocks, never touches the database): a bounded queue; when it is full the
 *       oldest event is dropped and counted, so a database outage cannot grow memory without limit;</li>
 *   <li>{@link #flush} (the existing async timer) writes the queue as JDBC batches on the I/O executor;</li>
 *   <li>{@code session_end} events are also written as one {@code suld_sessions} row (V14) with the totals;</li>
 *   <li>configured high-volume types (default {@code exp_gain}) are not stored — the session totals carry them;</li>
 *   <li>{@link #purge} deletes rows older than the retention, in bounded batches.</li>
 * </ul>
 */
public final class JdbcAnalyticsSink implements AnalyticsSink {

    private static final int BATCH = 500;
    private static final int PURGE_BATCH = 5_000;

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final Executor executor;
    private final Logger logger;
    private final Set<String> skip;
    private final int limit;
    private final ConcurrentLinkedDeque<AnalyticsEvent> queue = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong written = new AtomicLong();

    public JdbcAnalyticsSink(DataSource dataSource, SqlDialect dialect, Executor executor, Logger logger, Set<String> skipTypes, int queueLimit) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = executor;
        this.logger = logger;
        this.skip = Set.copyOf(skipTypes);
        this.limit = queueLimit;
    }

    @Override
    public void record(AnalyticsEvent event) {
        if (skip.contains(event.type())) return;
        queue.addLast(event);
        if (size.incrementAndGet() > limit && queue.pollFirst() != null) {
            size.decrementAndGet();
            dropped.incrementAndGet();
        }
    }

    /** Events dropped because the queue was full (diagnostics). */
    public long dropped() {
        return dropped.get();
    }

    public long written() {
        return written.get();
    }

    @Override
    public CompletableFuture<Void> flush() {
        List<AnalyticsEvent> batch = new ArrayList<>();
        AnalyticsEvent e;
        while ((e = queue.pollFirst()) != null) {
            size.decrementAndGet();
            batch.add(e);
        }
        if (batch.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> write(batch), executor).exceptionally(ex -> {
            logger.warning("analytics: " + batch.size() + " event(s) not written: " + ex.getClass().getSimpleName());
            return null;
        });
    }

    private void write(List<AnalyticsEvent> batch) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ev = c.prepareStatement("INSERT INTO suld_analytics_events (type, player_uuid, at, attributes) VALUES (?, ?, ?, ?)");
                 PreparedStatement se = c.prepareStatement("INSERT INTO suld_sessions (player_uuid, started_at, ended_at, duration_s, active_min, "
                         + "combat_min, mobs, exp, deaths, totals) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int n = 0;
                for (AnalyticsEvent e : batch) {
                    ev.setString(1, e.type());
                    uuid(ev, 2, e.player());
                    ev.setLong(3, e.at().toEpochMilli());
                    ev.setString(4, e.attributes().isEmpty() ? null : Json.write(plain(e.attributes())));
                    ev.addBatch();
                    if (AnalyticsEventType.SESSION_END.equals(e.type()) && e.player() != null && e.attributes().containsKey("duration_s")) {
                        Map<String, Object> a = e.attributes();
                        uuid(se, 1, e.player());
                        se.setLong(2, num(a, "started_at"));
                        se.setLong(3, e.at().toEpochMilli());
                        se.setLong(4, num(a, "duration_s"));
                        se.setLong(5, num(a, "active_min"));
                        se.setLong(6, num(a, "combat_min"));
                        se.setLong(7, num(a, "mobs"));
                        se.setLong(8, num(a, "exp"));
                        se.setInt(9, (int) num(a, "deaths"));
                        se.setString(10, Json.write(plain(a)));
                        se.addBatch();
                    }
                    if (++n % BATCH == 0) {
                        ev.executeBatch();
                        se.executeBatch();
                    }
                }
                ev.executeBatch();
                se.executeBatch();
                c.commit();
                written.addAndGet(batch.size());
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("analytics write failed", ex);
        }
    }

    /** Delete events and sessions older than {@code days} (bounded batches; call off the main thread). */
    public int purge(int days) {
        if (days <= 0) return 0;
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        int total = 0;
        for (String table : List.of("suld_analytics_events", "suld_sessions")) {
            String col = table.equals("suld_sessions") ? "ended_at" : "at";
            String sql = switch (dialect) {
                case POSTGRESQL -> "DELETE FROM " + table + " WHERE id IN (SELECT id FROM " + table + " WHERE " + col + " < ? LIMIT " + PURGE_BATCH + ")";
                case MYSQL -> "DELETE FROM " + table + " WHERE " + col + " < ? LIMIT " + PURGE_BATCH;
            };
            try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                int n;
                do {
                    ps.setLong(1, cutoff);
                    n = ps.executeUpdate();
                    total += n;
                } while (n == PURGE_BATCH);
            } catch (SQLException ex) {
                logger.warning("analytics purge of " + table + " failed: " + ex.getClass().getSimpleName());
            }
        }
        return total;
    }

    /** JSON-safe copy: numbers, booleans and strings as they are, anything else as its text. */
    private static Map<String, Object> plain(Map<String, Object> a) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        a.forEach((k, v) -> m.put(k, v == null || v instanceof Number && Double.isFinite(((Number) v).doubleValue()) || v instanceof Boolean
                || v instanceof String ? v : String.valueOf(v)));
        return m;
    }

    private static long num(Map<String, Object> a, String k) {
        return a.get(k) instanceof Number n ? n.longValue() : 0;
    }

    private void uuid(PreparedStatement ps, int i, UUID id) throws SQLException {
        if (id == null) ps.setNull(i, dialect == SqlDialect.POSTGRESQL ? java.sql.Types.OTHER : java.sql.Types.CHAR);
        else if (dialect == SqlDialect.POSTGRESQL) ps.setObject(i, id);
        else ps.setString(i, id.toString());
    }
}
