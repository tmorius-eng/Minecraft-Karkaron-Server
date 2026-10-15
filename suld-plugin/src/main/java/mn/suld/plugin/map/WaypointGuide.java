package mn.suld.plugin.map;

import mn.suld.api.region.Navigation;
import mn.suld.plugin.hud.HudService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The waypoint (docs/WORLD_MAP.md): one place a player is heading for — a pin, a place picked on the atlas, the latest
 * death. A bar names it with the arrow, the compass point and the distance (like the dungeon guide), and a label
 * «◆ Нэр · 412 м» floats toward it, seen through blocks and by its owner only: at the place itself within
 * {@value #NEAR} blocks, otherwise {@value #NEAR} blocks along the way, larger so it reads the same. Arriving within
 * {@value #ARRIVED} blocks ends it. Main thread.
 */
public final class WaypointGuide implements Listener {

    static final String TAG = "suld_waypoint";
    static final double NEAR = 24, ARRIVED = 6, LABEL_SCALE = 0.125;
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
        TextDisplay label;
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
    /** Set by SuldPlugin: true while the player is in a sky window (atlas, menu, skill tree), which the bar and the label would cover. */
    public volatile java.util.function.Predicate<Player> busy = p -> false;

    public WaypointGuide(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    /** The player's waypoint, or null. */
    public Target target(UUID player) {
        Guide g = guides.get(player);
        return g == null ? null : g.target;
    }

    public void guide(Player p, Target t) {
        stop(p);
        Guide g = new Guide(t);
        guides.put(p.getUniqueId(), g);
        // the compass in the waypoint's own world (a waypoint set from another world points into the overworld)
        World tw = Bukkit.getWorld(t.world());
        g.compass = new Location(tw != null ? tw : p.getWorld(), t.x(), Double.isNaN(t.y()) ? 64 : t.y(), t.z());
        p.setCompassTarget(g.compass);
        p.playSound(p.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.8f, 1.2f);
        update(p, g);
    }

    public void stop(Player p) {
        Guide g = guides.remove(p.getUniqueId());
        if (g == null) return;
        mn.suld.plugin.ui.BossBars.hide(p, g.bar);
        if (g.label != null && g.label.isValid()) g.label.remove();
        // the compass stops pointing at a waypoint that is over, unless something else has pointed it since
        if (g.compass != null && g.compass.equals(p.getCompassTarget())) p.setCompassTarget(p.getWorld().getSpawnLocation());
    }

    /** The player's pin {@code pinId} was removed: a waypoint leading to it ends (bar, label, compass, minimap mark). */
    public void pinRemoved(Player p, UUID pinId) {
        Guide g = guides.get(p.getUniqueId());
        if (g == null || !g.target.leadsTo(pinId)) return;
        stop(p);
        p.sendMessage(mn.suld.plugin.ui.Messages.info("Тэмдэг устсан тул чиглэл зогслоо."));
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(guides.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stop(p);
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
            hideLabel(g);
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
            hideLabel(g);
            return;
        }
        double dx = t.x() - l.getX(), dz = t.z() - l.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist <= ARRIVED) {
            HudService.notice(p, Component.text(t.glyph() + " " + t.name() + " — ирлээ!", NamedTextColor.GREEN, TextDecoration.BOLD));
            p.playSound(l, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            stop(p);
            return;
        }
        double b = Navigation.bearing(dx, dz);
        String meters = Math.round(dist) + " м";
        g.bar.name(Component.text(t.glyph() + " " + t.name() + "  ", GOLD, TextDecoration.BOLD)
                .append(Component.text(Navigation.arrow(b, Navigation.facing(l.getYaw())) + " ", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(Navigation.compass(b) + " · " + meters, NamedTextColor.WHITE, TextDecoration.BOLD)));
        // the label: at the place when near, else NEAR blocks along the way at eye height; its size grows with the
        // distance so it reads the same on screen (seen on a 1080p client: 3× at 24 blocks is a 20 px line)
        Location eye = p.getEyeLocation();
        Location at;
        float scale;
        if (dist <= NEAR) {
            // a place from the map stands on the ground: its chunk is loaded this close
            World w = eye.getWorld();
            int bx = (int) Math.floor(t.x()), bz = (int) Math.floor(t.z());
            double y = !Double.isNaN(t.y()) ? t.y() : w.isChunkLoaded(bx >> 4, bz >> 4) ? w.getHighestBlockYAt(bx, bz) + 1 : eye.getY() - 1.6;
            at = new Location(w, t.x(), y + 2.2, t.z());
            scale = (float) Math.max(1.0, dist * LABEL_SCALE);
        } else {
            Vector dir = new Vector(dx, 0, dz).normalize().multiply(NEAR);
            at = eye.clone().add(dir).add(0, 0.6, 0);
            scale = (float) (NEAR * LABEL_SCALE);
        }
        Component text = Component.text(t.glyph() + " " + t.name(), GOLD, TextDecoration.BOLD).append(Component.newline())
                .append(Component.text(meters, NamedTextColor.WHITE));
        if (g.label == null || !g.label.isValid() || g.label.getWorld() != eye.getWorld()) {
            hideLabel(g);
            World w = eye.getWorld();
            g.label = w.spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.addScoreboardTag(TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setBackgroundColor(Color.fromARGB(110, 10, 16, 32));
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setTeleportDuration(5);
                d.setViewRange(4f);
                d.text(text);
            });
            p.showEntity(plugin, g.label);
        } else {
            g.label.teleport(at);
            g.label.text(text);
        }
        g.label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
    }

    private static void hideLabel(Guide g) {
        if (g.label != null && g.label.isValid()) g.label.remove();
        g.label = null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stop(e.getPlayer());
    }

    /** A label left behind by a crash: never kept. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (var en : e.getEntities()) if (en.getScoreboardTags().contains(TAG)) en.remove();
    }
}
