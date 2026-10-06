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
    private final mn.suld.plugin.style.StyleService styleService;
    private final ExecutorService ioExecutor;
    private final HikariDataSource dataSource; // null in MEMORY mode
    private final ProfileRepository repository;
    private final DefaultProfileService profileService;
    private final ProgressionService progressionService;
    private final AnalyticsSink analytics;
    private final EventDispatcher events;
    private final ItemFactory itemFactory;
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
        this.config = config;
        this.events = new BukkitEventDispatcher(plugin);
        this.analytics = config.analytics().enabled()
                ? new LoggingAnalyticsSink(plugin.getLogger())
                : AnalyticsSink.NOOP;

        ProgressionEngine engine = new ProgressionEngine(config.progression().toCurve());
        this.progressionService = new DefaultProgressionService(engine, events, analytics);

        int poolSize = Math.max(2, config.database().poolSize());
        this.ioExecutor = Executors.newFixedThreadPool(poolSize, daemonThreadFactory());

        switch (config.database().type()) {
            case MEMORY -> {
                this.dataSource = null;
                this.repository = new InMemoryProfileRepository();
                plugin.getLogger().warning("SULD storage is MEMORY — data is volatile and not persisted.");
            }
            case MYSQL, POSTGRESQL -> {
                SqlDialect dialect = SqlDialect.forStorage(config.database().type());
                this.dataSource = DataSourceFactory.create(config.database());
                int applied = new SchemaMigrator(dataSource, dialect).migrate();
                plugin.getLogger().info("SULD storage ready (" + dialect + "), migrations applied: " + applied);
                this.repository = new JdbcProfileRepository(dataSource, dialect, ioExecutor);
            }
            default -> throw new IllegalStateException("Unhandled storage type: " + config.database().type());
        }

        this.profileService = new DefaultProfileService(repository);

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
        this.hudService = new HudService(progressionService);
        this.questService = new QuestService(progressionService);
        this.mobService = new MobService(plugin);
        this.resourcePackService = new ResourcePackService(plugin, config.resourcePack());
        this.classSelectionGui = new ClassSelectionGui(plugin, this, hudService, questService, itemFactory);
        this.combatListener = new CombatListener(this, mobService, questService, hudService, itemFactory);

        // Vertical Slice 2 services.
        this.partyService = new PartyService();
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
    }

    public mn.suld.plugin.style.StyleService styles() {
        return styleService;
    }

    public SuldConfig config() {
        return config;
    }

    public DefaultProfileService profiles() {
        return profileService;
    }

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

    /** Flush analytics, stop the IO pool, and close the connection pool. */
    public void close() {
        styleService.stop();
        styleExecutor.shutdown();
        try {
            styleExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        worldEventService.shutdown();
        dungeonService.shutdown();
        clanService.shutdown();
        clanExecutor.shutdown();
        relicExecutor.shutdown();
        try {
            relicExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        try {
            clanExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        try {
            analytics.flush().get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // best-effort flush on shutdown
        }
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (dataSource != null) {
            dataSource.close();
        }
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
