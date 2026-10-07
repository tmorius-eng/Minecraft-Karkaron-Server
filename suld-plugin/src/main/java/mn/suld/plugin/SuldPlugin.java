package mn.suld.plugin;

import mn.suld.api.config.SuldConfig;
import mn.suld.api.config.SuldConfigFactory;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.command.ReviveCommand;
import mn.suld.plugin.command.SuldCommand;
import mn.suld.plugin.config.BukkitConfigView;
import mn.suld.plugin.listener.PlayerLifecycleListener;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * SULD plugin entry point. Keeps almost no logic itself: it loads config, builds
 * the {@link SuldServices} container, registers listeners/commands, schedules
 * maintenance tasks, and tears everything down cleanly on disable.
 */
public final class SuldPlugin extends JavaPlugin {

    private static final long TICKS_PER_SECOND = 20L;
    private static final long AUTOSAVE_SECONDS = 300L;

    private SuldServices services;
    private mn.suld.plugin.mount.HorseService horses;
    private mn.suld.plugin.branding.GuideBoards guide;
    private mn.suld.plugin.trade.TradeService trades;
    private mn.suld.plugin.style.CosmeticEffects effects;
    private mn.suld.plugin.death.DeathService deaths;
    private mn.suld.plugin.quest.QuestTracker tracker;
    private mn.suld.plugin.worldbuild.WorldBuildService worldBuild;
    private mn.suld.plugin.worldbuild.PregenService pregen;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        SuldConfig config;
        try {
            config = SuldConfigFactory.load(new BukkitConfigView(getConfig()));
        } catch (IllegalArgumentException ex) {
            getLogger().severe("Invalid configuration: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            this.services = new SuldServices(this, config);
        } catch (Exception ex) {
            getLogger().severe("Failed to initialise SULD services: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Authentication first: it gates pre-login and binds sessions before any gameplay listener.
        getServer().getPluginManager().registerEvents(services.auth(), this);
        services.auth().announceMode();
        services.auth().startSweeper();
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this, services), this);
        getServer().getPluginManager().registerEvents(services.classSelectionGui(), this);
        getServer().getPluginManager().registerEvents(services.combatListener(), this);
        getServer().getPluginManager().registerEvents(services.resourcePacks(), this);
        services.resourcePacks().start();
        getServer().getPluginManager().registerEvents(services.bosses(), this);
        getServer().getPluginManager().registerEvents(services.mobs(), this);
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.combat.CombatFeedback(this, services.mobs()), this);
        getServer().getPluginManager().registerEvents(
                new mn.suld.plugin.dungeon.DungeonListener(services.dungeons(), services.parties()), this);
        registerCommand("suld", new SuldCommand(this, services));
        registerCommand("party", new mn.suld.plugin.command.PartyCommand(services.parties()));
        registerCommand("dungeon", new mn.suld.plugin.command.DungeonCommand(services));
        getServer().getPluginManager().registerEvents(services.styles(), this);
        services.styles().start();
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.clan.ChatGuardListener(this), this);
        getServer().getPluginManager().registerEvents(
                new mn.suld.plugin.clan.ChatListener(services.clans(), services.styles(), services.resourcePacks()), this);
        services.hud().attach(this, services);
        services.styles().onChange(p -> services.hud().refreshTeams());
        effects = new mn.suld.plugin.style.CosmeticEffects(this, services);
        getServer().getPluginManager().registerEvents(effects, this);
        effects.start();
        getServer().getPluginManager().registerEvents(
                new mn.suld.plugin.clan.SocialListener(services.clans(), services.worldEvents()), this);
        mn.suld.plugin.command.ClanCommand clanCommand = new mn.suld.plugin.command.ClanCommand(services.clans());
        registerCommand("clan", clanCommand);
        registerCommand("cc", clanCommand);
        registerCommand("suldevent", new mn.suld.plugin.command.SuldEventCommand(services.worldEvents()));
        services.worldEvents().startTicker();
        getServer().getPluginManager().registerEvents(
                new mn.suld.plugin.relic.RelicListener(this, services.relics()), this);
        registerCommand("relic", new mn.suld.plugin.command.RelicCommand(services.relics()));
        services.relics().start();
        long clanFlushTicks = TICKS_PER_SECOND * 60;
        getServer().getScheduler().runTaskTimer(this, () -> services.clans().flushDirty(), clanFlushTicks, clanFlushTicks);
        deaths = new mn.suld.plugin.death.DeathService(this, services);
        getServer().getPluginManager().registerEvents(deaths, this);
        deaths.start();
        services.isSoul = deaths::isSoul;
        registerCommand("revive", new ReviveCommand(this, services, deaths));
        registerCommand("suldpack", new mn.suld.plugin.command.ResourcePackCommand(this, services.resourcePacks()));

        worldBuild = new mn.suld.plugin.worldbuild.WorldBuildService(this, config.world());
        getServer().getPluginManager().registerEvents(worldBuild, this);
        PluginCommand wb = getCommand("worldbuild");
        if (wb != null) {
            mn.suld.plugin.worldbuild.WorldBuildCommand wbc = new mn.suld.plugin.worldbuild.WorldBuildCommand(worldBuild);
            wb.setExecutor(wbc);
            wb.setTabCompleter(wbc);
        }
        worldBuild.start();
        services.city(worldBuild);
        mn.suld.plugin.worldbuild.CityProtectionListener protection =
                new mn.suld.plugin.worldbuild.CityProtectionListener(this, services, worldBuild);
        getServer().getPluginManager().registerEvents(protection, this);
        protection.start();
        new mn.suld.plugin.mob.RegionSpawner(this, services).start();
        getServer().getPluginManager().registerEvents(services.classWeapons(), this);
        services.classWeapons().start();

        mn.suld.plugin.command.CityCommands city = new mn.suld.plugin.command.CityCommands(this, services, worldBuild);
        getServer().getPluginManager().registerEvents(city, this);
        registerTab("help", city.help());
        registerTab("rules", city.rules());
        registerTab("spawn", city.spawn());
        registerTab("balance", city.balance());
        registerTab("pay", city.pay());
        mn.suld.plugin.gui.Menus menus = new mn.suld.plugin.gui.Menus(this, services);
        city.menus(menus);
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.gui.MenuListener(this, services, menus), this);
        mn.suld.plugin.npc.NpcService npcs = new mn.suld.plugin.npc.NpcService(this, services, worldBuild, menus);
        getServer().getPluginManager().registerEvents(npcs, this);
        npcs.start();
        services.hud().glowSource(npcs::glowEntries);
        mn.suld.plugin.command.MenuCommands mc = new mn.suld.plugin.command.MenuCommands(services, menus);
        registerTab("menu", mc.menu());
        registerTab("tutorial", mc.tutorial());
        registerTab("cosmetics", mc.cosmetics());
        registerTab("shop", mc.shop());
        registerTab("buy", mc.buy());
        registerTab("rankup", mc.rankup());
        registerTab("lvlup", mc.lvlup());
        registerTab("credits", mc.credits());
        registerTab("skills", mc.skills());
        mn.suld.plugin.skill.SkillService skills = new mn.suld.plugin.skill.SkillService(this, services);
        getServer().getPluginManager().registerEvents(skills, this);
        skills.start();
        services.quests().onChange((p, pr) -> services.hud().update(p, pr));
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.quest.QuestListener(this, services), this);
        mn.suld.plugin.command.ProgressCommands progress = new mn.suld.plugin.command.ProgressCommands(services);
        progress.menus(menus);
        tracker = new mn.suld.plugin.quest.QuestTracker(this, services);
        getServer().getPluginManager().registerEvents(tracker, this);
        tracker.start();
        services.regionIdAt = tracker::regionIdAt;
        services.quests().regionHere(tracker::regionIdAt);
        progress.tracker(tracker);
        registerTab("class", progress.clazz());
        registerTab("profile", progress.profile());
        registerTab("exp", progress.exp());
        registerTab("quest", progress.quest());

        horses = new mn.suld.plugin.mount.HorseService(this, services);
        getServer().getPluginManager().registerEvents(horses, this);
        registerTab("mori", horses);
        services.dismissHorse = horses::dismissFor;

        trades = new mn.suld.plugin.trade.TradeService(this, services);
        getServer().getPluginManager().registerEvents(trades, this);
        registerTab("trade", trades);

        mn.suld.plugin.gui.LeaderboardService top = new mn.suld.plugin.gui.LeaderboardService(this, services);
        registerTab("top", top);
        top.start();

        mn.suld.plugin.reward.TaskService tasks = new mn.suld.plugin.reward.TaskService(this, services);
        getServer().getPluginManager().registerEvents(tasks, this);
        registerTab("tasks", tasks);

        mn.suld.plugin.reward.DailyService daily = new mn.suld.plugin.reward.DailyService(this, services);
        getServer().getPluginManager().registerEvents(daily, this);
        registerTab("daily", daily);

        guide = new mn.suld.plugin.branding.GuideBoards(this, worldBuild);
        guide.start();
        mn.suld.plugin.branding.OnboardingService onboarding = new mn.suld.plugin.branding.OnboardingService(this, services);
        getServer().getPluginManager().registerEvents(onboarding, this);
        onboarding.start();
        onboarding.guide(guide);
        registerTab("commands", new mn.suld.plugin.command.CommandCatalog());
        new mn.suld.plugin.auth.PermissionSetup(this).start();

        mn.suld.plugin.branding.ServerListService serverList = new mn.suld.plugin.branding.ServerListService(this);
        serverList.start();
        new mn.suld.plugin.branding.TipsService(this).start();
        mn.suld.plugin.branding.LinkCommands links = new mn.suld.plugin.branding.LinkCommands(this);
        registerTab("discord", links);
        registerTab("website", links);
        registerTab("vote", links);
        getServer().getPluginManager().registerEvents(serverList, this);

        mn.suld.plugin.auth.OwnerService owners = new mn.suld.plugin.auth.OwnerService(
                this, services.auth().policy().mode(), getConfig().getStringList("owners"));
        getServer().getPluginManager().registerEvents(owners, this);
        owners.start();

        pregen = new mn.suld.plugin.worldbuild.PregenService(this, worldBuild);
        pregen.start();

        long flushTicks = TICKS_PER_SECOND * Math.max(1, config.analytics().flushIntervalSeconds());
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> services.analytics().flush(), flushTicks, flushTicks);

        long autosaveTicks = TICKS_PER_SECOND * AUTOSAVE_SECONDS;
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::autosave, autosaveTicks, autosaveTicks);

        getLogger().info("SULD enabled — storage=" + config.database().type()
                + ", maxLevel=" + config.progression().maxLevel());
    }

    @Override
    public void onDisable() {
        if (trades != null) {
            trades.shutdown();
        }
        if (horses != null) {
            horses.shutdown();
        }
        if (deaths != null) {
            deaths.shutdown();
        }
        if (tracker != null) {
            tracker.shutdown();
        }
        if (pregen != null) {
            pregen.stop();
        }
        if (worldBuild != null) {
            worldBuild.stop();
        }
        if (services != null) {
            services.resourcePacks().stop();
        }
        if (services == null) {
            return;
        }
        // Persist everyone still online — and any profile loaded for a login that never reached "join" — before the pools close.
        java.util.Set<java.util.UUID> toSave = new java.util.LinkedHashSet<>();
        for (Player player : getServer().getOnlinePlayers()) toSave.add(player.getUniqueId());
        for (var profile : services.profiles().cachedProfiles()) toSave.add(profile.playerId());
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (java.util.UUID id : toSave) {
            saves.add(services.profiles().saveAndUnload(id));
        }
        try {
            CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
        } catch (Exception ex) {
            getLogger().warning("Not all profiles saved cleanly on shutdown: " + ex.getMessage());
        }
        services.close();
        getLogger().info("SULD disabled.");
    }

    /**
     * One-time upgrades of an existing config.yml (a new jar never rewrites the admin's file):
     * v2 — the resource pack is now self-hosted, so the old "enabled: false, no url" default is switched on.
     */
    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= 3) return;
        if (version < 2 && getConfig().getString("resource-pack.url", "").isBlank()
                && !getConfig().getBoolean("resource-pack.enabled", false)) {
            getConfig().set("resource-pack.enabled", true);
            getLogger().info("config.yml migrated: resource-pack.enabled = true (the pack is now self-hosted by SULD)");
        }
        // v3: the UI (badges, menus, icons) is drawn by the pack -> required; new branding/store/ui sections
        getConfig().set("resource-pack.required", true);
        if (!getConfig().isSet("branding.domain")) getConfig().set("branding.domain", "suld.mn");
        if (!getConfig().isSet("branding.store-url")) getConfig().set("branding.store-url", "");
        if (!getConfig().isSet("ui.welcome-screen")) getConfig().set("ui.welcome-screen", true);
        if (!getConfig().isSet("owners")) getConfig().set("owners", java.util.List.of("qeevr_"));
        if (!getConfig().isSet("store.packages")) {
            getConfig().set("store.packages", java.util.List.of(
                    java.util.Map.of("credits", 100, "price", "₮5,000"), java.util.Map.of("credits", 250, "price", "₮11,000"),
                    java.util.Map.of("credits", 600, "price", "₮25,000"), java.util.Map.of("credits", 1300, "price", "₮50,000"),
                    java.util.Map.of("credits", 2800, "price", "₮100,000")));
        }
        getConfig().set("config-version", 3);
        saveConfig();
        getLogger().info("config.yml migrated to v3: resource pack required, branding/store/ui/owners sections added");
    }

    private void autosave() {
        if (services == null) {
            return;
        }
        for (PlayerProfile profile : services.profiles().cachedProfiles()) {
            if (profile.isDirty()) {
                services.profiles().save(profile).exceptionally(ex -> {
                    getLogger().warning("Auto-save failed for " + profile.playerId() + ": " + ex);
                    return null;
                });
            }
        }
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml");
        }
    }

    private void registerTab(String name, org.bukkit.command.TabExecutor executor) {
        registerCommand(name, executor);
        PluginCommand command = getCommand(name);
        if (command != null) command.setTabCompleter(executor);
    }

    public mn.suld.plugin.style.CosmeticEffects effects() {
        return effects;
    }

    public mn.suld.plugin.branding.GuideBoards guide() {
        return guide;
    }

    /** Exposed for tests / sibling modules that need the live service container. */
    public SuldServices services() {
        return services;
    }
}
