package mn.suld.plugin.social;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.persistence.ReferralRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.social.Referral;
import mn.suld.api.social.ReferralRules;
import mn.suld.api.social.ReferralSettings;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.auth.SuldJoinEvent;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * The referral loop (docs/REFERRAL.md): a newcomer names the friend who brought them with {@code /urih <нэр>}; when
 * the newcomer reaches {@link ReferralSettings#rewardLevel()} both are paid in coins, and after
 * {@link ReferralSettings#badgeAfter()} paid invitations the inviter earns the «Элч» tag. The invitee's level is read
 * every 20 s for online invitees whose row is still unpaid (no hook in the six places that level a player up); the
 * inviter is paid at once when online, else at their next login. Every write is compare-and-set in the repository,
 * so a double tick or a double login pays nobody twice. Main thread only; the repository runs off it.
 */
public final class ReferralService implements Listener {

    public static final String BADGE = "tag.elch";
    private static final long TICK = 400; // 20 s

    private final Plugin plugin;
    private final SuldServices services;
    private final ReferralRepository repository;
    private final ReferralSettings settings;
    /** Online invitees whose reward is still owed. */
    private final Map<UUID, Referral> pending = new HashMap<>();
    /** Invitees whose payment is being written. */
    private final Set<UUID> inFlight = new HashSet<>();
    private BukkitTask ticker;

    public ReferralService(Plugin plugin, SuldServices services, ReferralRepository repository, ReferralSettings settings) {
        this.plugin = plugin;
        this.services = services;
        this.repository = repository;
        this.settings = settings;
    }

    public ReferralSettings settings() {
        return settings;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (settings.enabled()) ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
        for (Player p : Bukkit.getOnlinePlayers()) load(p);
    }

    public void stop() {
        if (ticker != null) ticker.cancel();
        ticker = null;
        pending.clear();
        inFlight.clear();
    }

    // ------------------------------------------------------------------ /urih

    /** {@code /urih <нэр>}: {@code invitee} names the player who brought them. */
    public void name(Player invitee, String inviterName) {
        UUID id = invitee.getUniqueId();
        PlayerProfile pr = services.profiles().cached(id).orElse(null);
        if (pr == null) {
            invitee.sendMessage(Messages.error("Таны дүр хараахан ачаалагдаагүй байна."));
            return;
        }
        if (!settings.enabled()) {
            invitee.sendMessage(Messages.error("Урилгын шагнал одоогоор хаалттай."));
            return;
        }
        if (inviterName.equalsIgnoreCase(invitee.getName())) {
            invitee.sendMessage(Messages.error("Өөрийгөө урьж болохгүй."));
            return;
        }
        int level = pr.progression().level();
        whenDone(repository.byInvitee(id), (existing, err) -> {
            if (err != null || !invitee.isOnline()) {
                if (err != null) fail("byInvitee " + id, err, invitee);
                return;
            }
            ReferralRules.Refusal early = ReferralRules.refuse(settings, id, UUID.nameUUIDFromBytes(new byte[0]), level, existing.isPresent());
            if (early != ReferralRules.Refusal.NONE) {
                invitee.sendMessage(Messages.error(refusal(early)));
                return;
            }
            Player online = Bukkit.getPlayerExact(inviterName);
            CompletableFuture<Optional<UUID>> owner = online != null
                    ? CompletableFuture.completedFuture(Optional.of(online.getUniqueId()))
                    : services.profiles().ownerOfName(inviterName);
            whenDone(owner, (found, e2) -> {
                if (e2 != null || !invitee.isOnline()) {
                    if (e2 != null) fail("ownerOfName " + inviterName, e2, invitee);
                    return;
                }
                if (found.isEmpty()) {
                    invitee.sendMessage(Messages.error(inviterName + " нэртэй тоглогч олдсонгүй."));
                    return;
                }
                UUID inviter = found.get();
                ReferralRules.Refusal why = ReferralRules.refuse(settings, id, inviter, level, false);
                if (why != ReferralRules.Refusal.NONE) {
                    invitee.sendMessage(Messages.error(refusal(why)));
                    return;
                }
                Referral r = new Referral(id, inviter, System.currentTimeMillis(), null, null);
                whenDone(repository.create(r), (ok, e3) -> {
                    if (e3 != null) {
                        fail("create " + id, e3, invitee);
                        return;
                    }
                    if (!ok) {
                        if (invitee.isOnline()) invitee.sendMessage(Messages.error(refusal(ReferralRules.Refusal.ALREADY_NAMED)));
                        return;
                    }
                    String shownInviter = online != null ? online.getName() : inviterName;
                    pending.put(id, r);
                    services.audit().record(AuditEvent.of(invitee.getName(), AuditActions.REFERRAL_NAMED, shownInviter, "level " + level));
                    services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.REFERRAL_CREATED, id, Map.of("inviter", inviter.toString(), "level", level)));
                    if (invitee.isOnline()) {
                        invitee.sendMessage(Messages.success("✦ " + shownInviter + " таныг урьсан гэж бүртгэлээ. " + settings.rewardLevel()
                                + "-р түвшинд хүрэхэд та " + settings.inviteeCoins() + ", " + shownInviter + " " + settings.inviterCoins() + " зоос авна."));
                    }
                    Player inv = Bukkit.getPlayer(inviter);
                    if (inv != null) {
                        inv.sendMessage(Messages.success("✦ " + invitee.getName() + " таныг урьсан гэж нэрлэлээ: " + settings.rewardLevel()
                                + "-р түвшинд хүрэхэд нь та " + settings.inviterCoins() + " зоос авна."));
                    }
                });
            });
        });
    }

    private String refusal(ReferralRules.Refusal why) {
        return switch (why) {
            case OFF -> "Урилгын шагнал одоогоор хаалттай.";
            case SELF -> "Өөрийгөө урьж болохгүй.";
            case TOO_HIGH -> "Урьсан хүнээ " + settings.maxLevelToName() + "-р түвшин хүртэл л нэрлэж болно.";
            case ALREADY_NAMED -> "Та урьсан хүнээ аль хэдийн нэрлэсэн.";
            case NONE -> "";
        };
    }

    /** {@code /urih} alone: where this player stands. */
    public void status(Player p) {
        UUID id = p.getUniqueId();
        p.sendMessage(Messages.info("/urih <нэр> — таныг урьсан найзаа нэрлэ: " + settings.rewardLevel() + "-р түвшинд хүрэхэд та "
                + settings.inviteeCoins() + ", тэр " + settings.inviterCoins() + " зоос авна."));
        whenDone(repository.byInvitee(id), (r, err) -> {
            if (err != null || !p.isOnline()) return;
            p.sendMessage(Messages.info(r.map(x -> "Таныг урьсан: " + nameOf(x.inviter()) + (x.rewarded() ? " (шагнал олгогдсон)" : "")).orElse("Та хэнийг ч нэрлээгүй байна.")));
        });
        whenDone(repository.byInviter(id), (list, err) -> {
            if (err != null || !p.isOnline()) return;
            long paid = list.stream().filter(Referral::rewarded).count();
            p.sendMessage(Messages.info("Таны урьсан найзууд: " + list.size() + ", " + settings.rewardLevel() + "-р түвшинд хүрсэн: " + paid
                    + (settings.badgeAfter() > 0 ? " (" + settings.badgeAfter() + " болоход «Элч» цол)" : "") + "."));
        });
    }

    // ------------------------------------------------------------------ payments

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Referral r = pending.get(id);
            if (r == null || inFlight.contains(id)) continue;
            PlayerProfile pr = services.profiles().cached(id).orElse(null);
            if (pr == null || !ReferralRules.due(settings, r, pr.progression().level())) continue;
            inFlight.add(id);
            whenDone(repository.markRewarded(id, System.currentTimeMillis()), (ok, err) -> {
                inFlight.remove(id);
                if (err != null) {
                    plugin.getLogger().log(Level.WARNING, "[referral] reward of " + id + " not written: " + root(err));
                    return;
                }
                pending.remove(id);
                if (!ok) return; // paid elsewhere (a second server, a race): nothing owed
                payInvitee(p, r);
                payInviter(r, Bukkit.getPlayer(r.inviter()));
            });
        }
    }

    private void payInvitee(Player p, Referral r) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || !p.isOnline()) return;
        pr.addCurrency(settings.inviteeCoins());
        services.hud().update(p, pr);
        p.sendMessage(Messages.success("✦ Урилгын шагнал: +" + settings.inviteeCoins() + " зоос. " + nameOf(r.inviter()) + " таныг авчирсанд баярлалаа!"));
        services.audit().record(AuditEvent.of(p.getName(), AuditActions.REFERRAL_PAID, p.getName(), settings.inviteeCoins() + " coins, invitee reached " + settings.rewardLevel()));
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.REFERRAL_REWARDED, p.getUniqueId(), Map.of("inviter", r.inviter().toString())));
    }

    /** The inviter's share: now when online (the login path calls this too), else at their next login. */
    private void payInviter(Referral r, Player inv) {
        if (inv == null || !inv.isOnline()) return;
        whenDone(repository.markInviterPaid(r.invitee(), System.currentTimeMillis()), (ok, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[referral] inviter payment for " + r.invitee() + " not written: " + root(err));
                return;
            }
            if (!ok || !inv.isOnline()) return;
            PlayerProfile pr = services.profiles().cached(inv.getUniqueId()).orElse(null);
            if (pr == null) return;
            pr.addCurrency(settings.inviterCoins());
            services.hud().update(inv, pr);
            inv.sendMessage(Messages.success("✦ Таны урьсан " + nameOf(r.invitee()) + " " + settings.rewardLevel() + "-р түвшинд хүрлээ: +" + settings.inviterCoins() + " зоос."));
            services.audit().record(AuditEvent.of(inv.getName(), AuditActions.REFERRAL_PAID, nameOf(r.invitee()), settings.inviterCoins() + " coins, inviter"));
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.REFERRAL_INVITER_PAID, inv.getUniqueId(), Map.of("invitee", r.invitee().toString())));
            whenDone(repository.byInviter(inv.getUniqueId()), (list, e2) -> {
                if (e2 != null || !inv.isOnline()) return;
                int rewarded = (int) list.stream().filter(Referral::rewarded).count();
                if (ReferralRules.earnsBadge(settings, rewarded)) services.styles().grantEarned(inv, BADGE, "Элч — " + settings.badgeAfter() + " найзаа авчирлаа");
            });
        });
    }

    // ------------------------------------------------------------------ joins

    @EventHandler
    public void onJoin(SuldJoinEvent e) {
        load(e.getPlayer());
    }

    private void load(Player p) {
        if (!settings.enabled()) return;
        UUID id = p.getUniqueId();
        whenDone(repository.byInvitee(id), (r, err) -> {
            if (err != null || !p.isOnline()) return;
            r.filter(x -> !x.rewarded()).ifPresent(x -> pending.put(id, x));
        });
        whenDone(repository.byInviter(id), (list, err) -> {
            if (err != null || !p.isOnline()) return;
            for (Referral r : list) if (r.rewarded() && !r.inviterPaid()) payInviter(r, p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        inFlight.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ helpers

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "найз" : n;
    }

    private void fail(String what, Throwable err, Player p) {
        plugin.getLogger().log(Level.WARNING, "[referral] " + what + " failed: " + root(err));
        if (p.isOnline()) p.sendMessage(Messages.error("Түр алдаа гарлаа, дахин оролдоно уу."));
    }

    private static String root(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    private <T> void whenDone(CompletableFuture<T> future, BiConsumer<T, Throwable> callback) {
        future.whenComplete((value, err) -> Bukkit.getScheduler().runTask(plugin, () -> callback.accept(value, err)));
    }

    /** For tests and {@code /suld}: the online invitees still owed their reward. */
    public List<UUID> pendingInvitees() {
        return List.copyOf(pending.keySet());
    }
}
