package mn.suld.plugin.quest;

import mn.suld.api.loot.LootTable;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestState;
import mn.suld.api.region.Navigation;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.region.RegionIndex;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.DungeonContent;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.content.WorldContent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wynncraft-style quest tracker: a boss bar with the active chapter, its progress, and an arrow + distance towards
 * the region where the objective is (or a tick once you are there). Hidden in dungeons and while the chapter has no
 * place (level goals). {@code /quest track} toggles it per player.
 */
public final class QuestTracker implements Listener {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");

    private final Plugin plugin;
    private final SuldServices services;
    private final RegionIndex regions = new RegionIndex(WorldContent.REGIONS);
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    public QuestTracker(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    /** Toggle for {@code /quest track}; returns true if the tracker is now shown. */
    public boolean toggle(Player p) {
        if (hidden.remove(p.getUniqueId())) return true;
        hidden.add(p.getUniqueId());
        hide(p);
        return false;
    }

    /** Whether the tracker bar is on for {@code p} ({@code /quest track} turns it off). */
    public boolean shown(Player p) {
        return !hidden.contains(p.getUniqueId());
    }

    /** The wild region an objective points at, if it has one. */
    public static Optional<RegionDefinition> targetRegion(QuestDefinition d) {
        String id = switch (d.type()) {
            case DISCOVER_LOCATION -> d.targetId();
            case COMPLETE_DUNGEON -> DungeonContent.regionOf(d.targetId());
            case KILL_MOB -> regionWithMob(d.targetId());
            case COLLECT_ITEM -> regionDropping(d.targetId());
            case REACH_LEVEL -> null;
        };
        if (id == null) return Optional.empty();
        for (RegionDefinition r : WorldContent.REGIONS) if (r.id().equals(id)) return Optional.of(r);
        return Optional.empty();
    }

    private static String regionWithMob(String mobId) {
        for (RegionDefinition r : WorldContent.REGIONS) if (r.mobIds().contains(mobId)) return r.id();
        return null;
    }

    private static String regionDropping(String itemId) {
        for (RegionDefinition r : WorldContent.REGIONS) {
            for (String mobId : r.mobIds()) {
                var mob = SuldContent.mobFor(mobId);
                LootTable t = mob == null ? null : SuldContent.lootTableFor(mob.lootTableId());
                if (t != null && t.mayDrop(itemId)) return r.id();
            }
        }
        return null;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Component line = hidden.contains(p.getUniqueId()) ? null : line(p);
            if (line == null) {
                hide(p);
                continue;
            }
            BossBar bar = bars.computeIfAbsent(p.getUniqueId(), id -> {
                BossBar b = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                p.showBossBar(b);
                return b;
            });
            bar.name(line);
            PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
            QuestDefinition d = pr == null ? null : services.quests().definition(pr.questState().questId()).orElse(null);
            if (d != null) bar.progress(Math.max(0f, Math.min(1f, (float) pr.questState().progress() / d.requiredCount())));
        }
    }

