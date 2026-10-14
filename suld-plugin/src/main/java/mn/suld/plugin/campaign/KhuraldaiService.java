package mn.suld.plugin.campaign;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.campaign.CampaignDefinition;
import mn.suld.api.campaign.CampaignRotation;
import mn.suld.api.campaign.WeeklyCycle;
import mn.suld.api.campaign.vote.VoteCycle;
import mn.suld.api.campaign.vote.VoteRules;
import mn.suld.api.campaign.vote.VoteTally;
import mn.suld.api.config.CampaignSettings;
import mn.suld.api.persistence.CampaignRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.Content;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Их Хуралдай (docs/KHURALDAI.md): once a week the server votes on next week's campaign. The phases come from the
 * clock ({@link WeeklyCycle}); this class opens the ballot, takes one vote per player, closes it with the tally and
 * tells everyone. The vote held during cycle X elects the campaign of cycle X+1 and is stored under X.
 */
public final class KhuraldaiService {

    private final Plugin plugin;
    private final SuldServices services;
    private final CampaignRepository repository;
    private final CampaignSettings settings;
    private final WeeklyCycle cycle;
    private final mn.suld.plugin.chronicle.ChronicleService chronicle;

    /** The vote row of the current cycle (opened or closed), refreshed from the database; null before it opens. */
    private volatile VoteCycle current;
    private volatile Map<String, Integer> tally = Map.of();
    private long lastTallyRefresh;
    private boolean opening, closing;

    public KhuraldaiService(Plugin plugin, SuldServices services, CampaignRepository repository, CampaignSettings settings, WeeklyCycle cycle,
                            mn.suld.plugin.chronicle.ChronicleService chronicle) {
        this.plugin = plugin;
        this.services = services;
        this.repository = repository;
        this.settings = settings;
        this.cycle = cycle;
        this.chronicle = chronicle;
    }

    public void start() {
        String id = cycle.cycleId(Instant.now());
        // this week's ballot, else last week's when it was never closed (the server was down over vote-closes):
        // the tick then closes it, so its votes still decide this week's campaign
        CompletableFuture<Optional<VoteCycle>> rows = repository.voteCycle(id).thenCompose(row -> row.isPresent()
                ? CompletableFuture.completedFuture(row)
                : repository.voteCycle(cycle.previousCycleId(id)).thenApply(prev -> prev.filter(v -> !v.closed())));
        whenDone(rows, (row, err) -> {
            if (err != null) plugin.getLogger().log(Level.WARNING, "[khuraldai] vote cycle not read: " + root(err));
            else {
                current = row.orElse(null);
                refreshTally();
            }
            Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        });
    }

    public WeeklyCycle cycle() {
        return cycle;
    }

    public CampaignSettings settings() {
        return settings;
    }

    /** The current cycle's vote (null before the ballot opens). */
    public VoteCycle current() {
        return current;
    }

    public Map<String, Integer> tally() {
        return tally;
    }

    public boolean open() {
        VoteCycle c = current;
        return c != null && !c.closed() && Instant.now().toEpochMilli() < c.closesAt();
    }

    /** The campaign definitions on the current ballot, in ballot order. */
    public List<CampaignDefinition> ballot() {
        VoteCycle c = current;
        List<CampaignDefinition> out = new ArrayList<>();
        if (c == null) return out;
        for (String id : c.options()) {
            CampaignDefinition d = Content.pack().campaign(id);
            if (d != null) out.add(d);
        }
        return out;
    }

    // ------------------------------------------------------------------- tick

    private void tick() {
        Instant now = Instant.now();
        String id = cycle.cycleId(now);
        VoteCycle c = current;
        // the row carries its own closing time: the clock's ballot closes at vote-closes, an admin's late ballot a day later
        if (c != null && !c.closed() && !closing && now.toEpochMilli() >= c.closesAt()) close(c, "closes at " + Instant.ofEpochMilli(c.closesAt()));
        if (!settings.khuraldaiEnabled()) return;
        WeeklyCycle.Phase phase = cycle.phase(now);
        if (phase == WeeklyCycle.Phase.VOTING && (c == null || !c.cycleId().equals(id)) && !opening) openBallot(id, false, "clock");
        if (System.currentTimeMillis() - lastTallyRefresh > 30_000 && open()) refreshTally();
    }

