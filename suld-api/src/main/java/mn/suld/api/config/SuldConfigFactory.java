package mn.suld.api.config;

/**
 * Builds a {@link SuldConfig} from a {@link ConfigView}, applying defaults for
 * anything missing. This is the single place config keys are mapped, so the
 * schema is documented in exactly one spot and is unit-tested with
 * {@link MapConfigView}.
 */
public final class SuldConfigFactory {

    private SuldConfigFactory() {
    }

    public static SuldConfig load(ConfigView view) {
        if (view == null) {
            return SuldConfig.defaults();
        }
        SuldConfig d = SuldConfig.defaults();

        String locale = view.getString("locale", d.locale());

        ProgressionSettings progression = new ProgressionSettings(
                view.getInt("progression.max-level", d.progression().maxLevel()),
                view.getDouble("progression.curve.base", d.progression().base()),
                view.getDouble("progression.curve.exponent", d.progression().exponent()));

        DatabaseSettings dbd = d.database();
        StorageType type = StorageType.byId(view.getString("database.type", dbd.type().name()))
                .orElse(dbd.type());
        DatabaseSettings database = new DatabaseSettings(
                type,
                view.getString("database.host", dbd.host()),
                view.getInt("database.port", dbd.port()),
                view.getString("database.database", dbd.database()),
                view.getString("database.username", dbd.username()),
                view.getString("database.password", dbd.password()),
                view.getInt("database.pool-size", dbd.poolSize()),
                view.getLong("database.connection-timeout-ms", dbd.connectionTimeoutMs()),
                view.getBoolean("database.use-ssl", dbd.useSsl()));

        DeathSettings dd = d.death();
        DeathSettings death = new DeathSettings(
                view.getBoolean("death.enabled", dd.enabled()),
                view.getInt("death.soul-state-seconds", dd.soulStateSeconds()),
                view.getDouble("death.exp-loss-fraction", dd.expLossFraction()),
                view.getDouble("death.durability-damage-fraction", dd.durabilityDamageFraction()),
                view.getBoolean("death.drop-loot", dd.dropLoot()),
                view.getDouble("death.loot-loss-fraction", dd.lootLossFraction()),
                view.getBoolean("death.allow-free-revive", dd.allowFreeRevive()));

        AnalyticsSettings ad = d.analytics();
        AnalyticsSettings.Sink sink;
        try {
            sink = AnalyticsSettings.Sink.valueOf(
                    view.getString("analytics.sink", ad.sink().name()).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sink = ad.sink();
        }
        AnalyticsSettings analytics = new AnalyticsSettings(
                view.getBoolean("analytics.enabled", ad.enabled()),
                view.getInt("analytics.flush-interval-seconds", ad.flushIntervalSeconds()),
                sink);

        ResourcePackSettings rpd = d.resourcePack();
        ResourcePackSettings resourcePack = new ResourcePackSettings(
                view.getBoolean("resource-pack.enabled", rpd.enabled()),
                view.getString("resource-pack.url", rpd.url()),
                view.getString("resource-pack.sha1", rpd.sha1()),
                view.getBoolean("resource-pack.required", rpd.required()),
                view.getString("resource-pack.prompt", rpd.prompt()));

        SocialSettings sd = d.social();
        SocialSettings social = new SocialSettings(
                view.getLong("clans.create-cost", sd.clanCreateCost()),
                view.getBoolean("world-events.enabled", sd.worldEventsEnabled()),
                view.getInt("world-events.interval-minutes", sd.worldEventIntervalMin()),
                view.getInt("world-events.min-players", sd.worldEventMinPlayers()));

        AuthSettings authd = d.auth();
        AuthSettings auth = new AuthSettings(
                view.getBoolean("auth.allow-insecure-offline-dev-mode", authd.allowInsecureOfflineDevMode()),
                view.getInt("auth.profile-load-timeout-seconds", authd.profileLoadTimeoutSeconds()),
                view.getInt("auth.pending-session-ttl-seconds", authd.pendingSessionTtlSeconds()));

        RelicSettings rd = d.relics();
        RelicSettings relics = new RelicSettings(
                view.getBoolean("relics.enabled", rd.enabled()),
                view.getInt("relics.auto-place-min-radius", rd.autoPlaceMinRadius()),
                view.getInt("relics.auto-place-max-radius", rd.autoPlaceMaxRadius()),
                view.getInt("relics.offline-return-hours", rd.offlineReturnHours()),
                view.getInt("relics.ritual-seconds", rd.ritualSeconds()),
                view.getInt("relics.hint-cooldown-seconds", rd.hintCooldownSeconds()));

        WorldSettings wd = d.world();
        WorldSettings world = new WorldSettings(
                view.getBoolean("world.kharkhorum-auto-build", wd.autoBuildKharkhorum()),
                view.getInt("world.build-blocks-per-tick", wd.buildBlocksPerTick()),
                view.getBoolean("world.region-spawning", wd.regionSpawning()),
                view.getInt("world.region-mobs-per-player", wd.regionMobsPerPlayer()));

        return new SuldConfig(locale, progression, database, death, analytics, resourcePack, social, auth, relics, world);
    }
}
