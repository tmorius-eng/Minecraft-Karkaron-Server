package mn.suld.api.audit;

/** Canonical audit action keys (stable: dashboards and incident queries depend on them). */
public final class AuditActions {

    private AuditActions() {
    }

    public static final String AUTH_MODE = "auth.mode";
    public static final String FIRST_JOIN = "auth.first_join";
    public static final String LOGIN = "auth.login";
    public static final String NAME_CHANGE = "auth.name_change";
    public static final String DUPLICATE_SESSION = "auth.duplicate_session";
    public static final String DENIED = "auth.denied";
    public static final String INSECURE_DEV_LOGIN = "auth.insecure_dev_login";
    public static final String PROFILE_LOAD_FAILED = "auth.profile_load_failed";
    public static final String JOIN_WITHOUT_SESSION = "auth.join_without_session";
    public static final String PENDING_EXPIRED = "auth.pending_expired";
    public static final String SESSION_END = "auth.session_end";
}
