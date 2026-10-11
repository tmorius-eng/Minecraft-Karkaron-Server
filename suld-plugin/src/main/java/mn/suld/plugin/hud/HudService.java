package mn.suld.plugin.hud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.service.ProgressionService;
import mn.suld.api.style.PlayerStyle;
import mn.suld.api.style.StaffBadge;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Glyphs;
import mn.suld.plugin.ui.StyleFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The SÜLD screen furniture, all on the vanilla scoreboard/tab API (no TAB/BetterHud plugin):
 * <ul>
 *   <li>sidebar — sections with pixel icons (Дүр, Хөрөнгө, Эрэл, location), no red numbers;</li>
 *   <li>TAB list — logo header, live footer (online, ping, TPS), styled names with staff/rank badges;</li>
 *   <li>name tags — badges before and the equipped tag after the name above every head, sorted by rank in TAB.</li>
 * </ul>
 * One scoreboard per player, kept for the session; lines are rewritten only when they change.
 */
public final class HudService {

    private static final TextColor SECTION = TextColor.fromHexString("#FFB43C");
    private static final TextColor KEY = NamedTextColor.WHITE;
    private static final String[] ENTRIES = new String[15];

    static {
        for (int i = 0; i < ENTRIES.length; i++) ENTRIES[i] = "§" + Integer.toHexString(i) + "§r";
    }

    private final ProgressionService progression;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, List<Component>> lastLines = new ConcurrentHashMap<>();
    private final List<Function<UUID, Optional<String>>> statusLines = new java.util.concurrent.CopyOnWriteArrayList<>();
    private SuldServices services;
    private Plugin plugin;
    private java.util.function.Supplier<Map<String, NamedTextColor>> glow = Map::of;

    /** Entities (UUID strings) whose glowing outline gets a colour (city NPCs). */
    public void glowSource(java.util.function.Supplier<Map<String, NamedTextColor>> source) {
        this.glow = source;
    }

    public HudService(ProgressionService progression) {
        this.progression = progression;
    }

    private HudPanel panel;

    /** The lock-on target source (CombatFeel): the target frame shows it before whatever the crosshair touches. */
    public void lockOn(java.util.function.Function<org.bukkit.entity.Player, java.util.Optional<? extends org.bukkit.entity.Entity>> f) {
        if (panel != null) panel.lockOn(f);
    }

