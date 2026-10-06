package mn.suld.api.config;

/**
 * Authentication settings.
 *
 * @param allowInsecureOfflineDevMode LOCAL DEVELOPMENT ONLY. When the server is not verifying
 *                                    accounts (offline mode), SÜLD refuses every login unless
 *                                    this is true. Never enable on a public server.
 * @param profileLoadTimeoutSeconds   max wait for the profile during pre-login before the
 *                                    login is refused (never admitted without a profile)
 * @param pendingSessionTtlSeconds    a pre-login whose client never joins is dropped after this
 */
public record AuthSettings(boolean allowInsecureOfflineDevMode, int profileLoadTimeoutSeconds,
                           int pendingSessionTtlSeconds) {

    public AuthSettings {
        profileLoadTimeoutSeconds = Math.max(2, Math.min(30, profileLoadTimeoutSeconds));
        pendingSessionTtlSeconds = Math.max(15, Math.min(600, pendingSessionTtlSeconds));
    }

    public static AuthSettings defaults() {
        return new AuthSettings(false, 10, 60);
    }
}
