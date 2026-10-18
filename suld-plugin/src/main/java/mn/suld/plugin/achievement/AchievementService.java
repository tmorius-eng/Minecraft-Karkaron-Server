package mn.suld.plugin.achievement;

import mn.suld.api.achievement.Achievement;
import mn.suld.api.achievement.AchievementCatalog;
import mn.suld.api.achievement.Deed;
import mn.suld.api.achievement.DeedBook;
import mn.suld.api.battlepass.CampaignPass;
import mn.suld.api.battlepass.PassLayout;
import mn.suld.api.battlepass.Season;
import mn.suld.api.persistence.AchievementRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.CosmeticCatalog;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import mn.suld.plugin.auth.SuldJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Achievements and the season's pass «Хуралдайн Зам» (docs/ACHIEVEMENTS_AND_PASS.md). Main thread only.
 * <ul>
 *   <li>The game {@linkplain #record records} a player's deeds where they happen (a clear, a site, a level…); each one may
 *   unlock achievements and gives the pass its points, at most {@link CampaignPass#DAILY_CAP} a day.</li>
 *   <li>A player's deeds, unlocks and pass are read at join. Records made before the read is back wait and are replayed
 *   then; what the player already did before this system (levels, chapters, sites, ovoos, regions, crafts, mastery,
 *   ascension, clan) is recorded once the read is back (a backfill: it unlocks, but gives the pass no points).</li>
 *   <li>Writes commute (V28): counts and points add, bests keep the greater, unlocks and claims are inserted once.
 *   They go every 30 s, at quit and at shutdown; a failed write is kept for the next try.</li>
 *   <li>Every reward is a cosmetic, granted through the style service: never an item, a level or power.</li>
 * </ul>
 */
public final class AchievementService implements Listener {

    private static final long FLUSH_TICKS = 20L * 30;
    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A"), CREAM = TextColor.fromHexString("#F4E8C8");
    /** Records kept while a player's read is not back (more are dropped: a flood before the read is not news). */
    private static final int EARLY_MAX = 2_000;

    private final Plugin plugin;
    private final SuldServices services;
    private final AchievementRepository repo;
    private final AchievementCatalog catalog;
    private final CampaignPass.Layout layout;
    private final LocalDate epoch;
    private final int seasonWeeks;
    private final long heroicCredits;
    private final Map<UUID, State> states = new HashMap<>();
    /** Points the pass gave each player today (by deeds), kept across a relog: {day, points}. Lost at a restart. */
    private final Map<UUID, long[]> daily = new ConcurrentHashMap<>();

    private record Early(Deed deed, String subject, long amount, boolean backfill) {
    }

    private static final class State {
        boolean loaded;
        DeedBook book = new DeedBook();
        final Set<String> unlocked = new HashSet<>();
        final List<Early> early = new ArrayList<>();
        final Map<String, Long> addCounts = new HashMap<>(), raiseBests = new HashMap<>(), newUnlocks = new HashMap<>();
        String season;
        long points;          // the season's points as far as this server knows (stored + pending)
        long pendingPoints;   // not written yet
        boolean heroic;
        final Set<String> claimed = new HashSet<>();
        final Set<String> claiming = new HashSet<>();
    }

    public AchievementService(Plugin plugin, SuldServices services, AchievementRepository repo) {
        this.plugin = plugin;
        this.services = services;
        this.repo = repo;
        AchievementCatalog.Result r = AchievementCatalog.bundled();
        if (!r.ok()) plugin.getLogger().severe("[achievements] the catalog is refused: " + r.issues());
        this.catalog = r.catalog();
        CampaignPass.Layout l;
        try {
            l = PassLayout.bundled();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("[achievements] the pass layout is refused: " + e.getMessage());
            l = new CampaignPass.Layout(List.of());
        }
        this.layout = l;
        var c = plugin.getConfig();
        LocalDate ep;
        try {
            ep = LocalDate.parse(c.getString("pass.epoch", "2026-10-05"));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[achievements] pass.epoch is not a date (yyyy-mm-dd): " + c.getString("pass.epoch") + "; 2026-10-05 is used");
            ep = LocalDate.of(2026, 10, 5);
        }
        this.epoch = ep;
        this.seasonWeeks = Math.max(1, c.getInt("pass.season-weeks", 8));
        this.heroicCredits = Math.max(0, c.getLong("pass.heroic-credits", 0));
    }

    public void start() {
        for (Player p : Bukkit.getOnlinePlayers()) load(p);
        Bukkit.getScheduler().runTaskTimer(plugin, this::flushAll, FLUSH_TICKS, FLUSH_TICKS);
    }

    public AchievementCatalog catalog() {
        return catalog;
    }

    public CampaignPass.Layout layout() {
        return layout;
    }

    /** The season today, in the server's zone. */
    public Season season() {
        return Season.of(LocalDate.now(services.zone()), epoch, seasonWeeks);
    }

    /** Credits the heroic track costs this season (0: not sold; staff open it). */
    public long heroicCredits() {
        return heroicCredits;
    }

    // ================================================================== storage

    private void load(Player p) {
        UUID id = p.getUniqueId();
        State st = new State();
        st.season = season().id();
        states.put(id, st);
        String season = st.season;
        repo.load(id).thenCombine(repo.pass(id, season), (stored, pass) -> new Object[]{stored, pass})
                .whenComplete((both, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    State now = states.get(id);
                    if (now != st) return; // left (and maybe came back) before the read: that join reads again
                    if (ex != null) {
                        plugin.getLogger().log(Level.WARNING, "[achievements] " + id + ": deeds not read (records wait for the next join)", ex);
                        return;
                    }
                    AchievementRepository.Stored stored = (AchievementRepository.Stored) both[0];
                    AchievementRepository.Pass pass = (AchievementRepository.Pass) both[1];
                    st.book = new DeedBook(stored.deeds());
                    st.unlocked.addAll(stored.unlocked().keySet());
                    st.points = pass.points();
                    st.heroic = pass.heroic();
                    st.claimed.addAll(pass.claimed());
                    st.loaded = true;
                    Player online = Bukkit.getPlayer(id);
                    if (online == null) return;
                    List<Early> early = new ArrayList<>(st.early);
                    st.early.clear();
                    for (Early e : early) record(online, e.deed(), e.subject(), e.amount(), e.backfill());
                    backfill(online);
                }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(SuldJoinEvent e) {
        load(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        flush(id, true);
        states.remove(id);
    }

    private void flushAll() {
        for (UUID id : new ArrayList<>(states.keySet())) flush(id, false);
    }

    /** Writes a player's pending deeds, unlocks and points. */
    private CompletableFuture<Void> flush(UUID id, boolean leaving) {
        State st = states.get(id);
        if (st == null || !st.loaded) return CompletableFuture.completedFuture(null);
        Map<String, Long> counts = new HashMap<>(st.addCounts), bests = new HashMap<>(st.raiseBests), unlocks = new HashMap<>(st.newUnlocks);
        long points = st.pendingPoints;
        String season = st.season;
        st.addCounts.clear();
        st.raiseBests.clear();
        st.newUnlocks.clear();
        st.pendingPoints = 0;
        if (counts.isEmpty() && bests.isEmpty() && unlocks.isEmpty() && points == 0) return CompletableFuture.completedFuture(null);
        long now = System.currentTimeMillis();
        CompletableFuture<Void> deeds = repo.write(id, counts, bests, unlocks, now);
        CompletableFuture<Void> pass = repo.addPoints(id, season, points, now);
        return CompletableFuture.allOf(deeds, pass).whenComplete((v, ex) -> {
            if (ex == null || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().log(Level.WARNING, "[achievements] " + id + ": not written, kept for the next try", ex);
                State back = states.get(id);
                if (back == null) return; // left: the next join reads what was stored, the rest is lost (logged)
                if (deeds.isCompletedExceptionally()) {
                    counts.forEach((k, n) -> back.addCounts.merge(k, n, Long::sum));
                    bests.forEach((k, n) -> back.raiseBests.merge(k, n, Math::max));
                    unlocks.forEach(back.newUnlocks::putIfAbsent);
                }
                if (pass.isCompletedExceptionally() && season.equals(back.season)) back.pendingPoints += points;
            });
        });
    }

    /** Shutdown: every pending write (SuldServices waits within its budget). */
    public CompletableFuture<Void> shutdown() {
        List<CompletableFuture<Void>> all = new ArrayList<>();
        for (UUID id : new ArrayList<>(states.keySet())) all.add(flush(id, true));
        return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new));
    }

    // ================================================================== deeds

    /** A deed of {@code p} (see {@link Deed}): may unlock achievements and gives the pass its points. */
    public void record(Player p, Deed deed, String subject, long amount) {
        record(p, deed, subject, amount, false);
    }

    private void record(Player p, Deed deed, String subject, long amount, boolean backfill) {
        if (p == null || amount <= 0) return;
        UUID id = p.getUniqueId();
        if (services.training != null && services.training.training(id)) return; // a stand-in does nothing in a name
        State st = states.get(id);
        if (st == null) return;
        if (!st.loaded) {
            if (st.early.size() < EARLY_MAX) st.early.add(new Early(deed, subject == null ? "" : subject, amount, backfill));
            return;
        }
        String s = subject == null ? "" : subject;
        long delta = st.book.record(deed, s, amount);
        if (delta == 0) return;
        String key = deed.key(s);
        if (deed.keep() == Deed.Keep.COUNT) st.addCounts.merge(key, amount, Long::sum);
        else st.raiseBests.merge(key, st.book.value(deed, s), Math::max);
        if (!backfill) addPoints(p, st, CampaignPass.points(deed, delta), true);
        for (Achievement a : AchievementCatalog.newlyMet(catalog.on(deed), st.book, st.unlocked)) unlock(p, st, a);
    }

    /**
     * What the player did before this system, recorded now (each a best or a seen deed: recording it again changes
     * nothing). It unlocks what it completes; it gives the pass no points.
     */
    private void backfill(Player p) {
        UUID id = p.getUniqueId();
        PlayerProfile pr = services.profiles().cached(id).orElse(null);
        if (pr != null) {
            record(p, Deed.LEVEL, "", pr.progression().level(), true);
            record(p, Deed.ASCENSION, "", pr.ascension(), true);
            record(p, Deed.CLASS_MASTERS, "", mn.suld.api.clazz.ClassMastery.masters(pr), true);
            var chain = services.quests().chain();
            int done = chain.completedCount(pr.questState());
            for (int i = 0; i < done && i < chain.size(); i++) record(p, Deed.CHAPTER, chain.chapters().get(i).id(), 1, true);
        }
        if (services.historicSites != null) for (String site : services.historicSites.seen(p)) record(p, Deed.SITE, site, 1, true);
        // an ovoo walked round at least once keeps its last day in the player's data (OvooService, "ovoo_<id>")
        for (org.bukkit.NamespacedKey k : p.getPersistentDataContainer().getKeys()) {
            if (k.getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT)) && k.getKey().startsWith(mn.suld.plugin.progress.PlayerMarks.OVOO_PREFIX)) {
                record(p, Deed.OVOO, k.getKey().substring(mn.suld.plugin.progress.PlayerMarks.OVOO_PREFIX.length()), 1, true);
            }
        }
        long bits = services.styles().cached(id).map(mn.suld.api.style.PlayerStyle::discovered).orElse(0L);
        for (mn.suld.api.region.RegionDefinition r : mn.suld.plugin.content.Content.pack().regions()) {
            int bit = mn.suld.plugin.content.Content.pack().regionBit(r.id());
            if (bit >= 0 && (bits >>> bit & 1) == 1) record(p, Deed.REGION, r.id(), 1, true);
        }
        services.clans().clanOf(id).ifPresent(c -> record(p, Deed.CLAN_JOIN, c.id().toString(), 1, true));
        // weapon mastery and the crafts read their own tables at join: their levels a little later
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online == null) return;
            var wm = services.weaponMastery;
            if (wm != null && wm.loaded(id)) {
                for (mn.suld.api.mastery.WeaponMastery.Track t : mn.suld.api.mastery.WeaponMastery.Track.values()) record(online, Deed.WEAPON_MASTERY, t.name(), wm.level(id, t), true);
            }
            var ps = services.professions;
            if (ps != null && ps.loaded(id)) {
                for (mn.suld.api.mastery.Profession c : mn.suld.api.mastery.Profession.values()) record(online, Deed.CRAFT_LEVEL, c.name(), ps.level(id, c), true);
            }
        }, 100L);
    }

    private void unlock(Player p, State st, Achievement a) {
        long now = System.currentTimeMillis();
        st.unlocked.add(a.id());
        st.newUnlocks.put(a.id(), now);
        p.showTitle(Title.title(Component.text("✦ АМЖИЛТ", GOLD, TextDecoration.BOLD), Component.text(a.name(), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2200), Duration.ofMillis(500))));
        p.sendMessage(Component.text("✦ Амжилт: ", GOLD, TextDecoration.BOLD).append(Component.text(a.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" — " + a.description() + (a.points() > 0 ? " (+" + a.points() + " Хуралдайн Зам)" : ""), CREAM)));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.1f);
        Cosmetic reward = a.reward() == null ? null : CosmeticCatalog.byId(a.reward()).orElse(null);
        if (reward != null) services.styles().grantEarned(p, reward.id(), "Амжилтын шагнал");
        addPoints(p, st, a.points(), false);
        if (a.hidden() || (reward != null && reward.rarity() == Cosmetic.Rarity.LEGENDARY)) {
            Bukkit.broadcast(Component.text("✦ " + p.getName() + " — «" + a.name() + "» амжилтыг гүйцэтгэлээ!", GOLD, TextDecoration.BOLD));
        }
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("achievement_unlocked", p.getUniqueId(),
                Map.<String, Object>of("achievement", a.id(), "points", a.points())));
    }

    // ================================================================== the pass

    private void addPoints(Player p, State st, long add, boolean capped) {
        if (add <= 0) return;
        rollSeason(p, st);
        if (capped) {
            long day = LocalDate.now(services.zone()).toEpochDay();
            long[] d = daily.computeIfAbsent(p.getUniqueId(), k -> new long[]{day, 0});
            if (d[0] != day) {
                d[0] = day;
                d[1] = 0;
            }
            add = CampaignPass.underCap(d[1], add);
            if (add <= 0) return;
            d[1] += add;
        }
        int before = CampaignPass.tier(st.points);
        st.points += add;
        st.pendingPoints += add;
        int after = CampaignPass.tier(st.points);
        if (after > before) {
            p.sendMessage(Component.text("⚑ Хуралдайн Зам: " + after + "-р шат!", GOLD, TextDecoration.BOLD)
                    .append(Component.text(claimable(p).isEmpty() ? "" : "  Шагналаа /pass-аас ав.", CREAM)));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        }
    }

    /** A new season began while the player is online: the old one's points are written, the new one starts at 0. */
    private void rollSeason(Player p, State st) {
        String now = season().id();
        if (now.equals(st.season)) return;
        flush(p.getUniqueId(), false);
        st.season = now;
        st.points = 0;
        st.pendingPoints = 0;
        st.heroic = false;
        st.claimed.clear();
        p.sendMessage(Messages.info("Хуралдайн Замын шинэ улирал эхэллээ: " + now + ". /pass"));
    }

    public boolean loaded(UUID id) {
        State st = states.get(id);
        return st != null && st.loaded;
    }

    public long points(UUID id) {
        State st = states.get(id);
        return st == null ? 0 : st.points;
    }

    public boolean heroic(UUID id) {
        State st = states.get(id);
        return st != null && st.heroic;
    }

    public boolean claimed(UUID id, CampaignPass.Reward r) {
        State st = states.get(id);
        return st != null && st.claimed.contains(CampaignPass.key(r));
    }

    public Set<String> unlocked(UUID id) {
        State st = states.get(id);
        return st == null ? Set.of() : Set.copyOf(st.unlocked);
    }

    /** The number an achievement's criterion is measured by now (0 until the read is back). */
    public long progress(UUID id, Achievement a) {
        State st = states.get(id);
        return st == null || !st.loaded ? 0 : st.book.progress(a.criterion());
    }

    public List<CampaignPass.Reward> claimable(Player p) {
        State st = states.get(p.getUniqueId());
        if (st == null || !st.loaded) return List.of();
        return CampaignPass.claimable(layout, st.points, st.heroic, st.claimed);
    }

    /** Claims a reached reward: the claim row first (once), then the cosmetic. */
    public void claim(Player p, CampaignPass.Reward r, Runnable after) {
        UUID id = p.getUniqueId();
        State st = states.get(id);
        if (st == null || !st.loaded) {
            p.sendMessage(Messages.error("Ачаалж байна… түр хүлээгээд дахин."));
            return;
        }
        rollSeason(p, st);
        String key = CampaignPass.key(r);
        if (!CampaignPass.claimable(layout, st.points, st.heroic, st.claimed).contains(r)) {
            p.sendMessage(Messages.error(r.track() == CampaignPass.Track.HEROIC && !st.heroic ? "Баатарлаг зам нээгдээгүй байна."
                    : CampaignPass.tier(st.points) < r.tier() ? r.tier() + "-р шатанд хүрээгүй байна." : "Энэ шагналыг авсан."));
            return;
        }
        if (!st.claiming.add(key)) return;
        String season = st.season;
        repo.claim(id, season, key, System.currentTimeMillis()).whenComplete((made, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            st.claiming.remove(key);
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "[achievements] claim " + key + " of " + id + " not written", ex);
                if (p.isOnline()) p.sendMessage(Messages.error("Шагнал бичигдсэнгүй — дахин оролдоно уу."));
                return;
            }
            st.claimed.add(key);
            if (!Boolean.TRUE.equals(made) || !p.isOnline()) return; // claimed already (another server, a double click)
            services.styles().grantEarned(p, r.cosmetic(), "Хуралдайн Зам " + r.tier() + "-р шат");
            services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("pass_claim", id,
                    Map.<String, Object>of("season", season, "track", r.track().name(), "tier", r.tier())));
            after.run();
        }));
    }

    /** Opens the heroic track for this season ({@code source}: staff, credits). */
    public void openHeroic(UUID id, String source, java.util.function.Consumer<String> feedback) {
        String season = season().id();
        repo.openHeroic(id, season, source, System.currentTimeMillis()).whenComplete((made, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                feedback.accept("not written: " + ex.getMessage());
                return;
            }
            State st = states.get(id);
            if (st != null && season.equals(st.season)) st.heroic = true;
            Player p = Bukkit.getPlayer(id);
            if (Boolean.TRUE.equals(made) && p != null) {
                p.sendMessage(Component.text("⚑ Хуралдайн Замын Баатарлаг зам нээгдлээ! /pass", GOLD, TextDecoration.BOLD));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.3f);
            }
            feedback.accept(Boolean.TRUE.equals(made) ? "opened for " + season : "already open for " + season);
        }));
    }

    // ================================================================== staff

    /** Staff/QA: a deed recorded as the game would. */
    public void staffRecord(Player p, Deed deed, String subject, long amount) {
        record(p, deed, subject, amount, false);
    }

    /** Staff/QA: points straight to the pass (not capped). */
    public void staffPoints(Player p, long points) {
        State st = states.get(p.getUniqueId());
        if (st != null && st.loaded) addPoints(p, st, points, false);
    }

    /** Staff/QA: forgets a player's deeds, unlocks and passes (online: read again). */
    public CompletableFuture<Void> reset(Player p) {
        UUID id = p.getUniqueId();
        states.remove(id);
        daily.remove(id);
        return repo.reset(id).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) load(p);
        }));
    }
}
