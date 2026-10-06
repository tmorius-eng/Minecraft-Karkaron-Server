package mn.suld.api.audit;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * One security-relevant event (authentication, sessions, profile lifecycle, admin actions).
 * Fields are sanitized on construction: control characters (newlines, ANSI escapes) are
 * removed so a crafted value can never forge extra log lines, and lengths are capped to the
 * {@code suld_audit_log} column sizes. Never put secrets (passwords, tokens, IPs) in here.
 */
public record AuditEvent(Instant at, String actor, String action, String target, String detail) {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");

    public AuditEvent {
        actor = clean(actor, 64);
        action = clean(action, 64);
        target = clean(target, 64);
        detail = clean(detail, 1000);
    }

    public static AuditEvent of(String actor, String action, String target, String detail) {
        return new AuditEvent(Instant.now(), actor, action, target, detail);
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String s = CONTROL.matcher(value).replaceAll("?");
        return s.length() > max ? s.substring(0, max) : s;
    }
}
