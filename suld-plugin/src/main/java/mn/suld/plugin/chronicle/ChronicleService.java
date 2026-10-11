package mn.suld.plugin.chronicle;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.campaign.CampaignDefinition;
import mn.suld.api.campaign.CampaignOutcome;
import mn.suld.api.chronicle.ChronicleEntry;
import mn.suld.api.chronicle.ChronicleKind;
import mn.suld.api.chronicle.ChronicleRules;
import mn.suld.api.config.ChronicleSettings;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.persistence.ChronicleRepository;
import mn.suld.api.world.site.HistoricSite;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.Content;
import mn.suld.plugin.content.SuldContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Баатрын Хроник (docs/KHURALDAI.md §Хроник): the server's memory of who did what first, fastest, and together. Every
 * entry point is called on the main thread by the service that saw the deed; the write is asynchronous and the
 * announcement comes back on the main thread. A listing is a cached snapshot refreshed on every new entry, so menus
 * never wait on the database.
 */
public final class ChronicleService {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final int CACHE = 60;

    private final Plugin plugin;
    private final SuldServices services;
    private final ChronicleRepository repository;
    private final ChronicleSettings settings;
    private final Supplier<String> cycleId;
    private volatile List<ChronicleEntry> latest = List.of();

    public ChronicleService(Plugin plugin, SuldServices services, ChronicleRepository repository, ChronicleSettings settings, Supplier<String> cycleId) {
        this.plugin = plugin;
        this.services = services;
        this.repository = repository;
        this.settings = settings;
        this.cycleId = cycleId;
    }

