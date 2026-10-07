package mn.suld.plugin.style;

import mn.suld.api.persistence.StyleRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.CosmeticCatalog;
import mn.suld.api.style.LevelRewards;
import mn.suld.api.style.PlayerStyle;
import mn.suld.api.style.Rank;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Player style: the rank ladder ({@code /rankup}), cosmetics (buy with coins or credits, equip), level rewards
 * ({@code /lvlup}) and store credits. Loaded during pre-login (like the profile), saved when changed and on quit.
 * Every purchase is checked and charged on the main thread, so a double click can never pay twice.
 */
public final class StyleService implements Listener {

    public enum Currency { COINS, CREDITS }

    private final Plugin plugin;
    private final SuldServices services;
    private final StyleRepository repository;
    private final Map<UUID, PlayerStyle> cache = new ConcurrentHashMap<>();
    private final List<Consumer<Player>> changeListeners = new ArrayList<>();

    public StyleService(Plugin plugin, SuldServices services, StyleRepository repository) {
        this.plugin = plugin;
        this.services = services;
        this.repository = repository;
    }

    /** Styles that could not be loaded (reload while the database is down): never written, so defaults cannot overwrite. */
    private final java.util.Set<UUID> unsafe = ConcurrentHashMap.newKeySet();

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 20L * 60, 20L * 60);
        for (Player p : Bukkit.getOnlinePlayers()) { // plugin reload
            cache.computeIfAbsent(p.getUniqueId(), id -> {
                PlayerStyle loaded = tryLoad(id);
                if (loaded != null) return loaded;
                unsafe.add(id);
                return new PlayerStyle(id);
            });
        }
    }

    public void stop() {
        for (PlayerStyle s : cache.values()) {
            if (s.isDirty() && !unsafe.contains(s.player())) {
                try {
                    repository.save(s.snapshotAndClean()).get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    plugin.getLogger().warning("style save on shutdown failed for " + s.player() + ": " + e.getMessage());
                }
            }
        }
    }

    /** Called (main thread) after anything visible about a player's style changed: chat/TAB/name tag refresh. */
    public void onChange(Consumer<Player> listener) {
        changeListeners.add(listener);
    }

    /** After an equip change done directly on the style (e.g. the «unequip» button). */
    public void onEquipChanged(Player p) {
        changed(p);
    }

    private void changed(Player p) {
        changeListeners.forEach(l -> l.accept(p));
        save(p.getUniqueId());
    }

    /** The stored style, a fresh one for a player never seen, or null if storage failed (the login is then refused). */
    private PlayerStyle tryLoad(UUID id) {
        try {
            return repository.load(id).get(5, TimeUnit.SECONDS).map(PlayerStyle::restore).orElseGet(() -> new PlayerStyle(id));
        } catch (Exception e) {
            plugin.getLogger().warning("style load failed for " + id + ": " + e.getMessage());
            return null;
        }
    }

    /** When each player last passed pre-login (nanoTime): a quit's delayed eviction must not hit a login in progress. */
    private final Map<UUID, Long> preLoginAt = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        preLoginAt.put(e.getUniqueId(), System.nanoTime());
        // A duplicate or quick re-login reuses the live object: its unsaved changes must not be replaced by a stale read.
        if (cache.containsKey(e.getUniqueId())) return;
        PlayerStyle loaded = tryLoad(e.getUniqueId());
        if (loaded == null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Өгөгдөл ачаалж чадсангүй. Түр хүлээгээд дахин орно уу.", NamedTextColor.RED));
            return;
        }
        cache.putIfAbsent(e.getUniqueId(), loaded);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        PlayerStyle s = cache.get(id);
        if (s == null) return;
        persist(s);
        // Keep it a moment: a duplicate login's pre-login (which runs before this quit) already relies on this object,
        // and so does a quick reconnect whose pre-login came after this quit (the player is not visible until join)
        long quitAt = System.nanoTime();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long pre = preLoginAt.get(id);
            if (pre != null && pre - quitAt > 0) return; // logging in again right now: keep the live object
            if (Bukkit.getPlayer(id) == null && cache.remove(id, s)) {
                persist(s);
                preLoginAt.remove(id);
            }
        }, 40L);
    }

    private void flush() {
        for (PlayerStyle s : cache.values()) persist(s);
        // styles of logins refused after pre-login (ban, whitelist, full) never see a quit: drop them after 5 minutes
        long now = System.nanoTime();
        for (Map.Entry<UUID, Long> en : preLoginAt.entrySet()) {
            UUID id = en.getKey();
            if (now - en.getValue() > TimeUnit.MINUTES.toNanos(5) && Bukkit.getPlayer(id) == null) {
                PlayerStyle st = cache.get(id);
                if (st != null) {
                    persist(st);
                    cache.remove(id, st);
                }
                preLoginAt.remove(id, en.getValue());
            }
        }
    }

    private void save(UUID id) {
        PlayerStyle s = cache.get(id);
        if (s != null) persist(s);
    }

    /** Write a player's style now (e.g. together with a coin change); also used for styles no longer in the cache. */
    public void saveNow(UUID id) {
        save(id);
    }

    /**
     * Snapshot and write if dirty. Taking the snapshot and queueing the write happen under one lock so snapshots are
     * written in order, and a failed write marks the style dirty again so the next flush retries it.
     */
    private void persist(PlayerStyle s) {
        if (unsafe.contains(s.player())) return;
        java.util.concurrent.CompletableFuture<Void> write;
        synchronized (s) {
            if (!s.isDirty()) return;
            write = repository.save(s.snapshotAndClean());
        }
        write.whenComplete((v, err) -> {
            if (err != null) {
                s.markDirty();
                plugin.getLogger().warning("style save failed for " + s.player() + " (will retry): " + err.getMessage());
            }
        });
    }

    /** Refuse an action that would mutate a style whose stored state is unknown. */
    private boolean blocked(Player p) {
        if (!unsafe.contains(p.getUniqueId())) return false;
        p.sendMessage(Messages.error("Таны өгөгдөл бүрэн ачаалагдаагүй байна — дахин нэвтэрч орно уу."));
        return true;
    }

    /**
     * The player's style (online players always have one). Should it be missing, a placeholder is returned that is
     * never written and blocks paid actions ({@link #ready}) while the stored one loads in the background: a default
     * style must never overwrite a player's rank and claimed rewards.
     */
    public PlayerStyle of(UUID id) {
        PlayerStyle s = cache.get(id);
        if (s != null) return s;
        PlayerStyle placeholder = new PlayerStyle(id);
        PlayerStyle prev = cache.putIfAbsent(id, placeholder);
        if (prev != null) return prev;
        unsafe.add(id);
        plugin.getLogger().warning("style of " + id + " was not cached; reloading it");
        repository.load(id).whenComplete((stored, err) -> {
            if (err != null || !plugin.isEnabled()) return; // stays unsafe: never written
            PlayerStyle real = stored.map(PlayerStyle::restore).orElseGet(() -> new PlayerStyle(id));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (cache.replace(id, placeholder, real)) unsafe.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) changed(p);
            });
        });
        return placeholder;
    }

    /** False while a player's style is not known to match storage: rewards and purchases must wait. */
    public boolean ready(UUID id) {
        return cache.containsKey(id) && !unsafe.contains(id);
    }

    public Optional<PlayerStyle> cached(UUID id) {
        return Optional.ofNullable(cache.get(id));
    }

    // ------------------------------------------------------------------ cosmetics

    private final java.util.Set<UUID> purchasing = ConcurrentHashMap.newKeySet();

    /** Buy (if needed) and equip/unequip. Credit purchases complete asynchronously (atomic database spend). */
    public void buyOrEquip(Player p, Cosmetic c, Currency currency, Runnable after) {
        PlayerStyle s = of(p.getUniqueId());
        if (blocked(p)) return;
        if (s.owns(c.id())) {
            equipToggle(p, s, c);
            after.run();
            return;
        }
        if (!purchasing.add(p.getUniqueId())) return; // a purchase is already in flight: no double spend
        if (currency == Currency.CREDITS) {
            long cost = c.creditPrice();
            if (cost <= 0) {
                purchasing.remove(p.getUniqueId());
                p.sendMessage(Messages.error("Энэ зүйлийг кредитээр авах боломжгүй."));
                return;
            }
            repository.addCredits(p.getUniqueId(), -cost).whenComplete((balance, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                purchasing.remove(p.getUniqueId());
                if (err != null) {
                    plugin.getLogger().warning("credit spend failed for " + p.getName() + ": " + err.getMessage());
                    if (p.isOnline()) p.sendMessage(Messages.error("Кредит хасаж чадсангүй — дахин оролдоно уу."));
                    return;
                }
                if (balance < 0) {
                    if (p.isOnline()) p.sendMessage(Messages.error("Кредит хүрэлцэхгүй (" + s.credits() + "/" + cost + "). /buy"));
                    return;
                }
                s.creditsCache(balance);
                if (s.owns(c.id())) { // became ours while the payment was in flight (e.g. a level reward): give the credits back
                    repository.addCredits(p.getUniqueId(), cost).whenComplete((b, e2) -> {
                        if (e2 != null) plugin.getLogger().severe("REFUND FAILED for " + p.getUniqueId() + " (" + cost + " credits): " + e2.getMessage());
                    });
                    if (p.isOnline()) p.sendMessage(Messages.info("Энэ зүйл танд аль хэдийн байна — кредит буцаагдлаа."));
                    return;
                }
                if (!p.isOnline()) { // left during the payment: the purchase is still delivered and saved
                    s.grant(c.id());
                    persist(s);
                    return;
                }
                granted(p, s, c);
                persist(s); // even if the style was dropped from the cache meanwhile
                after.run();
            }));
            return;
        }
        try {
            if (!c.sold()) {
                p.sendMessage(Messages.error(c.source().startsWith("level:") ? "Түвшин " + c.source().substring(6) + "-д /lvlup-аар авна."
                        : "Энэ зүйл зөвхөн кредитээр авагдана. /buy"));
                return;
            }
            PlayerProfile profile = services.profiles().cached(p.getUniqueId()).orElse(null);
            if (profile == null || profile.currency() < c.price()) {
                p.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + (profile == null ? 0 : profile.currency()) + "/" + c.price() + " ₮)."));
                return;
            }
            profile.addCurrency(-c.price());
            granted(p, s, c);
            saveProfile(p);
            after.run();
        } finally {
            purchasing.remove(p.getUniqueId());
        }
    }

    /** Coins live on the profile: save it together with the style change they paid for, so a crash cannot split them. */
    private void saveProfile(Player p) {
        services.profiles().cached(p.getUniqueId()).ifPresent(services.profiles()::save);
    }

    private void granted(Player p, PlayerStyle s, Cosmetic c) {
        s.grant(c.id());
        p.sendMessage(Messages.success("Худалдаж авлаа: " + c.name() + " (" + c.category().displayName() + ")"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        equipToggle(p, s, c);
    }

    private void equipToggle(Player p, PlayerStyle s, Cosmetic c) {
        if (c.category() == Cosmetic.Category.EMOJI) {
            p.sendMessage(Messages.success("Эможи " + c.style() + " чатад ашиглаж болно."));
            changed(p);
            return;
        }
        boolean already = s.equipped(c.category()).map(x -> x.id().equals(c.id())).orElse(false);
        s.equip(c.category(), already ? null : c.id());
        p.sendMessage(already ? Messages.info(c.category().displayName() + " тайллаа.")
                : Messages.success(c.category().displayName() + ": " + c.name() + " зүүлээ."));
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
        changed(p);
    }

    // ------------------------------------------------------------------ rank ladder

    public Rank.Check rankUp(Player p) {
        PlayerStyle s = of(p.getUniqueId());
        if (blocked(p)) return Rank.Check.LEVEL_TOO_LOW;
        PlayerProfile profile = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (profile == null) return Rank.Check.LEVEL_TOO_LOW;
        Rank.Check check = Rank.canRankUp(s.rank(), profile.progression().level(), profile.currency());
        Rank next = s.rank().next().orElse(null);
        switch (check) {
            case OK -> {
                profile.addCurrency(-next.cost());
                s.rank(next);
                p.showTitle(Title.title(Component.text("ЦОЛ АХИЛАА", net.kyori.adventure.text.format.TextColor.fromHexString(next.color())),
                        Component.text(next.displayName(), NamedTextColor.WHITE),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                Bukkit.broadcast(Messages.accent(p.getName() + " " + next.displayName() + " цолд хүрлээ!"));
                changed(p);
                saveProfile(p);
            }
            case MAX_RANK -> p.sendMessage(Messages.info("Та хамгийн дээд цолтой — Хаан!"));
            case LEVEL_TOO_LOW -> p.sendMessage(Messages.error("Түвшин " + next.requiredLevel() + " хэрэгтэй (одоо "
                    + profile.progression().level() + "). /exp"));
            case NOT_ENOUGH_COINS -> p.sendMessage(Messages.error("Зоос " + next.cost() + " ₮ хэрэгтэй (одоо " + profile.currency() + ")."));
        }
        return check;
    }

    // ------------------------------------------------------------------ level rewards

    /** Claim one reached reward. Returns false if not reached or already claimed. */
    public boolean claimLevel(Player p, int level) {
        PlayerStyle s = of(p.getUniqueId());
        PlayerProfile profile = services.profiles().cached(p.getUniqueId()).orElse(null);
        LevelRewards.Reward r = LevelRewards.at(level).orElse(null);
        if (profile == null || r == null || blocked(p)) return false;
        if (profile.progression().level() < level) {
            p.sendMessage(Messages.error("Түвшин " + level + "-д хүрээгүй байна."));
            return false;
        }
        if (!s.claimLevel(level)) {
            p.sendMessage(Messages.info("Энэ шагналыг авсан."));
            return false;
        }
        profile.addCurrency(r.coins());
        String extra = "";
        if (r.cosmeticId() != null && s.grant(r.cosmeticId())) {
            extra = " + " + CosmeticCatalog.byId(r.cosmeticId()).map(Cosmetic::name).orElse(r.cosmeticId());
        }
        p.sendMessage(Messages.success("Түвшин " + level + " шагнал: +" + r.coins() + " ₮" + extra));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        changed(p);
        saveProfile(p);
        return true;
    }

    // ------------------------------------------------------------------ credits (admin / store webhook)

    /**
     * Add credits to a player, online or not — one atomic database update, so it is safe while the player logs in
     * or buys something. Used by {@code /credits give}, which a store (e.g. Tebex) runs from the console after payment.
     */
    public void giveCredits(UUID id, long amount, Consumer<String> feedback) {
        repository.addCredits(id, amount).whenComplete((balance, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                feedback.accept("FAILED: " + err.getMessage());
                return;
            }
            if (balance < 0) {
                feedback.accept("refused: balance would go below zero");
                return;
            }
            PlayerStyle s = cache.get(id);
            if (s != null) s.creditsCache(balance);
            Player p = Bukkit.getPlayer(id);
            if (p != null && amount > 0) {
                p.sendMessage(Messages.success("+" + amount + " Сүлд Кредит! Баярлалаа. (/cosmetics)"));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                changeListeners.forEach(l -> l.accept(p));
            }
            feedback.accept("ok: " + id + " balance " + balance + " credits");
        }));
    }
}
