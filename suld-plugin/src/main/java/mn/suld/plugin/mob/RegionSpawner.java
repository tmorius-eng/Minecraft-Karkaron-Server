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
 * A player whose story chapter asks for kills of one of the region's mobs gets that mob for half the spawns, so the
 * prey of the chapter is always there to find.
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

    /** The named area at a location (docs/world/AREAS.md), inside its region. */
    public java.util.Optional<mn.suld.api.region.Area> areaAt(Location l) {
        Location c = l.getWorld().getSpawnLocation();
        return mn.suld.api.region.Area.at(WorldContent.AREAS, l.getX() - c.getX(), l.getZ() - c.getZ());
    }

    private final java.util.Map<java.util.UUID, String> lastArea = new java.util.concurrent.ConcurrentHashMap<>();

    /** The region at a location (offsets from the world spawn = the Kharkhorum plaza). */
    public java.util.Optional<RegionDefinition> regionAt(Location l) {
        Location c = l.getWorld().getSpawnLocation();
        return regions.at(l.getX() - c.getX(), l.getZ() - c.getZ());
    }

    private boolean eligible(Player p) {
        return p.isValid() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                && p.getWorld().equals(Bukkit.getWorlds().get(0)) // the overworld only: not the dungeon halls
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
                String wanted = questPrey(p, region);
                String id;
                if (wanted != null && ThreadLocalRandom.current().nextDouble() < 0.5) id = wanted;
                else if (wanted == null && dist < OUTSKIRTS && ThreadLocalRandom.current().nextDouble() < 0.6) id = SuldContent.GOVIIN_CHONO.id();
                else id = region.mobIds().get(ThreadLocalRandom.current().nextInt(region.mobIds().size()));
                MobDefinition def = SuldContent.mobFor(id);
                if (def == null) continue;
                LivingEntity mob = services.mobs().spawn(def, at);
                if (mob != null) {
                    mob.addScoreboardTag(TAG);
                    regionMobs.add(mob.getUniqueId());
                    mob.setRemoveWhenFarAway(false);
                    mob.setPersistent(false); // a mob in an unloaded chunk is gone, never an untracked leftover
                    near++;
                }
            }
        }
    }

    /** The mob the player's active story chapter asks to kill, if this region spawns it; else null. */
    private String questPrey(Player p, RegionDefinition region) {
        var pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || !pr.questState().active()) return null;
        var d = services.quests().definition(pr.questState().questId()).orElse(null);
        if (d == null || d.type() != mn.suld.api.quest.QuestType.KILL_MOB) return null;
        return region.mobIds().contains(d.targetId()) ? d.targetId() : null;
    }

    /**
     * A surface spot 18–34 blocks from the player, in a loaded chunk, on solid dry ground, away from the city. The
     * ground is the highest block ignoring leaves: in a forest the old top-block search put mobs on the canopy (or
     * found no spot at all), so a chapter's prey never showed up under the trees.
     */
    private Location spot(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = p.getWorld();
        for (int attempt = 0; attempt < 14; attempt++) { // rivers and lakes: more tries before giving up
            double a = r.nextDouble(Math.PI * 2), d = 18 + r.nextDouble(16);
            int x = (int) Math.floor(p.getLocation().getX() + Math.cos(a) * d);
            int z = (int) Math.floor(p.getLocation().getZ() + Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            if (services.city().near(w.getName(), x, z, CITY_MARGIN)) continue;
            Block ground = w.getHighestBlockAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
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
        if (!p.getWorld().equals(Bukkit.getWorlds().get(0))) return; // regions exist in the overworld only
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
        // the named area (Туулын Хөндий, Хөдөө Арал, ...): its own banner and a one-time discovery reward
        mn.suld.api.region.Area area = r == null || r.safeZone() ? null : areaAt(p.getLocation()).orElse(null);
        String areaId = area == null ? "" : area.id();
        String areaBefore = lastArea.put(p.getUniqueId(), areaId);
        if (area != null && !areaId.equals(areaBefore)) {
            var pr = services.profiles().cached(p.getUniqueId()).orElse(null);
            if (pr != null && discoverArea(p, pr, area)) return;
            if (areaBefore != null) {
                areaBanner(p, area);
                return;
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

    private void areaBanner(Player p, mn.suld.api.region.Area a) {
        int level = services.profiles().cached(p.getUniqueId()).map(pr -> pr.progression().level()).orElse(1);
        boolean danger = level + 2 <= a.minLevel();
        p.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text(a.name(), net.kyori.adventure.text.format.TextColor.fromHexString("#FFD24A"),
                        net.kyori.adventure.text.format.TextDecoration.BOLD),
                net.kyori.adventure.text.Component.text((danger ? "⚠ Аюултай — " : a.description() + " · ") + "Түвшин " + a.levelBand(),
                        danger ? net.kyori.adventure.text.format.NamedTextColor.RED : net.kyori.adventure.text.format.NamedTextColor.WHITE,
                        net.kyori.adventure.text.format.TextDecoration.BOLD),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofMillis(2500),
                        java.time.Duration.ofMillis(700))));
    }

    /** First visit to a named area: a small EXP reward, once (discovery bits 16..63 of the style row). */
    private boolean discoverArea(Player p, mn.suld.api.profile.PlayerProfile pr, mn.suld.api.region.Area a) {
        var style = services.styles().cached(p.getUniqueId()).orElse(null);
        if (style == null || !style.discover(a.index())) return false;
        int from = pr.progression().level();
        var exp = services.progression().grantExp(pr, a.discoveryExp(), mn.suld.api.progression.ExpSource.DISCOVERY);
        p.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text("ШИНЭ ГАЗАР: " + a.name(), net.kyori.adventure.text.format.TextColor.fromHexString("#FFD24A"),
                        net.kyori.adventure.text.format.TextDecoration.BOLD),
                net.kyori.adventure.text.Component.text(a.description() + " · +" + a.discoveryExp() + " EXP",
                        net.kyori.adventure.text.format.NamedTextColor.WHITE, net.kyori.adventure.text.format.TextDecoration.BOLD),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofMillis(700))));
        p.sendMessage(mn.suld.plugin.ui.Messages.success("Шинэ газар нээлээ: " + a.name() + " (" + regionName(a.regionId()) + ", түвшин "
                + a.levelBand() + ") +" + a.discoveryExp() + " EXP"));
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        if (exp.leveledUp()) mn.suld.plugin.ui.Presentation.levelUp(p, from, exp.after().level());
        services.hud().update(p, pr);
        return true;
    }

    private static String regionName(String id) {
        for (RegionDefinition r : WorldContent.REGIONS) if (r.id().equals(id)) return r.displayName();
        return id;
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

    /** Region mobs alive now (UUIDs), so care does not scan every entity of the world. */
    private final java.util.Set<java.util.UUID> regionMobs = new java.util.LinkedHashSet<>();

    /** Keep region mobs hostile; remove them in/near the city or when no player is within 80 blocks. */
    private void careTick() {
        lastRegion.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        lastArea.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (eligible(p)) players.add(p);
            announceRegion(p);
        }
        // only the mobs this spawner made (it used to scan every living entity of every world every 2 s)
        for (java.util.Iterator<java.util.UUID> it = regionMobs.iterator(); it.hasNext(); ) {
            Entity e = Bukkit.getEntity(it.next());
            if (!(e instanceof LivingEntity mob) || !mob.isValid() || mob.isDead()) {
                it.remove();
                continue;
            }
            {
                World w = mob.getWorld();
                Location l = mob.getLocation();
                if (services.city().near(w.getName(), l.getBlockX(), l.getBlockZ(), 6)) {
                    mob.remove();
                    it.remove();
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
                    it.remove();
                    continue;
                }
                MobService.keepHostile(mob, players, 24);
            }
        }
    }
}
