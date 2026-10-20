package mn.suld.plugin.quest;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.persistence.SideQuestRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.quest.QuestType;
import mn.suld.api.quest.SideQuestBook;
import mn.suld.api.quest.SideQuestDefinition;
import mn.suld.api.quest.SideQuestRecord;
import mn.suld.api.region.Navigation;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.auth.SuldJoinEvent;
import mn.suld.plugin.content.QuestContent;
import mn.suld.plugin.item.PlayerDataSaves;
import mn.suld.plugin.npc.NpcDialogue;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Side quests in the game (docs/SIDE_QUESTS.md): a giver NPC offers them in its conversation, {@link QuestService}'s
 * objective events advance them ({@link QuestService.SideHooks}), and the same NPC takes them back and pays.
 *
 * <ul>
 *   <li>The records live in V32 ({@link SideQuestRepository}); this service keeps the online players' copy, main
 *   thread only. Progress is written on the side-quest executor every 30 s and at quit; a take and a hand-in are
 *   compare-and-set rows, so no click, relog or lag pays twice.</li>
 *   <li>A hand-in follows PlayerDataSaves: a COLLECT quest's items leave the bag and the player file is written first,
 *   then the row turns DONE, then the coins, EXP and loot are paid and saved. A refused DONE gives the items back.</li>
 *   <li>The events of the ~100 ms between a join and the records' arrival are not counted (documented).</li>
 * </ul>
 */
public final class SideQuestService implements Listener, QuestService.SideHooks {

    /** How close to the giver a player must stand to take or hand in a side quest. */
    public static final double GIVER_REACH = 8.0;
    private static final long FLUSH_TICKS = 20L * 30;
    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");

    private final Plugin plugin;
    private final SuldServices services;
    private final SideQuestRepository repo;
    private final SideQuestBook book = QuestContent.SIDE;
    /**
     * The online players' records by quest id (main thread); a player who left stays here only until the quit's
     * progress write is confirmed, so a failed write is retried from the record itself.
     */
    private final Map<UUID, Map<String, SideQuestRecord>> mine = new ConcurrentHashMap<>();
    /** Quest ids whose progress moved since the last write. */
    private final Map<UUID, Set<String>> dirty = new ConcurrentHashMap<>();
    /** Progress writes sent and not yet answered, by player: a record is never forgotten under one. */
    private final Map<UUID, Integer> writing = new ConcurrentHashMap<>();
    /** A take, hand-in or abandon waiting for the database ("uuid|quest"): a second click meanwhile is refused. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public SideQuestService(Plugin plugin, SuldServices services, SideQuestRepository repo) {
        this.plugin = plugin;
        this.services = services;
        this.repo = repo;
    }

    public SideQuestBook book() {
        return book;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::flushAll, FLUSH_TICKS, FLUSH_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBars, 40L, 10L);
        for (Player p : Bukkit.getOnlinePlayers()) load(p); // a reload with players online
    }

    // ------------------------------------------------------------------ joining and leaving

    @EventHandler
    public void onJoin(SuldJoinEvent e) {
        load(e.getPlayer());
    }

    /**
     * Reads the player's rows (a join, or the truth after a refused write). The moved progress is written first (the
     * one writer runs it before this read), and progress counted meanwhile — or left by a quit whose write failed — is
     * kept, never replaced by an older count. A failed read is tried again a few times while the session lasts.
     * A reward still owed (the player left before it was paid) is paid now.
     */
    private void load(Player p) {
        load(p, 0);
    }

    private void load(Player p, int attempt) {
        UUID id = p.getUniqueId();
        flush(id);
        whenDone(call(() -> repo.load(id)), (rows, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[squest] loading the side quests of " + p.getName() + " failed (try " + (attempt + 1) + "): " + root(err));
                if (attempt < 4 && plugin.isEnabled()) { // a passing database fault: try again, while it is the same session
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (same(p) != null) load(p, attempt + 1);
                    }, 20L * 5 * (attempt + 1));
                }
                return;
            }
            if (same(p) == null) return; // gone, or relogged: the new session's own load answers for it
            Map<String, SideQuestRecord> before = mine.get(id);
            Map<String, SideQuestRecord> now = new LinkedHashMap<>(rows);
            if (before != null) {
                for (SideQuestRecord r : before.values()) {
                    SideQuestRecord db = now.get(r.questId());
                    // the same take only (an abandoned and re-taken quest starts again at its row's count)
                    if (db == null || !db.active() || !r.active() || db.startedAt() != r.startedAt() || r.progress() <= db.progress()) continue;
                    now.put(r.questId(), db.withProgress(r.progress()));
                    dirty.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(r.questId());
                }
            }
            mine.put(id, now);
            services.profiles().cached(id).ifPresent(pr -> recount(p, pr));
            for (SideQuestRecord r : now.values()) {
                if (r.owed()) book.byId(r.questId()).ifPresent(d -> claim(p, d));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        flush(id);
        // the next tick: during PlayerQuitEvent Paper still lists the player, so forgetting now would keep the record
        // forever; a write still in flight forgets it from its own answer
        PlayerDataSaves.onMain(plugin, () -> forgetIfGone(id));
        BossBar bar = bars.remove(id);
        if (bar != null) mn.suld.plugin.ui.BossBars.hide(e.getPlayer(), bar);
    }

