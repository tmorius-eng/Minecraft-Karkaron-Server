package mn.suld.plugin.mob;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.region.RegionIndex;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.content.WorldContent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Natural SÜLD mobs in the wild regions around Kharkhorum (Хэрлэн: Govi wolves and bandits, Говь: scorpions and
 * sand spirits, Хангай: grey wolves and bears, Алтай: ice spirits and giants). Every 10 s each player out on the
 * steppe gets mobs of the region they stand in, up to {@code world.region-mobs-per-player}, 18–34 blocks away on
 * the surface. Never inside or next to the city, never for players in a dungeon run or in creative/spectator.
 * Within 450 blocks of the plaza most spawns are level-2 Govi wolves (the first quest's prey) in every direction.
 * Region mobs stay hostile (vanilla wolves calm down), are removed if they wander to the city, and despawn when
 * nobody is near.
 */
public final class RegionSpawner {

    public static final String TAG = "suld_region";
    private static final int CITY_MARGIN = 32;
    private static final double OUTSKIRTS = 450;

    private final Plugin plugin;
    private final SuldServices services;
    private final RegionIndex regions = new RegionIndex(WorldContent.REGIONS);
    private final java.util.Map<java.util.UUID, String> lastRegion = new java.util.concurrent.ConcurrentHashMap<>();

    public RegionSpawner(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        if (!services.config().world().regionSpawning()) {
            plugin.getLogger().info("Region spawning is off (world.region-spawning).");
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnTick, 200L, 200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::careTick, 40L, 40L);
    }

    /** The region at a location (offsets from the world spawn = the Kharkhorum plaza). */
    public java.util.Optional<RegionDefinition> regionAt(Location l) {
        Location c = l.getWorld().getSpawnLocation();
        return regions.at(l.getX() - c.getX(), l.getZ() - c.getZ());
    }

    private boolean eligible(Player p) {
        return p.isValid() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                && p.getWorld().getEnvironment() == World.Environment.NORMAL
                && !services.dungeons().isInAnyRun(p.getUniqueId())
                && !services.city().near(p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockZ(), CITY_MARGIN);
    }

    private void spawnTick() {
        int target = services.config().world().regionMobsPerPlayer();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!eligible(p)) continue;
            RegionDefinition region = regionAt(p.getLocation()).orElse(null);
            if (region == null || region.safeZone() || region.mobIds().isEmpty()) continue;
            long near = p.getWorld().getNearbyEntities(p.getLocation(), 40, 24, 40, e -> e.getScoreboardTags().contains(TAG)).size();
            for (int i = 0; i < 3 && near < target; i++) {
                Location at = spot(p);
                if (at == null) continue;
                // Outskirts (< 450 blocks from the plaza, any direction): mostly Govi wolves, the first hunt's prey,
                // so new players find level-appropriate mobs whatever the world's terrain is.
                Location c = at.getWorld().getSpawnLocation();
                double dist = Math.hypot(at.getX() - c.getX(), at.getZ() - c.getZ());
                String id = dist < OUTSKIRTS && ThreadLocalRandom.current().nextDouble() < 0.6
                        ? SuldContent.GOVIIN_CHONO.id()
                        : region.mobIds().get(ThreadLocalRandom.current().nextInt(region.mobIds().size()));
                MobDefinition def = SuldContent.mobFor(id);
                if (def == null) continue;
                LivingEntity mob = services.mobs().spawn(def, at);
                if (mob != null) {
                    mob.addScoreboardTag(TAG);
                    mob.setRemoveWhenFarAway(false);
                    near++;
                }
            }
        }
    }

    /** A surface spot 18–34 blocks from the player, in a loaded chunk, on solid dry ground, away from the city. */
    private Location spot(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = p.getWorld();
        for (int attempt = 0; attempt < 6; attempt++) {
            double a = r.nextDouble(Math.PI * 2), d = 18 + r.nextDouble(16);
            int x = (int) Math.floor(p.getLocation().getX() + Math.cos(a) * d);
            int z = (int) Math.floor(p.getLocation().getZ() + Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            if (services.city().near(w.getName(), x, z, CITY_MARGIN)) continue;
            Block ground = w.getHighestBlockAt(x, z);
            if (!ground.getType().isSolid() || ground.isLiquid()) continue;
            Block feet = ground.getRelative(0, 1, 0), head = ground.getRelative(0, 2, 0);
            if (!feet.isPassable() || !head.isPassable() || feet.isLiquid()) continue;
            if (Math.abs(ground.getY() - p.getLocation().getBlockY()) > 12) continue;
            return feet.getLocation().add(0.5, 0, 0.5);
        }
        return null;
    }

    /** Title when a player crosses into another wild region: name, description and level band. */
    private void announceRegion(Player p) {
        if (p.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        RegionDefinition r = regionAt(p.getLocation()).orElse(null);
        String id = r == null ? "" : r.id();
        String before = lastRegion.put(p.getUniqueId(), id);
        if (r != null && !r.safeZone() && !id.equals(before)) {
            var pr = services.profiles().cached(p.getUniqueId()).orElse(null);
            if (pr != null) {
                boolean fresh = discover(p, pr, r);
                services.quests().onRegion(p, pr, id);
                if (fresh) return; // the discovery title replaces the region banner
            }
        }
        if (r == null || r.safeZone() || id.equals(before) || before == null) return;
        p.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text(r.displayName(), net.kyori.adventure.text.format.TextColor.fromHexString("#FFD24A"),
                        net.kyori.adventure.text.format.TextDecoration.BOLD),
                danger(p, r)
                        ? net.kyori.adventure.text.Component.text("⚠ Аюултай нутаг — Түвшин " + r.levelBand() + " зөвлөнө",
                                net.kyori.adventure.text.format.NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD)
                        : net.kyori.adventure.text.Component.text(r.description() + " · Түвшин " + r.levelBand(),
                                net.kyori.adventure.text.format.NamedTextColor.WHITE, net.kyori.adventure.text.format.TextDecoration.BOLD),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofMillis(700))));
    }

    /** The player is well below the region's level band (2+ levels under its minimum). */
    private boolean danger(Player p, RegionDefinition r) {
        int level = services.profiles().cached(p.getUniqueId()).map(pr -> pr.progression().level()).orElse(1);
        return level + 2 <= r.minLevel();
    }

    /** First visit ever to a wild region: its discovery EXP, once per player (persisted with the style row). */
    private boolean discover(Player p, mn.suld.api.profile.PlayerProfile pr, RegionDefinition r) {
        int index = WorldContent.REGIONS.indexOf(r);
        var style = services.styles().cached(p.getUniqueId()).orElse(null);
        if (index < 0 || style == null || r.discoveryExp() <= 0 || !style.discover(index)) return false;
        int from = pr.progression().level();
        var exp = services.progression().grantExp(pr, r.discoveryExp(), mn.suld.api.progression.ExpSource.DISCOVERY);
        p.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text("ШИНЭ НУТАГ", net.kyori.adventure.text.format.TextColor.fromHexString("#FFD24A"),
                        net.kyori.adventure.text.format.TextDecoration.BOLD),
                net.kyori.adventure.text.Component.text(r.displayName() + " · +" + r.discoveryExp() + " EXP",
                        net.kyori.adventure.text.format.NamedTextColor.WHITE, net.kyori.adventure.text.format.TextDecoration.BOLD),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofMillis(700))));
        p.sendMessage(mn.suld.plugin.ui.Messages.success("Шинэ нутаг нээлээ: " + r.displayName() + " (+" + r.discoveryExp() + " EXP)"));
        if (danger(p, r)) p.sendMessage(mn.suld.plugin.ui.Messages.error("⚠ Аюултай нутаг — Түвшин " + r.levelBand() + " зөвлөнө."));
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        if (exp.leveledUp()) mn.suld.plugin.ui.Presentation.levelUp(p, from, exp.after().level());
        services.hud().update(p, pr);
        return true;
    }

    /** Keep region mobs hostile; remove them in/near the city or when no player is within 80 blocks. */
    private void careTick() {
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (eligible(p)) players.add(p);
            announceRegion(p);
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() != World.Environment.NORMAL) continue;
            for (Entity e : w.getEntitiesByClasses(LivingEntity.class)) {
                if (!e.getScoreboardTags().contains(TAG) || !(e instanceof LivingEntity mob)) continue;
                Location l = mob.getLocation();
                if (services.city().near(w.getName(), l.getBlockX(), l.getBlockZ(), 6)) {
                    mob.remove();
                    continue;
                }
                boolean anyone = false;
                for (Player p : players) {
                    if (p.getWorld().equals(w) && p.getLocation().distanceSquared(l) < 80 * 80) {
                        anyone = true;
                        break;
                    }
                }
                if (!anyone) {
                    mob.remove();
                    continue;
                }
                MobService.keepHostile(mob, players, 24);
            }
        }
    }
}
