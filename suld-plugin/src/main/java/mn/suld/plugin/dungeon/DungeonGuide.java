package mn.suld.plugin.dungeon;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.region.Navigation;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The way to a dungeon gate chosen in the dungeon window ({@code DungeonMenu}): a purple boss bar with an arrow,
 * the compass direction and the distance, until the player reaches the gate (or enters a run, or 30 minutes pass).
 */
public final class DungeonGuide implements Listener {

    private static final long MAX_MS = 30 * 60_000L;
    private static final double ARRIVED = 10;

    private record Target(String dungeonId, long since, BossBar bar) {
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<UUID, Target> targets = new ConcurrentHashMap<>();

    public DungeonGuide(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void guide(Player p, String dungeonId) {
        stop(p);
        BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        targets.put(p.getUniqueId(), new Target(dungeonId, System.currentTimeMillis(), bar));
        p.showBossBar(bar);
        update(p, targets.get(p.getUniqueId()));
    }

    public boolean guiding(Player p) {
        return targets.containsKey(p.getUniqueId());
    }

    public String target(Player p) {
        Target t = targets.get(p.getUniqueId());
        return t == null ? null : t.dungeonId();
    }

    public void stop(Player p) {
        Target t = targets.remove(p.getUniqueId());
        if (t != null) p.hideBossBar(t.bar());
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) stop(p);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Target> e : targets.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            Target t = e.getValue();
            if (now - t.since() > MAX_MS || services.dungeons().isInAnyRun(p.getUniqueId())) {
                stop(p);
                continue;
            }
            update(p, t);
        }
    }

    private void update(Player p, Target t) {
        DungeonDefinition def = SuldContent.dungeonFor(t.dungeonId());
        Location gate = services.dungeons().halls().flatMap(h -> h.gate(t.dungeonId())).orElse(null);
        if (def == null || gate == null || !gate.getWorld().equals(p.getWorld())) {
            t.bar().name(Component.text("⚔ " + (def == null ? "Агуй" : def.displayName()) + " — хаалга энэ ертөнцөд алга", NamedTextColor.GRAY, TextDecoration.BOLD));
            return;
        }
        Location l = p.getLocation();
        double dx = gate.getX() - l.getX(), dz = gate.getZ() - l.getZ();
        long dist = Math.round(Math.hypot(dx, dz));
        if (dist <= ARRIVED) {
            p.sendActionBar(Component.text("⚔ " + def.displayName() + " — хаалга энд! Хаалгыг дар", NamedTextColor.GREEN, TextDecoration.BOLD));
            stop(p);
            return;
        }
        double b = Navigation.bearing(dx, dz);
        t.bar().name(Component.text("⚔ " + def.displayName() + "  ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(Navigation.arrow(b, Navigation.facing(l.getYaw())) + " ", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(Navigation.compass(b) + " · " + dist + "м", NamedTextColor.WHITE, TextDecoration.BOLD)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stop(e.getPlayer());
    }
}