    public void start() {
        refresh();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 20L * 60, 20L * 60);
    }

    public ChronicleRepository repository() {
        return repository;
    }

    /** The newest entries (a main-thread-safe snapshot). */
    public List<ChronicleEntry> latest() {
        return latest;
    }

    public CompletableFuture<List<ChronicleEntry>> byKind(ChronicleKind kind, int limit) {
        return repository.byKind(kind, limit);
    }

    public CompletableFuture<List<ChronicleEntry>> byPlayer(UUID player, int limit) {
        return repository.byPlayer(player, limit);
    }

    public CompletableFuture<Boolean> hide(long id, String actor) {
        services.audit().record(AuditEvent.of(actor, AuditActions.CHRONICLE_ADMIN_HIDE, String.valueOf(id), ""));
        return repository.hide(id).whenComplete((ok, err) -> {
            if (ok != null && ok) refresh();
        });
    }

    private void refresh() {
        repository.latest(CACHE).whenComplete((list, err) -> {
            if (err != null) plugin.getLogger().log(Level.WARNING, "[chronicle] listing failed: " + root(err));
            else latest = List.copyOf(list);
        });
    }

    // ---------------------------------------------------------------- entries

    /** A dungeon was cleared: the first clear of it is claimed, and a faster time than the record replaces it. */
    public void dungeonCleared(DungeonDefinition def, List<Player> rewarded, long seconds) {
        if (!settings.enabled() || rewarded.isEmpty()) return;
        Player hero = rewarded.get(0);
        List<String> party = new ArrayList<>();
        for (Player p : rewarded) party.add(p.getName());
        String clan = services.clans().tagOf(hero.getUniqueId()).orElse("");
        long now = System.currentTimeMillis();
        ChronicleEntry first = ChronicleEntry.of(ChronicleKind.FIRST_DUNGEON_CLEAR, def.id(), hero.getUniqueId(), hero.getName(), party, clan,
                seconds, def.displayName(), cycleId.get(), now);
        whenDone(repository.claimFirst(first), (e, err) -> written("first clear", e, err));
        ChronicleEntry record = ChronicleEntry.of(ChronicleKind.FASTEST_DUNGEON_CLEAR, def.id(), hero.getUniqueId(), hero.getName(), party, clan,
                seconds, def.displayName(), cycleId.get(), now);
        whenDone(repository.recordIfBetter(record), (e, err) -> written("fastest clear", e, err));
    }

    /** A historic site was stood on for the first time by this player; the first of anyone is claimed. */
    public void siteDiscovered(HistoricSite site, Player p) {
        if (!settings.enabled()) return;
        ChronicleEntry e = ChronicleEntry.of(ChronicleKind.FIRST_SITE_DISCOVERY, site.id(), p.getUniqueId(), p.getName(), List.of(p.getName()),
                services.clans().tagOf(p.getUniqueId()).orElse(""), null, site.name(), cycleId.get(), System.currentTimeMillis());
        whenDone(repository.claimFirst(e), (w, err) -> written("first site", w, err));
    }

    /** A relic was claimed at its shrine (the relic history keeps the full ledger; this is the player-facing line). */
    public void relicClaimed(String key, String displayName, Player p) {
        if (!settings.enabled()) return;
        ChronicleEntry e = ChronicleEntry.of(ChronicleKind.RELIC_CLAIMED, key, p.getUniqueId(), p.getName(), List.of(p.getName()),
                services.clans().tagOf(p.getUniqueId()).orElse(""), null, displayName, cycleId.get(), System.currentTimeMillis());
        whenDone(repository.append(e), (w, err) -> written("relic", Optional.ofNullable(w), err));
    }

    /** The week's campaign resolved: the outcome and its top three go into the book, whether it succeeded or not. */
    public void campaignResolved(CampaignOutcome o) {
        if (!settings.enabled()) return;
        List<String> top = new ArrayList<>();
        for (int i = 0; i < Math.min(3, o.top().size()); i++) top.add(o.top().get(i).name());
        UUID hero = o.top().isEmpty() ? null : o.top().get(0).player();
        String detail = (o.success() ? "«" + o.campaign().name() + "» амжилттай" : "«" + o.campaign().name() + "» зорилгод хүрсэнгүй")
                + " — " + o.total() + "/" + o.target() + " " + o.campaign().goal().unitWord() + ", " + o.participants() + " оролцогч"
                + (top.isEmpty() ? "" : ", тэргүүн: " + String.join(", ", top))
                + (o.topClan() == null ? "" : ", шилдэг овог [" + o.topClan() + "]");
        ChronicleEntry e = ChronicleEntry.of(ChronicleKind.CAMPAIGN_RESULT, o.cycleId(), hero, top.isEmpty() ? "" : top.get(0), top,
                o.topClan() == null ? "" : o.topClan(), o.total(), detail, o.cycleId(), System.currentTimeMillis());
        whenDone(repository.append(e), (w, err) -> written("campaign", Optional.ofNullable(w), err));
    }

    /** The Хуралдай closed: what was chosen, by how many. */
    public void khuraldaiClosed(String cycleId, CampaignDefinition winner, int voters, boolean quorum) {
        if (!settings.enabled()) return;
        String detail = quorum
                ? "«" + winner.name() + "» " + voters + " саналаар дараагийн долоо хоногийн аян боллоо"
                : "санал хүрэлцсэнгүй (" + voters + "); эргэлтээр «" + winner.name() + "» сонгогдов";
        ChronicleEntry e = ChronicleEntry.of(ChronicleKind.KHURALDAI_RESULT, cycleId, null, "", List.of(), "", (long) voters, detail, cycleId,
                System.currentTimeMillis());
        whenDone(repository.append(e), (w, err) -> written("khuraldai", Optional.ofNullable(w), err));
    }

    // ---------------------------------------------------------------- display

    /** The display name of an entry's subject (dungeon, site, relic, campaign cycle). */
    public String subjectName(ChronicleEntry e) {
        switch (e.kind()) {
            case FIRST_DUNGEON_CLEAR, FASTEST_DUNGEON_CLEAR -> {
                DungeonDefinition d = SuldContent.dungeonFor(e.subjectKey());
                return d == null ? e.subjectKey() : d.displayName();
            }
            case FIRST_SITE_DISCOVERY -> {
                for (HistoricSite s : Content.pack().historicSites()) if (s.id().equals(e.subjectKey())) return s.name();
                return e.subjectKey();
            }
            case RELIC_CLAIMED -> {
                var r = SuldContent.relicFor(e.subjectKey());
                return r == null ? e.subjectKey() : r.displayName();
            }
            default -> {
                return e.detail().isBlank() ? e.subjectKey() : e.detail();
            }
        }
    }

    public String headline(ChronicleEntry e) {
        return ChronicleRules.headline(e, subjectName(e));
    }

    private void written(String what, Optional<ChronicleEntry> entry, Throwable err) {
        if (err != null) {
            plugin.getLogger().log(Level.WARNING, "[chronicle] " + what + " not written: " + root(err));
            return;
        }
        if (entry == null || entry.isEmpty()) return;
        ChronicleEntry e = entry.get();
        refresh();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("kind", e.kind().name());
        attrs.put("subject", e.subjectKey());
        if (e.value() != null) attrs.put("value", e.value());
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.CHRONICLE_ENTRY, e.player(), attrs));
        plugin.getLogger().info("[chronicle] " + headline(e));
        if (!settings.broadcast()) return;
        Component line = Component.text(headline(e), GOLD);
        Bukkit.broadcast(line);
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.5f, 1.3f);
    }

    /** Complete on the main thread, always. */
    private <T> void whenDone(CompletableFuture<T> future, java.util.function.BiConsumer<T, Throwable> callback) {
        future.whenComplete((value, err) -> Bukkit.getScheduler().runTask(plugin, () -> callback.accept(value, err)));
    }

    private static String root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    /** The colour of a kind in menus. */
    public static NamedTextColor colour(ChronicleKind k) {
        return switch (k) {
            case FIRST_DUNGEON_CLEAR -> NamedTextColor.GOLD;
            case FASTEST_DUNGEON_CLEAR -> NamedTextColor.AQUA;
            case RELIC_CLAIMED -> NamedTextColor.LIGHT_PURPLE;
            case FIRST_SITE_DISCOVERY -> NamedTextColor.GREEN;
            case CAMPAIGN_RESULT -> NamedTextColor.YELLOW;
            case KHURALDAI_RESULT -> NamedTextColor.WHITE;
        };
    }
}
