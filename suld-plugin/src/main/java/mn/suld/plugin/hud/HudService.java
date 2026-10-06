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

    /** Wire the services the HUD reads (called once everything exists) and start the refresh ticker. */
    public void attach(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    /**
     * Register a contextual HUD line (clan, dungeon, world event, ...). Each provider returns a formatted
     * (legacy §) line, or empty to hide it; they render in registration order under the quest section.
     */
    public void addStatusLine(Function<UUID, Optional<String>> provider) {
        statusLines.add(provider);
    }

    private void tick() {
        if (services == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            services.profiles().cached(p.getUniqueId()).ifPresent(pr -> update(p, pr));
            tab(p);
        }
        refreshTeams();
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
        lastLines.remove(player.getUniqueId());
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        refreshTeams();
    }

    private static Component title() {
        return Component.text("✦ ", TextColor.fromHexString("#FFD24A"))
                .append(StyleFormat.mini("<bold><gradient:#FFF0A0:#FFD24A:#FF9A3C>SÜLD</gradient></bold>"))
                .append(Component.text(" ✦", TextColor.fromHexString("#FFD24A")));
    }

    private static Component section(String icon, String label) {
        return StyleFormat.glyph(icon).append(Component.text(" " + label, SECTION, TextDecoration.BOLD));
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
            lines.add(StyleFormat.glyph(Glyphs.ICON_PIN).append(Component.text(safe ? " Хархорум " : " Тал нутаг ", NamedTextColor.WHITE))
                    .append(StyleFormat.glyph(safe ? Glyphs.BADGE_SAFE : Glyphs.BADGE_DANGER)));
        }
        lines.add(Component.text(domain(), NamedTextColor.GRAY));
        return lines;
    }

    private String domain() {
        return plugin == null ? "suld.mn" : plugin.getConfig().getString("branding.domain", "suld.mn");
    }

    // ------------------------------------------------------------------ TAB list

    private void tab(Player p) {
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        Component header = Component.text("\n").append(StyleFormat.glyph(Glyphs.LOGO)).append(Component.text("\n\n\n"))
                .append(Component.text("Монгол Hardcore MMORPG", TextColor.fromHexString("#FFE08A"), TextDecoration.BOLD))
                .append(Component.text("\n" + domain() + "\n", NamedTextColor.GRAY));
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
        p.playerListName(StyleFormat.badges(p, s, true).append(StyleFormat.name(p, s))
                .append(Component.text(" " + level, TextColor.fromHexString("#8C96A8"))));
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

    /** Make every player's board know every online player's team (badges, tag, order). */
    public void refreshTeams() {
        if (services == null) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player viewer : online) {
            Scoreboard board = board(viewer);
            Set<String> wanted = new HashSet<>();
            for (Player target : online) {
                PlayerStyle s = services.styles().of(target.getUniqueId());
                String name = teamName(target, s);
                wanted.add(name);
                Team team = board.getTeam(name);
                if (team == null) team = board.registerNewTeam(name);
                Component prefix = StyleFormat.badges(target, s, true);
                Component suffix = StyleFormat.tag(s).map(t -> Component.text(" ").append(t)).orElse(Component.empty());
                if (!prefix.equals(team.prefix())) team.prefix(prefix);
                if (!suffix.equals(team.suffix())) team.suffix(suffix);
                if (!team.hasEntry(target.getName())) team.addEntry(target.getName());
            }
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
            for (Team t : new ArrayList<>(board.getTeams())) {
                if (t.getName().startsWith("s") && t.getName().length() == 15 && !wanted.contains(t.getName())) t.unregister();
            }
        }
    }
}
