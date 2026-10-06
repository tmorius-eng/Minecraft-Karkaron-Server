package mn.suld.plugin.audit;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.audit.AuditLog;

import java.util.logging.Logger;

/** Audit trail to the server log (MEMORY mode), and the mirror used by {@link JdbcAuditLog}. */
public final class LoggingAuditLog implements AuditLog {

    private final Logger logger;

    public LoggingAuditLog(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void record(AuditEvent e) {
        // Fields are already stripped of control characters (no log-line forging).
        logger.info("[audit] " + e.action() + " actor=" + e.actor() + " target=" + e.target()
                + (e.detail().isEmpty() ? "" : " detail=" + e.detail()));
    }
}
