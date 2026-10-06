package mn.suld.api.config;

/**
 * Aggregate root of all SULD configuration. Immutable; reloaded by replacing the
 * whole instance so readers always see a consistent snapshot.
 */
public record SuldConfig(
        String locale,
        ProgressionSettings progression,
        DatabaseSettings database,
        DeathSettings death,
        AnalyticsSettings analytics,
        ResourcePackSettings resourcePack,
        SocialSettings social,
        AuthSettings auth) {

    public static SuldConfig defaults() {
        return new SuldConfig(
                "mn",
                ProgressionSettings.defaults(),
                DatabaseSettings.defaults(),
                DeathSettings.defaults(),
                AnalyticsSettings.defaults(),
                ResourcePackSettings.defaults(),
                SocialSettings.defaults(),
                AuthSettings.defaults());
    }
}
