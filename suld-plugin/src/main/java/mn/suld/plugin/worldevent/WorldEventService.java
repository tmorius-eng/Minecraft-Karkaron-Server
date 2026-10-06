package mn.suld.plugin.worldevent;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.clan.Clan;
import mn.suld.api.config.SocialSettings;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.worldevent.EventReward;
import mn.suld.api.worldevent.WorldEventDefinition;
import mn.suld.api.worldevent.WorldEventRun;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.mob.MobService;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs server-wide world events. Rules (progress, expiry, ranked rewards) live in the pure
 * {@link WorldEventRun}; this class spawns the raiders around players, keeps them hostile,
 * shows the boss bar, and pays out.
 */
public final class WorldEventService {

    private static final long TICK = 20L;
    private static final int SPAWN_EVERY_SECONDS = 20;
    private static final int SPAWN_PER_PLAYER = 2;
    private static final int ALIVE_PER_PLAYER = 4;
    private static final int ALIVE_CAP = 40;

    private final Plugin plugin;
    private final SuldServices services;
    private final MobService mobs;
    private final SocialSettings settings;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
    private final Set<UUID> eventMobs = new HashSet<>();

    private WorldEventRun active;
    private BukkitTask ticker;
    private long nextAutoStartMillis;
    private long lastSpawnMillis;

    public WorldEventService(Plugin plugin, SuldServices services, MobService mobs, SocialSettings settings) {
        this.plugin = plugin;
        this.services = services;
        this.mobs = mobs;
        this.settings = settings;
        scheduleNextAuto();
    }

