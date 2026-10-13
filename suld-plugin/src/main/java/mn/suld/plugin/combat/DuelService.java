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
import org.bukkit.event.entity.EntityDamageEvent;
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
        PvpRules.Verdict v = verdict(a, b);
        return v.allowed() && (v != PvpRules.Verdict.ALLOWED_WILD || settings.wildEnabled());
    }

    /** A duel or a fighting ground, never the open steppe of {@code pvp.wild-enabled: true}, where deaths stay real. */
    private boolean bout(Player a, Player b) {
        if (a == null || b == null || a.equals(b)) return false;
        PvpRules.Verdict v = verdict(a, b);
        return v.allowed() && v != PvpRules.Verdict.ALLOWED_WILD;
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
        if (!services.styles().setting(to.getUniqueId(), mn.suld.api.style.PlayerSetting.DUEL_REQUESTS)) {
            from.sendMessage(Messages.error(to.getName() + " тулааны урилга хүлээн авдаггүй (Тохиргоо)."));
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
            mn.suld.plugin.hud.HudService.notice(attacker, Component.text(PvpRules.reason(v), NamedTextColor.GRAY));
        }
    }

    /**
     * A consented fight is never a hardcore death (no lost loot, EXP or soul time for a friendly duel or a bout on a
     * fighting ground): the killing blow becomes a defeat. HIGHEST, so SÜLD's combat maths (HIGH) already set the
     * final damage. The loser keeps a quarter of their health and 3 s of protection, the duel ends, and a relic the loser bears passes to
     * the winner as a consented kill would hand it over.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalConsented(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player loser)) return;
        Player winner;
        if (e instanceof EntityDamageByEntityEvent byEntity) {
            winner = attacker(byEntity.getDamager());
            if (winner == null || !bout(winner, loser)) return;
            lastHit.put(loser.getUniqueId(), new Hit(winner.getUniqueId(), System.currentTimeMillis()));
            // Тэнгэрийн Зэрэг IV (AscensionHonor, the shrine promises it): a consented fight's hits land 3 % lighter on the bearer
            int rank = services.profiles().cached(loser.getUniqueId()).map(mn.suld.api.profile.PlayerProfile::ascension).orElse(0);
            double guard = mn.suld.api.balance.AscensionHonor.pvpTaken(rank);
            if (guard < 1) e.setDamage(e.getDamage() * guard);
        } else {
            // the burn, wither or fall a duel hit started is still the duel (never the void, /kill or the border)
            EntityDamageEvent.DamageCause cause = e.getCause();
            if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL
                    || cause == EntityDamageEvent.DamageCause.WORLD_BORDER || cause == EntityDamageEvent.DamageCause.SUICIDE) return;
            Hit h = lastHit.get(loser.getUniqueId());
            if (h == null || System.currentTimeMillis() - h.at() > LINGER_MS) return;
            winner = Bukkit.getPlayer(h.by());
            if (winner == null || !bout(winner, loser)) return;
        }
        if (e.getFinalDamage() < loser.getHealth() + loser.getAbsorptionAmount()) return;
        e.setCancelled(true);
        defeat(loser, winner);
    }

    /** The share of max health a defeated fighter keeps. */
    static final double DEFEAT_HEALTH = 0.25;

    /** How long after a duel hit its lingering damage (fire, wither, a fall) still counts as the duel. */
    private static final long LINGER_MS = 10_000;

    private record Hit(UUID by, long at) {
    }

    private final Map<UUID, Hit> lastHit = new HashMap<>();

    private void defeat(Player loser, Player winner) {
        lastHit.remove(loser.getUniqueId());
        lastHit.remove(winner.getUniqueId());
        // a quarter of their health: SÜLD health runs to ~80, and 4 points would hand the loser to the first mob nearby
        loser.setHealth(Math.max(1.0, maxHealth(loser) * DEFEAT_HEALTH));
        loser.setNoDamageTicks(60);
        loser.setFireTicks(0);
        loser.setFallDistance(0f);
        loser.removePotionEffect(org.bukkit.potion.PotionEffectType.WITHER);
        loser.removePotionEffect(org.bukkit.potion.PotionEffectType.POISON);
        duels.end(loser.getUniqueId());
        Component line = Messages.accent("⚔ " + winner.getName() + " тулаанд ялав, " + loser.getName() + " бууж өглөө.");
        loser.sendMessage(line);
        winner.sendMessage(line);
        winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        loser.playSound(loser.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
        if (services.relics() != null) services.relics().onConsentedDefeat(loser, winner);
    }

    private static double maxHealth(Player p) {
        var a = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        lastHit.remove(e.getEntity().getUniqueId());
        duels.end(e.getEntity().getUniqueId()).ifPresent(d -> {
            Player other = Bukkit.getPlayer(d.other(e.getEntity().getUniqueId()));
            if (other != null) other.sendMessage(Messages.success("Тулаанд ялав: " + e.getEntity().getName() + " унасан."));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        lastNotice.remove(e.getPlayer().getUniqueId());
        lastHit.remove(e.getPlayer().getUniqueId());
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
