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
    private mn.suld.plugin.worldbuild.WorldBuildService worldBuild;
    private mn.suld.plugin.worldbuild.PregenService pregen;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        SuldConfig config = SuldConfigFactory.load(new BukkitConfigView(getConfig()));

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
        getServer().getPluginManager().registerEvents(
                new mn.suld.plugin.dungeon.DungeonListener(services.dungeons(), services.parties()), this);
        registerCommand("suld", new SuldCommand(this, services));
        registerCommand("party", new mn.suld.plugin.command.PartyCommand(services.parties()));
        registerCommand("dungeon", new mn.suld.plugin.command.DungeonCommand(services));
        getServer().getPluginManager().registerEvents(new mn.suld.plugin.clan.ChatListener(services.clans()), this);
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
        registerCommand("revive", new ReviveCommand(this, services));
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

        mn.suld.plugin.command.CityCommands city = new mn.suld.plugin.command.CityCommands(this, services, worldBuild);
        getServer().getPluginManager().registerEvents(city, this);
        registerTab("help", city.help());
        registerTab("rules", city.rules());
        registerTab("spawn", city.spawn());
        registerTab("balance", city.balance());
        registerTab("pay", city.pay());
        mn.suld.plugin.command.ProgressCommands progress = new mn.suld.plugin.command.ProgressCommands(services);
        registerTab("class", progress.clazz());
        registerTab("profile", progress.profile());
        registerTab("exp", progress.exp());
        registerTab("quest", progress.quest());

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
        // Persist everyone still online before the pools close.
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (Player player : getServer().getOnlinePlayers()) {
            saves.add(services.profiles().saveAndUnload(player.getUniqueId()));
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
        if (version >= 2) return;
        if (getConfig().getString("resource-pack.url", "").isBlank() && !getConfig().getBoolean("resource-pack.enabled", false)) {
            getConfig().set("resource-pack.enabled", true);
            getLogger().info("config.yml migrated: resource-pack.enabled = true (the pack is now self-hosted by SULD)");
        }
        getConfig().set("config-version", 2);
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

    /** Exposed for tests / sibling modules that need the live service container. */
    public SuldServices services() {
        return services;
    }
}
