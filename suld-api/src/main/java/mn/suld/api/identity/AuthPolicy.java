package mn.suld.api.identity;

import java.util.UUID;

/**
 * The login gate. Pure and unit-tested. SÜLD never authenticates players itself and never holds a
 * password: Minecraft/Microsoft authentication (online mode, or an online-mode Velocity proxy) or, in
 * {@link AuthMode#HYBRID_AUTHME}, AuthMe on the proxy and the server is the proof of identity; this
 * policy only refuses logins whose identity cannot be trusted.
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
        if (mode == AuthMode.HYBRID_AUTHME) {
            // every account, premium or not, has the offline UUID of its name; any other UUID was not made by the
            // proxy for this name (a spoofed forwarding packet or a misconfigured proxy)
            if (!PlayerIdentity.isOfflineUuidFor(uuid, name)) {
                return AuthDecision.deny(AuthDecision.Code.DENIED_UNAUTHENTICATED_UUID,
                        "Account identity could not be verified.");
            }
            return AuthDecision.allow(AuthDecision.Code.ALLOWED_HYBRID);
        }
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
