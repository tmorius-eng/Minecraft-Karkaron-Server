package mn.suld.plugin.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Bridge to GrimAC (an open-source anticheat that runs as its own plugin; SÜLD has no compile dependency on it and
 * bundles none of its code). When GrimAC is installed, every Grim flag passes through here:
 * <ul>
 *   <li>a flag on a player inside a SÜLD movement window ({@link MovementExemptions}) for a check listed in
 *       {@code anticheat.grim.exempt-checks} is cancelled (no violation, no setback): SÜLD moved that player;</li>
 *   <li>every other flag is left to Grim untouched: Grim's own alerts and punishments decide.</li>
 * </ul>
 * Each flag is counted per check (outside a window / forgiven / inside a window but not forgiven) so
 * {@code /suld grim} shows what SÜLD movement actually triggers on this server, and the list can be narrowed to it.
 *
 * <p>Grim's {@code FlagEvent} is a Bukkit event fired from its network threads; it is registered by name through the
 * Bukkit event API and read by reflection, so SÜLD runs unchanged without Grim.
 */
public final class GrimBridge implements Listener {

    static final String GRIM_PLUGIN = "GrimAC";
    static final String FLAG_EVENT = "ac.grim.grimac.api.events.FlagEvent";

    private final Plugin plugin;
    private final MovementExemptions movement;
    private volatile Set<String> exemptChecks = Set.of();
    private final Map<String, LongAdder[]> counts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> reasons = new ConcurrentHashMap<>();
    private volatile boolean hooked;
    private boolean listening;
    private volatile String state = "GrimAC суугаагүй";

    private Method getUser, getUniqueId, getCheck, getCheckName, setCancelled;

    public GrimBridge(Plugin plugin, MovementExemptions movement) {
        this.plugin = plugin;
        this.movement = movement;
    }

    /** Reads the config and hooks Grim if it is installed and enabled. Safe to call again on reload. */
    public void start() {
        var cfg = plugin.getConfig();
        exemptChecks = cfg.getStringList("anticheat.grim.exempt-checks").stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (!listening) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listening = true;
        }
        if (hooked) return;
        Plugin grim = Bukkit.getPluginManager().getPlugin(GRIM_PLUGIN);
        if (grim == null || !grim.isEnabled()) {
            state = "GrimAC суугаагүй — SÜLD чөлөөлөлт идэвхгүй";
            return;
        }
        if (!cfg.getBoolean("anticheat.grim.enabled", true)) {
            state = "anticheat.grim.enabled: false — Grim бүх зүйлийг өөрөө шийднэ";
            return;
        }
        try {
            ClassLoader cl = grim.getClass().getClassLoader();
            Class<? extends Event> flag = Class.forName(FLAG_EVENT, true, cl).asSubclass(Event.class);
            getUser = flag.getMethod("getUser");
            getCheck = flag.getMethod("getCheck");
            setCancelled = flag.getMethod("setCancelled", boolean.class);
            getUniqueId = Class.forName("ac.grim.grimac.api.GrimIdentity", true, cl).getMethod("getUniqueId");
            getCheckName = Class.forName("ac.grim.grimac.api.AbstractCheck", true, cl).getMethod("getCheckName");
            EventExecutor exec = (listener, event) -> {
                if (flag.isInstance(event)) onFlag(event);
            };
            Bukkit.getPluginManager().registerEvent(flag, this, EventPriority.HIGH, exec, plugin, false);
            hooked = true;
            state = "GrimAC " + grim.getPluginMeta().getVersion() + " холбогдсон";
            plugin.getLogger().info("[anticheat] " + state + "; movement checks forgiven inside SÜLD windows: " + exemptChecks);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ex) {
            state = "GrimAC-ийн API танигдсангүй (" + ex.getClass().getSimpleName() + ") — Grim бүх зүйлийг өөрөө шийднэ";
            plugin.getLogger().warning("[anticheat] " + state + ": " + ex.getMessage());
        }
    }

    private void onFlag(Event event) {
        String check;
        UUID player;
        try {
            Object user = getUser.invoke(event);
            player = (UUID) getUniqueId.invoke(user);
            check = (String) getCheckName.invoke(getCheck.invoke(event));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return; // never break Grim's own handling
        }
        if (check == null) check = "?";
        String why = player == null ? null : movement.active(player);
        int slot;
        if (why == null) {
            slot = 0;
        } else if (exemptChecks.contains(check.toLowerCase(Locale.ROOT))) {
            try {
                setCancelled.invoke(event, true);
                slot = 1;
                reasons.computeIfAbsent(why, k -> new LongAdder()).increment();
            } catch (ReflectiveOperationException | RuntimeException ex) {
                slot = 2;
            }
        } else {
            slot = 2;
        }
        counts.computeIfAbsent(check, k -> new LongAdder[]{new LongAdder(), new LongAdder(), new LongAdder()})[slot].increment();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        movement.forget(e.getPlayer().getUniqueId());
    }

    public boolean hooked() {
        return hooked;
    }

    public void reset() {
        counts.clear();
        reasons.clear();
    }

    /** Lines for {@code /suld grim}: state, the forgiven list, and the per-check counts. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add(state);
        out.add("Чөлөөлөх шалгалт: " + (exemptChecks.isEmpty() ? "—" : String.join(", ", new java.util.TreeSet<>(exemptChecks))));
        if (counts.isEmpty()) {
            out.add("Flag алга.");
            return out;
        }
        out.add("Шалгалт: гадна / уучилсан (SÜLD хөдөлгөөн) / цонхонд боловч уучлаагүй");
        new TreeMap<>(counts).forEach((k, v) -> out.add("  " + k + ": " + v[0].sum() + " / " + v[1].sum() + " / " + v[2].sum()));
        if (!reasons.isEmpty()) {
            out.add("Уучилсан шалтгаан: " + new TreeMap<>(reasons).entrySet().stream()
                    .map(e -> e.getKey() + " " + e.getValue().sum()).collect(Collectors.joining(", ")));
        }
        return out;
    }

    /** Raw counts per check: [outside a window, forgiven, inside a window but not forgiven]. For tests and QA. */
    public Map<String, long[]> snapshot() {
        Map<String, long[]> m = new TreeMap<>();
        counts.forEach((k, v) -> m.put(k, new long[]{v[0].sum(), v[1].sum(), v[2].sum()}));
        return m;
    }
}
