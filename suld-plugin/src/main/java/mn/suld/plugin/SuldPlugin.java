package mn.suld.plugin;

import mn.suld.api.config.SuldConfig;
import mn.suld.api.config.SuldConfigFactory;
import mn.suld.api.profile.PlayerProfile;
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
    private mn.suld.plugin.gui.SkillSky skillSky;
    private mn.suld.plugin.quest.QuestTracker tracker;
    private mn.suld.plugin.dungeon.DungeonGuide dungeonGuide;
    private mn.suld.plugin.worldbuild.WorldBuildService worldBuild;
    private mn.suld.plugin.worldbuild.PregenService pregen;
    private mn.suld.plugin.combat.CombatFeel combatFeel;
    private mn.suld.plugin.dungeon.DungeonHalls halls;
    private mn.suld.plugin.branding.TutorialService tutorial;

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
        dungeonGuide = new mn.suld.plugin.dungeon.DungeonGuide(this, services);
        getServer().getPluginManager().registerEvents(dungeonGuide, this);
        dungeonGuide.start();
        registerCommand("dungeon", new mn.suld.plugin.command.DungeonCommand(services, new mn.suld.plugin.gui.DungeonMenu(services, dungeonGuide)));
        getServer().getPluginManager().registerEvents(services.styles(), this);
        services.styles().start();
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.clan.ChatGuardListener(this), this);
        mn.suld.plugin.clan.ChatChannels chatChannels = new mn.suld.plugin.clan.ChatChannels(this, services.parties(), services.clans());
        getServer().getPluginManager().registerEvents(chatChannels, this);
        chatChannels.start();
        services.clans().channels(chatChannels);
        getServer().getPluginManager().registerEvents(
                new mn.suld.plugin.clan.ChatListener(services.clans(), services.styles(), services.resourcePacks(), chatChannels), this);
        mn.suld.plugin.clan.ChatScreen chatScreen = new mn.suld.plugin.clan.ChatScreen(this, chatChannels);
        for (String c : java.util.List.of("chat", "ch", "g", "l", "pc", "tr")) registerTab(c, chatScreen);
        services.hud().attach(this, services);
        combatFeel = new mn.suld.plugin.combat.CombatFeel(this, services);
        getServer().getPluginManager().registerEvents(combatFeel, this);
        combatFeel.start();
        services.hud().lockOn(combatFeel::target);
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.combat.DodgeService(services), this);
        services.styles().onChange(p -> services.hud().teamChanged(p));
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
        // the item engine: catalog (validated data files), generation, loot, inventory checks, equipment
        mn.suld.plugin.item.ItemService itemService = new mn.suld.plugin.item.ItemService(this, services);
        services.itemService = itemService;
        itemService.reload();
        mn.suld.plugin.item.EquipmentService equipment = new mn.suld.plugin.item.EquipmentService(this, services);
        services.equipment = equipment;
        getServer().getPluginManager().registerEvents(equipment, this);
        getServer().getPluginManager().registerEvents(services.soulbound(), this);
        equipment.start();
        // two online players holding the same item identity: one copy is a duplicate
        getServer().getScheduler().runTaskTimer(this, itemService::sweepTick, TICKS_PER_SECOND * 30, 1L); // staggered: each player every 30 s
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
        services.woundFactor = deaths::woundFactor;
        mn.suld.plugin.command.DeathCommands deathCommands = new mn.suld.plugin.command.DeathCommands(this, services, deaths);
        for (String c : List.of("revive", "deathstatus", "deathinfo", "deathrevive", "deathreset")) registerTab(c, deathCommands);
        // the Display-Entity model renderer (docs/MODEL_RENDERER.md): rigged SÜLD mobs are dressed on spawn
        mn.suld.plugin.model.ModelService models = new mn.suld.plugin.model.ModelService(this);
        models.load();
        services.models = models;
        getServer().getPluginManager().registerEvents(models, this);
        models.start();
        services.mobs().onSpawn = (entity, def) -> {
            String rig = mn.suld.plugin.content.SuldContent.modelFor(def.id());
            if (rig == null && (def.tier() == mn.suld.api.mob.MobTier.BOSS || getConfig().getBoolean("models.mobs", true))) {
                rig = models.rigForMob(def.id()).orElse(null);
            }
            if (rig != null) models.attach(entity, rig);
        };
        // boss abilities (BossBrain): Хасар, the first rigged boss (docs/bosses/KHASAR.md)
        services.bosses().brain(mn.suld.plugin.content.SuldContent.KHASAR.id(), boss -> new mn.suld.plugin.dungeon.brain.KhasarBrain(
                this, services, mn.suld.plugin.content.SuldContent.KHASAR, mn.suld.plugin.content.SuldContent.ORKHON_CHONO));
        registerBossBrains();
        // class armour + ActivePlaytime (docs/CLASS_ARMOR_SYSTEM.md, docs/ACTIVE_PLAYTIME_SPEC.md)
        mn.suld.plugin.item.ClassArmor classArmor = new mn.suld.plugin.item.ClassArmor(this, services);
        services.classArmor = classArmor;
        getServer().getPluginManager().registerEvents(classArmor, this);
        mn.suld.plugin.activity.ActivePlaytimeService activity = new mn.suld.plugin.activity.ActivePlaytimeService(this, services);
        services.activity = activity;
        getServer().getPluginManager().registerEvents(activity, this);
        mn.suld.plugin.death.DeathService deathsForMinutes = deaths;
        activity.onMinute((pl, v) -> {
            if (v.active()) deathsForMinutes.activeMinute(pl);
        });
        activity.onMinute(classArmor::activeMinute);
        activity.start();
        registerTab("classgear", new mn.suld.plugin.command.ClassGearCommand(services));
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
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.worldbuild.VillageProtection(), this);
        mn.suld.plugin.mob.RegionSpawner regionSpawner = new mn.suld.plugin.mob.RegionSpawner(this, services);
        getServer().getPluginManager().registerEvents(regionSpawner, this);
        regionSpawner.start();
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
        tutorial = new mn.suld.plugin.branding.TutorialService(this, services, menus);
        getServer().getPluginManager().registerEvents(tutorial, this);
        tutorial.start();
        mn.suld.plugin.gui.SkillMapMenu.onOpen = p -> tutorial.opened(p, "skills");
        mn.suld.plugin.gui.Menus.onQuests = p -> tutorial.opened(p, "quest");
        registerTab("tutorial", tutorial);
        registerTab("cosmetics", mc.cosmetics());
        registerTab("shop", mc.shop());
        registerTab("buy", mc.buy());
        registerTab("rankup", mc.rankup());
        registerTab("lvlup", mc.lvlup());
        registerTab("credits", mc.credits());
        mn.suld.plugin.skill.SkillService skills = new mn.suld.plugin.skill.SkillService(this, services);
        getServer().getPluginManager().registerEvents(skills, this);
        skills.start();
        services.hud().skills(skills);
        mn.suld.plugin.skill.SkillTreeService skillTree = new mn.suld.plugin.skill.SkillTreeService(this, services);
        skillTree.skills(skills);
        services.skillTree = skillTree;
        getServer().getPluginManager().registerEvents(skillTree, this);
        skillTree.start();
        mn.suld.plugin.skill.OrbOfOblivion.registerRecipe(this);
        mn.suld.plugin.gui.SkillMapMenu skillMap = new mn.suld.plugin.gui.SkillMapMenu(this, services);
        skillMap.menus(menus);
        menus.skillMap(skillMap);
        skills.skillMap(skillMap::open); // sneak + right click with the class weapon
        getServer().getPluginManager().registerEvents(skillMap, this);
        // the full-screen tree (docs/SKILL_SKY.md); the chest map stays the fallback
        mn.suld.plugin.death.DeathService deathsForSky = deaths;
        skillSky = new mn.suld.plugin.gui.SkillSky(this, services, skillMap, id -> deathsForSky != null && deathsForSky.isSoul(id));
        skillMap.sky(skillSky);
        getServer().getPluginManager().registerEvents(skillSky, this);
        skillSky.start();
        mn.suld.plugin.command.SkillCommands skillCommands = new mn.suld.plugin.command.SkillCommands(services, skillMap, menus,
                new mn.suld.plugin.skill.qa.SkillQa(this, services, skillTree, skills));
        registerTab("skills", skillCommands.skills());
        registerTab("skill", skillCommands.skill());
        registerTab("skillsadmin", skillCommands.admin());
        mn.suld.plugin.command.ItemCommands itemCommands = new mn.suld.plugin.command.ItemCommands(services, new mn.suld.plugin.gui.ItemMenus(services));
        registerTab("item", itemCommands.item());
        registerTab("items", itemCommands.itemsMenu());
        registerTab("equipment", itemCommands.equipmentMenu());
        registerTab("loot", itemCommands.loot());
        registerTab("itemsadmin", itemCommands.admin());
        mn.suld.plugin.perf.PerfService perf = new mn.suld.plugin.perf.PerfService(this, services);
        getServer().getPluginManager().registerEvents(perf, this);
        registerTab("suldperf", perf.command());
        services.quests().onChange((p, pr) -> services.hud().update(p, pr));
        // a finished chapter pays coins and EXP (SQL) and may take collected items (player file): save both now
        services.quests().onComplete((p, pr) -> {
            services.profiles().save(pr);
            mn.suld.plugin.item.PlayerDataSaves.soon(this, p);
        });
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.quest.QuestListener(this, services), this);
        mn.suld.plugin.command.ProgressCommands progress = new mn.suld.plugin.command.ProgressCommands(services);
        progress.menus(menus);
        tracker = new mn.suld.plugin.quest.QuestTracker(this, services);
        menus.questMenu().tracker(tracker);
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
        halls = new mn.suld.plugin.dungeon.DungeonHalls(this, mn.suld.plugin.content.DungeonContent.SITES);
        getServer().getPluginManager().registerEvents(halls, this);
        halls.start();
        services.dungeons().halls(halls);
        if (tutorial != null) tutorial.hooks(p -> combatFeel == null ? java.util.Optional.empty() : combatFeel.target(p), halls::gate);
        for (mn.suld.api.dungeon.hall.DungeonSite site : mn.suld.plugin.content.DungeonContent.SITES) {
            int[] xz = halls.gateXZ(site); // the ground around every gate is generated ahead (P2)
            pregen.addPoint(new mn.suld.plugin.worldbuild.PregenService.Point("gate." + site.shortId(),
                    mn.suld.api.worldbuild.PregenPlan.Priority.P2, org.bukkit.Bukkit.getWorlds().get(0).getName(), xz[0], xz[1], 96));
        }
        mn.suld.plugin.worldbuild.WorldBorderService border = new mn.suld.plugin.worldbuild.WorldBorderService(this);
        getServer().getPluginManager().registerEvents(border, this);
        border.apply("start");
        worldBuild.onSpawnChanged(() -> {
            border.apply("spawn moved");
            halls.placeGates(); // gates follow the spawn like every region ring
        });
        registerTab("suldworld", new mn.suld.plugin.worldbuild.SuldWorldCommand(border, pregen, halls));
        // ovoo at the heart of every area (docs/world/OVOO.md): circle three times clockwise for Тэнгэрийн ивээл
        mn.suld.plugin.region.OvooService ovoo = new mn.suld.plugin.region.OvooService(this, services);
        getServer().getPluginManager().registerEvents(ovoo, this);
        ovoo.start();
        pregen.start();

        long flushTicks = TICKS_PER_SECOND * Math.max(1, config.analytics().flushIntervalSeconds());
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> services.analytics().flush(), flushTicks, flushTicks);
        // analytics retention (docs/ANALYTICS.md): the database sink purges old rows once a day, off the main thread
        if (services.analytics() instanceof mn.suld.plugin.analytics.JdbcAnalyticsSink db && config.analytics().retentionDays() > 0) {
            int days = config.analytics().retentionDays();
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                int n = db.purge(days);
                if (n > 0) getLogger().info("analytics: purged " + n + " row(s) older than " + days + " days");
            }, TICKS_PER_SECOND * 300, TICKS_PER_SECOND * 86_400);
        }

        long autosaveTicks = TICKS_PER_SECOND * AUTOSAVE_SECONDS;
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::autosave, autosaveTicks, autosaveTicks);

        getLogger().info("SULD enabled — storage=" + config.database().type()
                + ", maxLevel=" + config.progression().maxLevel());
    }

    @Override
    public void onDisable() {
        if (combatFeel != null) combatFeel.shutdown();
        if (halls != null) halls.shutdown();
        if (skillSky != null) skillSky.shutdown(); // players on the tree stage go back where they stood
        if (services != null && services.skillTree != null) {
            services.skillTree.stop();
        }
        if (trades != null) {
            trades.shutdown();
        }
        if (horses != null) {
            horses.shutdown();
        }
        if (services != null && services.models != null) {
            services.models.shutdown();
        }
        if (deaths != null) {
            deaths.shutdown();
        }
        if (dungeonGuide != null) dungeonGuide.shutdown();
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
        if (version >= 4) return;
        if (version == 3) {
            migrateStorage();
            return;
        }
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
        migrateStorage();
    }

    /**
     * v4: "memory" was the shipped default, so every restart of a server that never set up a database wiped every
     * profile (class, level, skills, class gear) while the players kept their vanilla inventories. Such a config now
     * uses the embedded H2 file database; a server that configured MySQL/PostgreSQL is not touched.
     */
    private void migrateStorage() {
        if ("memory".equalsIgnoreCase(getConfig().getString("database.type", "memory"))) {
            getConfig().set("database.type", "h2");
            getLogger().warning("config.yml migrated to v4: database.type memory -> h2 (plugins/SULD/data/). Progress now survives restarts.");
        }
        getConfig().set("config-version", 4);
        saveConfig();
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

    /** Every boss without a hand-written brain fights with archetype abilities (docs/bosses/BOSS_ABILITIES.md). */
    private void registerBossBrains() {
        record B(mn.suld.api.mob.MobDefinition boss, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour f) {
        }
        var A = mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability.class;
        java.util.function.Function<mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[], java.util.Set<mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability>> set =
                a -> mn.suld.plugin.dungeon.brain.ArchetypeBrain.of(a);
        var CLEAVE = mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability.CLEAVE;
        var SLAM = mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability.SLAM;
        var CHARGE = mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability.CHARGE;
        var BARRAGE = mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability.BARRAGE;
        var NOVA = mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability.NOVA;
        var SUMMON = mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability.SUMMON;
        var S = mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.class;
        var L = mn.suld.plugin.content.LadderContent.class;
        java.util.List<B> all = java.util.List.of(
                new B(mn.suld.plugin.content.DungeonContent.SAND_KHAN, new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Элсний Хаан",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{CLEAVE, SLAM, NOVA, SUMMON}), 0xE0B060, org.bukkit.Particle.WHITE_ASH,
                        org.bukkit.Material.SAND, org.bukkit.Sound.ENTITY_HUSK_AMBIENT, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.BLIND,
                        mn.suld.plugin.content.WorldContent.SAND_SPIRIT)),
                new B(mn.suld.plugin.content.DungeonContent.FOREST_LORD, new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Ойн Эзэн",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{CLEAVE, CHARGE, NOVA, SUMMON}), 0x4F7830, org.bukkit.Particle.SPORE_BLOSSOM_AIR,
                        org.bukkit.Material.MOSS_BLOCK, org.bukkit.Sound.ENTITY_POLAR_BEAR_WARNING, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.PUSH,
                        mn.suld.plugin.content.WorldContent.GREY_WOLF)),
                new B(mn.suld.plugin.content.DungeonContent.ICE_KHAN, new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Мөсөн Хаан",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{CLEAVE, BARRAGE, NOVA, SUMMON}), 0x9ED2EE, org.bukkit.Particle.SNOWFLAKE,
                        org.bukkit.Material.PACKED_ICE, org.bukkit.Sound.BLOCK_GLASS_BREAK, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.SLOW,
                        mn.suld.plugin.content.WorldContent.ICE_SPIRIT)),
                new B(mn.suld.plugin.content.LadderContent.LUS_KHAAN, new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Лусын Хаан",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{BARRAGE, SLAM, NOVA, SUMMON}), 0x40FFE0, org.bukkit.Particle.BUBBLE_POP,
                        org.bukkit.Material.PRISMARINE, org.bukkit.Sound.ENTITY_ELDER_GUARDIAN_CURSE, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.PULL,
                        mn.suld.plugin.content.LadderContent.LUS)),
                new B(mn.suld.plugin.content.LadderContent.BLACK_GENERAL, new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Хар Жанжин",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{CLEAVE, CHARGE, SLAM, SUMMON}), 0xA01414, org.bukkit.Particle.SMOKE,
                        org.bukkit.Material.RED_SAND, org.bukkit.Sound.ENTITY_WITHER_SKELETON_AMBIENT, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.BLIND,
                        mn.suld.plugin.content.LadderContent.TANGUT_GHOST)),
                new B(mn.suld.plugin.content.LadderContent.RED_LORD, new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Улаан Хадны Ноён",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{CLEAVE, CHARGE, NOVA, SUMMON}), 0xD84A3C, org.bukkit.Particle.FLAME,
                        org.bukkit.Material.RED_TERRACOTTA, org.bukkit.Sound.EVENT_RAID_HORN, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.PUSH,
                        mn.suld.plugin.content.LadderContent.RAIDER)),
                new B(mn.suld.plugin.content.LadderContent.MOUNTAIN_LORD, new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Хангай Савдаг",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{SLAM, CHARGE, NOVA, SUMMON}), 0x7AFF6A, org.bukkit.Particle.HAPPY_VILLAGER,
                        org.bukkit.Material.STONE, org.bukkit.Sound.ENTITY_RAVAGER_ROAR, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.PUSH,
                        mn.suld.plugin.content.LadderContent.SAVDAG)),
                new B(mn.suld.plugin.content.LadderContent.SKY_ENVOY, new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Хөх Тэнгэрийн Элч",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{BARRAGE, SLAM, NOVA, SUMMON}), 0x80C0FF, org.bukkit.Particle.ELECTRIC_SPARK,
                        org.bukkit.Material.LIGHT_BLUE_CONCRETE, org.bukkit.Sound.ITEM_TRIDENT_THUNDER, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.SLOW,
                        mn.suld.plugin.content.LadderContent.SKY_SOLDIER)),
                new B(mn.suld.plugin.content.LadderContent.BANNER_GUARDIAN, new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Хөх Сүлдийн Сахиул",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{CLEAVE, CHARGE, BARRAGE, NOVA, SUMMON}), 0x3A6AE0, org.bukkit.Particle.END_ROD,
                        org.bukkit.Material.GOLD_BLOCK, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.PUSH,
                        mn.suld.plugin.content.LadderContent.PALACE_GUARD)));
        for (B b : all) {
            services.bosses().brain(b.boss().id(), boss -> new mn.suld.plugin.dungeon.brain.ArchetypeBrain(this, services, b.boss(), b.f()));
        }
    }

}
