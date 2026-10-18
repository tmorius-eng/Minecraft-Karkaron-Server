package mn.suld.api.identity;

/** How the server establishes player identity. Determined once at startup. */
public enum AuthMode {
    /** {@code online-mode=true}: the server verifies every login with Mojang/Microsoft. */
    ONLINE(true, false),
    /** Behind a Velocity proxy with modern (HMAC-signed) forwarding and an online-mode proxy. */
    VELOCITY_FORWARDED(true, false),
    /**
     * Behind an offline-mode Velocity proxy with modern forwarding and AuthMe on both sides: premium accounts are
     * verified by AuthMe-Velocity, every other name by its AuthMe password. Every player has the offline UUID of
     * their name, so the UUID still names one account; but a joined player may not be logged in yet (see
     * {@link #deferredAuthentication()}).
     */
    HYBRID_AUTHME(true, true),
    /** Anything else (offline mode, legacy BungeeCord forwarding): identities can be spoofed. */
    INSECURE(false, false);

    private final boolean verified;
    private final boolean deferred;

    AuthMode(boolean verified, boolean deferred) {
        this.verified = verified;
        this.deferred = deferred;
    }

    /** Whether identities in this mode are verified (cryptographically, or by AuthMe before anything is granted). */
    public boolean verified() {
        return verified;
    }

    /**
     * Whether a player in the world may still have to log in: true only for {@link #HYBRID_AUTHME}, where AuthMe
     * holds a joined player in its login state until the password (or the proxy's premium check) is accepted.
     * Nothing that pays, gives, opens a screen or grants a permission may run before that.
     */
    public boolean deferredAuthentication() {
        return deferred;
    }
}