    private void refreshTally() {
        VoteCycle c = current;
        if (c == null) return;
        lastTallyRefresh = System.currentTimeMillis();
        repository.tally(c.cycleId()).whenComplete((t, err) -> {
            if (err == null) tally = Map.copyOf(t);
        });
    }

    // ------------------------------------------------------------------- open

    /**
     * Put next week's ballot up for the cycle {@code id}. The clock opens at vote-opens and closes at vote-closes; an
     * admin opening outside the window gets a ballot that stays up for one window's length, and {@code reopen} lets an
     * admin replace a closed ballot (its votes are discarded; the chronicle keeps the earlier result). Main thread.
     */
    public void openBallot(String id, boolean reopen, String actor) {
        if (opening) return;
        opening = true;
        List<CampaignDefinition> all = Content.pack().campaigns();
        if (all.size() < 2) {
            plugin.getLogger().warning("[khuraldai] fewer than 2 campaigns in content/campaigns.json: no vote");
            opening = false;
            return;
        }
        whenDone(repository.recentVotes(8), (recent, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[khuraldai] recent votes not read: " + root(err));
                opening = false;
                return;
            }
            List<String> winners = new ArrayList<>();
            List<String> lastLosers = new ArrayList<>();
            for (VoteCycle v : recent) {
                if (!v.closed() || v.winner() == null || v.cycleId().compareTo(id) >= 0) continue;
                winners.add(v.winner());
                if (lastLosers.isEmpty()) lastLosers.addAll(v.losers());
            }
            whenDone(repository.succeededCampaigns(), (succeeded, err2) -> {
            if (err2 != null) plugin.getLogger().log(Level.WARNING, "[khuraldai] arc unlocks not read, every campaign stands: " + root(err2));
            List<String> options = CampaignRotation.pick(all, winners, lastLosers, settings.options(), cycle.nextCycleId(id),
                            err2 == null ? succeeded : null)
                    .stream().map(CampaignDefinition::id).toList();
            long now = System.currentTimeMillis();
            long opensAt = Math.max(cycle.voteOpens(id).toEpochMilli(), Math.min(now, cycle.voteCloses(id).toEpochMilli()));
            long window = cycle.voteCloses(id).toEpochMilli() - cycle.voteOpens(id).toEpochMilli();
            long closesAt = now < cycle.voteCloses(id).toEpochMilli() ? cycle.voteCloses(id).toEpochMilli() : now + window;
            VoteCycle existing = current != null && current.cycleId().equals(id) ? current : null;
            CompletableFuture<VoteCycle> opened;
            if (reopen && existing != null) {
                long expected = existing.version();
                opened = repository.reopenVote(id, options, now, closesAt, expected)
                        .thenCompose(ok -> repository.voteCycle(id).thenApply(v -> v.orElse(null)));
            } else {
                opened = repository.openVote(id, options, opensAt, closesAt);
            }
            whenDone(opened, (row, e2) -> {
                opening = false;
                if (e2 != null || row == null) {
                    plugin.getLogger().log(Level.WARNING, "[khuraldai] vote not opened: " + (e2 == null ? "no row" : root(e2)));
                    return;
                }
                boolean fresh = existing == null || existing.closed() && !row.closed() || existing.version() != row.version();
                current = row;
                tally = Map.of();
                if (fresh && !row.closed()) announceOpen(row);
                plugin.getLogger().info("[khuraldai] vote " + row.cycleId() + (row.closed() ? " already closed: " : " open (" + actor + "): ") + row.options()
                        + " until " + Instant.ofEpochMilli(row.closesAt()));
            });
            });
        });
    }

    private void announceOpen(VoteCycle row) {
        List<CampaignDefinition> ballot = ballot();
        Bukkit.broadcast(Messages.accent("✦ Их Хуралдай нээгдлээ! Дараагийн долоо хоногийн аяныг сонго: /khuraldai"));
        for (int i = 0; i < ballot.size(); i++) {
            Bukkit.broadcast(Component.text("  " + (i + 1) + ". " + ballot.get(i).name() + " — " + ballot.get(i).description(), NamedTextColor.WHITE));
        }
        long hours = Duration.between(Instant.now(), Instant.ofEpochMilli(row.closesAt())).toHours();
        Bukkit.broadcast(Component.text("  Санал хураалт " + Math.max(1, hours) + " цагийн дараа хаагдана. /khuraldai vote <1-" + ballot.size() + ">", NamedTextColor.GRAY));
        for (Player p : Bukkit.getOnlinePlayers()) {
            Presentation.banner(p, "ИХ ХУРАЛДАЙ", "Дараагийн аянаа сонго: /khuraldai", NamedTextColor.GOLD);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
        }
    }

    // ------------------------------------------------------------------- vote

    /** {@code player} votes for ballot option {@code index} (1-based) or id. Main thread. */
    public void vote(Player player, String choice) {
        VoteCycle c = current;
        if (!open() || c == null) {
            player.sendMessage(Messages.error("Их Хуралдай одоогоор хуралдаагүй байна. Санал хураалт: " + settings.voteOpens() + " – " + settings.voteCloses() + "."));
            return;
        }
        String option = null;
        try {
            int i = Integer.parseInt(choice.trim());
            if (i >= 1 && i <= c.options().size()) option = c.options().get(i - 1);
        } catch (NumberFormatException notNumber) {
            for (String o : c.options()) if (o.equalsIgnoreCase(choice.trim()) || o.equalsIgnoreCase("campaign." + choice.trim())) option = o;
        }
        if (option == null) {
            player.sendMessage(Messages.error("Ийм сонголт алга. /khuraldai vote <1-" + c.options().size() + ">"));
            return;
        }
        PlayerProfile pr = services.profiles().cached(player.getUniqueId()).orElse(null);
        if (pr == null) return;
        long ageDays = Duration.between(pr.createdAt(), Instant.now()).toDays();
        Optional<VoteRules.Denial> denial = VoteRules.eligible(pr.hasSelectedClass(), pr.progression().level(), settings.minLevel(), ageDays, settings.minAccountDays());
        if (denial.isPresent()) {
            player.sendMessage(Messages.error(VoteRules.reason(denial.get(), settings.minLevel(), settings.minAccountDays())));
            return;
        }
        String chosen = option;
        CampaignDefinition def = Content.pack().campaign(chosen);
        whenDone(repository.castVote(c.cycleId(), player.getUniqueId(), chosen, pr.progression().level(), System.currentTimeMillis()), (r, err) -> {
            if (err != null) {
                player.sendMessage(Messages.error("Санал бүртгэгдсэнгүй (" + root(err) + ")."));
                return;
            }
            switch (r) {
                case CAST -> {
                    // the board and the screen show the vote at once; the 30 s refresh from the database corrects drift
                    Map<String, Integer> t = new java.util.HashMap<>(tally);
                    t.merge(chosen, 1, Integer::sum);
                    tally = Map.copyOf(t);
                    player.sendMessage(Messages.success("Санал бүртгэгдлээ: «" + (def == null ? chosen : def.name()) + "». Баярлалаа!"));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
                    services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.VOTE_CAST, player.getUniqueId(),
                            Map.of("cycle", c.cycleId(), "option", chosen, "level", pr.progression().level())));
                    services.achievements.record(player, mn.suld.api.achievement.Deed.VOTE, "", 1);
                    refreshTally();
                }
                case ALREADY_VOTED -> player.sendMessage(Messages.info("Та энэ долоо хоногт аль хэдийн санал өгсөн."));
                case CLOSED -> player.sendMessage(Messages.error("Санал хураалт хаагдсан."));
                default -> player.sendMessage(Messages.error("Санал бүртгэгдсэнгүй."));
            }
        });
    }

    // ------------------------------------------------------------------ close

    /** Count and close the vote; the winner becomes next cycle's campaign. Main thread. */
    public void close(VoteCycle c, String reason) {
        if (closing || c.closed()) return;
        closing = true;
        whenDone(repository.tally(c.cycleId()), (t, err) -> {
            if (err != null) {
                closing = false;
                plugin.getLogger().log(Level.WARNING, "[khuraldai] tally failed: " + root(err));
                return;
            }
            whenDone(repository.recentVotes(8), (recent, e2) -> {
                Map<String, Integer> recentWins = new HashMap<>();
                if (e2 == null) for (VoteCycle v : recent) if (v.winner() != null && !v.cycleId().equals(c.cycleId())) recentWins.merge(v.winner(), 1, Integer::sum);
                Optional<String> w = VoteTally.winner(c.options(), t, settings.minVoters(), recentWins);
                boolean quorum = w.isPresent();
                String winner = w.orElse(c.options().isEmpty() ? null : c.options().get(0));
                if (winner == null) {
                    closing = false;
                    return;
                }
                int voters = VoteTally.voters(c.options(), t);
                whenDone(repository.closeVote(c.cycleId(), winner, t, System.currentTimeMillis(), c.version()), (ok, e3) -> {
                    closing = false;
                    if (e3 != null) {
                        plugin.getLogger().log(Level.WARNING, "[khuraldai] close failed: " + root(e3));
                        return;
                    }
                    whenDone(repository.voteCycle(c.cycleId()), (row, e4) -> {
                        if (e4 == null && row.isPresent()) current = row.get();
                    });
                    if (!ok) {
                        plugin.getLogger().info("[khuraldai] vote " + c.cycleId() + " was closed elsewhere (" + reason + ")");
                        return;
                    }
                    CampaignDefinition def = Content.pack().campaign(winner);
                    String name = def == null ? winner : def.name();
                    plugin.getLogger().info("[khuraldai] vote " + c.cycleId() + " closed: " + winner + " (" + voters + " voters, quorum " + quorum + ")");
                    Bukkit.broadcast(Messages.accent(quorum
                            ? "✦ Их Хуралдай шийдвэрлэв: дараагийн долоо хоногийн аян — «" + name + "» (" + voters + " санал)."
                            : "✦ Их Хуралдай: санал хүрэлцсэнгүй (" + voters + "). Эргэлтээр «" + name + "» сонгогдов."));
                    for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
                    if (def != null) chronicle.khuraldaiClosed(c.cycleId(), def, voters, quorum);
                    services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.VOTE_CLOSED, null,
                            Map.of("cycle", c.cycleId(), "winner", winner, "voters", voters, "quorum", quorum)));
                });
            });
        });
    }

    /** The campaign the previous cycle's vote chose for {@code cycleId}, if that vote closed. */
    public CompletableFuture<Optional<String>> winnerFor(String cycleId) {
        return repository.voteCycle(cycle.previousCycleId(cycleId)).thenApply(v -> {
            // a ballot still waiting to be closed (the server was down at vote-closes) is not "no vote": the tick
            // closes it within a second, and the players' choice must win over the rotation
            if (v.isPresent() && !v.get().closed()) throw new IllegalStateException("vote " + v.get().cycleId() + " not closed yet");
            return v.map(VoteCycle::winner);
        });
    }

    // ------------------------------------------------------------------ admin

    /** Admin: open the ballot now; a closed ballot of this cycle is replaced (its votes discarded). */
    public void adminOpen(String actor) {
        String id = cycle.cycleId(Instant.now());
        VoteCycle c = current;
        boolean reopen = c != null && c.cycleId().equals(id) && c.closed();
        services.audit().record(AuditEvent.of(actor, AuditActions.CAMPAIGN_ADMIN_OPEN, id, reopen ? "reopen" : ""));
        openBallot(id, reopen, actor);
    }

    public boolean adminClose(String actor) {
        VoteCycle c = current;
        if (c == null || c.closed()) return false;
        services.audit().record(AuditEvent.of(actor, AuditActions.CAMPAIGN_ADMIN_CLOSE, c.cycleId(), ""));
        close(c, "admin " + actor);
        return true;
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

    /** For menus: how many votes an option has (cached). */
    public int votesFor(String option) {
        return tally.getOrDefault(option, 0);
    }
}
