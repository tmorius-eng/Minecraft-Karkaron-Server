package mn.suld.plugin.ui;

import mn.suld.plugin.gui.ClassSelectionHolder;
import mn.suld.plugin.gui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The one answer to "is a full-screen SÜLD view up for this player?": the death screen, a sky window (/map, /menu,
 * /skills) or a SÜLD chest menu (the welcome screen, the hub, the class choice). The HUD steps aside while it is,
 * as one policy: the boss bars ({@link BossBars}), the corner minimap, the screen markers and the «✦ SÜLD ✦» sidebar.
 * <p>
 * Before this, only the three sky windows hid anything. The welcome chest of 2026-10-15 opened under three boss bars,
 * the minimap, a waypoint card in the chat and the sidebar. It is polled once a tick rather than following
 * InventoryOpen/Close: a menu-to-menu click closes and reopens in the same tick, and a sky window reopens a chest a
 * tick later. Sampling gives no flicker, and ESC, quit, death, respawn, teleport and world changes all come out right
 * with no restore code per path. {@link #onChange} tells the views that do not poll (the sidebar) the tick it flips.
 */
public final class UiFocus {

    private static volatile Predicate<Player> sky = p -> false;
    private static final Map<UUID, Boolean> last = new ConcurrentHashMap<>();
    private static final List<Consumer<Player>> listeners = new CopyOnWriteArrayList<>();
    private static BukkitTask task;

    private UiFocus() {
    }

    /** Whether a sky window is open (set once by SuldPlugin: the atlas, the menu sky, the skill sky). */
    public static void sky(Predicate<Player> openSky) {
        sky = openSky == null ? p -> false : openSky;
    }

    public static boolean inSky(Player p) {
        return sky.test(p);
    }

    /** A SÜLD chest is open: a {@link Menu} (welcome, hub fallback, shop, quest book…) or the class choice. */
    public static boolean inChest(Player p) {
        return isSuldChest(p.getOpenInventory().getTopInventory().getHolder(false));
    }

    static boolean isSuldChest(Object holder) {
        return holder instanceof Menu || holder instanceof ClassSelectionHolder;
    }

    public static boolean isModal(Player p) {
        return modal(p.isDead(), inSky(p), inChest(p));
    }

    static boolean modal(boolean dead, boolean sky, boolean chest) {
        return dead || sky || chest;
    }

    /** Called (main thread) the tick a player's {@link #isModal} flips, either way. */
    public static void onChange(Consumer<Player> listener) {
        listeners.add(listener);
    }

    /** Records {@code now}; true when it differs from the last sample (never on a player's first sample). */
    static boolean flipped(Map<UUID, Boolean> last, UUID id, boolean now) {
        Boolean was = last.put(id, now);
        return was != null && was != now;
    }

    public static synchronized void start(Plugin plugin) {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!flipped(last, p.getUniqueId(), isModal(p))) continue;
                for (Consumer<Player> l : listeners) {
                    try {
                        l.accept(p);
                    } catch (RuntimeException ex) {
                        plugin.getLogger().warning("UiFocus listener: " + ex);
                    }
                }
            }
            last.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);   // left: a rejoin starts fresh
        }, 1L, 1L);
    }

    public static synchronized void stop() {
        if (task != null) task.cancel();
        task = null;
        last.clear();
        listeners.clear();
        sky = p -> false;
    }
}
