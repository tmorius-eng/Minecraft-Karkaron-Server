package mn.suld.plugin.audit;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.audit.AuditLog;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Durable audit trail in {@code suld_audit_log} (V1 schema), written off-thread. Every event
 * is also mirrored to the server log so an outage of the database never hides an event.
 */
public final class JdbcAuditLog implements AuditLog {

    private static final String INSERT =
            "INSERT INTO suld_audit_log (at, actor, action, target, detail) VALUES (?, ?, ?, ?, ?)";

    private final DataSource dataSource;
    private final Executor executor;
    private final Logger logger;
    private final LoggingAuditLog mirror;

    public JdbcAuditLog(DataSource dataSource, Executor executor, Logger logger) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.logger = logger;
        this.mirror = new LoggingAuditLog(logger);
    }

    @Override
    public void record(AuditEvent e) {
        mirror.record(e);
        try {
            executor.execute(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(INSERT)) {
                    ps.setLong(1, e.at().toEpochMilli());
                    ps.setString(2, e.actor());
                    ps.setString(3, e.action());
                    ps.setString(4, e.target());
                    ps.setString(5, e.detail());
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    logger.warning("Audit write failed (" + e.action() + "): " + ex.getClass().getSimpleName());
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            logger.warning("Audit write skipped during shutdown: " + e.action());
        }
    }
}
