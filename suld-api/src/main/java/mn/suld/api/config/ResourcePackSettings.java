package mn.suld.api.config;

/**
 * Resource-pack delivery settings.
 *
 * @param enabled  whether the pack is sent to joining players
 * @param url      download URL of the hosted SÜLD pack ZIP
 * @param sha1     hex SHA-1 of the ZIP (empty = skip hash validation)
 * @param required whether players must accept the pack to play
 * @param prompt   message shown on the acceptance screen
 */
public record ResourcePackSettings(boolean enabled, String url, String sha1, boolean required, String prompt) {

    public static ResourcePackSettings defaults() {
        return new ResourcePackSettings(false, "", "", false,
                "SÜLD-ийн дүрс багцыг татаж авна уу (Download the SÜLD resource pack)");
    }
}
