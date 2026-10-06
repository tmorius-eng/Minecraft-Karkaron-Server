package mn.suld.api.identity;

/** Outcome of {@link AuthPolicy#evaluate}. {@code reason} is safe to show to the player. */
public record AuthDecision(boolean allowed, Code code, String reason) {

    public enum Code {
        ALLOWED,
        ALLOWED_INSECURE_DEV,
        DENIED_SERVER_INSECURE,
        DENIED_UNAUTHENTICATED_UUID,
        DENIED_INVALID_NAME
    }

    static AuthDecision allow(Code code) {
        return new AuthDecision(true, code, "");
    }

    static AuthDecision deny(Code code, String reason) {
        return new AuthDecision(false, code, reason);
    }
}
