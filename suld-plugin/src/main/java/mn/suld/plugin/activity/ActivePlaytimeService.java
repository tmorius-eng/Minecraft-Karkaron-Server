package mn.suld.plugin.activity;

import mn.suld.api.activity.ActivitySignal;
import mn.suld.api.activity.ActivityTracker;
import mn.suld.api.event.ExpGainedEvent;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.event.SuldDomainBukkitEvent;
import mn.suld.plugin.gui.ClassSelectionHolder;
import mn.suld.plugin.gui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * ActivePlaytime (docs/ACTIVE_PLAYTIME_SPEC.md): feeds the pure {@link ActivityTracker} from events that already fire
 * and closes every online player's window once a minute. A validated active minute is added to the profile
 * ({@code active_minutes} per category) and handed to the listeners (the class armour's armour XP, the death wound's
 * healing). Idle connection time, menus, chat, AFK mob farms and auto-clickers never count.
 *
 * <p>Cost: one map lookup per signal; position samples at most once per 5 s per player (in the tracker); one window
 * close per player per minute. Main thread only.
 */
public final class ActivePlaytimeService implements Listener {

    /** At most this many 16×16 areas are remembered per session for the "first visit" signal. */
    private static final int AREA_MEMORY = 4096;

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<UUID, ActivityTracker> trackers = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Long>> areas = new ConcurrentHashMap<>();
    private final List<BiConsumer<Player, ActivityTracker.Verdict>> listeners = new ArrayList<>();

    public ActivePlaytimeService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    /** Called with every closed minute, active or not (check {@code verdict.active()}). */
    public void onMinute(BiConsumer<Player, ActivityTracker.Verdict> listener) {
        listeners.add(listener);
    }

    public void start() {
        for (Player p : Bukkit.getOnlinePlayers()) tracker(p.getUniqueId());
        Bukkit.getScheduler().runTaskTimer(plugin, this::closeMinute, 20L * 60, 20L * 60);
    }

    private ActivityTracker tracker(UUID id) {
        return trackers.computeIfAbsent(id, k -> new ActivityTracker());
    }

    /** A signal from another service (spell cast, trade, quest). */
    public void signal(UUID player, ActivitySignal s) {
        ActivityTracker t = trackers.get(player);
        if (t != null) t.signal(s);
    }

    /** No signal for 15 minutes. */
    public boolean away(UUID player) {
        ActivityTracker t = trackers.get(player);
        return t != null && t.away();
    }

    /** Area fatigue: armour XP stops until the player moves on. */
    public boolean fatigued(UUID player) {
        ActivityTracker t = trackers.get(player);
        return t != null && t.areaFatigued();
    }

    private void closeMinute() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ActivityTracker t = tracker(p.getUniqueId());
            if (services.isSoul.test(p.getUniqueId())) t.exclude();
            ActivityTracker.Verdict v = t.closeMinute();
            if (v.active()) {
                PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
                if (pr != null) pr.activeMinutes(pr.activeMinutes().plus(v.category(), 1));
            }
            for (BiConsumer<Player, ActivityTracker.Verdict> l : listeners) {
                try {
                    l.accept(p, v);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("active-minute listener failed for " + p.getName() + ": " + ex);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ signals

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) d = shooter;
        if (d instanceof Player p && !(e.getEntity() instanceof Player) && e.getEntity() instanceof LivingEntity) {
            ActivityTracker t = trackers.get(p.getUniqueId());
            if (t != null) {
                if (e.getDamager() instanceof Projectile) t.signal(ActivitySignal.DAMAGE_DEALT);
                else t.attack(System.currentTimeMillis());
            }
        }
        if (e.getEntity() instanceof Player p && !(d instanceof Player) && d instanceof LivingEntity) {
            signal(p.getUniqueId(), ActivitySignal.DAMAGE_TAKEN);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        ActivityTracker t = trackers.get(k.getUniqueId());
        if (t != null) t.kill(e.getEntity().getLocation().getX(), e.getEntity().getLocation().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDomain(SuldDomainBukkitEvent e) {
        if (!(e.payload() instanceof ExpGainedEvent g)) return;
        ActivitySignal s = switch (g.source()) {
            case QUEST -> ActivitySignal.QUEST;
            case DUNGEON -> ActivitySignal.DUNGEON;
            case DISCOVERY -> ActivitySignal.DISCOVERY;
            default -> null; // kills come from the death event; ADMIN grants are not activity
        };
        if (s != null) signal(g.player(), s);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        signal(e.getWhoClicked().getUniqueId(), ActivitySignal.CRAFT);
    }

    /** Inventory clicks outside SÜLD menus (a menu-only minute is not active). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        var holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof Menu || holder instanceof ClassSelectionHolder) return;
        signal(e.getWhoClicked().getUniqueId(), ActivitySignal.INVENTORY);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        signal(e.getPlayer().getUniqueId(), ActivitySignal.BLOCK);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        signal(e.getPlayer().getUniqueId(), ActivitySignal.BLOCK);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        Player p = e.getPlayer();
        ActivityTracker t = trackers.get(p.getUniqueId());
        if (t == null) return;
        boolean rail = p.getVehicle() instanceof Minecart;
        // not the player's own movement: a cart on a rail, a boat, an elytra glide, a water current
        boolean passive = p.isInsideVehicle() && !(p.getVehicle() instanceof LivingEntity) || p.isGliding() || (p.isInWater() && !p.isSwimming());
        t.move(to.getX(), to.getZ(), passive, System.currentTimeMillis());
        boolean newChunk = (to.getBlockX() >> 4) != (e.getFrom().getBlockX() >> 4) || (to.getBlockZ() >> 4) != (e.getFrom().getBlockZ() >> 4);
        if (newChunk && !rail) {
            Set<Long> seen = areas.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashSet<>());
            long key = ((long) (to.getBlockX() >> 4) << 32) ^ ((to.getBlockZ() >> 4) & 0xffffffffL);
            if (seen.size() < AREA_MEMORY && seen.add(key) && seen.size() > 1) t.signal(ActivitySignal.NEW_AREA);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        tracker(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        trackers.remove(e.getPlayer().getUniqueId());
        areas.remove(e.getPlayer().getUniqueId());
    }
}
