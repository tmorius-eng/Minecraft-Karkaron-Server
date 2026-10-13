package mn.suld.plugin.mastery;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.mastery.WeaponMastery;
import mn.suld.api.persistence.WeaponMasteryRepository;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Зэвсгийн ур чадвар (docs/MASTERY_AND_PROFESSIONS.md): the five weapon tracks, levelled by the damage a player deals
 * to SÜLD mobs with that kind of weapon (CombatListener calls {@link #hit}). Main thread only. The stored totals (V25)
 * are read at join; points earned since are kept apart and added to the table every 30 s and at quit
 * (an additive write, so the order of the writes does not matter). A player's points are first written only once their
 * read is back, so the read can never miss or double a write of theirs.
 */
public final class WeaponMasteryService implements Listener {

    private static final long FLUSH_TICKS = 20L * 30;

    private final Plugin plugin;
    private final SuldServices services;
    private final WeaponMasteryRepository repo;
    /** What the table holds for an online player, as far as this server knows (read at join, raised by each write). */
    private final Map<UUID, EnumMap<WeaponMastery.Track, Double>> stored = new HashMap<>();
    /** Points earned and not written yet. */
    private final Map<UUID, EnumMap<WeaponMastery.Track, Double>> pending = new HashMap<>();
    /** Players whose stored totals are read. */
    private final Set<UUID> loaded = new HashSet<>();

    public WeaponMasteryService(Plugin plugin, SuldServices services, WeaponMasteryRepository repo) {
        this.plugin = plugin;
        this.services = services;
        this.repo = repo;
    }

    public void start() {
        for (Player p : Bukkit.getOnlinePlayers()) load(p.getUniqueId());
        Bukkit.getScheduler().runTaskTimer(plugin, this::flushAll, FLUSH_TICKS, FLUSH_TICKS);
    }

    // ---- storage ----------------------------------------------------------------------------------------------------

    private void load(UUID id) {
        repo.totals(id).whenComplete((m, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "[mastery] weapon mastery of " + id + " not read (points stay pending)", ex);
                return;
            }
            if (Bukkit.getPlayer(id) == null) return; // left before the read came back; its pending points were written at quit
            EnumMap<WeaponMastery.Track, Double> s = new EnumMap<>(WeaponMastery.Track.class);
            s.putAll(m);
            stored.put(id, s);
            loaded.add(id);
        }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        load(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        flush(id, true);
        stored.remove(id);
        loaded.remove(id);
    }

    private void flushAll() {
        for (UUID id : new HashSet<>(pending.keySet())) flush(id, false);
    }

    /** Writes a player's pending points; {@code leaving} writes them even before the read is back. */
    private CompletableFuture<Void> flush(UUID id, boolean leaving) {
        if (!leaving && !loaded.contains(id)) return CompletableFuture.completedFuture(null);
        EnumMap<WeaponMastery.Track, Double> batch = pending.remove(id);
        if (batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);
        return repo.add(id, batch, System.currentTimeMillis()).whenComplete((v, ex) -> {
            if (!plugin.isEnabled()) return; // shutdown: SuldServices waits on this write, nothing to merge back into
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ex != null) {
                    plugin.getLogger().log(Level.WARNING, "[mastery] " + id + ": points not written, kept for the next try", ex);
                    if (Bukkit.getPlayer(id) != null) batch.forEach((t, p) -> pending.computeIfAbsent(id, k -> new EnumMap<>(WeaponMastery.Track.class)).merge(t, p, Double::sum));
                    return;
                }
                EnumMap<WeaponMastery.Track, Double> s = stored.get(id);
                if (s != null) batch.forEach((t, p) -> s.merge(t, p, Double::sum));
            });
        });
    }

    /** Shutdown: every pending point written (SuldServices waits within its budget). */
    public CompletableFuture<Void> shutdown() {
        java.util.List<CompletableFuture<Void>> writes = new java.util.ArrayList<>();
        for (UUID id : new HashSet<>(pending.keySet())) writes.add(flush(id, true));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    private double total(UUID id, WeaponMastery.Track t) {
        EnumMap<WeaponMastery.Track, Double> s = stored.get(id);
        EnumMap<WeaponMastery.Track, Double> p = pending.get(id);
        return (s == null ? 0 : s.getOrDefault(t, 0.0)) + (p == null ? 0 : p.getOrDefault(t, 0.0));
    }

    /** A track's state for a player whose totals are read (START until then). */
    public WeaponMastery.State state(UUID id, WeaponMastery.Track t) {
        return loaded.contains(id) ? WeaponMastery.fromTotal(total(id, t)) : WeaponMastery.State.START;
    }

    public int level(UUID id, WeaponMastery.Track t) {
        return state(id, t).level();
    }

    /** The highest level of a player's tracks (the Ascension IV–X gates read it). */
    public int best(UUID id) {
        int best = 0;
        for (WeaponMastery.Track t : WeaponMastery.Track.values()) best = Math.max(best, level(id, t));
        return best;
    }

    public boolean loaded(UUID id) {
        return loaded.contains(id);
    }

    /** A player's stored totals, read on the io pool (the armory's offline sheets; any thread). */
    public CompletableFuture<Map<WeaponMastery.Track, Double>> totalsOf(UUID id) {
        return repo.totals(id);
    }

    /** The track of what the player holds in the main hand: a SÜLD weapon of one of the five kinds, else empty. */
    public Optional<WeaponMastery.Track> trackOf(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return Optional.empty();
        return services.items().read(hand)
                .map(i -> mn.suld.plugin.content.SuldContent.definitionFor(i.definitionId()))
                .map(ItemDefinition::type)
                .flatMap(WeaponMastery.Track::of);
    }

    /** The damage bonus of the held weapon's track (0 in the training yard: the kit is the measure there). */
    public double bonus(Player p) {
        if (services.training != null && services.training.training(p.getUniqueId())) return 0;
        return trackOf(p).map(t -> WeaponMastery.damageBonus(level(p.getUniqueId(), t))).orElse(0.0);
    }

    // ---- earning ----------------------------------------------------------------------------------------------------

    /**
     * A basic hit (melee or arrow) of {@code p} on a SÜLD mob of the content, for {@code damage}, with {@code attack}
     * as the player's own basic hit: the held weapon's track earns {@code damage ÷ attack} points.
     */
    public void hit(Player p, double damage, double attack) {
        if (services.training != null && services.training.training(p.getUniqueId())) return; // a stand-in earns nothing
        WeaponMastery.Track t = trackOf(p).orElse(null);
        if (t == null) return;
        double pts = WeaponMastery.points(damage, attack);
        if (!(pts > 0)) return;
        UUID id = p.getUniqueId();
        int before = level(id, t);
        pending.computeIfAbsent(id, k -> new EnumMap<>(WeaponMastery.Track.class)).merge(t, pts, Double::sum);
        if (!loaded.contains(id)) return; // the level shows once the stored total is known
        int after = level(id, t);
        if (after > before) levelled(p, t, before, after);
    }

    private void levelled(Player p, WeaponMastery.Track t, int before, int after) {
        p.sendMessage(Messages.success("⚔ " + t.masterTitle() + " ур чадвар " + after + " (+"
                + Math.round(WeaponMastery.damageBonus(after) * 1000) / 10.0 + " % цохилт)"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        for (int l = before + 1; l <= after; l++) {
            String title = WeaponMastery.title(t, l);
            if (title == null) continue;
            p.sendMessage(Component.text("✦ Шинэ цол: «" + title + "»", NamedTextColor.GOLD, TextDecoration.BOLD));
            if (l == WeaponMastery.MAX_LEVEL) {
                Bukkit.broadcast(Component.text("✦ " + p.getName() + " — «" + title + "» боллоо!", NamedTextColor.GOLD, TextDecoration.BOLD));
            }
        }
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("weapon_mastery_level", p.getUniqueId(),
                Map.<String, Object>of("track", t.name(), "level", after)));
    }

    /** Staff/QA: a track set to the start of {@code level}. */
    public void set(UUID id, WeaponMastery.Track t, int level) {
        double total = WeaponMastery.totalOf(new WeaponMastery.State(level, 0));
        EnumMap<WeaponMastery.Track, Double> p = pending.get(id);
        if (p != null) p.remove(t);
        stored.computeIfAbsent(id, k -> new EnumMap<>(WeaponMastery.Track.class)).put(t, total);
        repo.set(id, t, total, System.currentTimeMillis()).whenComplete((v, ex) -> {
            if (ex != null) plugin.getLogger().log(Level.WARNING, "[mastery] set of " + id + " not written", ex);
        });
    }

    // ---- the page ---------------------------------------------------------------------------------------------------

    /** {@code /mastery}: the five tracks, their level, the way to the next one, the bonus and the next title. */
    public void show(Player p) {
        UUID id = p.getUniqueId();
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (!loaded.contains(id)) lines.add(mn.suld.plugin.ui.Pages.dim("Ачаалж байна… түр хүлээгээд дахин нээ."));
        WeaponMastery.Track held = trackOf(p).orElse(null);
        for (WeaponMastery.Track t : WeaponMastery.Track.values()) {
            WeaponMastery.State s = state(id, t);
            int bars = (int) Math.round(s.progress() * 10);
            String next = null;
            for (int l : WeaponMastery.TITLE_LEVELS) {
                if (l > s.level()) {
                    next = "«" + WeaponMastery.title(t, l) + "» " + l + "-д";
                    break;
                }
            }
            lines.add(mn.suld.plugin.ui.Pages.line((t == held ? "▶ " : "") + t.displayName() + " " + s.level() + "/" + WeaponMastery.MAX_LEVEL
                    + "  " + "▮".repeat(bars) + "▯".repeat(10 - bars)
                    + "  +" + Math.round(WeaponMastery.damageBonus(s.level()) * 1000) / 10.0 + " %"));
            if (next != null) lines.add(mn.suld.plugin.ui.Pages.dim("   дараагийн цол: " + next));
        }
        lines.add(mn.suld.plugin.ui.Pages.dim("Тухайн төрлийн зэвсгээр SÜLD мангас цохиход ахина (дасгалын дүрс, тоглогч тооцогдохгүй)."));
        mn.suld.plugin.ui.Pages.text(p, "ЗЭВСГИЙН УР ЧАДВАР", lines, java.util.List.of(), null);
    }
}