    public void startTicker() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
    }

    public Optional<WorldEventRun> active() {
        return Optional.ofNullable(active).filter(WorldEventRun::isActive);
    }

    /** @return an error, or null when started. */
    public Component start(WorldEventDefinition def) {
        if (active().isPresent()) {
            return Messages.error("Өөр үйл явдал явагдаж байна.");
        }
        active = new WorldEventRun(def, Instant.now());
        lastSpawnMillis = 0;
        eventMobs.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            Presentation.banner(p, def.displayName().toUpperCase(java.util.Locale.ROOT), def.description(),
                    NamedTextColor.DARK_PURPLE);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 0.9f);
        }
        services.analytics().record(AnalyticsEvent.of("world_event_start", null, Map.of("event", def.id())));
        plugin.getLogger().info("World event started: " + def.id());
        updateBar();
        return null;
    }

    public boolean stop() {
        if (active().isEmpty()) {
            return false;
        }
        active.cancel();
        finish();
        return true;
    }

    /** Players joining mid-event see the bar. */
    public void onJoin(Player player) {
        if (active().isPresent()) {
            player.showBossBar(bar);
        }
    }

    /** Called (MONITOR) for every SÜLD mob death. */
    public void onMobDeath(LivingEntity entity) {
        if (active().isEmpty() || !eventMobs.remove(entity.getUniqueId())) {
            return;
        }
        Player killer = entity.getKiller();
        String mobId = mobs.mobId(entity).orElse("");
        if (killer == null || !active.recordKill(killer.getUniqueId(), mobId, Instant.now())) {
            updateBar();
            return;
        }
        services.clans().contribute(killer.getUniqueId(), active.definition().clanExpPerKill());
        int mine = active.contributions().getOrDefault(killer.getUniqueId(), 0);
        killer.sendActionBar(Component.text(active.definition().displayName() + ": таны хувь " + mine
                + " · нийт " + active.kills() + "/" + active.definition().targetKills(), NamedTextColor.LIGHT_PURPLE));
        updateBar();
        if (!active.isActive()) {
            finish();
        }
    }

    public Optional<String> hudLine(UUID player) {
        return active().map(run -> "§7Үйл явдал: §d" + run.kills() + "/" + run.definition().targetKills()
                + " §7(" + format(run.remainingSeconds(Instant.now())) + ")");
    }

    // ----------------------------------------------------------------- tick

    private void tick() {
        long now = System.currentTimeMillis();
        if (active == null || !active.isActive()) {
            if (settings.worldEventsEnabled() && now >= nextAutoStartMillis) {
                if (eligiblePlayers().size() >= settings.worldEventMinPlayers()) {
                    start(SuldContent.WOLF_RAID);
                } else {
                    scheduleNextAuto();
                }
            }
            return;
        }
        if (active.expireIfDue(Instant.now())) {
            finish();
            return;
        }
        eventMobs.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        });
        List<Player> players = eligiblePlayers();
        for (UUID id : eventMobs) {
            if (Bukkit.getEntity(id) instanceof LivingEntity mob) {
                MobService.keepHostile(mob, players, 24);
            }
        }
        if (now - lastSpawnMillis >= SPAWN_EVERY_SECONDS * 1000L) {
            lastSpawnMillis = now;
            spawnWave(players);
        }
        updateBar();
    }

    private void spawnWave(List<Player> players) {
        int cap = Math.min(ALIVE_CAP, ALIVE_PER_PLAYER * Math.max(1, players.size()));
        List<String> ids = new ArrayList<>(active.definition().targetMobIds());
        ids.sort(String::compareTo);
        for (Player p : players) {
            for (int i = 0; i < SPAWN_PER_PLAYER && eventMobs.size() < cap; i++) {
                MobDefinition def = SuldContent.mobFor(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
                Location loc = surfaceNear(p.getLocation());
                if (def == null || loc == null) {
                    continue;
                }
                LivingEntity mob = mobs.spawn(def, loc);
                eventMobs.add(mob.getUniqueId());
                MobService.keepHostile(mob, List.of(p), 32);
            }
        }
    }

    // --------------------------------------------------------------- finish

    private void finish() {
        WorldEventRun run = active;
        WorldEventDefinition def = run.definition();
        boolean success = run.state() == mn.suld.api.worldevent.WorldEventState.SUCCEEDED;
        List<EventReward> rewards = run.rewards();
        Set<UUID> rewardedClans = new HashSet<>();
        Map<UUID, UUID> clanCreditor = new HashMap<>();

        for (EventReward r : rewards) {
            Player p = Bukkit.getPlayer(r.playerId());
            PlayerProfile profile = services.profiles().cached(r.playerId()).orElse(null);
            if (p == null || profile == null) {
                continue; // offline at payout: hardcore rule, no mail-in rewards
            }
            int from = profile.progression().level();
            long exp = services.clans().boostedExp(r.playerId(), r.exp());
            ExpGainResult gain = services.progression().grantExp(profile, exp, ExpSource.WORLD_EVENT);
            profile.addCurrency(r.currency());
            p.sendMessage(Messages.success("#" + r.rank() + " (" + r.contribution() + " чоно): +" + exp + " EXP, +"
                    + r.currency() + " зоос"));
            if (gain.leveledUp()) {
                Presentation.levelUp(p, from, gain.after().level());
            }
            services.profiles().save(profile);
            services.clans().clanOf(r.playerId()).map(Clan::id).ifPresent(cid -> {
                if (rewardedClans.add(cid)) {
                    clanCreditor.put(cid, r.playerId());
                }
            });
        }
        // Social progression: every clan that had a qualifying member shares the victory.
        clanCreditor.values().forEach(member -> services.clans().contribute(member, def.clanSuccessBonus()));

        Component headline = success
                ? Messages.success(def.displayName() + " ялагдлаа! " + rewards.size() + " баатар шагнагдлаа.")
                : Messages.error(def.displayName() + " — хугацаа дууслаа. Чоно ялав...");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bar);
            p.sendMessage(headline);
            if (success) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
        for (UUID id : eventMobs) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        eventMobs.clear();
        services.analytics().record(AnalyticsEvent.of("world_event_end", null, Map.of(
                "event", def.id(), "success", success, "kills", run.kills(), "rewarded", rewards.size())));
        plugin.getLogger().info("World event ended: " + def.id() + " success=" + success + " kills=" + run.kills());
        active = null;
        scheduleNextAuto();
        for (Player p : Bukkit.getOnlinePlayers()) {
            services.profiles().cached(p.getUniqueId()).ifPresent(pr -> services.hud().update(p, pr));
        }
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
        }
        if (active().isPresent()) {
            active.cancel();
            for (UUID id : eventMobs) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            }
            eventMobs.clear();
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bar));
            active = null;
        }
    }

    // -------------------------------------------------------------- helpers

    /** Online players with a chosen class, alive, in the overworld and not inside a dungeon run. */
    private List<Player> eligiblePlayers() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getWorld().getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            if (services.dungeons().isInAnyRun(p.getUniqueId())) {
                continue;
            }
            boolean hasClass = services.profiles().cached(p.getUniqueId())
                    .map(pr -> pr.playerClass().isPresent()).orElse(false);
            if (hasClass) {
                out.add(p);
            }
        }
        return out;
    }

    /** A random surface spot 12–18 blocks away, or null if none is safe (water/lava). */
    private static Location surfaceNear(Location center) {
        World world = center.getWorld();
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double dist = 12 + ThreadLocalRandom.current().nextDouble(6);
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z);
            org.bukkit.block.Block ground = world.getBlockAt(x, y, z);
            if (ground.isLiquid() || Math.abs(y - center.getY()) > 12) {
                continue;
            }
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    private void updateBar() {
        if (active == null) {
            return;
        }
        WorldEventDefinition def = active.definition();
        bar.name(Component.text(def.displayName() + " — " + active.kills() + "/" + def.targetKills()
                + " · " + format(active.remainingSeconds(Instant.now())), NamedTextColor.LIGHT_PURPLE));
        bar.progress((float) active.progress());
    }

    private void scheduleNextAuto() {
        nextAutoStartMillis = System.currentTimeMillis() + settings.worldEventIntervalMin() * 60_000L;
    }

    private static String format(long seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }
}
