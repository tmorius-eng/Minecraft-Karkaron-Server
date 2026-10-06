package mn.suld.api.audit;

/** Append-only audit trail. Implementations must not block the caller (main thread). */
public interface AuditLog {

    void record(AuditEvent event);

    AuditLog NOOP = event -> { };
}
