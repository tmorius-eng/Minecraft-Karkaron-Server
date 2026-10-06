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
import mn.suld.plugin.combat.CombatListener;
import mn.suld.plugin.event.BukkitEventDispatcher;
import mn.suld.plugin.gui.ClassSelectionGui;
import mn.suld.plugin.hud.HudService;
import mn.suld.plugin.item.ItemFactory;
import mn.suld.plugin.mob.MobService;
import mn.suld.plugin.persistence.DataSourceFactory;
import mn.suld.plugin.persistence.JdbcProfileRepository;
import mn.suld.plugin.persistence.SchemaMigrator;
import mn.suld.plugin.persistence.SqlDialect;
import mn.suld.plugin.profile.DefaultProfileService;
import mn.suld.plugin.quest.QuestService;
import mn.suld.plugin.resourcepack.ResourcePackService;
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

        // Vertical Slice 1 services.
        this.itemFactory = new ItemFactory(plugin);
        this.hudService = new HudService(progressionService);
        this.questService = new QuestService(progressionService);
        this.mobService = new MobService(plugin);
        this.resourcePackService = new ResourcePackService(plugin, config.resourcePack());
        this.classSelectionGui = new ClassSelectionGui(plugin, this, hudService, questService, itemFactory);
        this.combatListener = new CombatListener(this, mobService, questService, hudService, itemFactory);
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

    /** Flush analytics, stop the IO pool, and close the connection pool. */
    public void close() {
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
