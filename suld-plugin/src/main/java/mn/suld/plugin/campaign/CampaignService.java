package mn.suld.plugin.campaign;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.campaign.CampaignDefinition;
import mn.suld.api.campaign.CampaignGoal;
import mn.suld.api.campaign.CampaignOutcome;
import mn.suld.api.campaign.CampaignRotation;
import mn.suld.api.campaign.CampaignRun;
import mn.suld.api.campaign.CampaignSnapshot;
import mn.suld.api.campaign.CampaignState;
import mn.suld.api.campaign.Contribution;
import mn.suld.api.campaign.WeeklyCycle;
import mn.suld.api.clan.Clan;
import mn.suld.api.config.CampaignSettings;
import mn.suld.api.persistence.CampaignRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.ExpSource;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.chronicle.ChronicleService;
import mn.suld.plugin.content.Content;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * The week's campaign (docs/KHURALDAI.md §Амьд ертөнц): the shared goal the Хуралдай chose. Rules live in the pure
 * {@link CampaignRun}; this class starts the campaign when the clock says so, buffers contributions on the main thread
 * and flushes them every few seconds, keeps the boss bar, resolves the week (from the database totals), pays the tiers,
 * applies the world effect, and writes the chronicle. Restart-safe: on start it rebuilds its state from the database and
 * performs any phase change that happened while the server was down.
 */
public final class CampaignService {