    /** Plugin disable: the progress still in memory, written against the shutdown budget. */
    public CompletableFuture<Void> shutdown() {
        for (Map.Entry<UUID, BossBar> b : bars.entrySet()) {
            Player p = Bukkit.getPlayer(b.getKey());
            if (p != null) mn.suld.plugin.ui.BossBars.hide(p, b.getValue());
        }
        bars.clear();
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (UUID id : new ArrayList<>(dirty.keySet())) writes.addAll(flush(id));
        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]));
    }

    private void flushAll() {
        for (UUID id : new ArrayList<>(dirty.keySet())) flush(id);
    }

    /**
     * Writes the moved progress on the one writer, in order, from the records themselves. A failed write marks its
     * quest dirty again (the next flush writes the record's count then, never an older one); once a player who left
     * has nothing unwritten, the record is forgotten.
     */
    private List<CompletableFuture<Void>> flush(UUID id) {
        Set<String> ids = dirty.remove(id);
        Map<String, SideQuestRecord> m = mine.get(id);
        List<CompletableFuture<Void>> out = new ArrayList<>();
        if (ids == null || m == null) return out;
        if (Bukkit.getPlayer(id) == null) PlayerDataSaves.onMain(plugin, () -> forgetIfGone(id)); // also when nothing is written
        for (String q : ids) {
            SideQuestRecord r = m.get(q);
            if (r == null || !r.active()) continue;
            writing.merge(id, 1, Integer::sum); // atomic, like the decrement below
            out.add(call(() -> repo.progress(id, q, r.progress())).handle((ok, err) -> {
                if (err != null) {
                    plugin.getLogger().warning("[squest] saving the progress of " + q + " failed (retried at the next flush): " + root(err));
                    // atomic against the main thread's flush taking the set away: the id lands in the set that is mapped
                    dirty.compute(id, (k, set) -> {
                        Set<String> s2 = set == null ? ConcurrentHashMap.newKeySet() : set;
                        s2.add(q);
                        return s2;
                    });
                }
                writing.computeIfPresent(id, (k, n) -> n <= 1 ? null : n - 1);
                PlayerDataSaves.onMain(plugin, () -> forgetIfGone(id));
                return null;
            }));
        }
        return out;
    }

    /** A player who left, with no progress left to write: the record goes (per-player state is cleared on quit). */
    private void forgetIfGone(UUID id) {
        if (Bukkit.getPlayer(id) != null || writing.containsKey(id)) return; // a write's own answer comes back here
        Set<String> d = dirty.get(id);
        if (d != null && !d.isEmpty()) return;
        dirty.remove(id);
        mine.remove(id);
    }

    // ------------------------------------------------------------------ QuestService.SideHooks

    @Override
    public boolean objective(Player p, PlayerProfile pr, QuestType type, String target, long value) {
        Map<String, SideQuestRecord> m = mine.get(p.getUniqueId());
        if (m == null || target == null || pr.ephemeral()) return false; // the training yard's stand-in earns nothing
        boolean moved = false;
        for (SideQuestRecord r : new ArrayList<>(m.values())) {
            if (!r.active()) continue;
            SideQuestDefinition d = book.byId(r.questId()).orElse(null);
            if (d == null || d.type() != type || !d.targetId().equals(target)) continue;
            moved |= advance(p, m, r, d, type, target, value);
        }
        return moved;
    }

    @Override
    public boolean wants(UUID player, QuestType type, String target) {
        Map<String, SideQuestRecord> m = mine.get(player);
        if (m == null) return false;
        for (SideQuestRecord r : m.values()) {
            if (!r.active()) continue;
            SideQuestDefinition d = book.byId(r.questId()).orElse(null);
            if (d == null || d.type() != type || target != null && !d.targetId().equals(target)) continue;
            if (type == QuestType.COLLECT_ITEM || !r.ready(d)) return true; // a met collect can still fall back
        }
        return false;
    }

    @Override
    public void recount(Player p, PlayerProfile pr) {
        Map<String, SideQuestRecord> m = mine.get(p.getUniqueId());
        if (m == null || pr.ephemeral()) return;
        for (SideQuestRecord r : new ArrayList<>(m.values())) {
            if (!r.active()) continue;
            SideQuestDefinition d = book.byId(r.questId()).orElse(null);
            if (d == null || d.type() != QuestType.COLLECT_ITEM) continue;
            advance(p, m, r, d, QuestType.COLLECT_ITEM, d.targetId(), services.quests().count(p, d.targetId()));
        }
    }

    private boolean advance(Player p, Map<String, SideQuestRecord> m, SideQuestRecord r, SideQuestDefinition d, QuestType type, String target, long value) {
        SideQuestRecord after = SideQuestBook.advance(r, d, type, target, value);
        if (after == r || after.progress() == r.progress()) return false;
        m.put(d.id(), after);
        dirty.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(d.id());
        if (after.ready(d) && !r.ready(d)) {
            p.sendMessage(Component.text("✔ Нэмэлт эрэл бэлэн: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text(d.title(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" — " + giverName(d) + "-д тушаа.", NamedTextColor.GRAY)));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        } else if (after.progress() > r.progress()) {
            p.sendMessage(Component.text("✦ " + d.title() + " ", NamedTextColor.WHITE, TextDecoration.BOLD)
                    .append(Component.text(after.progress() + "/" + d.requiredCount(), GOLD, TextDecoration.BOLD)));
        }
        return true;
    }

    // ------------------------------------------------------------------ what a player can do

    /** The player's records (a copy), or empty before they are loaded. */
    public Map<String, SideQuestRecord> records(UUID player) {
        Map<String, SideQuestRecord> m = mine.get(player);
        return m == null ? Map.of() : new LinkedHashMap<>(m);
    }

    public boolean loaded(UUID player) {
        return mine.containsKey(player);
    }

    /** Whether {@code p} may take {@code d} now. */
    public SideQuestBook.Gate gate(Player p, PlayerProfile pr, SideQuestDefinition d) {
        return book.gate(d, pr.progression().level(), pr.playerClass().orElse(null), chapterDone(pr), records(p.getUniqueId()));
    }

    /** Whether a story chapter is behind the player (the chain is linear: its index below the completed count). */
    private java.util.function.Predicate<String> chapterDone(PlayerProfile pr) {
        var chain = services.quests().chain();
        int done = chain.completedCount(pr.questState());
        return req -> {
            int i = chain.indexOf(req);
            return i >= 0 && i < done;
        };
    }

    public String refusal(Player p, PlayerProfile pr, SideQuestBook.Gate g, SideQuestDefinition d) {
        return book.refusal(g, d, id -> services.quests().definition(id).map(mn.suld.api.quest.QuestDefinition::title).orElse(id),
                chapterDone(pr), records(p.getUniqueId()));
    }

    /** The buttons a giver's conversation shows: a hand-in for each ready quest, a page for each one to take (at most 3). */
    public List<NpcDialogue.Extra> buttons(Player p, String npcId) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        List<SideQuestDefinition> offered = book.offeredBy(npcId);
        if (offered.isEmpty() || pr == null || !pr.hasSelectedClass() || pr.ephemeral() || !loaded(p.getUniqueId())) return List.of();
        recount(p, pr); // items that came without a pickup (refining, the mailbox) count before the buttons are chosen
        Map<String, SideQuestRecord> m = records(p.getUniqueId());
        java.util.function.Predicate<String> chapters = chapterDone(pr);
        List<NpcDialogue.Extra> out = new ArrayList<>();
        int offers = 0;
        for (SideQuestDefinition d : offered) {
            SideQuestRecord r = m.get(d.id());
            if (r != null && r.ready(d)) {
                out.add(new NpcDialogue.Extra(Component.text("✔ Тушаах: " + d.title(), NamedTextColor.GREEN, TextDecoration.BOLD), pl -> {
                    pl.closeDialog();
                    handIn(pl, d.id(), false);
                }));
            } else if (r == null && offers < 3
                    && book.gate(d, pr.progression().level(), pr.playerClass().orElse(null), chapters, m) == SideQuestBook.Gate.OPEN) {
                offers++;
                out.add(new NpcDialogue.Extra(Component.text("✦ Эрэл: " + d.title(), GOLD, TextDecoration.BOLD), pl -> page(pl, d)));
            }
        }
        return out;
    }

    /** A side quest's page in the giver's conversation: its story, its objective, its reward, «Авах» and «✕ Хаах». */
    private void page(Player p, SideQuestDefinition d) {
        var giver = mn.suld.plugin.content.SuldContent.npcFor(d.giverNpcId());
        if (giver == null || services.npcs == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(d.title(), GOLD, TextDecoration.BOLD));
        if (!d.story().isEmpty()) {
            lines.add(Component.text(d.story(), NamedTextColor.GRAY)
                    .append(Component.text("  — " + historyLabel(d.history()), NamedTextColor.DARK_GRAY)));
        }
        lines.add(Component.text("Зорилго: " + objectiveText(d), NamedTextColor.WHITE));
        lines.add(Component.text("Шагнал: " + d.expReward() + " EXP, " + d.currencyReward() + " зоос" + (d.lootTableId() != null ? ", бэлэг" : ""), GOLD));
        List<NpcDialogue.Extra> buttons = List.of(
                new NpcDialogue.Extra(Component.text("Авах", NamedTextColor.GREEN, TextDecoration.BOLD), pl -> {
                    pl.closeDialog();
                    accept(pl, d.id(), false);
                }),
                new NpcDialogue.Extra(Component.text("✕ Хаах"), pl -> pl.closeDialog()));
        services.npcs.dialogue().page(p, giver, lines, buttons);
    }

    /** The label a side quest's story carries (as an NPC's paragraph, NpcDialogue). */
    public static String historyLabel(String history) {
        return NpcDialogue.label(history);
    }

    /** What to do, in a short Mongolian line (the story book's wording, gui/QuestMenu). */
    public static String objectiveText(SideQuestDefinition d) {
        return mn.suld.plugin.gui.QuestMenu.objective(d.objective());
    }

    private static String giverName(SideQuestDefinition d) {
        var n = mn.suld.plugin.content.SuldContent.npcFor(d.giverNpcId());
        return n == null ? d.giverNpcId() : n.name();
    }

    /** Within reach of the giver (its entity, else its point), in the same world. */
    public boolean nearGiver(Player p, String npcId) {
        return nearGiver(p.getLocation(), npcId);
    }

    /** Whether {@code from} is within reach of the giver (the side-quest window asks for where the player stands). */
    public boolean nearGiver(Location from, String npcId) {
        Location at = services.npcs == null ? null : services.npcs.where(npcId);
        return at != null && at.getWorld() != null && at.getWorld().equals(from.getWorld())
                && at.distanceSquared(from) <= GIVER_REACH * GIVER_REACH;
    }

    // ------------------------------------------------------------------ take, hand in, abandon

    /** Takes {@code questId}; {@code staff} skips the giver's reach and the level, class and requirement gates. */
    public void accept(Player p, String questId, boolean staff) {
        SideQuestDefinition d = book.byId(questId).orElse(null);
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (d == null || pr == null) return;
        if (!loaded(p.getUniqueId())) {
            p.sendMessage(Messages.info("Нэмэлт эрлүүд ачаалж байна, дахин оролдоно уу."));
            return;
        }
        if (pr.ephemeral()) {
            p.sendMessage(Messages.error("Дасгалын талбайд эрэл авахгүй — дасгалаа дуусгаад ав."));
            return;
        }
        if (!staff) {
            if (!pr.hasSelectedClass() || services.isSoul.test(p.getUniqueId())) {
                p.sendMessage(Messages.error("Сүнс эсвэл ангигүй хүн эрэл авахгүй."));
                return;
            }
            if (!nearGiver(p, d.giverNpcId())) {
                p.sendMessage(Messages.error(giverName(d) + "-ийн хажууд очиж ав."));
                return;
            }
            SideQuestBook.Gate g = gate(p, pr, d);
            if (g != SideQuestBook.Gate.OPEN) {
                p.sendMessage(Messages.error(refusal(p, pr, g, d)));
                return;
            }
        } else {
            SideQuestRecord had = mine.get(p.getUniqueId()).get(questId);
            if (had != null) {
                p.sendMessage(Messages.error(refusal(p, pr, had.done() ? SideQuestBook.Gate.DONE : SideQuestBook.Gate.ACTIVE, d)));
                return;
            }
        }
        // one take at a time per player: a take counts only once the database answers, so two takes in flight together
        // would both pass the limit of three
        String key = p.getUniqueId() + "|take";
        if (!inFlight.add(key)) {
            p.sendMessage(Messages.info("Өмнөх эрлийг бүртгэж байна, түр хүлээнэ үү."));
            return;
        }
        long now = System.currentTimeMillis();
        whenDone(call(() -> repo.start(p.getUniqueId(), questId, now)), (ok, err) -> {
            inFlight.remove(key);
            if (err != null) {
                fail("taking " + questId, err, p);
                return;
            }
            if (same(p) == null) return; // gone, or relogged: the new session's load reads the row back
            if (!Boolean.TRUE.equals(ok)) { // a row this copy did not know: read the truth back
                p.sendMessage(Messages.info("Энэ эрэл аль хэдийн таны дэвтэрт бий."));
                load(p);
                return;
            }
            Map<String, SideQuestRecord> m = mine.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());
            m.put(questId, SideQuestRecord.started(questId, now));
            p.showTitle(Title.title(Component.text("✦ НЭМЭЛТ ЭРЭЛ", GOLD, TextDecoration.BOLD),
                    Component.text(d.title(), NamedTextColor.WHITE, TextDecoration.BOLD),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(500))));
            p.sendMessage(Messages.success("Нэмэлт эрэл авлаа: " + d.title() + " — " + objectiveText(d) + "."));
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.SIDE_QUEST_ACCEPT, p.getUniqueId(),
                    Map.of("quest", questId, "level", pr.progression().level())));
            // what is already true counts at once: the bag for a collect, the region underfoot for a discovery
            PlayerProfile now2 = services.profiles().cached(p.getUniqueId()).orElse(pr);
            if (d.type() == QuestType.COLLECT_ITEM) recount(p, now2);
            if (d.type() == QuestType.DISCOVER_LOCATION && d.targetId().equals(services.regionIdAt.apply(p))) {
                objective(p, now2, QuestType.DISCOVER_LOCATION, d.targetId(), 1);
            }
        });
    }

    /** Hands {@code questId} in; {@code staff} skips the giver's reach. */
    public void handIn(Player p, String questId, boolean staff) {
        SideQuestDefinition d = book.byId(questId).orElse(null);
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        Map<String, SideQuestRecord> m = mine.get(p.getUniqueId());
        if (d == null || pr == null || m == null) return;
        String key = p.getUniqueId() + "|" + questId;
        if (inFlight.contains(key)) return; // a second click while the first is answered (its items already left the bag)
        if (d.type() == QuestType.COLLECT_ITEM) recount(p, pr);
        SideQuestRecord r = m.get(questId);
        if (r == null || !r.ready(d)) {
            p.sendMessage(Messages.error("«" + d.title() + "» эрлийн зорилго биелээгүй байна."));
            return;
        }
        if (services.isSoul.test(p.getUniqueId()) || p.isDead()) {
            p.sendMessage(Messages.error("Сүнс эрэл тушаахгүй — эхлээд амилаарай."));
            return;
        }
        if (pr.ephemeral()) {
            p.sendMessage(Messages.error("Дасгалын талбайд эрэл тушаахгүй — дасгалаа дуусгаад тушаа."));
            return;
        }
        if (!staff && !nearGiver(p, d.giverNpcId())) {
            p.sendMessage(Messages.error(giverName(d) + "-д очиж тушаа."));
            return;
        }
        if (!inFlight.add(key)) return;
        // ITEMS first (PlayerDataSaves): a collect quest's items leave the bag and the player file is written before
        // the row turns DONE, so a crash can lose them but never both keep them and pay for them
        List<ItemStack> taken = new ArrayList<>();
        if (d.type() == QuestType.COLLECT_ITEM) {
            taken.addAll(services.quests().take(p, d.targetId(), d.requiredCount()));
            PlayerDataSaves.now(p);
        }
        UUID id = p.getUniqueId();
        long at = System.currentTimeMillis();
        whenDone(call(() -> repo.complete(id, questId, d.requiredCount(), at)), (ok, err) -> {
            if (Boolean.TRUE.equals(ok)) {
                inFlight.remove(key);
                handedIn(id, r, d);
                return;
            }
            if (err == null) { // handed in before (another click, another server): the items were never paid for
                inFlight.remove(key);
                giveBack(id, taken, questId);
                Player cur = same(p);
                if (cur != null) {
                    cur.sendMessage(Messages.info("Энэ эрэл аль хэдийн тушаагдсан эсвэл өөрчлөгдсөн байна — дэвтрээ дахин уншлаа."));
                    load(cur);
                }
                return;
            }
            fail("handing in " + questId, err, p);
            // the update may have committed before the error came back: read the row before the items go back, so they
            // are never both returned and paid for (the reward of a DONE row is owed, and the next claim pays it)
            whenDone(call(() -> repo.load(id)), (rows, err2) -> {
                inFlight.remove(key);
                SideQuestRecord row = rows == null ? null : rows.get(questId);
                if (err2 != null) {
                    String held = taken.stream().map(i -> i.getAmount() + "x " + i.getType()).reduce((a, b) -> a + ", " + b).orElse("-");
                    plugin.getLogger().severe("[squest] the hand-in of " + questId + " by " + p.getName() + " could not be confirmed (" + root(err2)
                            + "): its items are held, not returned — restore them by hand if the row is still ACTIVE: " + held);
                    services.audit().record(AuditEvent.of(id.toString(), AuditActions.SIDE_QUEST_COMPLETE, questId, "unconfirmed, items held: " + held));
                } else if (row != null && row.done() && row.completedAt() == at) { // this hand-in committed
                    handedIn(id, r, d);
                } else { // still ACTIVE, or handed in before (another server): these items were never paid for
                    giveBack(id, taken, questId);
                    if (row != null && row.done()) load(p);
                }
            });
        });
    }

    /** The row just turned DONE: the record follows, and the reward is claimed for the player's current session. */
    private void handedIn(UUID id, SideQuestRecord r, SideQuestDefinition d) {
        Map<String, SideQuestRecord> mm = mine.get(id);
        if (mm != null) mm.put(d.id(), r.handedIn(d.requiredCount(), System.currentTimeMillis()));
        Set<String> ds = dirty.get(id);
        if (ds != null) ds.remove(d.id());
        Player cur = Bukkit.getPlayer(id);
        if (cur != null) claim(cur, d); // offline: owed, the next load pays it
        else forgetIfGone(id);
    }

    /** A refused hand-in's items, back to the player's current session (bag, else the mailbox) or to the mailbox. */
    private void giveBack(UUID id, List<ItemStack> taken, String questId) {
        if (taken.isEmpty()) return;
        String why = "side quest " + questId + " not handed in";
        Player cur = Bukkit.getPlayer(id);
        PlayerProfile pr = cur == null ? null : services.profiles().cached(id).orElse(null);
        if (cur != null && pr != null && pr.ephemeral()) {
            // the yard's kit bag is thrown away: the mailbox keeps them on disk, delivered when training ends (or at the next join)
            services.mailbox(plugin).keep(id, taken, why);
            if (services.training != null) services.training.defer(id, "Нэмэлт эрлийн зүйлс", q -> services.mailbox(plugin).deliver(q));
        } else if (cur != null) {
            services.mailbox(plugin).giveOrKeep(cur, taken, why);
            PlayerDataSaves.now(cur);
            if (pr != null) recount(cur, pr); // back in the bag without a pickup: the collect count follows
        } else {
            services.mailbox(plugin).keep(id, taken, why);
        }
    }

    /** A repository call that answers with a failed future instead of throwing (the writer refuses work while the plugin stops). */
    private static <T> CompletableFuture<T> call(java.util.function.Supplier<CompletableFuture<T>> op) {
        try {
            return op.get();
        } catch (RuntimeException rejected) {
            return CompletableFuture.failedFuture(rejected);
        }
    }

    /** The player's session if it is still {@code p} (a relog makes a new Player object, and the old one is dead), else null. */
    private static Player same(Player p) {
        Player now = Bukkit.getPlayer(p.getUniqueId());
        return now == p ? now : null;
    }

    /**
     * Pays a handed-in quest whose reward is owed: the row's reward is claimed (once, whatever the clicks), then paid.
     * A player gone or in the training yard keeps it owed; the next load pays it. If the player left while the claim
     * was in flight, the claim is undone, so the reward is owed again rather than lost.
     */
    private void claim(Player p, SideQuestDefinition d) {
        claim(p, d, 0);
    }

    private void claim(Player p, SideQuestDefinition d, int attempt) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || !p.isOnline() || !plugin.isEnabled()) return; // stopping: owed, the next load pays it
        UUID id = p.getUniqueId();
        if (pr.ephemeral()) { // the yard's stand-in: paid to the real profile when training ends
            if (services.training != null) services.training.defer(id, "Нэмэлт эрлийн шагнал «" + d.title() + "»", q -> claim(q, d));
            return;
        }
        String key = id + "|pay|" + d.id();
        if (!inFlight.add(key)) return;
        long now = System.currentTimeMillis();
        whenDone(call(() -> repo.claimReward(id, d.id(), now)), (ok, err) -> {
            if (err == null) {
                inFlight.remove(key);
                claimed(p, d, now, Boolean.TRUE.equals(ok));
                return;
            }
            // the update may have committed before the error came back: the row tells whether this claim holds it
            whenDone(call(() -> repo.load(id)), (rows, err2) -> {
                inFlight.remove(key);
                SideQuestRecord row = rows == null ? null : rows.get(d.id());
                if (err2 != null) {
                    plugin.getLogger().severe("[squest] the reward claim of " + d.id() + " for " + p.getName() + " could not be confirmed (" + root(err)
                            + "; " + root(err2) + "): if the row shows paid_at " + now + ", pay it by hand");
                    services.audit().record(AuditEvent.of(id.toString(), AuditActions.SIDE_QUEST_COMPLETE, d.id(), "claim unconfirmed at " + now + ": " + rewardText(d)));
                } else if (row != null && row.done() && row.paidAt() == now) {
                    claimed(p, d, now, true);
                } else {
                    plugin.getLogger().warning("[squest] claiming the reward of " + d.id() + " for " + p.getName() + " failed (it stays owed, try "
                            + (attempt + 1) + "): " + root(err));
                    if (attempt < 4 && plugin.isEnabled()) { // a passing database fault: claim again while it is the same session
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            Player again = Bukkit.getPlayer(id); // after a relog, the new session (its own claim was turned away)
                            if (again != null) claim(again, d, attempt + 1);
                        }, 20L * 10 * (attempt + 1));
                    }
                }
            });
        });
    }

    /** The answer of a claim of {@code now}: {@code ok} = this claim holds the reward, so it is paid now. */
    private void claimed(Player p, SideQuestDefinition d, long now, boolean ok) {
        UUID id = p.getUniqueId();
        Map<String, SideQuestRecord> m = mine.get(id);
        SideQuestRecord r = m == null ? null : m.get(d.id());
        if (!ok) { // paid already (another server, an earlier claim): never twice
            if (r != null && r.owed()) m.put(d.id(), r.paid(now));
            return;
        }
        Player cur = same(p);
        PlayerProfile now2 = cur == null ? null : services.profiles().cached(id).orElse(null);
        if (cur == null || now2 == null || now2.ephemeral()) {
            // gone, relogged (a new session whose own claim this one turned away) or in the training yard: owed
            // again, and claimed once more for the session there is now
            unclaim(id, p.getName(), d, now);
            return;
        }
        if (r != null) m.put(d.id(), r.paid(now));
        pay(cur, now2, d);
    }

    /** Undoes a claim nobody could be paid for; if even that fails, the reward is in the audit log for staff. */
    private void unclaim(UUID id, String name, SideQuestDefinition d, long now) {
        call(() -> repo.unclaimReward(id, d.id(), now)).whenComplete((v, e) -> {
            if (e != null) {
                plugin.getLogger().severe("[squest] " + name + " could not be paid " + d.id() + ", and the claim could not be undone: " + root(e)
                        + " — pay by hand: " + rewardText(d));
                services.audit().record(AuditEvent.of(id.toString(), AuditActions.SIDE_QUEST_COMPLETE, d.id(), "claimed, unpaid: " + rewardText(d)));
                return;
            }
            PlayerDataSaves.onMain(plugin, () -> {
                Player again = Bukkit.getPlayer(id);
                if (again != null) claim(again, d);
            });
        });
    }

    private static String rewardText(SideQuestDefinition d) {
        return "exp " + d.expReward() + ", coins " + d.currencyReward() + (d.lootTableId() == null ? "" : ", loot " + d.lootTableId());
    }

    /** The reward of a quest whose claim just succeeded (online, not in the training yard). */
    private void pay(Player p, PlayerProfile pr, SideQuestDefinition d) {
        pr.addCurrency(d.currencyReward());
        int from = pr.progression().level();
        var gained = services.progression().grantExp(pr, d.expReward(), ExpSource.QUEST);
        String lootLine = "";
        boolean gave = false;
        if (d.lootTableId() != null && services.itemService() != null && pr.playerClass().isPresent()) {
            var items = services.itemService();
            var tree = services.skillTree();
            var ctx = new mn.suld.api.loot.LootContext(Math.max(d.minLevel(), from), mn.suld.api.loot.LootTier.QUEST, pr.playerClass().get(),
                    tree == null ? 0 : tree.lootPct(p), p.getUniqueId(), "squest:" + d.id());
            List<ItemStack> gifts = new ArrayList<>();
            for (mn.suld.api.loot.LootDrop drop : items.filtered(p, items.roll(d.lootTableId(), ctx))) {
                gifts.add(items.stackFor(drop.item(), p, drop.amount()));
                lootLine += " + " + mn.suld.api.item.ItemTooltip.name(items.catalog(), items.catalog().require(drop.item().definitionId()), drop.item());
                items.announce(p, drop.item());
            }
            services.mailbox(plugin).giveOrKeep(p, gifts, "side quest " + d.id());
            gave = !gifts.isEmpty();
        }
        // a gift: the player file is written, then the coins and EXP are saved; no gift: the profile alone (PlayerDataSaves)
        if (gave) PlayerDataSaves.itemsForCoins(plugin, services, p, pr, "side quest " + d.id());
        else PlayerDataSaves.saveCoins(plugin, services, p, pr, "side quest " + d.id());
        p.showTitle(Title.title(Component.text("НЭМЭЛТ ЭРЭЛ ДУУСЛАА", Messages.BRAND, TextDecoration.BOLD),
                Component.text(d.title(), NamedTextColor.WHITE, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
        p.sendMessage(Messages.success("Нэмэлт эрэл дууслаа: " + d.title() + "  (+" + d.expReward() + " EXP, +" + d.currencyReward() + " зоос" + lootLine + ")"));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        if (gained.leveledUp()) mn.suld.plugin.ui.Presentation.levelUp(p, from, gained.after().level());
        if (services.achievements != null) services.achievements.record(p, mn.suld.api.achievement.Deed.SIDE_QUEST, d.id(), 1);
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.SIDE_QUEST_COMPLETE, p.getUniqueId(),
                Map.of("quest", d.id(), "level", from)));
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), AuditActions.SIDE_QUEST_COMPLETE, d.id(), rewardText(d)));
    }

    /** Drops an active quest (its progress is lost; it can be taken again). */
    public void abandon(Player p, String questId) {
        Map<String, SideQuestRecord> m = mine.get(p.getUniqueId());
        SideQuestRecord r = m == null ? null : m.get(questId);
        SideQuestDefinition d = book.byId(questId).orElse(null);
        if (r == null || !r.active() || d == null) return;
        String key = p.getUniqueId() + "|" + questId;
        if (!inFlight.add(key)) return;
        whenDone(call(() -> repo.abandon(p.getUniqueId(), questId)), (ok, err) -> {
            inFlight.remove(key);
            if (err != null) {
                fail("abandoning " + questId, err, p);
                return;
            }
            if (!Boolean.TRUE.equals(ok)) { // the row changed meanwhile (staff, another server): read the truth back
                if (same(p) != null) {
                    p.sendMessage(Messages.info("«" + d.title() + "» эрэл өөрчлөгдсөн байна — дэвтрээ дахин уншлаа."));
                    load(p);
                }
                return;
            }
            Map<String, SideQuestRecord> mm = mine.get(p.getUniqueId());
            if (mm != null) mm.remove(questId);
            Set<String> ds = dirty.get(p.getUniqueId());
            if (ds != null) ds.remove(questId);
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.SIDE_QUEST_ABANDON, p.getUniqueId(), Map.of("quest", questId)));
            if (same(p) != null) p.sendMessage(Messages.info("«" + d.title() + "» эрлийг орхилоо — дахин авч болно."));
        });
    }

    // ------------------------------------------------------------------ staff: /suld squest

    /** {@code /suld squest <player> accept|ready|done|reset <squest.id>} (docs/SIDE_QUESTS.md). */
    public void admin(CommandSender sender, Player target, String action, String questId) {
        SideQuestDefinition d = book.byId(questId).orElse(null);
        if (d == null) {
            sender.sendMessage(Messages.error("Ийм нэмэлт эрэл алга: " + questId));
            return;
        }
        if (!java.util.Set.of("accept", "ready", "done", "reset").contains(action)) {
            sender.sendMessage(Messages.error("accept | ready | done | reset"));
            return;
        }
        // the request is recorded (its result follows in the player's own rows and the squest.complete audit)
        services.audit().record(AuditEvent.of(sender.getName(), AuditActions.SIDE_QUEST_ADMIN, target.getUniqueId() + ":" + questId, action + " requested"));
        switch (action) {
            case "accept" -> {
                accept(target, questId, true);
                sender.sendMessage(Messages.info(target.getName() + ": " + questId + " авах хүсэлт явлаа — хариу нь тоглогчийн чатад."));
            }
            case "ready" -> {
                Map<String, SideQuestRecord> m = mine.get(target.getUniqueId());
                SideQuestRecord r = m == null ? null : m.get(questId);
                if (r == null || !r.active()) {
                    sender.sendMessage(Messages.error(target.getName() + "-д идэвхтэй " + questId + " алга."));
                    return;
                }
                if (d.type() == QuestType.COLLECT_ITEM) {
                    sender.sendMessage(Messages.info("Цуглуулах эрэл цүнхнээс тоологддог: зүйлийг нь өгнө үү."));
                    return;
                }
                m.put(questId, r.withProgress(d.requiredCount()));
                dirty.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(questId);
                sender.sendMessage(Messages.success(target.getName() + ": " + questId + " бэлэн боллоо."));
            }
            case "done" -> {
                handIn(target, questId, true);
                sender.sendMessage(Messages.info(target.getName() + ": " + questId + " тушаах хүсэлт явлаа — хариу нь тоглогчийн чатад."));
            }
            case "reset" -> whenDone(call(() -> repo.reset(target.getUniqueId(), questId)), (ok, err) -> {
                if (err != null) {
                    sender.sendMessage(Messages.error("Алдаа: " + root(err)));
                    return;
                }
                Map<String, SideQuestRecord> m = mine.get(target.getUniqueId());
                if (m != null) m.remove(questId);
                Set<String> ds = dirty.get(target.getUniqueId());
                if (ds != null) ds.remove(questId);
                sender.sendMessage(Messages.success(target.getName() + ": " + questId + (Boolean.TRUE.equals(ok) ? " арилгалаа." : " байгаагүй.")));
            });
            default -> sender.sendMessage(Messages.error("accept | ready | done | reset"));
        }
    }

    // ------------------------------------------------------------------ the tracker bar

    private void tickBars() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Object[] line = barLine(p);
            UUID id = p.getUniqueId();
            if (line == null) {
                BossBar bar = bars.remove(id);
                if (bar != null) mn.suld.plugin.ui.BossBars.hide(p, bar);
                continue;
            }
            BossBar bar = bars.computeIfAbsent(id, k -> {
                BossBar b = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                mn.suld.plugin.ui.BossBars.show(p, b);
                return b;
            });
            bar.name((Component) line[0]);
            bar.progress((float) line[1]);
        }
    }

    /** The bar's text and progress for the player's oldest active side quest, or null (none, a run, the tracker off). */
    private Object[] barLine(Player p) {
        if (!services.styles().setting(p.getUniqueId(), mn.suld.api.style.PlayerSetting.QUEST_TRACKER)) return null;
        if (services.dungeons().isInAnyRun(p.getUniqueId()) || p.getWorld().getEnvironment() != World.Environment.NORMAL) return null;
        Map<String, SideQuestRecord> m = mine.get(p.getUniqueId());
        if (m == null) return null;
        SideQuestRecord first = null;
        SideQuestDefinition def = null;
        for (SideQuestRecord r : m.values()) {
            if (!r.active()) continue;
            SideQuestDefinition d = book.byId(r.questId()).orElse(null);
            if (d == null) continue;
            if (first == null || r.startedAt() < first.startedAt()) {
                first = r;
                def = d;
            }
        }
        if (first == null) return null;
        float progress = Math.max(0f, Math.min(1f, (float) first.progress() / def.requiredCount()));
        Location l = p.getLocation();
        if (first.ready(def)) {
            Component head = Component.text("✔ Тушаах: " + giverName(def), NamedTextColor.GREEN, TextDecoration.BOLD);
            Location at = services.npcs == null ? null : services.npcs.where(def.giverNpcId());
            return new Object[] {at == null || !l.getWorld().equals(at.getWorld()) ? head : head.append(pointer(l, at)), 1f};
        }
        Component head = Component.text("✦ " + def.title() + " ", NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.text(first.progress() + "/" + def.requiredCount(), GOLD, TextDecoration.BOLD));
        if (def.type() == QuestType.DISCOVER_LOCATION && def.targetId().startsWith("site.") && services.historicSites != null) {
            Location site = services.historicSites.where(def.targetId()).orElse(null);
            if (site != null && l.getWorld().equals(site.getWorld())) head = head.append(pointer(l, site));
        }
        return new Object[] {head, progress};
    }

    private static Component pointer(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double b = Navigation.bearing(dx, dz);
        return Component.text("  " + Navigation.arrow(b, Navigation.facing(from.getYaw())) + " ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(Navigation.compass(b) + " · " + Math.round(Math.hypot(dx, dz)) + "м", NamedTextColor.WHITE, TextDecoration.BOLD));
    }

    // ------------------------------------------------------------------ helpers

    private void fail(String what, Throwable err, Player p) {
        plugin.getLogger().log(Level.WARNING, "[squest] " + what + " failed: " + root(err));
        if (p.isOnline()) p.sendMessage(Messages.error("Түр алдаа гарлаа, дахин оролдоно уу."));
    }

    private static String root(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    /**
     * The answer on the main thread. While the plugin is stopping it waits for {@code PlayerDataSaves.drainShutdown}
     * instead of being dropped: a hand-in whose items already left the bag still gives them back, or pays.
     */
    private <T> void whenDone(CompletableFuture<T> future, BiConsumer<T, Throwable> callback) {
        future.whenComplete((value, err) -> PlayerDataSaves.onMain(plugin, () -> callback.accept(value, err)));
    }
}
