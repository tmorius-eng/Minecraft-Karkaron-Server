package mn.suld.plugin;

import com.zaxxer.hikari.HikariDataSource;
import mn.suld.api.analytics.AnalyticsSink;
import mn.suld.api.config.SuldConfig;
import mn.suld.api.event.EventDispatcher;
import mn.suld.api.persistence.InMemoryProfileRepository;
import mn.suld.api.persistence.ProfileRepository;
import mn.suld.api.progression.ProgressionEngine;
import mn.suld.api.service.DefaultProgressionService;
import mn.suld.api.service.ProgressionService;
import mn.suld.plugin.analytics.LoggingAnalyticsSink;
import mn.suld.plugin.clan.ClanService;
import mn.suld.plugin.relic.RelicItems;
import mn.suld.plugin.relic.RelicService;
import mn.suld.plugin.progression.ProgressionBoosts;
import mn.suld.plugin.persistence.JdbcRelicRepository;
import mn.suld.api.persistence.InMemoryRelicRepository;
import mn.suld.api.persistence.RelicRepository;
import mn.suld.plugin.auth.AuthenticationService;
import mn.suld.plugin.audit.JdbcAuditLog;
import mn.suld.plugin.audit.LoggingAuditLog;
import mn.suld.api.audit.AuditLog;
import mn.suld.api.identity.AuthPolicy;
import mn.suld.plugin.combat.CombatListener;
import mn.suld.plugin.dungeon.BossService;
import mn.suld.plugin.dungeon.DungeonService;
import mn.suld.plugin.party.PartyService;
import mn.suld.plugin.event.BukkitEventDispatcher;
import mn.suld.plugin.gui.ClassSelectionGui;
import mn.suld.plugin.hud.HudService;
import mn.suld.plugin.item.ItemFactory;
import mn.suld.plugin.mob.MobService;
import mn.suld.plugin.persistence.DataSourceFactory;
import mn.suld.plugin.persistence.JdbcClanRepository;
import mn.suld.plugin.persistence.JdbcProfileRepository;
import mn.suld.plugin.persistence.SchemaMigrator;
import mn.suld.plugin.persistence.SqlDialect;
import mn.suld.plugin.profile.DefaultProfileService;
import mn.suld.plugin.quest.QuestService;
import mn.suld.plugin.resourcepack.ResourcePackService;
import mn.suld.plugin.worldevent.WorldEventService;
import mn.suld.api.persistence.ClanRepository;
import mn.suld.api.persistence.InMemoryClanRepository;
import org.bukkit.plugin.Plugin;

import mn.suld.plugin.persistence.PoolShutdown;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service container: constructs and owns every SULD service, wired together
 * once. Holding the single reference here (on the plugin) avoids static global
 * state and makes the dependency graph explicit and testable.
 */
public final class SuldServices {

    private final SuldConfig config;
    private final java.util.concurrent.ExecutorService styleExecutor;
    private final java.util.concurrent.ExecutorService deathExecutor;
    private final mn.suld.api.persistence.DeathRepository deathRepository;
    private final mn.suld.plugin.style.StyleService styleService;
    private final mn.suld.plugin.item.ClassWeapons classWeapons;
    private final ExecutorService ioExecutor;
    private final java.util.logging.Logger shutdownLogger;
    /** The server's calendar zone ({@code daily.timezone}): daily resets, death times, chat times, ovoo days. */
    private final java.time.ZoneId zone;
    private final HikariDataSource dataSource; // null in MEMORY mode
    private final ProfileRepository repository;
    private final DefaultProfileService profileService;
    private final ProgressionService progressionService;
    private final AnalyticsSink analytics;
    private final EventDispatcher events;
    private final ItemFactory itemFactory;
    private final mn.suld.plugin.item.SoulboundGuard soulboundGuard;
    private final HudService hudService;
    private final QuestService questService;
    private final MobService mobService;
    private final ResourcePackService resourcePackService;
    private final ClassSelectionGui classSelectionGui;
    private final CombatListener combatListener;
    private final PartyService partyService;
    private final BossService bossService;
    private final DungeonService dungeonService;
    private final ExecutorService clanExecutor;
    private final ExecutorService relicExecutor;
    private final RelicService relicService;
    private final ProgressionBoosts boosts;
    private final AuditLog auditLog;
    private final AuthenticationService authService;
    private final ClanService clanService;
    private final WorldEventService worldEventService;

