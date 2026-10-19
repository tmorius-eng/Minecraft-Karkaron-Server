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
    /** Hybrid login: joined, waiting for AuthMe; logged in by AuthMe (enters the game); logged out again (kicked). */
    public static final String AUTHME_PENDING = "auth.authme_pending";
    public static final String AUTHME_LOGIN = "auth.authme_login";
    public static final String AUTHME_LOGOUT = "auth.authme_logout";
    public static final String PROFILE_LOAD_FAILED = "auth.profile_load_failed";
    public static final String JOIN_WITHOUT_SESSION = "auth.join_without_session";
    public static final String PENDING_EXPIRED = "auth.pending_expired";
    public static final String SESSION_END = "auth.session_end";

    // Их Хуралдай and the chronicle (admin actions and world changes)
    public static final String CAMPAIGN_ADMIN_OPEN = "campaign.admin.open";
    public static final String CAMPAIGN_ADMIN_CLOSE = "campaign.admin.close";
    public static final String CAMPAIGN_ADMIN_FORCE = "campaign.admin.force";
    public static final String CAMPAIGN_ADMIN_RESOLVE = "campaign.admin.resolve";
    public static final String CAMPAIGN_ADMIN_CANCEL = "campaign.admin.cancel";
    public static final String CAMPAIGN_EFFECT_APPLIED = "campaign.effect_applied";
    public static final String CHRONICLE_ADMIN_HIDE = "chronicle.admin.hide";
    /** A player's {@code /report}: target = world and block position, detail = the text. */
    public static final String PLAYER_REPORT = "player.report";
    /** A purchase asked for (target = player, detail = intent, product, amount). */
    public static final String PAYMENT_INTENT = "payment.intent";
    /** A provider's signed event paid an intent and its credits were granted (actor = the provider). */
    public static final String PAYMENT_CONFIRMED = "payment.confirmed";
    /** A signed event paid another amount or currency than its intent's: nothing granted, staff alerted. */
    public static final String PAYMENT_MISMATCH = "payment.mismatch";
    /** /urih: the invitee (actor) named the inviter (target). */
    public static final String REFERRAL_NAMED = "referral.named";
    /** A referral payment: actor = who was paid, target = the invitee, detail = coins and why. */
    public static final String REFERRAL_PAID = "referral.paid";
}
