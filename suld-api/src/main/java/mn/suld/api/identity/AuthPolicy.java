package mn.suld.api.identity;

import java.util.UUID;

/**
 * The login gate. Pure and unit-tested. SÜLD never authenticates players itself — there is
 * no /register or /login and no password ever exists. Minecraft/Microsoft authentication
 * (online mode) is the only proof of identity; this policy only refuses logins whose
 * identity cannot be trusted.
 *
 * <p>Fail closed: if the server is not verifying identities, every login is denied unless
 * an operator explicitly enabled the local-development escape hatch.
 */
public final class AuthPolicy {

    private final AuthMode mode;
    private final boolean allowInsecureDevMode;

    public AuthPolicy(AuthMode mode, boolean allowInsecureDevMode) {
        this.mode = mode;
        this.allowInsecureDevMode = allowInsecureDevMode;
    }

    public AuthMode mode() {
        return mode;
    }

    /** Whether the server will accept any login at all. */
    public boolean acceptsLogins() {
        return mode.verified() || allowInsecureDevMode;
    }

    public AuthDecision evaluate(UUID uuid, String name) {
        if (!PlayerIdentity.isValidName(name) || uuid == null) {
            return AuthDecision.deny(AuthDecision.Code.DENIED_INVALID_NAME, "Invalid account name.");
        }
        PlayerIdentity identity = PlayerIdentity.of(uuid, name);
        if (mode.verified()) {
            if (!identity.hasAuthenticatedUuidShape()) {
                // A verified server never hands out offline-style UUIDs: treat as spoofing/misconfig.
                return AuthDecision.deny(AuthDecision.Code.DENIED_UNAUTHENTICATED_UUID,
                        "Account identity could not be verified.");
            }
            return AuthDecision.allow(AuthDecision.Code.ALLOWED);
        }
        if (allowInsecureDevMode) {
            return AuthDecision.allow(AuthDecision.Code.ALLOWED_INSECURE_DEV);
        }
        return AuthDecision.deny(AuthDecision.Code.DENIED_SERVER_INSECURE,
                "Server is misconfigured (account verification is off). Please contact an admin.");
    }
}