    private final Plugin plugin;
    private final SuldServices services;
    private final CampaignRepository repository;
    private final CampaignSettings settings;
    private final WeeklyCycle cycle;
    private final KhuraldaiService khuraldai;
    private final ChronicleService chronicle;
    private final CampaignEffects effects;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);

    private CampaignRun active;
    private long activeVersion;
    private final Map<UUID, Contribution> buffer = new LinkedHashMap<>();
    private final Set<UUID> tierToasted = new HashSet<>();
    private long lastFlush;
    private boolean flushing, resolving, starting;
    private String startedCycle = "";
    private String restoredCycle = "";
    private volatile CampaignOutcome lastOutcome;

    public CampaignService(Plugin plugin, SuldServices services, CampaignRepository repository, CampaignSettings settings, WeeklyCycle cycle,
                           KhuraldaiService khuraldai, ChronicleService chronicle, CampaignEffects effects) {
        this.plugin = plugin;
        this.services = services;
        this.repository = repository;
        this.settings = settings;
        this.cycle = cycle;
        this.khuraldai = khuraldai;
        this.chronicle = chronicle;
        this.effects = effects;
    }

    /** After the city, props and NPCs started: rebuild from the database, then follow the clock. */
    public void start() {
        restore();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public Optional<CampaignRun> active() {
        return Optional.ofNullable(active);
    }

    public CampaignSettings settings() {
        return settings;
    }

    public @Nullable CampaignOutcome lastOutcome() {
        return lastOutcome;
    }

    /** The world effects campaigns have left that are live now (their flag keys). */
    public List<String> worldMarks() {
        return effects.liveKeys();
    }

    public String cycleId() {
        return cycle.cycleId(Instant.now());
    }

    // --------------------------------------------------------------- restore

    private void restore() {
        String id = cycleId();
        restoredCycle = id;
        effects.restore(id);
        restore(id, true);
    }

    /**
     * Rebuild the run stored for {@code id}. With {@code thenNext}, a week with no live row also looks at next week's:
     * an admin force during the weekend interlude is filed under the week it runs into (see {@link #adminForce}).
     */
    private void restore(String id, boolean thenNext) {
        whenDone(repository.campaign(id), (row, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[campaign] row not read: " + root(err));
                return;
            }
            boolean live = row.isPresent() && !row.get().state().resolved() && Content.pack().campaign(row.get().campaignId()) != null;
            if (!live && thenNext && cycle.phase(Instant.now()) != WeeklyCycle.Phase.CAMPAIGN) {
                String next = cycle.nextCycleId(id);
                whenDone(repository.campaign(next), (n, e) -> {
                    if (e == null && n.isPresent() && !n.get().state().resolved() && Content.pack().campaign(n.get().campaignId()) != null) {
                        restore(next, false);
                    } else if (row.isPresent()) {
                        restore(id, false);
                    }
                });
                return;
            }
            if (row.isEmpty()) return; // the tick starts one when the clock says so
            CampaignSnapshot s = row.get();
            CampaignDefinition def = Content.pack().campaign(s.campaignId());
            if (def == null) {
                startedCycle = id;
                if (s.state().resolved()) {
                    plugin.getLogger().warning("[campaign] " + s.campaignId() + " of " + id + " is no longer in campaigns.json (already resolved)");
                    return;
                }
                // an orphaned live row would block the week: nothing can run it, cancel finds no run, force sees it live
                whenDone(repository.saveCampaign(s.withState(mn.suld.api.campaign.CampaignState.CANCELLED, System.currentTimeMillis()), s.version()),
                        (ok, e) -> plugin.getLogger().warning("[campaign] " + s.campaignId() + " of " + id + " is no longer in campaigns.json; "
                                + (e == null && Boolean.TRUE.equals(ok) ? "cancelled, so the week can start another campaign" : "could not cancel it: " + (e == null ? "version moved" : root(e)))));
                return;
            }
            if (s.state().resolved()) {
                startedCycle = id; // this week is done, nothing to run
                return;
            }
            whenDone(repository.contributions(id), (totals, e2) -> {
                if (e2 != null) {
                    plugin.getLogger().log(Level.WARNING, "[campaign] totals not read: " + root(e2));
                    return;
                }
                active = CampaignRun.restore(def, s, totals);
                activeVersion = s.version();
                startedCycle = id;
                for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
                updateBar();
                plugin.getLogger().info("[campaign] restored " + def.id() + " for " + id + ": " + active.total() + "/" + def.goal().target()
                        + " (" + totals.size() + " contributors)");
            });
        });
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        Instant now = Instant.now();
        String id = cycle.cycleId(now);
        if (!id.equals(restoredCycle)) { // a new week began while running: lapsed effects go, live ones stay
            restoredCycle = id;
            effects.restore(id);
        }
        if (active == null) {
            if (settings.campaignEnabled() && cycle.phase(now) == WeeklyCycle.Phase.CAMPAIGN && !id.equals(startedCycle) && !starting) startScheduled(id);
            return;
        }
        if (active.isActive() && active.expireIfDue(now)) {
            resolve("the week ended");
            return;
        }
        if (!active.isActive() && !resolving && System.currentTimeMillis() - lastResolveTry >= 10_000) {
            // the goal was reached, or a resolution that could not finish (database, unsaved contributions) retries
            resolve(active.state() == CampaignState.SUCCEEDED ? "the goal was reached" : "retry");
            return;
        }
        if (!active.isActive()) return;
        if (System.currentTimeMillis() - lastFlush >= settings.flushSeconds() * 1000L) flushDirty();
        updateBar();
    }

    private long lastVoteWait;
    private long lastResolveTry;

    /** The clock says a campaign should run this week: the winner of last week's vote, else the rotation's pick. */
    private void startScheduled(String id) {
        starting = true;
        whenDone(khuraldai.winnerFor(id), (winner, err) -> {
            if (err != null) { // unknown or still being closed, not "no vote": a later tick tries again
                starting = false;
                long now = System.currentTimeMillis();
                if (now - lastVoteWait > 60_000) {
                    lastVoteWait = now;
                    plugin.getLogger().info("[campaign] waiting for the vote result of " + id + ": " + root(err));
                }
                return;
            }
            CampaignDefinition voted = winner.map(w -> Content.pack().campaign(w)).orElse(null);
            if (voted != null) {
                begin(voted, id, cycle.campaignStarts(id), cycle.campaignEnds(id), "clock");
                return;
            }
            List<CampaignDefinition> all = Content.pack().campaigns();
            if (all.isEmpty()) {
                starting = false;
                startedCycle = id;
                return;
            }
            // the rotation honours the arc like the ballot does: a locked chapter never runs without its vote
            whenDone(repository.succeededCampaigns(), (succeeded, err2) -> {
                if (err2 != null) {
                    starting = false;
                    plugin.getLogger().log(Level.WARNING, "[campaign] arc state not read: " + root(err2));
                    return;
                }
                List<CampaignDefinition> pick = CampaignRotation.pick(all, List.of(), List.of(), 2, id, succeeded);
                if (pick.isEmpty()) {
                    starting = false;
                    startedCycle = id;
                    return;
                }
                CampaignDefinition def = pick.get(0);
                plugin.getLogger().info("[campaign] no vote for " + id + ": the rotation picks " + def.id());
                begin(def, id, cycle.campaignStarts(id), cycle.campaignEnds(id), "clock");
            });
        });
    }

    /** Insert the week's row and run. The insert is the race guard: a second server finds the row and restores instead. */
    private void begin(CampaignDefinition def, String id, Instant startsAt, Instant endsAt, String actor) {
        CampaignRun run = new CampaignRun(def, id, startsAt, endsAt);
        CampaignSnapshot row = run.snapshot(0, null, null);
        whenDone(repository.saveCampaign(row, -1), (inserted, err) -> {
            starting = false;
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[campaign] start of " + def.id() + " not saved: " + root(err));
                return;
            }
            if (!inserted) {
                if (!"clock".equals(actor)) { // an admin force after this week's campaign resolved: replace the row
                    whenDone(repository.campaign(id), (existing, e2) -> {
                        boolean orphan = existing.isPresent() && Content.pack().campaign(existing.get().campaignId()) == null;
                        if (e2 != null || existing.isEmpty() || (!existing.get().state().resolved() && !orphan)) {
                            plugin.getLogger().info("[campaign] " + id + " already has a live campaign row: restoring it");
                            restore();
                            return;
                        }
                        long expected = existing.get().version();
                        whenDone(repository.replaceCampaign(row, expected), (ok, e3) -> {
                            if (e3 != null || !ok) {
                                plugin.getLogger().warning("[campaign] " + id + " could not be replaced (" + (e3 == null ? "version moved" : root(e3)) + ")");
                                restore();
                                return;
                            }
                            activate(run, expected + 1, actor);
                        });
                    });
                    return;
                }
                plugin.getLogger().info("[campaign] " + id + " already has a campaign row: restoring it");
                restore();
                return;
            }
            activate(run, 0, actor);
        });
    }

    private void activate(CampaignRun run, long version, String actor) {
        CampaignDefinition def = run.definition();
        String id = run.cycleId();
        {
            active = run;
            activeVersion = version;
            startedCycle = id;
            buffer.clear();
            tierToasted.clear();
            lastOutcome = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showBossBar(bar);
                Presentation.banner(p, def.name().toUpperCase(java.util.Locale.ROOT), "Долоо хоногийн аян эхэллээ · /khuraldai", NamedTextColor.GOLD);
                p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            }
            Bukkit.broadcast(Messages.accent("✦ Аян эхэллээ: «" + def.name() + "» — " + def.description()));
            Bukkit.broadcast(Component.text("  Зорилго: " + def.goal().target() + " " + def.goal().unitWord() + " · Шат: " + def.tiers()[0] + " / "
                    + def.tiers()[1] + " / " + def.tiers()[2] + " · дуусах: " + settings.campaignEnds(), NamedTextColor.GRAY));
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.CAMPAIGN_START, null, Map.of("cycle", id, "campaign", def.id(), "by", actor)));
            plugin.getLogger().info("[campaign] " + def.id() + " started for " + id + " (" + actor + ")");
            updateBar();
        }
    }

    // ----------------------------------------------------------------- hooks

    /** A SÜLD mob died to a player (MONITOR). */
    public void onMobKill(Player killer, String mobId) {
        record(killer, CampaignGoal.Type.KILL_MOBS, mobId, 1, null);
    }

    /** A dungeon run completed; everyone rewarded is credited once for this run. */
    public void onDungeonCleared(String dungeonId, List<Player> rewarded, UUID runId) {
        for (Player p : rewarded) record(p, CampaignGoal.Type.CLEAR_DUNGEON, dungeonId, 1, "run:" + runId);
    }

    /** A historic site was seen for the first time by this player. */
    public void onSiteDiscovered(Player p, String siteId) {
        record(p, CampaignGoal.Type.DISCOVER_SITES, siteId, 1, "site:" + siteId);
    }

    /** Items handed to the campaign ({@code /khuraldai deliver}); returns how many counted. */
    public long deliver(Player p, String itemId, long amount) {
        long before = active == null ? 0 : active.unitsOf(p.getUniqueId());
        if (!record(p, CampaignGoal.Type.DELIVER_ITEMS, itemId, amount, null)) return 0;
        return active == null ? amount : active.unitsOf(p.getUniqueId()) - before;
    }

    /** Whether the running campaign takes {@code itemId} deliveries. */
    public boolean accepts(CampaignGoal.Type type, String id) {
        return active != null && active.isActive() && active.definition().goal().counts(type, id);
    }

    private boolean record(Player p, CampaignGoal.Type type, String targetId, long amount, @Nullable String dedupe) {
        CampaignRun run = active;
        if (run == null || !run.isActive() || !run.definition().goal().counts(type, targetId)) return false;
        if (services.isSoul.test(p.getUniqueId())) return false;
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || !pr.hasSelectedClass()) return false;
        UUID clan = services.clans().clanOf(p.getUniqueId()).map(Clan::id).orElse(null);
        int tierBefore = run.tierOf(p.getUniqueId());
        if (!run.record(p.getUniqueId(), p.getName(), clan, type, targetId, amount, dedupe, Instant.now())) return false;
        Contribution cur = buffer.get(p.getUniqueId());
        buffer.put(p.getUniqueId(), cur == null ? Contribution.delta(p.getUniqueId(), p.getName(), clan, amount) : cur.plus(amount));
        if (run.definition().clanExpPerUnit() > 0) services.clans().contribute(p.getUniqueId(), run.definition().clanExpPerUnit() * amount);
        long mine = run.unitsOf(p.getUniqueId());
        int tier = run.tierOf(p.getUniqueId());
        if (tier > tierBefore) {
            String[] names = {"", "хүрэл", "мөнгөн", "алтан"};
            p.sendMessage(Messages.success("Аян: та " + names[tier] + " шатанд хүрлээ (" + mine + " " + run.definition().goal().unitWord() + ")."));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.CAMPAIGN_CONTRIBUTION, p.getUniqueId(),
                    Map.of("cycle", run.cycleId(), "campaign", run.definition().id(), "tier", tier, "units", mine)));
        } else if (tierToasted.add(p.getUniqueId()) || amount > 1 || type != CampaignGoal.Type.KILL_MOBS) {
            services.hud().toast(p, Component.text(run.definition().name() + ": таны хувь " + mine + " · нийт " + run.total() + "/"
                    + run.definition().goal().target(), NamedTextColor.GOLD));
        }
        updateBar();
        if (run.state() == CampaignState.SUCCEEDED && !resolving) resolve("the goal was reached");
        return true;
    }

    // ----------------------------------------------------------------- flush

    /** The flush in progress (completed when none is); its callback runs on the main thread. */
    private CompletableFuture<Void> flight = CompletableFuture.completedFuture(null);

    /**
     * Everything counted so far is in the database: waits for a flush already running, then writes what the buffer
     * still holds. True only when nothing is left behind; resolving on less would pay tiers on a short total.
     */
    private CompletableFuture<Boolean> drain() {
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        flight.whenComplete((v, e) -> Bukkit.getScheduler().runTask(plugin, () ->
                flushDirty().whenComplete((v2, e2) -> Bukkit.getScheduler().runTask(plugin, () -> out.complete(buffer.isEmpty() && !flushing)))));
        return out;
    }

    private CompletableFuture<Void> flushDirty() {
        lastFlush = System.currentTimeMillis();
        if (buffer.isEmpty() || flushing || active == null) return CompletableFuture.completedFuture(null);
        List<Contribution> deltas = new ArrayList<>(buffer.values());
        String id = active.cycleId();
        buffer.clear();
        flushing = true;
        CompletableFuture<Void> done = new CompletableFuture<>();
        flight = done;
        repository.addContributions(id, deltas).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            flushing = false;
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[campaign] flush of " + deltas.size() + " contributions failed (" + root(err) + "); kept for the next flush");
                for (Contribution d : deltas) {
                    Contribution cur = buffer.get(d.player());
                    buffer.put(d.player(), cur == null ? d : cur.plus(d.units()));
                }
            }
            done.complete(null);
        }));
        return done;
    }

    // --------------------------------------------------------------- resolve

    /** End the week: flush, read the authoritative totals, CAS the state, pay, change the world, write the book. */
    /** The arc moves on: tell everyone which chapters this success opened for the next Хуралдай. */
    private void announceUnlocks(CampaignDefinition won) {
        whenDone(repository.succeededCampaigns(), (succeeded, err) -> {
            if (err != null) return;
            java.util.Set<String> done = new java.util.HashSet<>(succeeded);
            done.add(won.id()); // the success row may not be read back yet
            for (CampaignDefinition d : Content.pack().campaigns()) {
                if (d.requires().contains(won.id()) && d.unlocked(done)) {
                    Bukkit.broadcast(Messages.accent("✦ Шинэ аян нээгдлээ: «" + d.name() + "» — дараагийн Их Хуралдайд гарна."));
                    plugin.getLogger().info("[campaign] " + won.id() + " unlocked " + d.id());
                }
            }
        });
    }

    private void resolve(String reason) {
        CampaignRun run = active;
        if (run == null || resolving) return;
        resolving = true;
        lastResolveTry = System.currentTimeMillis();
        if (run.isActive()) run.expireIfDue(Instant.MAX); // an admin resolve: the clock did not end it
        CampaignState state = run.state();
        drain().thenAccept(flushed -> {
            if (!flushed) { // the last contributions are not stored yet: a later tick resolves again
                resolving = false;
                plugin.getLogger().warning("[campaign] " + run.cycleId() + " not resolved yet: contributions still unsaved");
                return;
            }
            resolveStored(run, state, reason);
        });
    }

    private void resolveStored(CampaignRun run, CampaignState state, String reason) {
        whenDone(repository.contributions(run.cycleId()), (totals, err) -> {
            if (err != null) {
                resolving = false;
                plugin.getLogger().log(Level.WARNING, "[campaign] totals not read at resolution: " + root(err));
                return;
            }
            long sum = 0;
            for (Contribution c : totals) sum += c.units();
            final long total = sum;
            boolean success = state == CampaignState.SUCCEEDED || total >= run.definition().goal().target();
            CampaignState finalState = state == CampaignState.CANCELLED ? CampaignState.CANCELLED : success ? CampaignState.SUCCEEDED : CampaignState.FAILED;
            long now = System.currentTimeMillis();
            CampaignSnapshot next = run.snapshot(activeVersion, now, null).withState(finalState, now).withTotal(total);
            whenDone(repository.saveCampaign(next, activeVersion), (ok, e2) -> {
                if (e2 != null) {
                    resolving = false;
                    plugin.getLogger().log(Level.WARNING, "[campaign] resolution not saved: " + root(e2));
                    return;
                }
                if (!ok) {
                    plugin.getLogger().info("[campaign] " + run.cycleId() + " was resolved elsewhere; dropping the local run");
                    finish(run);
                    return;
                }
                activeVersion++;
                CampaignOutcome outcome = outcome(run, totals, total, finalState == CampaignState.SUCCEEDED);
                lastOutcome = outcome;
                plugin.getLogger().info("[campaign] " + run.definition().id() + " resolved " + finalState + " (" + reason + "): " + total + "/" + run.definition().goal().target());
                if (finalState != CampaignState.CANCELLED) {
                    payRewards(run, totals, outcome);
                    if (finalState == CampaignState.SUCCEEDED && run.definition().effect() != null) {
                        effects.apply(run.definition().effect(), run.definition().id(), run.cycleId());
                        whenDone(repository.saveCampaign(next.withEffectApplied(System.currentTimeMillis()), activeVersion), (o2, e3) -> {
                            if (o2 != null && o2) activeVersion++;
                        });
                    }
                    chronicle.campaignResolved(outcome);
                    Bukkit.broadcast(Messages.accent(outcome.headline()));
                    if (finalState == CampaignState.SUCCEEDED) announceUnlocks(run.definition());
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), finalState == CampaignState.SUCCEEDED ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_BELL_USE, 1f, 1f);
                    }
                } else {
                    Bukkit.broadcast(Messages.info("Аян «" + run.definition().name() + "» зогсоогдлоо."));
                }
                services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.CAMPAIGN_END, null, Map.of("cycle", run.cycleId(),
                        "campaign", run.definition().id(), "state", finalState.name(), "total", total, "participants", outcome.participants())));
                finish(run);
            });
        });
    }

    private void finish(CampaignRun run) {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        if (active == run) active = null;
        resolving = false;
        buffer.clear();
        tierToasted.clear();
        startedCycle = run.cycleId(); // one campaign per cycle
        for (Player p : Bukkit.getOnlinePlayers()) services.profiles().cached(p.getUniqueId()).ifPresent(pr -> services.hud().update(p, pr));
    }

    private CampaignOutcome outcome(CampaignRun run, List<Contribution> totals, long total, boolean success) {
        Map<UUID, Long> clanUnits = new HashMap<>();
        int participants = 0;
        for (Contribution c : totals) {
            if (run.definition().tierOf(c.units()) >= 1) participants++;
            if (c.clan() != null) clanUnits.merge(c.clan(), c.units(), Long::sum);
        }
        UUID bestClan = clanUnits.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        String tag = bestClan == null ? null : services.clans().registry().byId(bestClan).map(Clan::tag).orElse(null);
        return new CampaignOutcome(run.cycleId(), run.definition(), success, total, run.definition().goal().target(), totals, tag, participants);
    }

    /**
     * Tier rewards: online players now; offline ones owed in the database and paid on their next login. A player in the
     * training yard is owed too (the row is written first, so a crash there pays it on the next join) and is paid the
     * moment training ends ({@link #pay}).
     */
    private void payRewards(CampaignRun run, List<Contribution> totals, CampaignOutcome outcome) {
        CampaignDefinition def = run.definition();
        Set<UUID> clansPaid = new HashSet<>();
        int rank = 0;
        for (Contribution c : totals) {
            rank++;
            int tier = def.tierOf(c.units());
            if (tier < 1) continue;
            if (outcome.success() && c.clan() != null && def.clanSuccessBonus() > 0 && clansPaid.add(c.clan())) {
                services.clans().contribute(c.player(), def.clanSuccessBonus());
            }
            Player p = Bukkit.getPlayer(c.player());
            PlayerProfile held = services.profiles().cached(c.player()).orElse(null);
            if (p != null && held != null) {
                if (held.ephemeral()) repository.markReward(run.cycleId(), c.player(), tier, null); // owed until training ends
                pay(p, run.cycleId(), def, tier, rank, outcome.success());
            } else {
                repository.markReward(run.cycleId(), c.player(), tier, settings.rewardClaimDays() > 0 ? null : 0L);
            }
        }
    }

    private void pay(Player p, String cycleId, CampaignDefinition def, int tier, int rank, boolean success) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        if (pr.ephemeral()) { // the training stand-in would take it and drop it: it waits for the real character (the row stays owed)
            if (services.training != null) services.training.defer(p.getUniqueId(), "Аяны шагнал «" + def.name() + "»",
                    q -> pay(q, cycleId, def, tier, rank, success));
            return;
        }
        CampaignDefinition.TierReward r = def.reward(tier);
        double mult = def.podiumMultiplier(rank) * (success ? 1.0 : 0.5); // a failed week still pays half for the effort
        long exp = services.boosts().apply(p.getUniqueId(), Math.round(r.exp() * mult));
        long coins = Math.round(r.coins() * mult);
        int from = pr.progression().level();
        ExpGainResult gain = services.progression().grantExp(pr, exp, ExpSource.WORLD_EVENT);
        pr.addCurrency(coins);
        services.profiles().save(pr);
        String[] names = {"", "хүрэл", "мөнгөн", "алтан"};
        p.sendMessage(Messages.success("Аян «" + def.name() + "»: " + names[tier] + " шат" + (rank <= 3 ? ", #" + rank + " тэргүүн" : "")
                + " → +" + exp + " EXP, +" + coins + " зоос"));
        if (gain.leveledUp()) Presentation.levelUp(p, from, gain.after().level());
        repository.markReward(cycleId, p.getUniqueId(), tier, System.currentTimeMillis());
        services.achievements.record(p, mn.suld.api.achievement.Deed.CAMPAIGN_TIER, def.id(), tier);
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.CAMPAIGN_REWARD_CLAIM, p.getUniqueId(),
                Map.of("cycle", cycleId, "campaign", def.id(), "tier", tier, "rank", rank, "exp", exp, "coins", coins)));
    }

    /** On login: a tier owed from a recent week (within reward-claim-days) is paid now. */
    public void onJoin(Player p) {
        if (active != null) p.showBossBar(bar);
        if (settings.rewardClaimDays() <= 0) return;
        long since = System.currentTimeMillis() - settings.rewardClaimDays() * 86_400_000L;
        whenDone(repository.recentCampaigns(3), (rows, err) -> {
            if (err != null || !p.isOnline()) return;
            for (CampaignSnapshot s : rows) {
                if (!s.state().resolved() || s.state() == CampaignState.CANCELLED || s.resolvedAt() == null || s.resolvedAt() < since) continue;
                CampaignDefinition def = Content.pack().campaign(s.campaignId());
                if (def == null) continue;
                whenDone(repository.contributions(s.cycleId()), (totals, e2) -> {
                    if (e2 != null || !p.isOnline()) return;
                    int rank = 0;
                    for (Contribution c : totals) {
                        rank++;
                        if (!c.player().equals(p.getUniqueId())) continue;
                        if (c.rewardTier() != null && c.rewardTier() >= 1 && c.claimedAt() == null) {
                            pay(p, s.cycleId(), def, c.rewardTier(), rank, s.state() == CampaignState.SUCCEEDED);
                        }
                        break;
                    }
                });
            }
        });
    }

    public void onQuit(Player p) {
        tierToasted.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ admin

    /** Start {@code def} now for the current cycle (an admin force); false when a campaign already runs. */
    public boolean adminForce(CampaignDefinition def, String actor) {
        if (active != null || starting) return false;
        String id = cycleId();
        services.audit().record(AuditEvent.of(actor, AuditActions.CAMPAIGN_ADMIN_FORCE, id, def.id()));
        starting = true;
        Instant now = Instant.now();
        Instant ends = cycle.campaignEnds(id);
        if (!ends.isAfter(now)) { // forced during the interlude: it is next week's campaign, filed under that week
            id = cycle.nextCycleId(id);
            ends = cycle.campaignEnds(id);
        }
        begin(def, id, now, ends, actor);
        return true;
    }

    public boolean adminResolve(String actor) {
        if (active == null) return false;
        services.audit().record(AuditEvent.of(actor, AuditActions.CAMPAIGN_ADMIN_RESOLVE, active.cycleId(), active.definition().id()));
        resolve("admin " + actor);
        return true;
    }

    public boolean adminCancel(String actor) {
        if (active == null) return false;
        services.audit().record(AuditEvent.of(actor, AuditActions.CAMPAIGN_ADMIN_CANCEL, active.cycleId(), active.definition().id()));
        active.cancel();
        resolve("cancelled by " + actor);
        return true;
    }

    // ------------------------------------------------------------------- HUD

    public Optional<String> hudLine(UUID player) {
        CampaignRun run = active;
        if (run == null || !run.isActive()) return Optional.empty();
        return Optional.of("§7Аян: §6" + run.total() + "/" + run.definition().goal().target() + " §7(таны " + run.unitsOf(player) + ")");
    }

    private void updateBar() {
        CampaignRun run = active;
        if (run == null) return;
        long left = run.remainingSeconds(Instant.now());
        String time = left >= 86_400 ? (left / 86_400) + " өдөр" : left >= 3600 ? (left / 3600) + " цаг" : (left / 60) + " мин";
        bar.name(Component.text("✦ " + run.definition().name() + " — " + run.total() + "/" + run.definition().goal().target() + " · " + time, NamedTextColor.GOLD));
        bar.progress((float) run.progress());
    }

    /** Shutdown: the buffered contributions are written; the caller waits with the shutdown budget. */
    public CompletableFuture<Void> shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        if (buffer.isEmpty() || active == null) return CompletableFuture.completedFuture(null);
        List<Contribution> deltas = new ArrayList<>(buffer.values());
        buffer.clear();
        return repository.addContributions(active.cycleId(), deltas);
    }

    // ---------------------------------------------------------------- helpers

    private <T> void whenDone(CompletableFuture<T> future, BiConsumer<T, Throwable> callback) {
        future.whenComplete((value, err) -> Bukkit.getScheduler().runTask(plugin, () -> callback.accept(value, err)));
    }

    private static String root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }
}