    public SuldServices(Plugin plugin, SuldConfig config) {
        // OPS-005: what this constructor opens is closed again if a later step fails (no leaked pool, no held H2 lock)
        java.util.List<AutoCloseable> opened = new java.util.ArrayList<>();
        try {
            this.config = config;
            this.events = new BukkitEventDispatcher(plugin);
            int poolSize = Math.max(2, config.database().poolSize());
            this.ioExecutor = Executors.newFixedThreadPool(poolSize, daemonThreadFactory());
            opened.add(ioExecutor::shutdownNow);
            this.shutdownLogger = plugin.getLogger();
            java.time.ZoneId z;
            String zoneId = plugin.getConfig().getString("daily.timezone", "Asia/Ulaanbaatar");
            try {
                z = java.time.ZoneId.of(zoneId);
            } catch (java.time.DateTimeException ex) {
                plugin.getLogger().warning("daily.timezone '" + zoneId + "' is not a valid time zone; using Asia/Ulaanbaatar");
                z = java.time.ZoneId.of("Asia/Ulaanbaatar");
            }
            this.zone = z;

            switch (config.database().type()) {
                case MEMORY -> {
                    this.dataSource = null;
                    this.repository = new InMemoryProfileRepository();
                    plugin.getLogger().warning("SULD storage is MEMORY — data is volatile and not persisted.");
                }
                case H2, MYSQL, POSTGRESQL -> {
                    SqlDialect dialect = SqlDialect.forStorage(config.database().type());
                    this.dataSource = DataSourceFactory.create(config.database(), plugin.getDataFolder().toPath());
                    if (dataSource instanceof AutoCloseable c) opened.add(c::close);
                    int applied = new SchemaMigrator(dataSource, dialect).migrate();
                    plugin.getLogger().info("SULD storage ready (" + config.database().type() + (config.database().type().name().equals(dialect.name()) ? "" : ", " + dialect + " SQL") + "), migrations applied: " + applied);
                    this.repository = new JdbcProfileRepository(dataSource, dialect, ioExecutor);
                }
                default -> throw new IllegalStateException("Unhandled storage type: " + config.database().type());
            }

            this.profileService = new DefaultProfileService(repository);

            // analytics: the database sink needs the storage above (docs/ANALYTICS.md); otherwise the log sink
            if (!config.analytics().enabled()) {
                this.analytics = AnalyticsSink.NOOP;
            } else if (config.analytics().sink() == mn.suld.api.config.AnalyticsSettings.Sink.DATABASE && dataSource != null) {
                this.analytics = new mn.suld.plugin.analytics.JdbcAnalyticsSink(dataSource, SqlDialect.forStorage(config.database().type()),
                        ioExecutor, plugin.getLogger(), config.analytics().skipTypes(), config.analytics().queueLimit());
            } else {
                if (config.analytics().sink() == mn.suld.api.config.AnalyticsSettings.Sink.DATABASE) {
                    plugin.getLogger().warning("analytics.sink: database needs MySQL/PostgreSQL storage; using the log sink.");
                }
                this.analytics = new LoggingAnalyticsSink(plugin.getLogger());
            }
            ProgressionEngine engine = new ProgressionEngine(config.progression().toCurve());
            this.curve = engine.curve();
            this.progressionService = new DefaultProgressionService(engine, events, analytics);

            // Authentication + audit (identity = authenticated UUID; fail closed when unverified).
            this.auditLog = dataSource == null
                    ? new LoggingAuditLog(plugin.getLogger())
                    : new JdbcAuditLog(dataSource, ioExecutor, plugin.getLogger());
            java.io.File serverRoot = plugin.getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
            AuthPolicy policy = new AuthPolicy(
                    AuthenticationService.detectMode(plugin.getServer(), serverRoot),
                    config.auth().allowInsecureOfflineDevMode());
            this.authService = new AuthenticationService(plugin, this, policy, config.auth(), auditLog);

            // Clan writes must be applied in order -> one dedicated single-threaded writer.
            this.clanExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "suld-clan-io");
                thread.setDaemon(true);
                return thread;
            });
            ClanRepository clanRepository = dataSource == null
                    ? new InMemoryClanRepository()
                    : new JdbcClanRepository(dataSource, SqlDialect.forStorage(config.database().type()),
                            clanExecutor, plugin.getLogger());

            // Vertical Slice 1 services.
            this.itemFactory = new ItemFactory(plugin);
            this.soulboundGuard = new mn.suld.plugin.item.SoulboundGuard(itemFactory);
            this.hudService = new HudService(progressionService);
            this.questService = new QuestService(progressionService);
            this.questService.items(itemFactory);
            this.questService.analytics = analytics::record;
            this.mobService = new MobService(plugin);
            this.resourcePackService = new ResourcePackService(plugin, config.resourcePack());
            this.resourcePackService.analytics = analytics::record;
            this.classSelectionGui = new ClassSelectionGui(plugin, this, hudService, questService, itemFactory);
            this.combatListener = new CombatListener(this, mobService, questService, hudService, itemFactory);

            // Vertical Slice 2 services.
            this.partyService = new PartyService();
            this.partyService.soul = id -> isSoul.test(id); // isSoul is set later by the death system
            this.partyService.acceptsInvites = id -> styles().setting(id, mn.suld.api.style.PlayerSetting.PARTY_INVITES);
            this.partyService.classOf = id -> profileService.cached(id).flatMap(pr -> pr.playerClass()).orElse(null);
            this.bossService = new BossService();
            this.dungeonService = new DungeonService(plugin, this, mobService, partyService, bossService);
            this.hudService.addStatusLine(id -> dungeonService.statusLine(id).map(s -> "§7Агуй: §c" + s));

            // Vertical Slice 3 services.
            this.clanService = new ClanService(plugin, this, clanRepository, config.social());
            int clans = clanService.load();
            plugin.getLogger().info("SULD clans loaded: " + clans);
            this.worldEventService = new WorldEventService(plugin, this, mobService, config.social());
            this.hudService.addStatusLine(clanService::hudLine);
            this.hudService.addStatusLine(worldEventService::hudLine);

            // Vertical Slice 4: world-unique relics. Ordered writer; the DB CAS is the real guarantee.
            this.relicExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "suld-relic-io");
                thread.setDaemon(true);
                return thread;
            });
            RelicRepository relicRepository = dataSource == null
                    ? new InMemoryRelicRepository()
                    : new JdbcRelicRepository(dataSource, SqlDialect.forStorage(config.database().type()), relicExecutor);
            this.relicService = new RelicService(plugin, this, relicRepository, config.relics(),
                    new RelicItems(plugin, itemFactory));
            plugin.getLogger().info("SULD relics loaded: " + relicService.load());
            this.hudService.addStatusLine(relicService::hudLine);
            this.boosts = new ProgressionBoosts(this);

            // Player style (ranks, cosmetics, credits): one ordered writer so saves of a player never reorder.
            this.styleExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "suld-style-io");
                thread.setDaemon(true);
                return thread;
            });
            mn.suld.api.persistence.StyleRepository styleRepository = dataSource == null
                    ? new mn.suld.api.persistence.InMemoryStyleRepository()
                    : new mn.suld.plugin.persistence.JdbcStyleRepository(dataSource,
                            SqlDialect.forStorage(config.database().type()), styleExecutor);
            this.styleService = new mn.suld.plugin.style.StyleService(plugin, this, styleRepository);
            // Hardcore death (V12): one ordered writer, so a player's death and recovery never reorder.
            this.deathExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "suld-death-io");
                thread.setDaemon(true);
                return thread;
            });
            this.deathRepository = dataSource == null
                    ? new mn.suld.api.persistence.InMemoryDeathRepository()
                    : new mn.suld.plugin.persistence.JdbcDeathRepository(dataSource,
                            SqlDialect.forStorage(config.database().type()), deathExecutor);
            this.classWeapons = new mn.suld.plugin.item.ClassWeapons(plugin, this);
            // Progress marks (V17): the database mirror of the player file's clears / sites / ovoo days. Upserts, so the
            // shared pool is fine (order does not matter).
            this.marks = new mn.suld.plugin.progress.PlayerMarks(plugin, dataSource == null
                    ? new mn.suld.api.persistence.InMemoryMarksRepository()
                    : new mn.suld.plugin.persistence.JdbcMarksRepository(dataSource, SqlDialect.forStorage(config.database().type()), ioExecutor));
            this.duels = new mn.suld.plugin.combat.DuelService(plugin, this, config.pvp());

            // Их Хуралдай, the week's campaign and the chronicle (docs/KHURALDAI.md): one ordered writer, so a vote's close
            // and a campaign's resolution never reorder; contributions are additive and the chronicle is append-only.
            this.campaignExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "suld-campaign-io");
                thread.setDaemon(true);
                return thread;
            });
            mn.suld.api.persistence.CampaignRepository campaignRepository = dataSource == null
                    ? new mn.suld.api.persistence.InMemoryCampaignRepository()
                    : new mn.suld.plugin.persistence.JdbcCampaignRepository(dataSource, SqlDialect.forStorage(config.database().type()), campaignExecutor);
            mn.suld.api.persistence.ChronicleRepository chronicleRepository = dataSource == null
                    ? new mn.suld.api.persistence.InMemoryChronicleRepository()
                    : new mn.suld.plugin.persistence.JdbcChronicleRepository(dataSource, SqlDialect.forStorage(config.database().type()), campaignExecutor);
            mn.suld.api.config.CampaignSettings cs = config.campaign();
            this.weeklyCycle = new mn.suld.api.campaign.WeeklyCycle(zone, cs.campaignStarts(), cs.campaignEnds(), cs.voteOpens(), cs.voteCloses());
            this.chronicleService = new mn.suld.plugin.chronicle.ChronicleService(plugin, this, chronicleRepository, config.chronicle(),
                    () -> weeklyCycle.cycleId(java.time.Instant.now()));
            this.khuraldaiService = new mn.suld.plugin.campaign.KhuraldaiService(plugin, this, campaignRepository, cs, weeklyCycle, chronicleService);
            mn.suld.plugin.campaign.CampaignEffects effects = new mn.suld.plugin.campaign.CampaignEffects(plugin, this, campaignRepository, cs, weeklyCycle);
            this.campaignService = new mn.suld.plugin.campaign.CampaignService(plugin, this, campaignRepository, cs, weeklyCycle, khuraldaiService,
                    chronicleService, effects);
            this.hudService.addStatusLine(campaignService::hudLine);
            // Heroic keys (V24, docs/HEROIC_DUNGEONS.md): every write only raises a key or appends a run, so the order of
            // the writes does not matter and the shared pool is fine
            this.heroic = new mn.suld.plugin.dungeon.heroic.HeroicDungeonService(plugin, this, dataSource == null
                    ? new mn.suld.api.persistence.InMemoryHeroicRepository()
                    : new mn.suld.plugin.persistence.JdbcHeroicRepository(dataSource, SqlDialect.forStorage(config.database().type()), ioExecutor));
            // Weapon mastery (V25, docs/MASTERY_AND_PROFESSIONS.md): writes only add points, so the shared pool is fine
            this.weaponMastery = new mn.suld.plugin.mastery.WeaponMasteryService(plugin, this, dataSource == null
                    ? new mn.suld.api.persistence.InMemoryWeaponMasteryRepository()
                    : new mn.suld.plugin.persistence.JdbcWeaponMasteryRepository(dataSource, SqlDialect.forStorage(config.database().type()), ioExecutor));
            // the crafts (V27, docs/MASTERY_AND_PROFESSIONS.md): writes only add points, so the shared pool is fine
            this.professions = new mn.suld.plugin.mastery.ProfessionService(plugin, this, dataSource == null
                    ? new mn.suld.api.persistence.InMemoryProfessionRepository()
                    : new mn.suld.plugin.persistence.JdbcProfessionRepository(dataSource, SqlDialect.forStorage(config.database().type()), ioExecutor));
            // achievements and the season's pass (V28, docs/ACHIEVEMENTS_AND_PASS.md): every write commutes, the pool is fine
            this.achievements = new mn.suld.plugin.achievement.AchievementService(plugin, this, dataSource == null
                    ? new mn.suld.api.persistence.InMemoryAchievementRepository()
                    : new mn.suld.plugin.persistence.JdbcAchievementRepository(dataSource, SqlDialect.forStorage(config.database().type()), ioExecutor));
            // the Bonum credit store (V29, docs/PAYMENT_ARCHITECTURE.md): an intent moves only by compare-and-set and a grant
            // is keyed by the intent, so the shared pool is fine; off unless payment.bonum.enabled and its secrets are set
            this.payments = new mn.suld.plugin.payment.PaymentService(plugin, this, dataSource == null
                    ? new mn.suld.api.persistence.InMemoryPaymentRepository()
                    : new mn.suld.plugin.persistence.JdbcPaymentRepository(dataSource, SqlDialect.forStorage(config.database().type()), ioExecutor),
                    ioExecutor);
            this.mapPins = dataSource == null
                    ? new mn.suld.api.persistence.InMemoryMapPinRepository()
                    : new mn.suld.plugin.persistence.JdbcMapPinRepository(dataSource, SqlDialect.forStorage(config.database().type()), ioExecutor);
        } catch (RuntimeException | Error ex) {
            for (int i = opened.size() - 1; i >= 0; i--) {
                try {
                    opened.get(i).close();
                } catch (Exception closeFailure) {
                    ex.addSuppressed(closeFailure);
                }
            }
            throw ex;
        }
    }

    private final ExecutorService campaignExecutor;
    /** Heroic keys, affixes and the clock of a heroic run (docs/HEROIC_DUNGEONS.md). */
    public final mn.suld.plugin.dungeon.heroic.HeroicDungeonService heroic;
    /** Зэвсгийн ур чадвар: the five weapon tracks (docs/MASTERY_AND_PROFESSIONS.md). */
    public final mn.suld.plugin.mastery.WeaponMasteryService weaponMastery;
    /** Мэргэжил: the four crafts, their nodes and pens in the wild (docs/MASTERY_AND_PROFESSIONS.md). */
    public final mn.suld.plugin.mastery.ProfessionService professions;
    /** Achievements and the season's pass «Хуралдайн Зам» (docs/ACHIEVEMENTS_AND_PASS.md). */
    public final mn.suld.plugin.achievement.AchievementService achievements;
    /** The credit store paid through Bonum (docs/PAYMENT_ARCHITECTURE.md); off by default. */
    public final mn.suld.plugin.payment.PaymentService payments;
    private final mn.suld.api.campaign.WeeklyCycle weeklyCycle;
    private final mn.suld.plugin.chronicle.ChronicleService chronicleService;
    private final mn.suld.plugin.campaign.KhuraldaiService khuraldaiService;
    private final mn.suld.plugin.campaign.CampaignService campaignService;
    /** The 3D props (set by SuldPlugin once they are placed; campaign effects add dynamic ones). */
    public volatile mn.suld.plugin.prop.PropService props;
    /** The city NPCs (set by SuldPlugin; campaign effects add dynamic ones). */
    public volatile mn.suld.plugin.npc.NpcService npcs;

    /** The weekly calendar of Их Хуралдай. */
    public mn.suld.api.campaign.WeeklyCycle weeklyCycle() {
        return weeklyCycle;
    }

    public mn.suld.plugin.chronicle.ChronicleService chronicle() {
        return chronicleService;
    }

    public mn.suld.plugin.campaign.KhuraldaiService khuraldai() {
        return khuraldaiService;
    }

    public mn.suld.plugin.campaign.CampaignService campaigns() {
        return campaignService;
    }

    private final mn.suld.plugin.progress.PlayerMarks marks;
    private final mn.suld.plugin.combat.DuelService duels;

    /** The PvP policy: consented duels and fighting grounds (docs/GAME_DESIGN.md «PvP»). */
    public mn.suld.plugin.combat.DuelService duels() {
        return duels;
    }

    /** Per-player progress marks (dungeon clears, sites, ovoo days) mirrored to the database. */
    public mn.suld.plugin.progress.PlayerMarks marks() {
        return marks;
    }

    /** The shared "never leaves the owner" guard of soulbound items. */
    public mn.suld.plugin.item.SoulboundGuard soulbound() {
        return soulboundGuard;
    }

    public mn.suld.api.persistence.DeathRepository deathRepository() {
        return deathRepository;
    }

    public mn.suld.plugin.item.ClassWeapons classWeapons() {
        return classWeapons;
    }

    public mn.suld.plugin.style.StyleService styles() {
        return styleService;
    }

    /** The one time zone every clock in the game uses (config {@code daily.timezone}). */
    public java.time.ZoneId zone() {
        return zone;
    }

    public SuldConfig config() {
        return config;
    }

    public DefaultProfileService profiles() {
        return profileService;
    }

    /** The live level curve (config.yml progression.curve). */
    public mn.suld.api.progression.LevelCurve curve() {
        return curve;
    }

    private final mn.suld.api.progression.LevelCurve curve;

    public ProgressionService progression() {
        return progressionService;
    }

    public AnalyticsSink analytics() {
        return analytics;
    }

    public EventDispatcher events() {
        return events;
    }

    public ItemFactory items() {
        return itemFactory;
    }

    private volatile mn.suld.api.zone.CityZone city = mn.suld.api.zone.CityZone.NONE;

    /** Kharkhorum's extent (set once the WorldBuilder knows where the city stands). */
    public mn.suld.api.zone.CityZone city() {
        return city;
    }

    public void city(mn.suld.api.zone.CityZone zone) {
        this.city = zone == null ? mn.suld.api.zone.CityZone.NONE : zone;
    }

    public boolean inCity(org.bukkit.Location l) {
        return l != null && l.getWorld() != null && city.contains(l.getWorld().getName(), l.getBlockX(), l.getBlockZ());
    }

    public HudService hud() {
        return hudService;
    }

    public QuestService quests() {
        return questService;
    }

    /** Filled by SuldPlugin: whether a player is currently a soul (hardcore death state). */
    public volatile java.util.function.Predicate<java.util.UUID> isSoul = id -> false;
    /** A soul fighting its «Тэнгэрийн сорил» (set by the death system): it may shoot and cast at its own wolves. */
    public volatile java.util.function.Predicate<java.util.UUID> inDeathTrial = id -> false;
    /** SÜLD-made movement windows the anticheat bridge forgives (dashes, leaps, the sky tree seat). */
    public final mn.suld.plugin.anticheat.MovementExemptions movement = new mn.suld.plugin.anticheat.MovementExemptions();
    /** Multiplier of a player's class-gear stats (below 1 while a death wound is open; set by the DeathService). */
    public volatile java.util.function.ToDoubleFunction<java.util.UUID> woundFactor = id -> 1.0;
    /** Filled by SuldPlugin: send a player's steppe horse away (dungeon entry). */
    public volatile java.util.function.Consumer<java.util.UUID> dismissHorse = id -> { };
    /** Filled by SuldPlugin: id of the wild region at a player's feet, or null. */
    public volatile java.util.function.Function<org.bukkit.entity.Player, String> regionIdAt = p -> null;

    /** Filled by SuldPlugin: the item engine (catalog, generation, loot, validation). */
    public volatile mn.suld.plugin.item.ItemService itemService;
    /** Filled by SuldPlugin: the nine equipment slots of every player. */
    public volatile mn.suld.plugin.item.EquipmentService equipment;
    /** Aggregate totals of each online player's current session (docs/ANALYTICS.md); main thread. */
    private final java.util.Map<java.util.UUID, mn.suld.api.analytics.SessionTotals> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    /** The session totals of an online player (a throwaway instance for anyone else, so hooks never null-check). */
    public mn.suld.api.analytics.SessionTotals session(java.util.UUID player) {
        mn.suld.api.analytics.SessionTotals t = sessions.get(player);
        return t != null ? t : new mn.suld.api.analytics.SessionTotals(System.currentTimeMillis());
    }

    public void sessionStarted(java.util.UUID player) {
        sessions.put(player, new mn.suld.api.analytics.SessionTotals(System.currentTimeMillis()));
    }

    /** Removes and returns the session totals (null if none was open). */
    public mn.suld.api.analytics.SessionTotals sessionEnded(java.util.UUID player) {
        return sessions.remove(player);
    }

    /** The Display-Entity model renderer (null until the plugin enabled it). */
    public volatile mn.suld.plugin.model.ModelService models;

    /** ActivePlaytime (null until the plugin enabled it). */
    public volatile mn.suld.plugin.activity.ActivePlaytimeService activity;
    /** The class armour (null until the plugin enabled it). */
    public volatile mn.suld.plugin.item.ClassArmor classArmor;
    /** Class change and the academy (docs/CLASS_CHANGE.md); null until the plugin enabled it. */
    public volatile mn.suld.plugin.clazz.ClassChangeService classChange;
    public volatile mn.suld.plugin.clazz.ClassTrialService classTrial;
    /** «Дайчдын Дасгалын Талбай» (docs/TRAINING_GROUNDS.md). */
    public volatile mn.suld.plugin.training.TrainingService training;
    /** Тэнгэрийн Зэрэг window and rite (set by SuldPlugin). */
    public volatile mn.suld.plugin.gui.AscensionMenu ascension;
    /** The historic sites (set by SuldPlugin). */
    public volatile mn.suld.plugin.region.HistoricSiteService historicSites;
    /** Kharkhorum's monument to the «Мөнх Тэнгэрийн Элч» (Тэнгэрийн Зэрэг X), set by SuldPlugin. */
    public volatile mn.suld.plugin.chronicle.EnvoyMonument envoys;
    /** The areas' ovoos (set by SuldPlugin); the travel page points the compass at the nearest. */
    public volatile mn.suld.plugin.region.OvooService ovoos;
    private final mn.suld.api.persistence.MapPinRepository mapPins;

    /** Players' map pins (V26, docs/WORLD_MAP.md). */
    public mn.suld.api.persistence.MapPinRepository mapPins() {
        return mapPins;
    }

    /** «Сүлдийн Атлас»: the world map's real-block picture (docs/WORLD_MAP.md); null while map.enabled is false. */
    public volatile mn.suld.plugin.map.MapService map;
    /** The souls' beacons (set by SuldPlugin): the minimap and the atlas mark a standing one. */
    public volatile mn.suld.plugin.death.SoulBeaconService soulBeacons;
    /** World bosses (docs/WORLD_BOSSES.md); set once the historic sites are placed. */
    public volatile mn.suld.plugin.worldboss.WorldBossService worldBosses;

    /** The Дархан's order board (docs/CRAFTING.md); set once the item system is up. */
    public volatile mn.suld.plugin.item.CraftOrderService craftOrders;

    private volatile mn.suld.plugin.trade.Mailbox mailbox;

    /** SULD-F12: shared offline item mailbox so TradeService and CraftOrderService synchronize on one lock and stuck set. */
    public synchronized mn.suld.plugin.trade.Mailbox mailbox(Plugin plugin) {
        if (mailbox == null) mailbox = new mn.suld.plugin.trade.Mailbox(plugin);
        return mailbox;
    }

    private volatile mn.suld.plugin.item.CraftService crafting;

    /** The Дархан's recipes, shared by /item craft and the smith's window (docs/CRAFTING.md). */
    public mn.suld.plugin.item.CraftService crafting() {
        mn.suld.plugin.item.CraftService c = crafting;
        if (c == null) crafting = c = new mn.suld.plugin.item.CraftService(this);
        return c;
    }

    public mn.suld.plugin.item.ItemService itemService() {
        return itemService;
    }

    public mn.suld.plugin.item.EquipmentService equipment() {
        return equipment;
    }

    /** Filled by SuldPlugin once the skill tree has loaded; may be null very early in startup. */
    public volatile mn.suld.plugin.skill.SkillTreeService skillTree;

    public mn.suld.plugin.skill.SkillTreeService skillTree() {
        return skillTree;
    }

    public MobService mobs() {
        return mobService;
    }

    public ResourcePackService resourcePacks() {
        return resourcePackService;
    }

    public ClassSelectionGui classSelectionGui() {
        return classSelectionGui;
    }

    public CombatListener combatListener() {
        return combatListener;
    }

    public PartyService parties() {
        return partyService;
    }

    public BossService bosses() {
        return bossService;
    }

    public DungeonService dungeons() {
        return dungeonService;
    }

    public RelicService relics() {
        return relicService;
    }

    public ProgressionBoosts boosts() {
        return boosts;
    }

    /** Direct storage access for offline lookups (e.g. a relic bearer's last-seen time). */
    public ProfileRepository profileRepository() {
        return repository;
    }

    public AuthenticationService auth() {
        return authService;
    }

    public AuditLog audit() {
        return auditLog;
    }

    public ClanService clans() {
        return clanService;
    }

    public WorldEventService worldEvents() {
        return worldEventService;
    }

    /**
     * Shutdown with one deadline: the style, clan and analytics flushes run at the same time and are given what is
     * left of {@code budgetSeconds}; then every pool is drained against the same deadline ({@link PoolShutdown}),
     * anything still running is interrupted and logged, and the connection pool closes last. The worst case is the
     * budget, not the sum of six waits.
     */
    public void close(long budgetSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, budgetSeconds));
        java.util.logging.Logger log = shutdownLog();
        worldEventService.shutdown();
        dungeonService.shutdown();
        java.util.concurrent.CompletableFuture<Void> styles = styleService.stop();
        java.util.concurrent.CompletableFuture<Void> clans = clanService.shutdown();
        java.util.concurrent.CompletableFuture<Void> stats = analytics.flush();
        java.util.concurrent.CompletableFuture<Void> campaigns = campaignService.shutdown();
        java.util.concurrent.CompletableFuture<Void> mastery = weaponMastery.shutdown();
        java.util.concurrent.CompletableFuture<Void> crafts = professions.shutdown();
        java.util.concurrent.CompletableFuture<Void> deeds = achievements.shutdown();
        try {
            java.util.concurrent.CompletableFuture.allOf(styles, clans, stats, campaigns, mastery, crafts, deeds)
                    .get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warning("[shutdown] flushes incomplete within the budget: styles " + state(styles) + ", clans " + state(clans)
                    + ", analytics " + state(stats) + " (" + ex.getClass().getSimpleName() + ")");
        }
        java.util.LinkedHashMap<String, ExecutorService> pools = new java.util.LinkedHashMap<>();
        pools.put("style", styleExecutor);
        pools.put("death", deathExecutor);
        pools.put("relic", relicExecutor);
        pools.put("clan", clanExecutor);
        pools.put("campaign", campaignExecutor);
        pools.put("io", ioExecutor);
        PoolShutdown.drain(pools, deadline, log);
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static String state(java.util.concurrent.CompletableFuture<?> f) {
        return f.isCompletedExceptionally() ? "failed" : f.isDone() ? "done" : "pending";
    }

    private java.util.logging.Logger shutdownLog() {
        return shutdownLogger != null ? shutdownLogger : java.util.logging.Logger.getLogger("SULD");
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "suld-io-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
