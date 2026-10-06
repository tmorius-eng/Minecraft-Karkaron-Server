package mn.suld.api.config;

/**
 * Resource-pack delivery settings.
 *
 * @param enabled    whether the pack is sent to joining players
 * @param url        download URL of an externally hosted SÜLD pack ZIP (empty = self-host)
 * @param sha1       hex SHA-1 of that ZIP (empty = skip hash validation); self-hosting computes its own
 * @param required   whether players must accept the pack to play
 * @param prompt     message shown on the acceptance screen
 * @param selfHost   when no url is set, serve the pack bundled in the plugin jar over HTTP
 * @param hostBind   address the built-in pack server listens on
 * @param hostPort   port of the built-in pack server
 * @param publicUrl  base URL players reach the built-in server at (empty = the host they connected with)
 */
public record ResourcePackSettings(boolean enabled, String url, String sha1, boolean required, String prompt,
                                   boolean selfHost, String hostBind, int hostPort, String publicUrl) {

    public ResourcePackSettings {
        hostPort = Math.max(1, Math.min(65535, hostPort));
    }

    public static ResourcePackSettings defaults() {
        return new ResourcePackSettings(true, "", "", false,
                "SÜLD-ийн дүрс багцыг татаж авна уу (Download the SÜLD resource pack)",
                true, "0.0.0.0", 8164, "");
    }
}
