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
    /** Dirty profiles are written at least this often (async upserts). Was 300 s: a crash lost up to 5 min of EXP. */
    static final long AUTOSAVE_SECONDS = 60L;

    private SuldServices services;
    private mn.suld.plugin.mount.HorseService horses;
    private mn.suld.plugin.hologram.HologramService holograms;
    private mn.suld.plugin.trade.TradeService trades;
    private mn.suld.plugin.style.CosmeticEffects effects;
    private mn.suld.plugin.death.DeathService deaths;
    private mn.suld.plugin.death.DeathTrialService deathTrial;
    private mn.suld.plugin.death.SoulBeaconService soulBeacons;
    private mn.suld.plugin.gui.SkillSky skillSky;
    private mn.suld.plugin.gui.MenuSky menuSky;
    private mn.suld.plugin.map.AtlasSky atlas;
    private mn.suld.plugin.map.WaypointGuide waypoints;
    private mn.suld.plugin.map.ScreenMarkers screenMarkers;
    private mn.suld.plugin.web.ArmoryServer armory;
    private mn.suld.plugin.map.MinimapService minimap;
    private mn.suld.plugin.training.TrainingService training;
    private mn.suld.plugin.quest.QuestTracker tracker;
    private mn.suld.plugin.dungeon.DungeonGuide dungeonGuide;
    private mn.suld.plugin.worldbuild.WorldBuildService worldBuild;
    private mn.suld.plugin.worldbuild.PregenService pregen;
    private mn.suld.plugin.combat.CombatFeel combatFeel;
    private mn.suld.plugin.dungeon.DungeonHalls halls;
    private mn.suld.plugin.branding.TutorialService tutorial;

    /**
     * The content files are installed first, before anything reads a mob, region, dungeon or quest
     * (docs/CONTENT_DATA.md): the server's copies in plugins/SULD/content/ where it has them, else the bundled ones.
     */
    @Override
    public void onLoad() {
        // PROG-002: one level curve. Chapter EXP is priced while the content loads, so the configured curve goes in first.
        try {
            mn.suld.api.balance.Balance.configure(new mn.suld.api.progression.PolynomialLevelCurve(
                    getConfig().getDouble("progression.curve.base", mn.suld.api.balance.Balance.CURVE_BASE),
                    getConfig().getDouble("progression.curve.exponent", mn.suld.api.balance.Balance.CURVE_EXP),
                    getConfig().getInt("progression.max-level", mn.suld.api.balance.Balance.MAX_LEVEL)));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("progression.curve is invalid (" + ex.getMessage() + "); rewards use the design curve.");
        }
        mn.suld.plugin.content.Content.install(getDataFolder().toPath().resolve("content"), getLogger());
        mn.suld.plugin.hologram.Holograms.install(this); // content/holograms.json: every hologram's words, place and look
    }

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
            // progression v2: rescale bars still on the old curve once, fill the rested pool from offline time
            mn.suld.api.progression.LevelCurve legacyCurve = new mn.suld.api.progression.PolynomialLevelCurve(
                    getConfig().getDouble("progression.legacy-curve.base", 100.0),
                    getConfig().getDouble("progression.legacy-curve.exponent", 1.75), config.progression().maxLevel());
            mn.suld.api.progression.LevelCurve currentCurve = config.progression().toCurve();
            services.profiles().onLoad(pr -> mn.suld.api.balance.ProfileUpgrade.onLoad(pr, legacyCurve, currentCurve, java.time.Instant.now()));
        } catch (Exception ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to initialise SULD services: " + ex.getMessage(), ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Authentication first: it gates pre-login and binds sessions before any gameplay listener.
        services.auth().connectBridge();
        getServer().getPluginManager().registerEvents(services.auth(), this);
        services.auth().announceMode();
        services.auth().startSweeper();
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this, services), this);
        getServer().getPluginManager().registerEvents(services.classSelectionGui(), this);
        getServer().getPluginManager().registerEvents(services.combatListener(), this);
        getServer().getPluginManager().registerEvents(services.resourcePacks(), this);
        services.resourcePacks().start();
        getServer().getPluginManager().registerEvents(services.bosses(), this);
        services.bosses().levelOf(pl -> services.profiles().cached(pl.getUniqueId()).map(pr -> pr.progression().level()).orElse(1));
        getServer().getPluginManager().registerEvents(services.mobs(), this);
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.combat.CombatFeedback(this, services.mobs()), this);
        getServer().getPluginManager().registerEvents(
                new mn.suld.plugin.dungeon.DungeonListener(services.dungeons(), services.parties()), this);
        registerCommand("suld", new SuldCommand(this, services));
        registerCommand("party", new mn.suld.plugin.command.PartyCommand(services.parties()));
        // PvP policy (owner decision 2026-10-11): PvE-only; players fight each other only by /duel or on a fighting ground
        getServer().getPluginManager().registerEvents(services.duels(), this);
        services.duels().start();
        registerTab("duel", new mn.suld.plugin.command.DuelCommand(services.duels()));
        dungeonGuide = new mn.suld.plugin.dungeon.DungeonGuide(this, services);
        getServer().getPluginManager().registerEvents(dungeonGuide, this);
        dungeonGuide.start();
        mn.suld.plugin.gui.DungeonMenu dungeonMenu = new mn.suld.plugin.gui.DungeonMenu(services, dungeonGuide);
        registerCommand("dungeon", new mn.suld.plugin.command.DungeonCommand(services, dungeonMenu,
                new mn.suld.plugin.gui.HeroicMenu(services, dungeonGuide, dungeonMenu)));
        getServer().getPluginManager().registerEvents(services.styles(), this);
        services.styles().start();
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.clan.ChatGuardListener(this), this);
        mn.suld.plugin.clan.ChatChannels chatChannels = new mn.suld.plugin.clan.ChatChannels(this, services.parties(), services.clans());
        getServer().getPluginManager().registerEvents(chatChannels, this);
        chatChannels.start();
        services.clans().channels(chatChannels);
        getServer().getPluginManager().registerEvents(
                new mn.suld.plugin.clan.ChatListener(services.clans(), services.styles(), services.resourcePacks(), chatChannels,
                        services.auth()), this);
        mn.suld.plugin.clan.ChatScreen chatScreen = new mn.suld.plugin.clan.ChatScreen(this, chatChannels, services.zone());
        for (String c : java.util.List.of("chat", "ch", "g", "l", "pc", "tr")) registerTab(c, chatScreen);
        services.hud().attach(this, services);
        combatFeel = new mn.suld.plugin.combat.CombatFeel(this, services);
        getServer().getPluginManager().registerEvents(combatFeel, this);
        combatFeel.start();
        services.hud().lockOn(combatFeel::target);
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.combat.DodgeService(services), this);
        grim = new mn.suld.plugin.anticheat.GrimBridge(this, services.movement);
        grim.start();
        services.styles().onChange(p -> services.hud().teamChanged(p));
        effects = new mn.suld.plugin.style.CosmeticEffects(this, services);
        getServer().getPluginManager().registerEvents(effects, this);
        effects.start();
        getServer().getPluginManager().registerEvents(
                social = new mn.suld.plugin.clan.SocialListener(services.clans(), services.worldEvents()), this);
        social.mobIds = services.mobs()::mobId;
        social.campaignKills = (killer, mobId) -> services.campaigns().onMobKill(killer, mobId);
        mn.suld.plugin.command.ClanCommand clanCommand = new mn.suld.plugin.command.ClanCommand(services.clans());
        registerCommand("clan", clanCommand);
        registerCommand("cc", clanCommand);
        registerCommand("suldevent", new mn.suld.plugin.command.SuldEventCommand(services.worldEvents(), services));
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
        // «Тэнгэрийн сорил»: the active way out of a death lock (docs/DEATH_AND_RECOVERY.md)
        deathTrial = new mn.suld.plugin.death.DeathTrialService(this, services, deaths);
        deaths.trial = deathTrial;
        services.inDeathTrial = deathTrial::inTrial;
        getServer().getPluginManager().registerEvents(deathTrial, this);
        registerTab("trial", deathTrial);
        // the death reform of 2026-10-13: the beam where a player fell, and the trial's shard
        soulBeacons = new mn.suld.plugin.death.SoulBeaconService(this, services);
        services.soulBeacons = soulBeacons;
        deaths.beacons = soulBeacons;
        getServer().getPluginManager().registerEvents(soulBeacons, this);
        soulBeacons.start();
        mn.suld.plugin.death.SoulShardService soulShards = new mn.suld.plugin.death.SoulShardService(this, services, deaths);
        deathTrial.shards = soulShards;
        getServer().getPluginManager().registerEvents(soulShards, this);
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
        // creative mode: the client owns that inventory, so a class piece dropped on the "destroy" slot is simply gone;
        // when the creative inventory closes, whatever is missing comes back with the same identity
        services.soulbound().restorer(pl -> getServer().getScheduler().runTask(this, () -> {
            if (!pl.isOnline() || services.isSoul.test(pl.getUniqueId())) return;
            boolean weapon = services.classWeapons().recover(pl) == mn.suld.plugin.item.ClassWeapons.Recovery.RESTORED;
            mn.suld.plugin.item.ClassArmor.Recovered a = classArmor.recover(pl);
            if (weapon || a.restored() > 0) {
                services.equipment().dirty(pl);
                pl.sendMessage(mn.suld.plugin.ui.Messages.info("Ангийн эд таны сүнстэй холбоотой — устгах боломжгүй, буцааж өглөө."));
            }
        }));
        services.soulbound().overflow((pl, it) -> services.mailbox(this).keepOverflow(pl, java.util.List.of(it), "soulbound give-back"));
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
        // Тэнгэрийн Зэрэг (Ascension I-III): /ascend and the Тэнгэрийн Тахилч in Kharkhorum
        services.ascension = new mn.suld.plugin.gui.AscensionMenu(services);
        services.ascension.watch(this);
        registerCommand("ascend", (sender, cmd, label, args) -> {
            if (sender instanceof org.bukkit.entity.Player pl) services.ascension.open(pl);
            else sender.sendMessage("/ascend: in game only");
            return true;
        });
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
        mn.suld.plugin.item.BowQuiver quiver = new mn.suld.plugin.item.BowQuiver(this, services.items());
        getServer().getPluginManager().registerEvents(quiver, this);
        quiver.start();

        mn.suld.plugin.ui.Pages.install(this);
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
        registerTab("travel", mc.travel());
        registerTab("settings", mc.settings());
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
        services.classChange = new mn.suld.plugin.clazz.ClassChangeService(this, services, skills);
        getServer().getPluginManager().registerEvents(services.classChange, this);
        services.classTrial = new mn.suld.plugin.clazz.ClassTrialService(this, services, services.classChange);
        getServer().getPluginManager().registerEvents(services.classTrial, this);
        registerTab("classtrial", services.classTrial);
        mn.suld.plugin.skill.OrbOfOblivion.registerRecipe(this);
        mn.suld.plugin.gui.SkillMapMenu skillMap = new mn.suld.plugin.gui.SkillMapMenu(this, services);
        skillMap.menus(menus);
        menus.skillMap(skillMap);
        getServer().getPluginManager().registerEvents(skillMap, this);
        // the full-screen tree (docs/SKILL_SKY.md); the chest map stays the fallback
        mn.suld.plugin.death.DeathService deathsForSky = deaths;
        skillSky = new mn.suld.plugin.gui.SkillSky(this, services, skillMap, id -> deathsForSky != null && deathsForSky.isSoul(id));
        skillMap.sky(skillSky);
        getServer().getPluginManager().registerEvents(skillSky, this);
        skillSky.start();
        menuSky = new mn.suld.plugin.gui.MenuSky(this, services);
        getServer().getPluginManager().registerEvents(menuSky, this);
        menuSky.start();
        // a click in a sky window is the window's: no combo, no shot (UiFocus's sky test, set below once the atlas exists)
        skills.paused = mn.suld.plugin.ui.UiFocus::inSky;
        // «Дайчдын Дасгалын Талбай» (docs/TRAINING_GROUNDS.md): every class at level 60 on the training Wardens, nothing kept
        training = new mn.suld.plugin.training.TrainingService(this, services, skills, skillTree);
        mn.suld.plugin.gui.MenuSky trainingMenuSky = menuSky;
        mn.suld.plugin.gui.SkillSky trainingSkillSky = skillSky;
        training.closeWindows = pl -> {
            if (trainingMenuSky.isOpen(pl)) trainingMenuSky.close(pl);
            if (trainingSkillSky.isOpen(pl)) trainingSkillSky.close(pl);
            if (atlas != null && atlas.isOpen(pl)) atlas.close(pl);
        };
        services.dungeons().closeWindows = training.closeWindows; // a run moves every member: off any sky window first
        services.training = training;
        getServer().getPluginManager().registerEvents(training, this);
        registerTab("dasgal", training);
        mn.suld.plugin.training.TrainingService trainingForMenu = training;
        services.classSelectionGui().tryOut = (pl, c) -> trainingForMenu.enter(pl, c, 0);
        training.start();
        mn.suld.plugin.command.SkillCommands skillCommands = new mn.suld.plugin.command.SkillCommands(services, skillMap, menus,
                new mn.suld.plugin.skill.qa.SkillQa(this, services, skillTree, skills));
        registerTab("skills", skillCommands.skills());
        registerTab("skill", skillCommands.skill());
        registerTab("skillsadmin", skillCommands.admin());
        mn.suld.plugin.command.ItemCommands itemCommands = new mn.suld.plugin.command.ItemCommands(services, new mn.suld.plugin.gui.ItemMenus(services));
        // the Дархан's order board: a fee held for a recipe, the item to the poster, the fee to the crafter (docs/CRAFTING.md)
        if (services.itemService() != null) {
            mn.suld.plugin.item.CraftOrderService craftOrders = new mn.suld.plugin.item.CraftOrderService(this, services);
            getServer().getPluginManager().registerEvents(craftOrders, this);
            craftOrders.start();
            services.craftOrders = craftOrders;
            // the steppe consumables: Айраг, Талын Эмийн Ургамал, Арц Хүж, Дарханы Билүү (audit 2026-10-14 IT-12/IT-20)
            getServer().getPluginManager().registerEvents(new mn.suld.plugin.item.ConsumableService(this, services, skills, deaths), this);
        }
        registerTab("order", new mn.suld.plugin.command.OrderCommand(this, services));
        registerTab("item", itemCommands.item());
        registerTab("items", itemCommands.itemsMenu());
        registerTab("equipment", itemCommands.equipmentMenu());
        registerTab("loot", itemCommands.loot());
        registerTab("itemsadmin", itemCommands.admin());
        mn.suld.plugin.perf.PerfService perf = new mn.suld.plugin.perf.PerfService(this, services);
        getServer().getPluginManager().registerEvents(perf, this);
        registerTab("suldperf", perf.command());
        services.quests().onChange((p, pr) -> services.hud().update(p, pr));
        // a finished chapter may take collected items (player file) and pays coins and EXP (SQL): the items leave
        // disk first, then the reward is saved (PlayerDataSaves: ITEMS → COINS)
        services.quests().onComplete((p, pr) -> mn.suld.plugin.item.PlayerDataSaves.itemsForCoins(this, services, p, pr, "chapter reward"));
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

        // the boards of content/holograms.json: the guide boards, «Мөнх Тэнгэрийн Элч», the Хуралдай board, the Hall of Fame
        holograms = new mn.suld.plugin.hologram.HologramService(this, services, worldBuild);
        services.holograms = holograms;
        holograms.start();
        mn.suld.plugin.branding.OnboardingService onboarding = new mn.suld.plugin.branding.OnboardingService(this, services);
        getServer().getPluginManager().registerEvents(onboarding, this);
        onboarding.start();
        onboarding.guide(holograms);
        registerTab("commands", new mn.suld.plugin.command.CommandCatalog());
        new mn.suld.plugin.auth.PermissionSetup(this).start();

        mn.suld.plugin.branding.ServerListService serverList = new mn.suld.plugin.branding.ServerListService(this);
        serverList.start();
        new mn.suld.plugin.branding.TipsService(this)
                .filter(p -> services.styles().setting(p.getUniqueId(), mn.suld.api.style.PlayerSetting.TIPS)).start();
        mn.suld.plugin.gui.Menu.clickSound = p -> services.styles().setting(p.getUniqueId(), mn.suld.api.style.PlayerSetting.MENU_SOUNDS);
        mn.suld.plugin.branding.LinkCommands links = new mn.suld.plugin.branding.LinkCommands(this);
        registerTab("discord", links);
        registerTab("website", links);
        registerTab("vote", links);
        getServer().getPluginManager().registerEvents(serverList, this);

        mn.suld.plugin.auth.OwnerService owners = new mn.suld.plugin.auth.OwnerService(
                this, services.auth(), getConfig().getStringList("owners"));
        getServer().getPluginManager().registerEvents(owners, this);
        owners.start();

        pregen = new mn.suld.plugin.worldbuild.PregenService(this, worldBuild);
        halls = new mn.suld.plugin.dungeon.DungeonHalls(this, mn.suld.plugin.content.DungeonContent.SITES);
        getServer().getPluginManager().registerEvents(halls, this);
        halls.start();
        services.dungeons().halls(halls);
        halls.heroicGate(p -> services.profiles().cached(p.getUniqueId())
                .map(pr -> pr.progression().level() >= mn.suld.api.dungeon.heroic.HeroicLadder.PLAYER_LEVEL).orElse(false));
        if (tutorial != null) tutorial.hooks(p -> combatFeel == null ? java.util.Optional.empty() : combatFeel.target(p), halls::gate);
        for (mn.suld.api.dungeon.hall.DungeonSite site : mn.suld.plugin.content.DungeonContent.SITES) {
            int[] xz = halls.gateXZ(site); // the ground around every gate is generated ahead (P2)
            pregen.addPoint(new mn.suld.plugin.worldbuild.PregenService.Point("gate." + site.shortId(),
                    mn.suld.api.worldbuild.PregenPlan.Priority.P2, org.bukkit.Bukkit.getWorlds().get(0).getName(), xz[0], xz[1], 96));
        }
        mn.suld.plugin.worldbuild.WorldBorderService border = new mn.suld.plugin.worldbuild.WorldBorderService(this);
        getServer().getPluginManager().registerEvents(border, this);
        border.apply("start");
        // what stands by the spawn follows it when it moves (the city's first build, a rollback, /setworldspawn), like
        // every region ring; what is built in the world waits until the city has set it (map inspection, 2026-10-13)
        halls.spawnSettled = worldBuild::spawnSettled;
        mn.suld.plugin.dungeon.DungeonHalls spawnHalls = halls;
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onSpawnMoved(org.bukkit.event.world.SpawnChangeEvent e) {
                if (!e.getWorld().equals(org.bukkit.Bukkit.getWorlds().get(0))) return;
                border.apply("spawn moved");
                spawnHalls.placeGates();
                if (services.ovoos != null) services.ovoos.spawnMoved();
                if (services.historicSites != null) services.historicSites.spawnMoved();
                services.professions.spawnMoved(); // after the ovoos and sites: its nodes keep clear of them
            }
        }, this);
        if (getConfig().getBoolean("map.enabled", true)) {
            // «Сүлдийн Атлас» (docs/WORLD_MAP.md): after the border, whose square the map covers
            services.map = new mn.suld.plugin.map.MapService(this);
            mn.suld.plugin.dungeon.DungeonHalls mapHalls = halls;
            services.map.pois = mn.suld.plugin.map.MapPois.cached(() -> mn.suld.plugin.map.MapPois.collect(services, worldBuild, mapHalls, getConfig()),
                    mn.suld.plugin.map.MapPois.CACHE_MS, System::currentTimeMillis);
            services.map.start();
            // /map: the atlas screen in the sky window engine (docs/WORLD_MAP.md)
            atlas = new mn.suld.plugin.map.AtlasSky(this, services, services.map);
            mn.suld.plugin.gui.MenuSky atlasMenuSky = menuSky;
            mn.suld.plugin.gui.SkillSky atlasSkillSky = skillSky;
            atlas.closeOthers = pl -> {
                if (atlasMenuSky.isOpen(pl)) atlasMenuSky.close(pl);
                if (atlasSkillSky.isOpen(pl)) atlasSkillSky.close(pl);
            };
            getServer().getPluginManager().registerEvents(atlas, this);
            atlas.start();
            registerTab("map", atlas);
            // pins, the waypoint and the corner minimap (docs/WORLD_MAP.md part C)
            mn.suld.plugin.map.PinService mapPins = new mn.suld.plugin.map.PinService(this, services.mapPins());
            getServer().getPluginManager().registerEvents(mapPins, this);
            mapPins.start();
            waypoints = new mn.suld.plugin.map.WaypointGuide(this);
            getServer().getPluginManager().registerEvents(waypoints, this);
            waypoints.start();
            atlas.pins = mapPins;
            atlas.guide = waypoints;
            waypoints.onChange(atlas::waypointChanged); // the atlas's «Чиглэл» flag follows the waypoint
            mn.suld.plugin.map.AtlasSky openAtlas = atlas;
            // the bar and the label stay off every sky window (the label, seen through blocks, would float over the menu
            // and the skill tree too), as the minimap does
            waypoints.busy = mn.suld.plugin.ui.UiFocus::inSky;
            services.hud().quiet(openAtlas::isOpen); // so do the HUD panel and the target frame
            atlas.toggleScreenMarkers = pl -> {
                boolean on = services.styles().toggleSetting(pl, mn.suld.api.style.PlayerSetting.SCREEN_MARKERS);
                pl.sendMessage(on ? mn.suld.plugin.ui.Messages.success("Дэлгэцийн тэмдэг асаалттай: тэмдэг, сүүлд унасан газар дэлгэц дээр зайтай харагдана.")
                        : mn.suld.plugin.ui.Messages.info("Дэлгэцийн тэмдэг унтраалттай: зөвхөн чиглэл харагдана (/map markers — асаах)."));
            };
            atlas.toggleMinimap = pl -> {
                boolean on = services.styles().toggleSetting(pl, mn.suld.api.style.PlayerSetting.MINIMAP);
                pl.sendMessage(on ? mn.suld.plugin.ui.Messages.success("Мини газрын зураг асаалттай (зүүн дээд буланд, SÜLD дүрс багцтай үед).")
                        : mn.suld.plugin.ui.Messages.info("Мини газрын зураг унтраалттай (/map minimap — асаах)."));
            };
            minimap = new mn.suld.plugin.map.MinimapService(this, services, services.map, mapPins, waypoints);
            minimap.busy = mn.suld.plugin.ui.UiFocus::isModal; // sky windows, SÜLD chests (welcome), the death screen
            getServer().getPluginManager().registerEvents(minimap, this);
            minimap.start();
            // the waypoint, the pins and the latest death on the game screen (docs/WORLD_MAP.md, «Дэлгэцийн тэмдэг»)
            screenMarkers = new mn.suld.plugin.map.ScreenMarkers(this, services, mapPins, waypoints);
            screenMarkers.busy = mn.suld.plugin.ui.UiFocus::isModal; // no card floats over a menu or the welcome chest
            getServer().getPluginManager().registerEvents(screenMarkers, this);
            screenMarkers.start();
        }
        // one HUD policy (ui/UiFocus): while a sky window, a SÜLD chest (the welcome screen) or the death screen is up,
        // the boss bars, the minimap, the screen markers and the sidebar step aside, and come back the tick it closes
        mn.suld.plugin.ui.UiFocus.sky(pl -> atlas != null && atlas.isOpen(pl) || menuSky != null && menuSky.isOpen(pl)
                || skillSky != null && skillSky.isOpen(pl));
        mn.suld.plugin.ui.UiFocus.onChange(pl -> services.profiles().cached(pl.getUniqueId()).ifPresent(pr -> services.hud().update(pl, pr)));
        mn.suld.plugin.ui.UiFocus.start(this);
        mn.suld.plugin.ui.BossBars.start(this, mn.suld.plugin.ui.UiFocus::isModal);
        mn.suld.plugin.worldbuild.SuldWorldCommand worldCommand = new mn.suld.plugin.worldbuild.SuldWorldCommand(border, pregen, halls);
        registerTab("suldworld", worldCommand);
        // ovoo at the heart of every area (docs/world/OVOO.md): circle three times clockwise for Тэнгэрийн ивээл
        mn.suld.plugin.region.OvooService ovoo = new mn.suld.plugin.region.OvooService(this, services);
        ovoo.spawnSettled = worldBuild::spawnSettled;
        getServer().getPluginManager().registerEvents(ovoo, this);
        ovoo.deaths = deaths; // a soul's «Овооны цацал» ends the soul state
        ovoo.start();
        services.ovoos = ovoo;
        // the historical places of the Mongol lands in the wild (content/sites.json, docs/world/HISTORIC_SITES.md)
        mn.suld.plugin.region.HistoricSiteService historicSites = new mn.suld.plugin.region.HistoricSiteService(this, services);
        historicSites.spawnSettled = worldBuild::spawnSettled;
        historicSites.start();
        services.historicSites = historicSites;
        // world bosses rise at historic sites at the weekly hours of worldboss.times (docs/WORLD_BOSSES.md)
        mn.suld.plugin.worldboss.WorldBossService worldBosses = new mn.suld.plugin.worldboss.WorldBossService(this, services);
        getServer().getPluginManager().registerEvents(worldBosses, this);
        worldBosses.start();
        services.worldBosses = worldBosses;
        // «Нурууны туяа»: the back aura of Тэнгэрийн Зэрэг VII–IX; the monument to the envoys of X (AscensionHonor)
        new mn.suld.plugin.hud.HonorAura(this, services).start();
        props = new mn.suld.plugin.prop.PropService(this, services);
        props.start();
        services.props = props;
        services.npcs = npcs;
        // Их Хуралдай, the week's campaign and the chronicle (docs/KHURALDAI.md): after the city, props and NPCs, so a
        // restored world effect has something to attach to
        services.chronicle().start();
        services.khuraldai().start();
        services.campaigns().start();
        // heroic keys (docs/HEROIC_DUNGEONS.md): the hit lift, the affixes and the keys of the players online
        getServer().getPluginManager().registerEvents(services.heroic, this);
        services.heroic.start();
        // weapon mastery (docs/MASTERY_AND_PROFESSIONS.md): CombatListener feeds it, /mastery shows it
        getServer().getPluginManager().registerEvents(services.weaponMastery, this);
        services.weaponMastery.start();
        java.util.Objects.requireNonNull(getCommand("mastery")).setExecutor((sender, command, label, args) -> {
            if (sender instanceof org.bukkit.entity.Player p) services.weaponMastery.show(p);
            else sender.sendMessage(mn.suld.plugin.ui.Messages.error("Зөвхөн тоглогч."));
            return true;
        });
        // the crafts (docs/MASTERY_AND_PROFESSIONS.md): veins, herb patches and herders' pens in the wild, after the
        // ovoos, sites and gates they keep clear of; /mergejil shows them and refines
        mn.suld.plugin.dungeon.DungeonHalls craftHalls = halls;
        services.professions.spawnSettled = worldBuild::spawnSettled;
        services.professions.keepAway = () -> {
            java.util.List<int[]> out = new java.util.ArrayList<>();
            for (mn.suld.plugin.map.MapPois.Poi poi : mn.suld.plugin.map.MapPois.collect(services, worldBuild, craftHalls, getConfig())) {
                if (poi.kind() != mn.suld.plugin.map.MapPois.Kind.CITY) out.add(new int[]{poi.x(), poi.z()});
            }
            return out;
        };
        services.professions.guide = waypoints;
        getServer().getPluginManager().registerEvents(services.professions, this);
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.item.MaterialGuard(services), this);
        services.professions.start();
        registerTab("mergejil", new mn.suld.plugin.command.ProfessionCommand(services));
        // achievements and the season's pass «Хуралдайн Зам» (docs/ACHIEVEMENTS_AND_PASS.md)
        getServer().getPluginManager().registerEvents(services.achievements, this);
        services.achievements.start();
        mn.suld.plugin.ui.Presentation.onLevel = (pl, level) -> services.achievements.record(pl, mn.suld.api.achievement.Deed.LEVEL, "", level);
        services.quests().onChapter = (pl, chapter) -> services.achievements.record(pl, mn.suld.api.achievement.Deed.CHAPTER, chapter, 1);
        mn.suld.plugin.gui.AchievementMenu achievementMenu = new mn.suld.plugin.gui.AchievementMenu(services, menus);
        registerTab("achievements", new mn.suld.plugin.command.AchievementCommand(services, achievementMenu, false));
        registerTab("pass", new mn.suld.plugin.command.AchievementCommand(services, achievementMenu, true));
        // the read-only armory API for the web (docs/ARMORY_API.md): off unless web.armory.enabled
        armory = new mn.suld.plugin.web.ArmoryServer(this, services);
        armory.start();
        // the Bonum credit store (docs/PAYMENT_ARCHITECTURE.md): off unless payment.bonum.enabled and its secrets are set
        services.payments.start();
        registerTab("khuraldai", new mn.suld.plugin.command.KhuraldaiCommand(services, menus));
        registerTab("chronicle", new mn.suld.plugin.command.ChronicleCommand(services, menus));
        java.util.Objects.requireNonNull(getCommand("report")).setExecutor(new mn.suld.plugin.command.ReportCommand(services));
        worldCommand.sites(historicSites);
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
        if (deathTrial != null) deathTrial.shutdown();
        if (soulBeacons != null) soulBeacons.shutdown(); // the beams go; the beacons are stored and read again
        if (props != null) props.stop();
        if (combatFeel != null) combatFeel.shutdown();
        if (halls != null) halls.shutdown();
        if (training != null) training.shutdown(); // trainees back to their own character and things, before the last saves
        if (services != null && services.classTrial != null) services.classTrial.shutdown(); // trial players back to their class
        if (menuSky != null) menuSky.shutdown(); // players in the menu's sky window go back where they stood
        if (skillSky != null) skillSky.shutdown(); // players on the tree stage go back where they stood
        if (services != null && services.skillTree != null) {
            services.skillTree.stop();
        }
        if (trades != null) {
            trades.shutdown();
        }
        if (services != null && services.worldBosses != null) services.worldBosses.shutdown(); // the boss never outlives the server
        if (services != null && services.activity != null) services.activity.stop(); // farming fatigue to disk
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
        if (minimap != null) minimap.shutdown(); // the minimaps' frames go
        if (screenMarkers != null) screenMarkers.shutdown(); // the screen cards go
        if (waypoints != null) waypoints.shutdown(); // the waypoint bars go
        if (armory != null) armory.stop();
        if (services != null && services.payments != null) services.payments.stop(); // the webhook closes, QR maps go blank
        if (atlas != null) atlas.shutdown(); // players in the atlas go back where they stood
        mn.suld.plugin.ui.BossBars.stop(); // quiet players get their bars back before the services hide theirs
        mn.suld.plugin.ui.UiFocus.stop();
        if (services != null && services.map != null) services.map.shutdown(); // the map's regions to disk
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
        java.util.Map<java.util.UUID, CompletableFuture<Void>> saves = new java.util.LinkedHashMap<>();
        for (java.util.UUID id : toSave) {
            saves.put(id, services.profiles().saveAndUnload(id));
        }
        try {
            CompletableFuture.allOf(saves.values().toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            List<java.util.UUID> missing = new ArrayList<>();
            for (var e : saves.entrySet()) if (!e.getValue().isDone() || e.getValue().isCompletedExceptionally()) missing.add(e.getKey());
            getLogger().severe("[shutdown] " + missing.size() + " profile save(s) not confirmed within 15 s (" + ex.getClass().getSimpleName()
                    + "): " + missing + " — these players' last changes may be missing; the database row is what it was before");
        }
        mn.suld.plugin.item.PlayerDataSaves.drainShutdown(getLogger()); // purchases whose coins committed meanwhile get their items
        // after the profile flush: style/clan/analytics flushes and every pool, all against one deadline
        services.close(getConfig().getLong("shutdown.budget-seconds", 20));
        mn.suld.plugin.item.PlayerDataSaves.drainShutdown(getLogger());
        getLogger().info("SULD disabled.");
    }

    /**
     * One-time upgrades of an existing config.yml (a new jar never rewrites the admin's file):
     * v2 — the resource pack is now self-hosted, so the old "enabled: false, no url" default is switched on.
     */
    private void migrateConfig() {
        migrateToV4(getConfig().getInt("config-version", 1));
        if (getConfig().getInt("config-version", 1) < 5) migrateCurve();
        if (getConfig().getInt("config-version", 1) < 6) migrateActTwoCurve();
        if (getConfig().getInt("config-version", 1) < 7) migrateDeathCap();
        if (getConfig().getInt("config-version", 1) < 8) migrateDeathReform();
        if (getConfig().getInt("config-version", 1) < 9) migrateTrainingYard();
    }

    /**
     * v9: the training yard's centre moves from its first spot (plot A, -63 72 -92) to the one the owner chose on
     * 2026-10-13, the middle of the walled field (-30 69 -79). A file without the keys already reads the new default;
     * a centre staff set themselves ({@code /dasgal yard set}) is kept.
     */
    private void migrateTrainingYard() {
        var c = getConfig();
        if (c.isSet("training.x") && c.getInt("training.x") == -63 && c.getInt("training.y") == 72 && c.getInt("training.z") == -92) {
            c.set("training.x", mn.suld.plugin.training.TrainingService.DEFAULT_X);
            c.set("training.y", mn.suld.plugin.training.TrainingService.DEFAULT_Y);
            c.set("training.z", mn.suld.plugin.training.TrainingService.DEFAULT_Z);
            getLogger().warning("config.yml migrated to v9: the training yard's centre -63 72 -92 -> "
                    + c.getInt("training.x") + " " + c.getInt("training.y") + " " + c.getInt("training.z") + " (the owner's spot)");
        }
        c.set("config-version", 9);
        saveConfig();
    }

    /**
     * v8, the death reform of 2026-10-13: no timed lock (a soul comes back by «Тэнгэрийн сорил», the ovoo ritual or
     * staff), levels 1–10 lose nothing, a death from level 11 takes 10 % of the bar instead of 5 % (the Сүнсний Гэрэлт
     * gives 70 % of it back). A server that set its own EXP fraction keeps it; the lock mode is set unless already chosen.
     */
    private void migrateDeathReform() {
        if (getConfig().getDouble("death.exp-loss-fraction", 0.05) == 0.05) getConfig().set("death.exp-loss-fraction", 0.10);
        if (!getConfig().isSet("death.lock.mode")) getConfig().set("death.lock.mode", "UNTIL_RECOVERY");
        if (!getConfig().isSet("death.beginner-levels")) getConfig().set("death.beginner-levels", 10);
        if (!getConfig().isSet("death.ritual-seconds")) getConfig().set("death.ritual-seconds", 90);
        if (!getConfig().isSet("death.beacon.minutes")) getConfig().set("death.beacon.minutes", 10);
        if (!getConfig().isSet("death.beacon.refund")) getConfig().set("death.beacon.refund", 0.70);
        getLogger().warning("config.yml migrated to v8 (death reform): no timed lock; a soul comes back by /trial or the ovoo"
                + " ritual; levels 1-10 lose nothing; a death takes " + getConfig().getDouble("death.exp-loss-fraction")
                + " of the bar and the soul beacon gives 70 % back. Souls locked before keep their end time.");
        getConfig().set("config-version", 8);
        saveConfig();
    }

    /**
     * v5, progression v2: the shipped 100·L^1.75 curve (≈10 h to level 60) becomes 315·L^2.2 (≈200 h). A server that
     * set its own curve keeps it. The old curve is kept as {@code progression.legacy-curve}: each profile still on it
     * is rescaled once when it next loads (level kept, the same fraction of the bar; nobody loses a level).
     */
    private void migrateCurve() {
        double base = getConfig().getDouble("progression.curve.base", 100.0);
        double exp = getConfig().getDouble("progression.curve.exponent", 1.75);
        if (!getConfig().isSet("progression.legacy-curve.base")) {
            getConfig().set("progression.legacy-curve.base", base);
            getConfig().set("progression.legacy-curve.exponent", exp);
        }
        if (base == 100.0 && exp == 1.75) {
            getConfig().set("progression.curve.base", mn.suld.api.balance.Balance.CURVE_BASE);
            getConfig().set("progression.curve.exponent", mn.suld.api.balance.Balance.CURVE_EXP);
            getLogger().warning("config.yml migrated to v5: level curve 100*L^1.75 -> " + mn.suld.api.balance.Balance.CURVE_BASE + "*L^2.2 (progression v2). "
                    + "Existing players keep their level; their bar is rescaled on their next login.");
        }
        getConfig().set("config-version", 5);
        saveConfig();
    }

    /**
     * v6, Act II: the live curve 190·L^2.2 becomes {@link mn.suld.api.balance.Balance#CURVE_BASE}·L^2.2 (the story grew
     * from 18 to 42 chapters; the simulation keeps ≈ 200 h to level 60). A server that set its own curve keeps it.
     * Stored bars on the 190 curve (curve version 2) are rescaled once when they next load.
     */
    private void migrateActTwoCurve() {
        double base = getConfig().getDouble("progression.curve.base", mn.suld.api.balance.Balance.CURVE_V2_BASE);
        double exp = getConfig().getDouble("progression.curve.exponent", mn.suld.api.balance.Balance.CURVE_EXP);
        if (base == mn.suld.api.balance.Balance.CURVE_V2_BASE && exp == mn.suld.api.balance.Balance.CURVE_EXP) {
            getConfig().set("progression.curve.base", mn.suld.api.balance.Balance.CURVE_BASE);
            getLogger().warning("config.yml migrated to v6: level curve " + mn.suld.api.balance.Balance.CURVE_V2_BASE + "*L^2.2 -> "
                    + mn.suld.api.balance.Balance.CURVE_BASE + "*L^2.2 (Act II). Existing players keep their level; their bar is rescaled on their next login.");
        }
        getConfig().set("config-version", 6);
        saveConfig();
    }

    /** v7 (external audit FIND-05): the lock tops out at 30 minutes, not 24 hours; «Тэнгэрийн сорил» ends it sooner. */
    private void migrateDeathCap() {
        if (getConfig().getDouble("death.lock.max-minutes", 30) == 1440) {
            getConfig().set("death.lock.max-minutes", 30);
            getLogger().warning("config.yml migrated to v7: death.lock.max-minutes 1440 -> 30 (a lock now lasts 5 min at level 1 to"
                    + " 30 min at 60; a soul can end it with /trial). Locks already running keep their end time.");
        }
        getConfig().set("config-version", 7);
        saveConfig();
    }

    private void migrateToV4(int version) {
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
        if (!getConfig().isSet("owners")) getConfig().set("owners", java.util.List.of()); // SEC-002: no shipped owner
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

    private mn.suld.plugin.prop.PropService props;
    private mn.suld.plugin.clan.SocialListener social;
    private mn.suld.plugin.anticheat.GrimBridge grim;

    /** The GrimAC bridge (inactive when GrimAC is not installed). */
    public mn.suld.plugin.anticheat.GrimBridge grim() {
        return grim;
    }

    /** The 3D props (Meshy models) placed in the world. */
    public mn.suld.plugin.prop.PropService props() {
        return props;
    }

    public mn.suld.plugin.hologram.HologramService holograms() {
        return holograms;
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
                        mn.suld.plugin.content.LadderContent.PALACE_GUARD)),
                // world bosses (docs/WORLD_BOSSES.md): the same archetypes, out in the open
                new B(mn.suld.plugin.content.SuldContent.mobFor("mob.khuvsgul_ikh_baavgai"), new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Хөвсгөлийн Их Баавгай",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{CLEAVE, CHARGE, SLAM, SUMMON}), 0x6B4A2B, org.bukkit.Particle.CLOUD,
                        org.bukkit.Material.SPRUCE_LOG, org.bukkit.Sound.ENTITY_POLAR_BEAR_WARNING, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.PUSH,
                        mn.suld.plugin.content.WorldContent.GREY_WOLF)),
                new B(mn.suld.plugin.content.SuldContent.mobFor("mob.altai_ikh_avarga"), new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Flavour("Алтайн Их Аварга",
                        set.apply(new mn.suld.plugin.dungeon.brain.ArchetypeBrain.Ability[]{SLAM, CHARGE, BARRAGE, NOVA, SUMMON}), 0x9ED2EE, org.bukkit.Particle.SNOWFLAKE,
                        org.bukkit.Material.PACKED_ICE, org.bukkit.Sound.ENTITY_RAVAGER_ROAR, mn.suld.plugin.dungeon.brain.ArchetypeBrain.Status.SLOW,
                        mn.suld.plugin.content.WorldContent.ICE_SPIRIT)));
        for (B b : all) {
            if (b.boss() == null) continue; // a world boss taken out of content/mobs.json: nothing to dress
            services.bosses().brain(b.boss().id(), boss -> new mn.suld.plugin.dungeon.brain.ArchetypeBrain(this, services, b.boss(), b.f()));
        }
    }

}
