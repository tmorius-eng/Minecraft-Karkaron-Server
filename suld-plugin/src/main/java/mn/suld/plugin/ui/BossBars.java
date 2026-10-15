package mn.suld.plugin.ui;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Every SÜLD boss bar goes through here, so the bars can step aside while a sky window is open: the atlas's title
 * (and the menu's and the skill tree's) sits at the top of the screen, where three quest, tutorial and event bars
 * covered «СҮЛДИЙН АТЛАС» (seen on a 1.21.11 client). A service shows and hides its bar as before; while the player
 * is {@linkplain #start quiet} the bar is only remembered, and when the window closes the bars still wanted come back.
 * A bar its service hid meanwhile (a quest done while the atlas was open) stays hidden.
 */
public final class BossBars {

    /** The bookkeeping, apart from the server so it is tested alone. Any thread. */
    static final class Gate {
        final Map<UUID, Set<BossBar>> wanted = new ConcurrentHashMap<>();
        final Set<UUID> quiet = ConcurrentHashMap.newKeySet();

        /** The service wants the bar shown; true when it goes to the client now (the player is not quiet). */
        boolean show(UUID id, BossBar bar) {
            wanted.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(bar);
            return !quiet.contains(id);
        }

        /** The service no longer wants the bar: it is never brought back. */
        void hide(UUID id, BossBar bar) {
            Set<BossBar> s = wanted.get(id);
            if (s != null) s.remove(bar);
        }

        /**
         * The player becomes quiet (or not): the bars to hide (or to show again) now, empty when nothing changed.
         */
        List<BossBar> quiet(UUID id, boolean on) {
            boolean changed = on ? quiet.add(id) : quiet.remove(id);
            if (!changed) return List.of();
            Set<BossBar> s = wanted.get(id);
            return s == null ? List.of() : new ArrayList<>(s);
        }

        boolean isQuiet(UUID id) {
            return quiet.contains(id);
        }

        void forget(UUID id) {
            wanted.remove(id);
            quiet.remove(id);
        }
    }

    static final Gate GATE = new Gate();
    private static volatile Predicate<Player> quietWhen = p -> false;
    private static BukkitTask task;

    private BossBars() {
    }

    /** Shows {@code bar} to {@code p}, or keeps it for later while a sky window is open. */
    public static void show(Player p, BossBar bar) {
        if (GATE.show(p.getUniqueId(), bar)) p.showBossBar(bar);
    }

    /** Hides {@code bar} from {@code p} for good (until shown again). */
    public static void hide(Player p, BossBar bar) {
        GATE.hide(p.getUniqueId(), bar);
        p.hideBossBar(bar);
    }

    /** Starts the gate: every tick a player for whom {@code quiet} turns true loses the bars, and gets them back after. */
    public static synchronized void start(Plugin plugin, Predicate<Player> quiet) {
        quietWhen = quiet;
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, BossBars::tick, 1L, 1L);
    }

    /** Plugin disable: whoever is quiet gets the bars back (the services hide theirs after), nothing is kept. */
    public static synchronized void stop() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : new ArrayList<>(GATE.quiet)) {
            Player p = Bukkit.getPlayer(id);
            for (BossBar b : GATE.quiet(id, false)) if (p != null) p.showBossBar(b);
        }
        GATE.wanted.clear();
        quietWhen = p -> false;
    }

    private static void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean q = quietWhen.test(p);
            if (q == GATE.isQuiet(id)) continue;
            for (BossBar b : GATE.quiet(id, q)) {
                if (q) p.hideBossBar(b);
                else p.showBossBar(b);
            }
        }
        GATE.wanted.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        GATE.quiet.removeIf(id -> Bukkit.getPlayer(id) == null);
    }
}