    private Component line(Player p) {
        if (services.dungeons().isInAnyRun(p.getUniqueId()) || p.getWorld().getEnvironment() != World.Environment.NORMAL) return null;
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || !pr.hasSelectedClass()) return null;
        QuestState s = pr.questState();
        QuestDefinition d = services.quests().definition(s.questId()).orElse(null);
        if (d == null || !s.active()) return null;
        Component head = Component.text("✦ " + d.title() + " ", GOLD, TextDecoration.BOLD)
                .append(Component.text(s.progress() + "/" + d.requiredCount(), NamedTextColor.WHITE, TextDecoration.BOLD));
        Location l = p.getLocation();
        if (d.type() == mn.suld.api.quest.QuestType.COMPLETE_DUNGEON) {
            // a dungeon chapter points at the dungeon's gate itself (docs/world/DUNGEON_HALLS.md)
            Location gate = services.dungeons().halls().flatMap(h -> h.gate(d.targetId())).orElse(null);
            if (gate != null && gate.getWorld().equals(l.getWorld())) {
                double gx = gate.getX() - l.getX(), gz = gate.getZ() - l.getZ();
                long gd = Math.round(Math.hypot(gx, gz));
                var def = SuldContent.dungeonFor(d.targetId());
                String name = def == null ? "Агуйн хаалга" : def.displayName() + "-ийн хаалга";
                if (gd <= 16) return head.append(Component.text("  ⚔ " + name + " — энд! Хаалгыг дар", NamedTextColor.GREEN, TextDecoration.BOLD));
                double gb = Navigation.bearing(gx, gz);
                return head.append(Component.text("  " + Navigation.arrow(gb, Navigation.facing(l.getYaw())) + " ", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text("⚔ " + name + " · " + Navigation.compass(gb) + " · " + gd + "м", NamedTextColor.WHITE, TextDecoration.BOLD));
            }
        }
        RegionDefinition target = targetRegion(d).orElse(null);
        if (target == null) return head;
        Location spawn = p.getWorld().getSpawnLocation();
        double dx = l.getX() - spawn.getX(), dz = l.getZ() - spawn.getZ();
        RegionDefinition here = regions.at(dx, dz).orElse(null);
        if (here != null && here.id().equals(target.id())) {
            // in the right region: a kill chapter points at the nearest prey (they spawn around the player)
            if (d.type() == mn.suld.api.quest.QuestType.KILL_MOB) {
                org.bukkit.entity.Entity prey = nearestPrey(p, d.targetId());
                var def = SuldContent.mobFor(d.targetId());
                String name = def == null ? "Бай" : def.displayName();
                if (prey != null) {
                    double px = prey.getLocation().getX() - l.getX(), pz = prey.getLocation().getZ() - l.getZ();
                    double pb = Navigation.bearing(px, pz);
                    return head.append(Component.text("  " + Navigation.arrow(pb, Navigation.facing(l.getYaw())) + " ", NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.text("⚔ " + name + " · " + Math.round(Math.hypot(px, pz)) + "м", NamedTextColor.WHITE, TextDecoration.BOLD));
                }
                return head.append(Component.text("  ✔ " + target.displayName() + " — " + name + " ойролцоо гарч ирнэ, хүлээ", NamedTextColor.GREEN, TextDecoration.BOLD));
            }
            return head.append(Component.text("  ✔ " + target.displayName() + " — энд байна", NamedTextColor.GREEN, TextDecoration.BOLD));
        }
        double[] w = Navigation.waypoint(target.shape());
        double tx = w[0] - dx, tz = w[1] - dz;
        double bearing = Navigation.bearing(tx, tz);
        long dist = Math.round(Math.hypot(tx, tz));
        return head.append(Component.text("  " + Navigation.arrow(bearing, Navigation.facing(l.getYaw())) + " ", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(target.displayName() + " · " + Navigation.compass(bearing) + " · " + dist + "м", NamedTextColor.WHITE, TextDecoration.BOLD));
    }

    /** The closest living SÜLD mob of {@code mobId} within 64 blocks, or null. */
    private org.bukkit.entity.Entity nearestPrey(Player p, String mobId) {
        org.bukkit.entity.Entity best = null;
        double bd = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity e : p.getNearbyEntities(64, 32, 64)) {
            if (!(e instanceof org.bukkit.entity.LivingEntity le) || le.isDead()) continue;
            if (!mobId.equals(services.mobs().mobId(e).orElse(null))) continue;
            double dd = e.getLocation().distanceSquared(p.getLocation());
            if (dd < bd) {
                bd = dd;
                best = e;
            }
        }
        return best;
    }

    /** Id of the wild region at the player's feet, or null (city, outside the map). */
    public String regionIdAt(Player p) {
        Location spawn = p.getWorld().getSpawnLocation();
        return regions.at(p.getLocation().getX() - spawn.getX(), p.getLocation().getZ() - spawn.getZ())
                .filter(r -> !r.safeZone()).map(RegionDefinition::id).orElse(null);
    }

    /** Plugin disable: remove every tracker bar. */
    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) hide(p);
    }

    private void hide(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hide(e.getPlayer());
    }
}
