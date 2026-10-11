package mn.suld.plugin.combat;

import mn.suld.api.config.PvpSettings;
import mn.suld.api.pvp.DuelRegistry;
import mn.suld.api.pvp.PvpRules;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The PvP policy (owner decision 2026-10-11, docs/GAME_DESIGN.md «PvP»): SÜLD is PvE. A player's hit on another player
 * is cancelled unless the two are in an accepted {@code /duel}, both stand on a declared fighting ground, or the owner
 * opened the steppe ({@code pvp.wild-enabled}). The rules are pure ({@link PvpRules}, {@link DuelRegistry}); this class
 * resolves the attacker behind arrows, tridents, TNT and lingering clouds, tells the attacker why, and keeps duels in
 * step with deaths and quits. Kharkhorum's own protection still cancels first.
 */
public final class DuelService implements Listener {

    private static final long NOTICE_COOLDOWN_MS = 2500;

    private final Plugin plugin;
    private final SuldServices services;
    private final PvpSettings settings;
    private final DuelRegistry duels;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public DuelService(Plugin plugin, SuldServices services, PvpSettings settings) {
        this.plugin = plugin;
        this.services = services;
        this.settings = settings;
        this.duels = new DuelRegistry(Clock.systemUTC(), Duration.ofSeconds(settings.duelRequestSeconds()),
                Duration.ofMinutes(settings.duelMinutes()));
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (DuelRegistry.Duel d : duels.expire()) {
                for (UUID id : new UUID[]{d.a(), d.b()}) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendMessage(Messages.info("Тулааны хугацаа дууслаа — эв найртай салцгаая."));
                }
            }
        }, 100L, 100L);
    }

    public PvpSettings settings() {
        return settings;
    }

    /** True when {@code a} hurting {@code b} is a consented fight (duel or fighting ground); the relic seize rule asks. */
    public boolean consented(Player a, Player b) {
        if (a == null || b == null || a.equals(b)) return false;
        return verdict(a, b).allowed() && verdict(a, b) != PvpRules.Verdict.ALLOWED_WILD || settings.wildEnabled() && verdict(a, b).allowed();
    }

    private PvpRules.Verdict verdict(Player attacker, Player victim) {
        boolean inCity = services.inCity(attacker.getLocation()) || services.inCity(victim.getLocation());
        boolean soul = services.isSoul.test(attacker.getUniqueId()) || services.isSoul.test(victim.getUniqueId());
        boolean arena = inArena(attacker.getLocation()) && inArena(victim.getLocation());
        return PvpRules.judge(inCity, soul, duels.dueling(attacker.getUniqueId(), victim.getUniqueId()), arena, settings.wildEnabled());
    }

    private boolean inArena(Location l) {
        return l.getWorld() != null && settings.inArena(l.getWorld().getName(), l.getBlockX(), l.getBlockZ());
    }

    // ------------------------------------------------------------- commands

    public void challenge(Player from, Player to) {
        if (!settings.duelEnabled()) {
            from.sendMessage(Messages.error("Тулаан (duel) энэ серверт хаалттай."));
            return;
        }
        if (services.isSoul.test(from.getUniqueId()) || services.isSoul.test(to.getUniqueId())) {
            from.sendMessage(Messages.error(PvpRules.reason(PvpRules.Verdict.DENIED_SOUL)));
            return;
        }
        switch (duels.challenge(from.getUniqueId(), to.getUniqueId())) {
            case CHALLENGED -> {
                from.sendMessage(Messages.success(to.getName() + "-д тулааны урилга илгээлээ (" + settings.duelRequestSeconds() + " с)."));
                to.sendMessage(Messages.accent(from.getName() + " таныг тулаанд уриж байна. Зөвшөөрөх: /duel accept · Татгалзах: /duel decline"));
                to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
            }
            case ACCEPTED -> opened(from, to);
            case ALREADY_PENDING -> from.sendMessage(Messages.info("Урилга аль хэдийн илгээгдсэн; хариуг хүлээ."));
            case TARGET_BUSY -> from.sendMessage(Messages.error("Та эсвэл " + to.getName() + " өөр тулаанд байна."));
            case SELF -> from.sendMessage(Messages.error("Өөртэйгөө тулалдахгүй."));
            default -> from.sendMessage(Messages.error("Урилга илгээж чадсангүй."));
        }
    }

    public void accept(Player p) {
        Optional<UUID> from = duels.accept(p.getUniqueId());
        if (from.isEmpty()) {
            p.sendMessage(Messages.error("Хүлээгдэж буй тулааны урилга алга (эсвэл хугацаа нь дууссан)."));
            return;
        }
        Player challenger = Bukkit.getPlayer(from.get());
        if (challenger == null) {
            duels.end(p.getUniqueId());
            p.sendMessage(Messages.error("Уригч гарчихсан байна."));
            return;
        }
        opened(challenger, p);
    }

    public void decline(Player p) {
        Optional<UUID> from = duels.pendingFrom(p.getUniqueId());
        if (duels.decline(p.getUniqueId()) == DuelRegistry.Result.NO_CHALLENGE) {
            p.sendMessage(Messages.error("Хүлээгдэж буй урилга алга."));
            return;
        }
        p.sendMessage(Messages.info("Татгалзлаа."));
        from.map(Bukkit::getPlayer).ifPresent(c -> c.sendMessage(Messages.info(p.getName() + " тулаанаас татгалзлаа.")));
    }

    public void end(Player p) {
        Optional<DuelRegistry.Duel> d = duels.end(p.getUniqueId());
        if (d.isEmpty()) {
            p.sendMessage(Messages.error("Та тулаанд байхгүй."));
            return;
        }
        Player other = Bukkit.getPlayer(d.get().other(p.getUniqueId()));
        p.sendMessage(Messages.info("Тулаан дууслаа."));
        if (other != null) other.sendMessage(Messages.info(p.getName() + " тулааныг дуусгалаа."));
    }

    public Optional<DuelRegistry.Duel> duelOf(UUID p) {
        return duels.duelOf(p);
    }

    private void opened(Player a, Player b) {
        Component line = Messages.accent("⚔ " + a.getName() + " ба " + b.getName() + " тулаанд орлоо (" + settings.duelMinutes()
                + " мин, Хархорумаас гадуур). Дуусгах: /duel end");
        for (Player p : new Player[]{a, b}) {
            p.sendMessage(line);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.4f);
        }
    }

    // -------------------------------------------------------------- events

    /** LOWEST: the verdict comes before SÜLD's combat maths and before any damage number shows. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        PvpRules.Verdict v = verdict(attacker, victim);
        if (v.allowed()) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Projectile pr && !(pr instanceof org.bukkit.entity.Trident)) pr.remove();
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(attacker.getUniqueId());
        if (last == null || now - last > NOTICE_COOLDOWN_MS) {
            lastNotice.put(attacker.getUniqueId(), now);
            attacker.sendActionBar(Component.text(PvpRules.reason(v), NamedTextColor.GRAY));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        duels.end(e.getEntity().getUniqueId()).ifPresent(d -> {
            Player other = Bukkit.getPlayer(d.other(e.getEntity().getUniqueId()));
            if (other != null) other.sendMessage(Messages.success("Тулаанд ялав: " + e.getEntity().getName() + " унасан."));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        lastNotice.remove(e.getPlayer().getUniqueId());
        duels.end(e.getPlayer().getUniqueId()).ifPresent(d -> {
            Player other = Bukkit.getPlayer(d.other(e.getPlayer().getUniqueId()));
            if (other != null) other.sendMessage(Messages.info(e.getPlayer().getName() + " гарсан тул тулаан дууслаа."));
        });
    }

    /** The player behind a hit: direct, a projectile's shooter, TNT's igniter or a lingering cloud's thrower. */
    static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) return p;
        return null;
    }
}
