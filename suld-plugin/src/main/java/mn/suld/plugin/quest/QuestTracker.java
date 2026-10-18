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
        boolean on = services.styles().toggleSetting(p, mn.suld.api.style.PlayerSetting.QUEST_TRACKER);
        if (!on) hide(p);
        return on;
    }

    /** Whether the tracker bar is on for {@code p} ({@code /quest track} or the Тохиргоо page turns it off). */
    public boolean shown(Player p) {
        return !hidden.contains(p.getUniqueId()) && services.styles().setting(p.getUniqueId(), mn.suld.api.style.PlayerSetting.QUEST_TRACKER);
    }

    /** The wild region an objective points at, if it has one. */
    public static Optional<RegionDefinition> targetRegion(QuestDefinition d) {
        String own = mn.suld.plugin.content.QuestContent.lore(d.id()).region();
        String id = own != null ? own : switch (d.type()) {
            case DISCOVER_LOCATION -> d.targetId().startsWith("site.") ? siteRegion(d.targetId()) : d.targetId();
            case COMPLETE_DUNGEON -> DungeonContent.regionOf(d.targetId());
            case KILL_MOB -> regionWithMob(d.targetId());
            case COLLECT_ITEM -> regionDropping(d.targetId());
            case OVOO_RITUAL -> d.targetId();
            case REACH_LEVEL, INSPECT_CLUE, CRAFT_ITEM -> null; // a prop or the smith: the chapter's own "region" names it
        };
        if (id == null) return Optional.empty();
        for (RegionDefinition r : WorldContent.REGIONS) if (r.id().equals(id)) return Optional.of(r);
        return Optional.empty();
    }

    /** The region of a historic site's area. */
    private static String siteRegion(String siteId) {
        return mn.suld.plugin.content.Content.pack().historicSites().stream().filter(x -> x.id().equals(siteId)).findFirst()
                .flatMap(x -> WorldContent.AREAS.stream().filter(a -> a.id().equals(x.areaId())).findFirst())
                .map(mn.suld.api.region.Area::regionId).orElse(null);
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
            Component line = shown(p) ? line(p) : null;
            if (line == null) {
                hide(p);
                continue;
            }
            BossBar bar = bars.computeIfAbsent(p.getUniqueId(), id -> {
                BossBar b = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                mn.suld.plugin.ui.BossBars.show(p, b);
                return b;
            });
            bar.name(line);
            PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
            QuestDefinition d = pr == null ? null : services.quests().definition(pr.questState().questId()).orElse(null);
            if (d != null) bar.progress(Math.max(0f, Math.min(1f, (float) pr.questState().progress() / d.requiredCount())));
        }
    }

    /** Player id -> {checked at ms, refusal text or null}: the gate check reads gear power, so it is redone every 2 s. */
    private static final java.util.Map<java.util.UUID, Object[]> GATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * QS-02: why the gate of a dungeon chapter would refuse {@code p} right now (level, the previous rung, the story or
     * gear-power gate — DungeonService.soloEntryRefusal), as plain text; null when it would open or the chapter is not
     * a dungeon's.
     */
    public static String gateRefusal(SuldServices services, Player p, QuestDefinition d) {
        if (d == null || d.type() != mn.suld.api.quest.QuestType.COMPLETE_DUNGEON || services.dungeons() == null) return null;
        var def = SuldContent.dungeonFor(d.targetId());
        if (def == null) return null;
        long now = System.currentTimeMillis();
        Object[] hit = GATE_CACHE.get(p.getUniqueId());
        if (hit != null && now - (long) hit[0] < 2_000 && d.targetId().equals(hit[2])) return (String) hit[1];
        Component why = services.dungeons().soloEntryRefusal(p, def);
        String text = why == null ? null : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(why)
                .replaceFirst("^[✖✗⚠!\\s]+", "");
        GATE_CACHE.put(p.getUniqueId(), new Object[] {now, text, d.targetId()});
        return text;
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
            // QS-02: a gate that would refuse the player says what it wants, not "here! press the gate"
            String refusal = gateRefusal(services, p, d);
            if (refusal != null) return head.append(Component.text("  ⛔ " + refusal, NamedTextColor.RED, TextDecoration.BOLD));
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
        if (d.type() == mn.suld.api.quest.QuestType.DISCOVER_LOCATION && d.targetId().startsWith("site.") && services.historicSites != null) {
            // a historic-site chapter points at the site itself (docs/world/HISTORIC_SITES.md)
            Location site = services.historicSites.where(d.targetId()).orElse(null);
            if (site != null && site.getWorld().equals(l.getWorld())) {
                double sx = site.getX() - l.getX(), sz = site.getZ() - l.getZ();
                long sd = Math.round(Math.hypot(sx, sz));
                String name = mn.suld.plugin.content.Content.pack().historicSites().stream().filter(x -> x.id().equals(d.targetId()))
                        .findFirst().map(mn.suld.api.world.site.HistoricSite::name).orElse("Түүхэн газар");
                double sb = Navigation.bearing(sx, sz);
                return head.append(Component.text("  " + Navigation.arrow(sb, Navigation.facing(l.getYaw())) + " ", NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text("✦ " + name + " · " + Navigation.compass(sb) + " · " + sd + "м", NamedTextColor.WHITE, TextDecoration.BOLD));
            }
        }
        RegionDefinition target = targetRegion(d).orElse(null);
        if (target == null) return head;
        Location spawn = p.getWorld().getSpawnLocation();
        double dx = l.getX() - spawn.getX(), dz = l.getZ() - spawn.getZ();
        RegionDefinition here = regions.at(dx, dz).orElse(null);
        // the city's built footprint (plus the spawner's margin) spawns nothing, whatever region its streets fall in:
        // inside it the bar keeps pointing out of town instead of saying "wait here" (audit 2026-10-11 ONB-004)
        boolean inTown = services.city() != null
                && services.city().near(l.getWorld().getName(), l.getBlockX(), l.getBlockZ(), mn.suld.plugin.mob.RegionSpawner.CITY_MARGIN);
        if (here != null && here.id().equals(target.id()) && !inTown) {
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
        if (bar != null) mn.suld.plugin.ui.BossBars.hide(p, bar);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hide(e.getPlayer());
        GATE_CACHE.remove(e.getPlayer().getUniqueId());
    }
}
