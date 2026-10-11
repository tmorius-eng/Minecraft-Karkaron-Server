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
 *   <li>a flag on a player inside a SÜLD window ({@link MovementExemptions}) for a check listed for that kind of
 *       window is cancelled (no violation, no setback): {@code exempt-checks} for SÜLD movement,
 *       {@code lock-on-exempt-checks} while the server turns the camera, {@code mount-exempt-checks} on and off a
 *       SÜLD horse;</li>
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
    private volatile Map<MovementExemptions.Kind, Set<String>> exemptChecks = Map.of();
    private final Map<String, LongAdder[]> counts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> reasons = new ConcurrentHashMap<>();
    /**
     * At most this many flags are forgiven per player in a burst, then {@code perSecond} (config
     * anticheat.grim.budget-burst / budget-per-second). An honest SÜLD dash costs a handful; a cheat that keeps casting
     * SÜLD movement to hide inside its windows runs dry within seconds and Grim judges the rest.
     */
    private volatile TokenBucket budget = new TokenBucket(60, 6);
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
        Map<MovementExemptions.Kind, Set<String>> lists = new java.util.EnumMap<>(MovementExemptions.Kind.class);
        lists.put(MovementExemptions.Kind.MOVE, checks(cfg.getStringList("anticheat.grim.exempt-checks")));
        lists.put(MovementExemptions.Kind.LOCKON, checks(cfg.getStringList("anticheat.grim.lock-on-exempt-checks")));
        lists.put(MovementExemptions.Kind.MOUNT, checks(cfg.getStringList("anticheat.grim.mount-exempt-checks")));
        exemptChecks = lists;
        budget = new TokenBucket(Math.max(1, cfg.getInt("anticheat.grim.budget-burst", 60)),
                Math.max(0.1, cfg.getDouble("anticheat.grim.budget-per-second", 6)));
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
            plugin.getLogger().info("[anticheat] " + state + "; checks forgiven inside SÜLD windows: " + exemptChecks);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ex) {
            state = "GrimAC-ийн API танигдсангүй (" + ex.getClass().getSimpleName() + ") — Grim бүх зүйлийг өөрөө шийднэ";
            plugin.getLogger().warning("[anticheat] " + state + ": " + ex.getMessage());
        }
    }

    private static Set<String> checks(List<String> names) {
        return names.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** The open window whose kind forgives this check, or null. Any thread. */
    private MovementExemptions.Window forgiving(UUID player, String check) {
        String c = check.toLowerCase(Locale.ROOT);
        for (MovementExemptions.Kind k : MovementExemptions.Kind.values()) {
            Set<String> list = exemptChecks.get(k);
            if (list == null || !list.contains(c)) continue;
            MovementExemptions.Window w = movement.window(player, k);
            if (w != null) return w;
        }
        return null;
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
        int slot;
        MovementExemptions.Window w = player == null ? null : forgiving(player, check);
        if (w != null && !budget.allow(player, System.currentTimeMillis())) {
            counts.computeIfAbsent(check, k -> new LongAdder[]{new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder()})[3].increment();
            return; // over budget: Grim decides
        }
        if (w != null) {
            try {
                setCancelled.invoke(event, true);
                slot = 1;
                reasons.computeIfAbsent(w.reason(), k -> new LongAdder()).increment();
            } catch (ReflectiveOperationException | RuntimeException ex) {
                slot = 2;
            }
        } else {
            slot = player != null && movement.active(player) != null ? 2 : 0;
        }
        counts.computeIfAbsent(check, k -> new LongAdder[]{new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder()})[slot].increment();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        movement.forget(e.getPlayer().getUniqueId());
        budget.forget(e.getPlayer().getUniqueId());
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
        exemptChecks.forEach((k, v) -> out.add("Чөлөөлөх шалгалт (" + k + "): " + (v.isEmpty() ? "—" : String.join(", ", new java.util.TreeSet<>(v)))));
        if (counts.isEmpty()) {
            out.add("Flag алга.");
            return out;
        }
        out.add("Шалгалт: гадна / уучилсан (SÜLD хөдөлгөөн) / цонхонд боловч уучлаагүй / лимит хэтэрсэн");
        new TreeMap<>(counts).forEach((k, v) -> out.add("  " + k + ": " + v[0].sum() + " / " + v[1].sum() + " / " + v[2].sum() + " / " + v[3].sum()));
        if (!reasons.isEmpty()) {
            out.add("Уучилсан шалтгаан: " + new TreeMap<>(reasons).entrySet().stream()
                    .map(e -> e.getKey() + " " + e.getValue().sum()).collect(Collectors.joining(", ")));
        }
        return out;
    }

    /** Raw counts per check: [outside a window, forgiven, in a window but not forgiven, over budget]. For QA. */
    public Map<String, long[]> snapshot() {
        Map<String, long[]> m = new TreeMap<>();
        counts.forEach((k, v) -> m.put(k, new long[]{v[0].sum(), v[1].sum(), v[2].sum(), v[3].sum()}));
        return m;
    }
}