    /** Wire the services the HUD reads (called once everything exists) and start the refresh tickers. */
    public void attach(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.panel = new HudPanel(plugin, services);
        Bukkit.getPluginManager().registerEvents(panel, plugin);
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                teamJoin(e.getPlayer());
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
                teamQuit(e.getPlayer());
            }
        }, plugin);
        panel.start();
        // every player is refreshed once per 40 ticks, spread over the ticks (it was all players on one tick)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long t = mn.suld.plugin.perf.PerfProbe.start();
            tick();
            mn.suld.plugin.perf.PerfProbe.stop("hud.sidebar_tick", t);
        }, 40L, 1L);
    }

    /** The spell system the panel reads (resource pool, cooldowns, combos); set once it exists. */
    public void skills(mn.suld.plugin.skill.SkillService skills) {
        if (panel != null) panel.skills(skills);
    }

    /** Measurement only (/suldperf hud on|off): turn the panel drawing off/on to A/B its cost. */
    public void panelEnabled(boolean on) {
        if (panel != null) panel.enabled = on;
    }

    /** Redraw this player's panel on the next tick (call after anything it shows changes). */
    public void refresh(Player player) {
        if (panel != null) panel.refresh(player);
    }

    /**
     * A short notice in the HUD's text line above the panel (instead of a raw action bar, which would replace the
     * panel). Clients without the SÜLD pack get the message as a plain action bar.
     */
    public void toast(Player player, Component message) {
        toast(player, message, 2500);
    }

    public void toast(Player player, Component message, long millis) {
        if (panel != null) panel.toast(player, message, millis);
        else player.sendActionBar(message);
    }

    /**
     * Register a contextual HUD line (clan, dungeon, world event, ...). Each provider returns a formatted
     * (legacy §) line, or empty to hide it; they render in registration order under the quest section.
     */
    public void addStatusLine(Function<UUID, Optional<String>> provider) {
        statusLines.add(provider);
    }

    private long sidebarTick;

    private void tick() {
        if (services == null) return;
        long tick = ++sidebarTick;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!mn.suld.plugin.perf.Stagger.due(p.getUniqueId(), tick, 40)) continue;
            long t0 = mn.suld.plugin.perf.PerfProbe.start();
            services.profiles().cached(p.getUniqueId()).ifPresent(pr -> update(p, pr));
            mn.suld.plugin.perf.PerfProbe.stop("hud.sidebar_lines", t0);
            long t1 = mn.suld.plugin.perf.PerfProbe.start();
            tab(p);
            mn.suld.plugin.perf.PerfProbe.stop("hud.tab", t1);
        }
        long now = System.currentTimeMillis();
        if (now - lastFullReconcile > 60_000) {
            lastFullReconcile = now;
            refreshTeams();
        }
    }

    private Scoreboard board(Player p) {
        Scoreboard b = boards.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        if (p.getScoreboard() != b) p.setScoreboard(b);
        return b;
    }

    public void update(Player player, PlayerProfile profile) {
        Scoreboard board = board(player);
        List<Component> lines = buildLines(player, profile);
        if (lines.equals(lastLines.get(player.getUniqueId()))) return;
        lastLines.put(player.getUniqueId(), lines);
        Objective obj = board.getObjective("suld");
        if (obj == null) {
            obj = board.registerNewObjective("suld", Criteria.DUMMY, title());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        }
        for (int i = 0; i < ENTRIES.length; i++) {
            if (i < lines.size()) {
                var score = obj.getScore(ENTRIES[i]);
                score.setScore(lines.size() - i);
                score.customName(lines.get(i));
            } else {
                board.resetScores(ENTRIES[i]);
            }
        }
    }

    public void clear(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        teamQuit(player);
    }

    private static Component title() {
        return Component.text("✦ ", TextColor.fromHexString("#FFD24A"))
                .append(StyleFormat.mini("<bold><gradient:#FFF0A0:#FFD24A:#FF9A3C>SÜLD</gradient></bold>"))
                .append(Component.text(" ✦", TextColor.fromHexString("#FFD24A")));
    }

    private static Component section(String icon, String label) {
        return StyleFormat.join(StyleFormat.glyph(icon), Component.text(" " + label, SECTION, TextDecoration.BOLD));
    }

    private static Component row(String key, Component value) {
        return Component.text("  " + key + ": ", KEY).append(value);
    }

    private static String num(long v) {
        return String.format("%,d", v);
    }

    private List<Component> buildLines(Player player, PlayerProfile profile) {
        Progression p = profile.progression();
        long toNext = progression.engine().expToNextLevel(p);
        double frac = progression.engine().progressFraction(p);
        PlayerStyle style = services == null ? null : services.styles().of(player.getUniqueId());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(section(Glyphs.ICON_PERSON, player.getName()));
        lines.add(row("Анги", profile.playerClass().map(c -> (Component) Component.text(c.displayName(), NamedTextColor.WHITE))
                .orElse(Component.text("— /class", NamedTextColor.YELLOW))));
        if (style != null) lines.add(Component.text("  Цол: ", KEY).append(StyleFormat.rankBadge(style.rank(), true)));
        lines.add(row("Түвшин", Component.text(p.level(), NamedTextColor.GREEN)
                .append(Component.text(toNext > 0 ? "  " + Math.round(frac * 100) + "%" : "  MAX", NamedTextColor.GRAY))));
        if (toNext <= 0 || profile.ascension() > 0) {
            // at the cap: the Тэнгэрийн Зэрэг rank and the оноо that EXP turns into (/ascend)
            lines.add(row("Зэрэг", Component.text(mn.suld.api.balance.Ascension.roman(profile.ascension()), NamedTextColor.AQUA)
                    .append(Component.text("  ✦ " + num(profile.endgame().tengeriPoints()) + " /ascend", NamedTextColor.GRAY))));
        }
        if (services != null && services.skillTree() != null && profile.hasSelectedClass()) {
            int avail = services.skillTree().available(player);
            if (avail > 0) lines.add(row("Чадвар", Component.text("◆ " + avail + " оноо /skills", NamedTextColor.AQUA)));
        }
        lines.add(Component.empty());
        lines.add(section(Glyphs.ICON_COIN, "Хөрөнгө"));
        lines.add(row("Зоос", Component.text(num(profile.currency()) + " ₮", NamedTextColor.GOLD)));
        if (style != null) lines.add(row("Кредит", Component.text(num(style.credits()) + " ✦", TextColor.fromHexString("#9FF3FF"))));
        lines.add(Component.empty());
        lines.add(section(Glyphs.ICON_SCROLL, "Эрэл"));
        var qs = profile.questState();
        var qd = services == null ? null : services.quests().definition(qs.questId()).orElse(null);
        String quest = qd != null && qs.active() ? qd.title() + " " + qs.progress() + "/" + qd.requiredCount()
                : (qs.completed() ? "Дууссан ✔" : "—");
        lines.add(Component.text("  " + quest, NamedTextColor.WHITE));
        for (Function<UUID, Optional<String>> provider : statusLines) {
            if (lines.size() >= 13) break;
            provider.apply(player.getUniqueId()).ifPresent(l ->
                    lines.add(Component.text("  ").append(LegacyComponentSerializer.legacySection().deserialize(l))));
        }
        if (services != null && lines.size() < 14) {
            boolean safe = services.inCity(player.getLocation());
            var danger = safe ? null : danger(player, profile.progression().level());
            lines.add(StyleFormat.join(StyleFormat.glyph(Glyphs.ICON_PIN),
                    Component.text(" " + (safe ? "Хархорум" : danger == null ? "Тал нутаг" : danger.place()) + " ", NamedTextColor.WHITE),
                    StyleFormat.glyph(safe ? Glyphs.BADGE_SAFE : Glyphs.BADGE_DANGER)));
            if (danger != null && lines.size() < 14) {
                lines.add(Component.text("  Lv " + danger.band() + " · Аюул: ", NamedTextColor.GRAY)
                        .append(Component.text(danger.rating(), danger.color())));
            }
        }
        lines.add(Component.text(domain(), NamedTextColor.GRAY));
        return lines;
    }

    /** Where the player stands in the wild and how dangerous it is for their level. */
    record Danger(String place, String band, String rating, net.kyori.adventure.text.format.TextColor color) {
    }

    private static final mn.suld.api.region.RegionIndex REGIONS = new mn.suld.api.region.RegionIndex(mn.suld.plugin.content.WorldContent.REGIONS);

    /**
     * The danger of the spot against the player's level: the wild's level here (the area's band, WorldContent.localLevel)
     * minus the player's. ≤ −6 Хялбар, −5…−2 Бага, −1…+2 Тохиромжтой, +3…+5 Өндөр, ≥ +6 Үхлийн.
     */
    Danger danger(Player p, int level) {
        org.bukkit.Location c = p.getWorld().getSpawnLocation();
        double dx = p.getLocation().getX() - c.getX(), dz = p.getLocation().getZ() - c.getZ();
        var r = REGIONS.at(dx, dz).filter(x -> !x.safeZone()).orElse(null);
        if (r == null || !p.getWorld().equals(org.bukkit.Bukkit.getWorlds().get(0))) return null;
        var area = mn.suld.plugin.content.WorldContent.areaAt(dx, dz).filter(a -> a.regionId().equals(r.id())).orElse(null);
        String band = area != null ? area.minLevel() + "–" + area.maxLevel() : r.levelBand();
        int gap = mn.suld.plugin.content.WorldContent.localLevel(r, dx, dz) - level;
        if (gap <= -6) return new Danger(area != null ? area.name() : r.displayName(), band, "Хялбар", NamedTextColor.GRAY);
        if (gap <= -2) return new Danger(area != null ? area.name() : r.displayName(), band, "Бага", NamedTextColor.GREEN);
        if (gap <= 2) return new Danger(area != null ? area.name() : r.displayName(), band, "Тохиромжтой", NamedTextColor.YELLOW);
        if (gap <= 5) return new Danger(area != null ? area.name() : r.displayName(), band, "Өндөр", NamedTextColor.GOLD);
        return new Danger(area != null ? area.name() : r.displayName(), band, "Үхлийн!", NamedTextColor.RED);
    }

    private String domain() {
        return plugin == null ? "suld.mn" : plugin.getConfig().getString("branding.domain", "suld.mn");
    }

    // ------------------------------------------------------------------ TAB list

    private Component tabHeader;
    private final Map<UUID, Component> lastListName = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastListLevel = new ConcurrentHashMap<>();
    private final Set<UUID> listDirty = ConcurrentHashMap.newKeySet();

    private void tab(Player p) {
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        // the logo glyph is 40 px tall from 7 px above the first line: five line breaks clear it
        if (tabHeader == null) tabHeader = Component.text("\n").append(StyleFormat.glyph(Glyphs.LOGO)).append(Component.text("\n\n\n\n\n"))
                .append(Component.text("Монгол Hardcore MMORPG", TextColor.fromHexString("#FFE08A"), TextDecoration.BOLD))
                .append(Component.text("\n" + domain() + "\n", NamedTextColor.GRAY));
        Component header = tabHeader;
        Component footer = Component.text("\n")
                .append(Component.text("Онлайн ", NamedTextColor.GRAY)).append(Component.text(Bukkit.getOnlinePlayers().size(), NamedTextColor.GREEN))
                .append(Component.text("  ·  Пинг ", NamedTextColor.GRAY)).append(Component.text(p.getPing() + "ms", ping(p.getPing())))
                .append(Component.text("  ·  TPS ", NamedTextColor.GRAY)).append(Component.text(String.format("%.1f", tps), tps >= 18 ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("\n/menu", NamedTextColor.GOLD)).append(Component.text(" цэс  ·  ", NamedTextColor.GRAY))
                .append(Component.text("/help", NamedTextColor.GOLD)).append(Component.text(" тусламж  ·  ", NamedTextColor.GRAY))
                .append(Component.text("/shop", NamedTextColor.GOLD)).append(Component.text(" дэлгүүр\n", NamedTextColor.GRAY));
        p.sendPlayerListHeaderAndFooter(header, footer);
        PlayerStyle s = services.styles().of(p.getUniqueId());
        int level = services.profiles().cached(p.getUniqueId()).map(pr -> pr.progression().level()).orElse(1);
        // playerListName broadcasts an info update to every online player: only when it actually changed, and the
        // name (badges + MiniMessage styles) is only rebuilt when the style or the level changed
        Integer seenLevel = lastListLevel.get(p.getUniqueId());
        if (seenLevel == null || seenLevel != level || listDirty.remove(p.getUniqueId())) {
            lastListLevel.put(p.getUniqueId(), level);
            Component listName = StyleFormat.badges(p, s, true).append(StyleFormat.name(p, s))
                    .append(Component.text(" " + level, TextColor.fromHexString("#8C96A8")));
            if (!listName.equals(lastListName.put(p.getUniqueId(), listName))) p.playerListName(listName);
        }
    }

    private static NamedTextColor ping(int ms) {
        return ms < 80 ? NamedTextColor.GREEN : ms < 200 ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    // ------------------------------------------------------------------ teams: TAB order + name tags

    private String teamName(Player p, PlayerStyle s) {
        int staff = StyleFormat.staffOf(p).map(StaffBadge::ordinal).orElse(9);
        int rank = 9 - s.rank().ordinal();
        return "s" + staff + rank + p.getUniqueId().toString().replace("-", "").substring(0, 12);
    }

    /** One player's name-tag team (TAB order, badges before, tag after): computed once, shared by every board. */
    record TeamSpec(String name, String entry, Component prefix, Component suffix) {
    }

    /** The current spec of every online player (the only place badges/tags are computed for name tags). */
    private final Map<UUID, TeamSpec> specs = new ConcurrentHashMap<>();
    private long lastFullReconcile;

    private TeamSpec specOf(Player p) {
        PlayerStyle s = services.styles().of(p.getUniqueId());
        Component prefix = StyleFormat.badges(p, s, true);
        Component suffix = StyleFormat.tag(s).map(t -> Component.text(" ").append(t)).orElse(Component.empty());
        return new TeamSpec(teamName(p, s), p.getName(), prefix, suffix);
    }

    /** Write one spec into one board; the old team of that player (another name after a rank change) goes. */
    private static void apply(Scoreboard board, TeamSpec spec, TeamSpec old) {
        if (old != null && !old.name().equals(spec.name())) {
            Team gone = board.getTeam(old.name());
            if (gone != null) gone.unregister();
        }
        Team team = board.getTeam(spec.name());
        if (team == null) team = board.registerNewTeam(spec.name());
        if (!spec.prefix().equals(team.prefix())) team.prefix(spec.prefix());
        if (!spec.suffix().equals(team.suffix())) team.suffix(spec.suffix());
        if (!team.hasEntry(spec.entry())) team.addEntry(spec.entry());
    }

    private void applyGlow(Scoreboard board) {
        for (Map.Entry<String, NamedTextColor> g : glow.get().entrySet()) {
            String name = "glow_" + g.getValue().toString();
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
                team.color(g.getValue());
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            }
            if (!team.hasEntry(g.getKey())) team.addEntry(g.getKey());
        }
    }

    /**
     * A player joined: their board gets every online player's team (and the NPC glow teams), and every other board
     * gets theirs. O(players), not O(players²).
     */
    public void teamJoin(Player p) {
        if (services == null) return;
        TeamSpec mine = specOf(p);
        TeamSpec old = specs.put(p.getUniqueId(), mine);
        Scoreboard own = board(p);
        for (Player other : Bukkit.getOnlinePlayers()) {
            TeamSpec theirs = other.equals(p) ? mine : specs.computeIfAbsent(other.getUniqueId(), k -> specOf(other));
            apply(own, theirs, null);
            if (!other.equals(p)) apply(board(other), mine, old);
        }
        applyGlow(own);
    }

    /** A player's badges, tag or rank changed: rewrite only their team, on every board, and only if it changed. */
    public void teamChanged(Player p) {
        if (services == null || !p.isOnline()) return;
        listDirty.add(p.getUniqueId());
        TeamSpec now = specOf(p);
        TeamSpec old = specs.put(p.getUniqueId(), now);
        if (now.equals(old)) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) apply(board(viewer), now, old);
    }

    /** A player left: their team leaves every board, their own board and caches go (they were kept forever before). */
    public void teamQuit(Player p) {
        TeamSpec old = specs.remove(p.getUniqueId());
        boards.remove(p.getUniqueId());
        lastLines.remove(p.getUniqueId());
        lastListName.remove(p.getUniqueId());
        lastListLevel.remove(p.getUniqueId());
        listDirty.remove(p.getUniqueId());
        if (old == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(p)) continue;
            Scoreboard b = boards.get(viewer.getUniqueId());
            Team t = b == null ? null : b.getTeam(old.name());
            if (t != null) t.unregister();
        }
    }

    /** The NPC glow set changed (an NPC respawned): only the glow teams are written. */
    public void refreshGlow() {
        for (Player viewer : Bukkit.getOnlinePlayers()) applyGlow(board(viewer));
    }

    /**
     * Full reconcile (the safety net for anything an event missed: a permission changed outside SÜLD): every board
     * against the cached specs. Still O(players²) but cheap per pair (no badge/MiniMessage work: specs are cached) and
     * at most once a minute.
     */
    public void refreshTeams() {
        if (services == null) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player p : online) specs.put(p.getUniqueId(), specOf(p));
        for (Player viewer : online) {
            Scoreboard board = board(viewer);
            Set<String> wanted = new HashSet<>();
            for (Player target : online) {
                TeamSpec spec = specs.get(target.getUniqueId());
                wanted.add(spec.name());
                apply(board, spec, null);
            }
            applyGlow(board);
            for (Team t : new ArrayList<>(board.getTeams())) {
                if (t.getName().startsWith("s") && t.getName().length() == 15 && !wanted.contains(t.getName())) t.unregister();
            }
        }
    }
}
