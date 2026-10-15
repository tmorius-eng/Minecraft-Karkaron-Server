package mn.suld.plugin.map;

import mn.suld.api.map.ScreenProjection;
import mn.suld.api.map.WaypointHud;
import mn.suld.api.region.Navigation;
import mn.suld.plugin.hud.HudService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The waypoint (docs/WORLD_MAP.md): one place a player is heading for — a pin, a place picked on the atlas, the latest
 * death. A bar names it with the arrow, the compass point and the distance (like the dungeon guide), the compass points
 * at it, and {@link ScreenMarkers} puts its card on the game screen («◆ Нэр» over «28.1 м» at the place, or at the
 * screen's edge with an arrow while it is out of view). Arriving within {@value #ARRIVED} blocks across the ground ends
 * it (a pin set from the atlas over an unloaded chunk holds a guessed height). Every change is told to the
 * {@link #onChange} listeners at once: the atlas marks the new waypoint; the minimap and the screen cards read it on
 * their next tick. Main thread.
 */
public final class WaypointGuide implements Listener {

    /** The screen cards' tag ({@link ScreenMarkers}; the waypoint's own label had it before them). */
    static final String TAG = "suld_waypoint";
    static final double ARRIVED = 6;
    /** The bar is rewritten this often (ticks), as often as the screen cards' distance. */
    static final long BAR_TICKS = ScreenMarkers.TEXT_TICKS;
    private static final TextColor GOLD = TextColor.fromHexString("#ECBE4C");

    /** A {@link Target#y()} meaning "on the ground there" (a place picked on the map, whose height the map does not hold). */
    public static final double GROUND = Double.NaN;

    /**
     * A waypoint: where, what it is called, the glyph shown before the name; {@code y} may be {@link #GROUND}. {@code pin}
     * is the id of the player's pin it leads to (null for a place, a death or typed coordinates), so removing that pin
     * ends the waypoint instead of leaving it pointing at a pin that no longer exists.
     */
    public record Target(String name, String glyph, String world, double x, double y, double z, UUID pin) {

        public Target(String name, String glyph, String world, double x, double y, double z) {
            this(name, glyph, world, x, y, z, null);
        }

        /** True when this waypoint leads to the pin {@code pinId}. */
        public boolean leadsTo(UUID pinId) {
            return pin != null && pin.equals(pinId);
        }
    }

    private final class Guide {
        final Target target;
        final BossBar bar;
        boolean shown;
        /** The compass target this waypoint set (handed back to the world spawn when it ends, if still ours). */
        Location compass;

        Guide(Target target) {
            this.target = target;
            this.bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        }
    }

    private final Plugin plugin;
    private final Map<UUID, Guide> guides = new HashMap<>();
    private final List<Consumer<Player>> listeners = new ArrayList<>();
    /** Set by SuldPlugin: true while the player is in a sky window (atlas, menu, skill tree), which the bar would cover. */
    public volatile java.util.function.Predicate<Player> busy = p -> false;

    public WaypointGuide(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, BAR_TICKS);
    }

    /** {@code l} hears of every waypoint set, stopped or reached, with the player whose it was. */
    public void onChange(Consumer<Player> l) {
        listeners.add(l);
    }

    private void changed(Player p) {
        for (Consumer<Player> l : listeners) l.accept(p);
    }

    /** The player's waypoint, or null. */
    public Target target(UUID player) {
        Guide g = guides.get(player);
        return g == null ? null : g.target;
    }

    public void guide(Player p, Target t) {
        end(p);
        Guide g = new Guide(t);
        guides.put(p.getUniqueId(), g);
        // the compass in the waypoint's own world (a waypoint set from another world points into the overworld)
        World tw = Bukkit.getWorld(t.world());
        g.compass = new Location(tw != null ? tw : p.getWorld(), t.x(), Double.isNaN(t.y()) ? 64 : t.y(), t.z());
        p.setCompassTarget(g.compass);
        p.playSound(p.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.8f, 1.2f);
        update(p, g);
        changed(p);
    }

    public void stop(Player p) {
        if (end(p)) changed(p);
    }

    /** Ends the player's waypoint (bar, compass) without telling the listeners; true when there was one. */
    private boolean end(Player p) {
        Guide g = guides.remove(p.getUniqueId());
        if (g == null) return false;
        mn.suld.plugin.ui.BossBars.hide(p, g.bar);
        // the compass stops pointing at a waypoint that is over, unless something else has pointed it since
        if (g.compass != null && g.compass.equals(p.getCompassTarget())) p.setCompassTarget(p.getWorld().getSpawnLocation());
        return true;
    }

    /** The player's pin {@code pinId} was removed: a waypoint leading to it ends (bar, screen card, compass, minimap mark). */
    public void pinRemoved(Player p, UUID pinId) {
        Guide g = guides.get(p.getUniqueId());
        if (g == null || !g.target.leadsTo(pinId)) return;
        stop(p);
        p.sendMessage(mn.suld.plugin.ui.Messages.info("Тэмдэг устсан тул чиглэл зогслоо."));
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(guides.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p);
        }
        guides.clear();
    }

    private void tick() {
        for (Map.Entry<UUID, Guide> e : new ArrayList<>(guides.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                guides.remove(e.getKey());
                continue;
            }
            update(p, e.getValue());
        }
    }

    private void update(Player p, Guide g) {
        Target t = g.target;
        if (busy.test(p) || p.isDead()) {
            mn.suld.plugin.ui.BossBars.hide(p, g.bar);
            g.shown = false;
            return;
        }
        if (!g.shown) {
            mn.suld.plugin.ui.BossBars.show(p, g.bar);
            g.shown = true;
        }
        Location l = p.getLocation();
        if (!l.getWorld().getName().equals(t.world())) {
            g.bar.name(Component.text(t.glyph() + " " + t.name() + " — энэ ертөнцөд алга", NamedTextColor.GRAY, TextDecoration.BOLD));
            return;
        }
        double dx = t.x() - l.getX(), dz = t.z() - l.getZ();
        if (Math.hypot(dx, dz) <= ARRIVED) {
            HudService.notice(p, Component.text(t.glyph() + " " + t.name() + " — ирлээ!", NamedTextColor.GREEN, TextDecoration.BOLD));
            p.playSound(l, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            stop(p);
            return;
        }
        double b = Navigation.bearing(dx, dz);
        String meters = WaypointHud.meters(ScreenProjection.distance(l.getX(), l.getY(), l.getZ(), t.x(), t.y(), t.z()));
        g.bar.name(Component.text(t.glyph() + " " + t.name() + "  ", GOLD, TextDecoration.BOLD)
                .append(Component.text(Navigation.arrow(b, Navigation.facing(l.getYaw())) + " ", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(Navigation.compass(b) + " · " + meters, NamedTextColor.WHITE, TextDecoration.BOLD)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        end(e.getPlayer());
    }
}
