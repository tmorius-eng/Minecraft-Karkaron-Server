package mn.suld.api.identity;

/** How the server establishes player identity. Determined once at startup. */
public enum AuthMode {
    /** {@code online-mode=true}: the server verifies every login with Mojang/Microsoft. */
    ONLINE(true),
    /** Behind a Velocity proxy with modern (HMAC-signed) forwarding and an online-mode proxy. */
    VELOCITY_FORWARDED(true),
    /** Anything else (offline mode, legacy BungeeCord forwarding): identities can be spoofed. */
    INSECURE(false);

    private final boolean verified;

    AuthMode(boolean verified) {
        this.verified = verified;
    }

    /** Whether identities in this mode are cryptographically verified. */
    public boolean verified() {
        return verified;
    }
}
