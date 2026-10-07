package mn.suld.plugin.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import mn.suld.api.skill.tree.EffectText;
import mn.suld.api.skill.tree.SkillAllocation;
import mn.suld.api.skill.tree.SkillAllocation.NodeState;
import mn.suld.api.skill.tree.SkillBuild;
import mn.suld.api.skill.tree.SkillCategory;
import mn.suld.api.skill.tree.SkillEngine;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.SkillTree;
import mn.suld.api.skill.tree.Ultimate;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.skill.OrbOfOblivion;
import mn.suld.plugin.skill.SkillText;
import mn.suld.plugin.skill.SkillTreeService;
import mn.suld.plugin.ui.Glyphs;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The skill map: a chest-based map the server fully controls (no client mod). Two zoom levels, panning, a category
 * filter, a chat-driven search, node states, connector lines drawn with pack-model items (gold = learned path,
 * turquoise = next step, red = exclusive choice), full tooltips, rank-up with left click and refund with right click.
 *
 * <p>Detailed view: a node every second slot with connector slots between them (5 x 3 nodes visible, pan to see the
 * rest). Overview: one node per slot (all columns, 5 rows), colour-coded, no connectors.
 */
public final class SkillMapMenu implements Listener {

    private static final TextColor GOLD = TextColor.fromHexString("#F2B632");
    private static final TextColor TURQ = TextColor.fromHexString("#2AC4B4");
    private static final TextColor RED = TextColor.fromHexString("#E24040");
    private static final TextColor GREEN = TextColor.fromHexString("#5CE05C");
    private static final TextColor ORANGE = TextColor.fromHexString("#FF9A3C");
    private static final TextColor PURPLE = TextColor.fromHexString("#C070FF");
    private static final TextColor GRAY = TextColor.fromHexString("#A0A0A0");

    private static final class View {
        int zoom = 1;
        int ox, oy;
        /** The detailed view's position, kept while the overview is shown so zooming back returns to the same place. */
        int detailOx, detailOy;
        SkillCategory filter;
        Set<String> found = new HashSet<>();
        String query = "";
        String selected = "";
        Menu menu;
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();

    private Menus menus;

    public SkillMapMenu(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    /** The other menus (the spell list is one click away from the info button). */
    public void menus(Menus menus) {
        this.menus = menus;
    }

    private SkillTreeService st() {
        return services.skillTree();
    }

    // ------------------------------------------------------------------ open / redraw

    /** The full-screen tree (SkillSky); the chest map is the fallback where it cannot open (combat, dungeon, soul). */
    private SkillSky sky;

    public void sky(SkillSky sky) {
        this.sky = sky;
    }

    public void open(Player p) {
        if (sky != null && sky.open(p)) return;
        open(p, null);
    }

    /** Opens the map; {@code focus} (a node id) centres the view on that node. */
    public void open(Player p, String focus) {
        SkillTree tree = st().tree(p);
        if (tree == null) {
            p.sendMessage(Messages.error("Эхлээд ангиа сонгоно уу — чадварын мод ангиас хамаарна."));
            return;
        }
        View v = views.computeIfAbsent(p.getUniqueId(), k -> new View());
        if (focus != null) tree.find(focus).ifPresent(n -> center(v, n));
        // the menu is kept so a redraw never flickers; a fresh one is made if the player's previous window is gone
        if (v.menu == null || v.menu.getInventory().getViewers().stream().noneMatch(h -> h.equals(p))) {
            v.menu = new Menu(6, "Чадварын Газрын Зураг · " + tree.clazz().displayName(), Glyphs.GUI_SKILLMAP);
        }
        render(p, v);
        v.menu.open(p);
    }

    private void center(View v, SkillNode n) {
        if (v.zoom == 1) {
            v.ox = n.x() - 2;
            v.oy = n.y() - 1;
        } else {
            v.ox = 0;
            v.oy = n.y() - 2;
        }
    }

    private void clamp(View v, SkillTree tree) {
        int maxX = tree.maxX(), maxY = tree.maxY();
        int visX = v.zoom == 1 ? 5 : 9, visY = v.zoom == 1 ? 3 : 5;
        v.ox = Math.max(0, Math.min(v.ox, Math.max(0, maxX + 1 - visX)));
        v.oy = Math.max(0, Math.min(v.oy, Math.max(0, maxY + 1 - visY)));
    }

    private void redraw(Player p) {
        View v = views.get(p.getUniqueId());
        if (v == null || v.menu == null) return;
        render(p, v);
        p.updateInventory();
    }

    private void render(Player p, View v) {
        SkillTree tree = st().tree(p);
        SkillAllocation a = st().allocation(p);
        Menu m = v.menu;
        m.clear();
        if (tree == null || a == null) return;
        clamp(v, tree);
        SkillEngine.Context ctx = st().context(p);
        int avail = st().available(p);
        if (v.zoom == 1) drawConnectors(m, tree, a, v, ctx, avail);
        for (SkillNode n : tree.nodes()) {
            int slot = nodeSlot(n, v);
            if (slot < 0 || !a.visible(n)) continue;
            NodeState state = a.state(n, ctx.level(), avail);
            m.set(slot, nodeItem(p, tree, a, n, state, v), (pl, click) -> onNode(pl, n, click));
        }
        toolbar(p, v, tree, a, ctx, avail);
    }

    // ------------------------------------------------------------------ geometry

    private int nodeSlot(SkillNode n, View v) {
        if (n.satellite()) return -1; // satellites live on the full-screen tree only (they have no grid cell)
        int cx = n.x() - v.ox, cy = n.y() - v.oy;
        if (v.zoom == 1) {
            if (cx < 0 || cx > 4 || cy < 0 || cy > 2) return -1;
            return (2 * cy) * 9 + 2 * cx;
        }
        if (cx < 0 || cx > 8 || cy < 0 || cy > 4) return -1;
        return cy * 9 + cx;
    }

    private void drawConnectors(Menu m, SkillTree tree, SkillAllocation a, View v, SkillEngine.Context ctx, int avail) {
        for (SkillNode x : tree.nodes()) {
            for (int nb : tree.neighbours(x.index())) {
                if (nb < x.index()) continue;
                SkillNode y = tree.node(nb);
                if (x.satellite() || y.satellite() || !a.visible(x) || !a.visible(y)) continue;
                boolean xu = a.unlocked(x), yu = a.unlocked(y);
                String color = xu && yu ? "on" : ((xu && a.reachable(y) && a.rival(y) == null) || (yu && a.reachable(x) && a.rival(x) == null)) ? "next" : "off";
                place(m, x, y, color, v, null);
            }
        }
        for (SkillNode x : tree.nodes()) {
            for (int ex : tree.exclusives(x.index())) {
                if (ex < x.index()) continue;
                SkillNode y = tree.node(ex);
                if (Math.abs(x.x() - y.x()) > 1 || Math.abs(x.y() - y.y()) > 1 || !a.visible(x) || !a.visible(y)) continue;
                place(m, x, y, "red", v, List.of(Menu.title("✖ Хамт авч болохгүй", RED),
                        Menu.line(x.name() + "  ↔  " + y.name()), Menu.line("Нэгийг нь л сонгоно.")));
            }
        }
    }

    private void place(Menu m, SkillNode x, SkillNode y, String color, View v, List<Component> tooltip) {
        int dx = Math.abs(x.x() - y.x()), dy = Math.abs(x.y() - y.y());
        SkillNode left = x.x() <= y.x() ? x : y;
        SkillNode top = x.y() <= y.y() ? x : y;
        int row, col;
        String kind;
        if (dy == 0) {
            row = 2 * (x.y() - v.oy);
            col = 2 * (left.x() - v.ox) + 1;
            kind = "h";
        } else if (dx == 0) {
            row = 2 * (top.y() - v.oy) + 1;
            col = 2 * (x.x() - v.ox);
            kind = "v";
        } else {
            row = 2 * (top.y() - v.oy) + 1;
            col = 2 * (left.x() - v.ox) + 1;
            kind = left.y() < (left == x ? y : x).y() ? "d1" : "d2";
        }
        if (row < 0 || row > 4 || col < 0 || col > 8) return;
        int slot = row * 9 + col;
        // a red link never hides a learned path: it only fills a free connector slot
        if (m.getInventory().getItem(slot) != null && color.equals("red")) return;
        m.set(slot, Menu.model(mn.suld.api.skill.tree.MapAssets.connector(kind, color), tooltip == null ? null : tooltip.get(0), tooltip), null);
    }

    // ------------------------------------------------------------------ nodes

    private Material material(String id, Material fallback) {
        Material mat = Material.matchMaterial(id);
        return mat == null || !mat.isItem() ? fallback : mat;
    }

    private static TextColor stateColor(NodeState s) {
        return switch (s) {
            case MAXED -> GOLD;
            case UNLOCKED -> GOLD;
            case AVAILABLE -> GREEN;
            case NEEDS_POINTS -> ORANGE;
            case LEVEL_LOCKED -> ORANGE;
            case PREREQUISITE_MISSING -> ORANGE;
            case EXCLUDED -> RED;
            case LOCKED -> GRAY;
        };
    }

    private ItemStack nodeItem(Player p, SkillTree tree, SkillAllocation a, SkillNode n, NodeState state, View v) {
        boolean dim = v.filter != null && n.category() != v.filter && !n.root();
        boolean found = v.found.contains(n.id());
        if (n.secret() && !a.unlocked(n)) {
            return Menu.item(Material.ENDER_EYE, Menu.title("???", PURPLE), List.of(Menu.line("Нууц чадвар"), Menu.line("Замыг нь нээж олоорой.")));
        }
        Material mat;
        if (dim) mat = Material.BLACK_STAINED_GLASS_PANE;
        else if (state == NodeState.LOCKED) mat = Material.GRAY_STAINED_GLASS_PANE;
        else if (state == NodeState.EXCLUDED) mat = Material.RED_STAINED_GLASS_PANE;
        else mat = material(n.icon(), Material.PAPER);
        String prefix = n.keystone() ? "★ " : n.capstone() ? "✦ " : "";
        TextColor color = n.keystone() && state != NodeState.LOCKED && state != NodeState.EXCLUDED ? PURPLE : stateColor(state);
        Component title = Menu.title((found ? "🔍 " : "") + prefix + n.name(), color);
        ItemStack it = Menu.item(mat, title, tooltip(p, tree, a, n, state, dim));
        // the glow means "learned" (or "found by the search"): set it explicitly both ways, because some icons (experience
        // bottle, nether star...) glow by themselves and would otherwise look learned when they are not
        boolean glow = !dim && (state == NodeState.UNLOCKED || state == NodeState.MAXED || found) && mat != Material.GRAY_STAINED_GLASS_PANE;
        var meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(glow);
        it.setItemMeta(meta);
        if (a.rank(n) > 1) it.setAmount(Math.min(64, a.rank(n)));
        return it;
    }

    List<Component> tooltip(Player p, SkillTree tree, SkillAllocation a, SkillNode n, NodeState state, boolean dim) {
        List<Component> out = new ArrayList<>();
        out.add(Menu.line(n.category().label() + (n.keystone() ? " · Түлхүүр чадвар" : n.capstone() ? " · Дуулал" : n.hasProc() ? " · Идэвхгүй шид" : "")));
        if (n.maxRank() > 1) out.add(Menu.kv("Түвшин", a.rank(n) + "/" + n.maxRank(), a.maxed(n) ? GOLD : NamedTextColor.WHITE));
        if (!n.description().isEmpty()) out.add(Menu.line(n.description()));
        out.add(Component.empty());
        int rank = Math.max(1, a.rank(n));
        for (EffectText.Line l : EffectText.lines(n)) {
            switch (l.kind()) {
                case EFFECT -> {
                    String extra = n.maxRank() > 1 && a.rank(n) > 1 ? "   (одоо ×" + a.rank(n) + ")" : "";
                    out.add(Component.text("▸ ", TURQ, TextDecoration.BOLD).append(Menu.line(l.text() + (n.maxRank() > 1 ? " / түвшин" : "") + extra)));
                }
                case PASSIVE -> out.add(Component.text("◆ ", TURQ, TextDecoration.BOLD).append(Menu.line(l.text())));
                case TRIGGER, COOLDOWN -> out.add(Component.text(l.text(), ORANGE, TextDecoration.BOLD));
                case KEYSTONE -> out.add(Component.text("★ " + l.text(), PURPLE, TextDecoration.BOLD));
                case ULTIMATE -> out.add(Component.text("✦ " + l.text(), GOLD, TextDecoration.BOLD));
            }
        }
        out.add(Component.empty());
        if (!n.root()) out.add(Menu.kv("Үнэ:", n.cost() + " оноо" + (n.maxRank() > 1 ? " / түвшин" : ""), GOLD));
        int lvl = st().context(p).level();
        if (n.effectiveLevel() > 1) out.add(Menu.kv("Шаардлагатай түвшин:", String.valueOf(n.effectiveLevel()), lvl >= n.effectiveLevel() ? GREEN : RED));
        for (SkillNode.Req r : n.requires()) {
            SkillNode need = tree.node(r.node());
            boolean ok = a.rank(need) >= r.rank();
            out.add(Component.text((ok ? "✔ " : "✖ ") + "Шаардлага: " + need.name() + (need.maxRank() > 1 ? " (түвшин " + r.rank() + ")" : ""), ok ? GREEN : RED, TextDecoration.BOLD));
        }
        List<String> rivals = new ArrayList<>();
        for (int ex : tree.exclusives(n.index())) rivals.add(tree.node(ex).name());
        if (!rivals.isEmpty()) out.add(Component.text("✖ Хамт авч болохгүй: " + String.join(", ", rivals), RED, TextDecoration.BOLD));
        out.add(Component.empty());
        out.add(switch (state) {
            case MAXED -> Component.text(n.root() ? "◆ Эхлэл — үргэлж нээлттэй" : "✔ Дээд түвшинд хүрсэн", GOLD, TextDecoration.BOLD);
            case UNLOCKED -> Component.text("✔ Нээгдсэн", GOLD, TextDecoration.BOLD);
            case AVAILABLE -> Component.text("● Нээж болно", GREEN, TextDecoration.BOLD);
            case NEEDS_POINTS -> Component.text("⚠ Оноо хүрэлцэхгүй", ORANGE, TextDecoration.BOLD);
            case LEVEL_LOCKED -> Component.text("⚠ Түвшин хүрэхгүй", ORANGE, TextDecoration.BOLD);
            case PREREQUISITE_MISSING -> Component.text("⚠ Шаардлага биелээгүй", ORANGE, TextDecoration.BOLD);
            case EXCLUDED -> Component.text("✖ Өөр сонголт хийсэн (улаан холбоос)", RED, TextDecoration.BOLD);
            case LOCKED -> Component.text("🔒 Эхлээд үүнтэй шугамаар холбогдсон чадварыг нээ: " + connected(tree, a, n), GRAY, TextDecoration.BOLD);
        });
        if (!n.root()) {
            out.add(Component.text("« Зүүн товш: нээх / дээшлүүлэх · Баруун товш: буцаах · Shift: дэлгэрэнгүй »", NamedTextColor.WHITE, TextDecoration.BOLD));
        }
        return out;
    }

    /** The names of the visible nodes a line joins to {@code n} (what the player has to learn first). */
    private static String connected(SkillTree tree, SkillAllocation a, SkillNode n) {
        List<String> names = new ArrayList<>();
        for (int nb : tree.neighbours(n.index())) {
            SkillNode m = tree.node(nb);
            if (a.visible(m)) names.add("«" + m.name() + "»");
        }
        return names.isEmpty() ? "—" : String.join(" эсвэл ", names);
    }

    private void onNode(Player p, SkillNode n, ClickType click) {
        View v = views.get(p.getUniqueId());
        if (v != null) v.selected = n.id();
        if (click.isShiftClick()) {
            detail(p, n);
        } else if (click.isRightClick()) {
            var c = st().refund(p, n);
            feedback(p, c.ok() ? "§e↩ " + n.name() + " буцаагдлаа" : "§c" + SkillText.why(c));
        } else {
            var c = st().unlock(p, n);
            feedback(p, c.ok() ? "§a✔ " + n.name() + " нээгдлээ" : "§c" + SkillText.why(c));
        }
        redraw(p);
    }

    private void feedback(Player p, String text) {
        services.hud().toast(p, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text)
                .decoration(TextDecoration.BOLD, true));
    }

    private void detail(Player p, SkillNode n) {
        p.sendMessage(Component.text("━━ " + n.name() + " ━━", GOLD, TextDecoration.BOLD));
        for (EffectText.Line l : EffectText.lines(n)) p.sendMessage(Menu.line("  " + l.text()));
        SkillTree tree = st().tree(p);
        p.sendMessage(Menu.line("  Үнэ: " + n.cost() + " оноо × " + n.maxRank() + " түвшин · Түвшин " + n.effectiveLevel()));
        for (SkillNode.Req r : n.requires()) p.sendMessage(Menu.line("  Шаардлага: " + tree.node(r.node()).name()));
    }

    // ------------------------------------------------------------------ toolbar

    private ItemStack bar(Material mat, String title, TextColor color, List<String> lines) {
        List<Component> lore = new ArrayList<>();
        for (String l : lines) lore.add(Menu.line(l));
        return Menu.item(mat, Menu.title(title, color), lore);
    }

    private void toolbar(Player p, View v, SkillTree tree, SkillAllocation a, SkillEngine.Context ctx, int avail) {
        Menu m = v.menu;
        m.set(45, bar(Material.ARROW, "◀ Зүүн", TURQ, List.of("Газрын зургийг зүүн тийш хөдөлгөнө")), (pl, c) -> pan(pl, -1, 0));
        m.set(46, bar(Material.ARROW, "▲ Дээш", TURQ, List.of("Газрын зургийг дээш хөдөлгөнө")), (pl, c) -> pan(pl, 0, -1));
        m.set(47, bar(Material.ARROW, "▼ Доош", TURQ, List.of("Газрын зургийг доош хөдөлгөнө")), (pl, c) -> pan(pl, 0, 1));
        m.set(48, bar(Material.ARROW, "▶ Баруун", TURQ, List.of("Газрын зургийг баруун тийш хөдөлгөнө")), (pl, c) -> pan(pl, 1, 0));
        m.set(49, bar(Material.SPYGLASS, v.zoom == 1 ? "Бүх газрыг харах" : "Нарийвчлан харах", GOLD,
                List.of(v.zoom == 1 ? "Одоо: нарийвчилсан (шугам, 5×3 нод)" : "Одоо: ерөнхий (бүх багана, шугамгүй)", "Товшиж зөөнө")), (pl, c) -> zoom(pl));
        m.set(50, bar(Material.COMPASS, v.query.isEmpty() ? "Хайх" : "Хайлт: " + v.query, GOLD,
                List.of(v.query.isEmpty() ? "Нэр эсвэл нөлөөгөөр хайна" : v.found.size() + " нод олдлоо", "Зүүн товш: чатад хайх үгээ бич", "Баруун товш: хайлтыг арилгах")),
                (pl, c) -> {
                    if (c.isRightClick()) clearSearch(pl);
                    else promptSearch(pl);
                });
        String cat = v.filter == null ? "Бүгд" : v.filter.label();
        m.set(51, bar(Material.HOPPER, "Шүүлтүүр: " + cat, TURQ, List.of("Зүүн товш: дараагийн салбар", "Баруун товш: өмнөх", "Shift: шүүлтүүр арилгах")),
                (pl, c) -> filter(pl, c));
        m.set(52, info(p, v, tree, a, ctx, avail), (pl, c) -> {
            if (menus != null) menus.skills(pl);
        });
        m.set(53, bar(Material.ENDER_EYE, "Дахин тохируулах · Бүтэц", PURPLE, List.of("Оноог буцаах (бүгд / салбар)", "Бүтэц хадгалах, ачаалах")),
                (pl, c) -> respecMenu(pl));
    }

    private ItemStack info(Player p, View v, SkillTree tree, SkillAllocation a, SkillEngine.Context ctx, int avail) {
        SkillBuild b = a.build();
        List<Component> l = new ArrayList<>();
        l.add(Menu.kv("Анги:", tree.clazz().displayName(), GOLD));
        l.add(Menu.kv("Түвшин:", String.valueOf(ctx.level()), NamedTextColor.WHITE));
        l.add(Menu.kv("Оноо:", avail + " чөлөөтэй · " + a.spent() + " зарцуулсан · " + st().total(p) + " нийт", avail > 0 ? GREEN : NamedTextColor.WHITE));
        Ultimate ult = b.ultimate();
        l.add(Menu.kv("Дуулал (F):", ult == null ? "—" : ult.displayName(), ult == null ? GRAY : GOLD));
        l.add(Menu.kv("Түлхүүр чадвар:", b.keystones().isEmpty() ? "—" : b.keystones().iterator().next().displayName(), b.keystones().isEmpty() ? GRAY : PURPLE));
        var pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        String build = pr == null || pr.skillState().activeBuild().isEmpty() ? "—" : pr.skillState().activeBuild();
        l.add(Menu.kv("Бүтэц:", build, NamedTextColor.WHITE));
        if (!v.selected.isEmpty()) l.add(Menu.kv("Сонгосон:", tree.find(v.selected).map(SkillNode::name).orElse("—"), TURQ));
        l.add(Component.empty());
        l.add(Menu.line("Оноо: түвшин, түүх, нутаг судлал"));
        l.add(Menu.line("Товш: ангийн шившлэгүүд"));
        return Menu.item(Material.EXPERIENCE_BOTTLE, Menu.title("Чадварын оноо: " + avail, avail > 0 ? GREEN : GOLD), l);
    }

    private void pan(Player p, int dx, int dy) {
        View v = views.get(p.getUniqueId());
        if (v == null) return;
        v.ox += dx;
        v.oy += dy;
        redraw(p);
    }

    private void zoom(Player p) {
        View v = views.get(p.getUniqueId());
        if (v == null) return;
        if (v.zoom == 1) {
            // to the overview: remember where we were, show all columns around the same rows
            v.detailOx = v.ox;
            v.detailOy = v.oy;
            int cy = v.oy + 1;
            v.zoom = 2;
            v.ox = 0;
            v.oy = cy - 2;
        } else {
            // back to the detailed view exactly where it was left
            v.zoom = 1;
            v.ox = v.detailOx;
            v.oy = v.detailOy;
        }
        redraw(p);
    }

    private static final SkillCategory[] FILTERS = {null, SkillCategory.DEFENSE, SkillCategory.SPELL, SkillCategory.OFFENSE,
            SkillCategory.UTILITY, SkillCategory.KEYSTONE, SkillCategory.ULTIMATE};

    private void filter(Player p, ClickType c) {
        View v = views.get(p.getUniqueId());
        if (v == null) return;
        if (c.isShiftClick()) {
            v.filter = null;
        } else {
            int i = java.util.Arrays.asList(FILTERS).indexOf(v.filter);
            int next = (i + (c.isRightClick() ? FILTERS.length - 1 : 1)) % FILTERS.length;
            v.filter = FILTERS[next];
        }
        redraw(p);
    }

    // ------------------------------------------------------------------ search

    private void promptSearch(Player p) {
        searching.add(p.getUniqueId());
        p.closeInventory();
        p.sendMessage(Messages.accent("🔍 Хайх нэр эсвэл нөлөөгөө чатад бичнэ үү (жишээ нь: «хурд», «амь», «шатаалт»). Цуцлах: «болих»"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!searching.remove(p.getUniqueId())) return;
        e.setCancelled(true);
        String q = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (q.equalsIgnoreCase("болих") || q.isEmpty()) {
                open(p);
                return;
            }
            search(p, q);
        });
    }

    /** Highlights the matching nodes on the map and centres the view on the first one. */
    public void search(Player p, String q) {
        View v = views.computeIfAbsent(p.getUniqueId(), k -> new View());
        List<SkillNode> hits = st().search(p, q);
        v.query = q;
        v.found = new HashSet<>();
        for (SkillNode n : hits) v.found.add(n.id());
        if (hits.isEmpty()) {
            p.sendMessage(Messages.error("«" + q + "» гэсэн нод олдсонгүй."));
            open(p);
            return;
        }
        p.sendMessage(Messages.success(hits.size() + " нод олдлоо: " + String.join(", ", hits.stream().limit(8).map(SkillNode::name).toList())
                + (hits.size() > 8 ? " …" : "")));
        open(p, hits.get(0).id());
    }

    private void clearSearch(Player p) {
        View v = views.get(p.getUniqueId());
        if (v == null) return;
        v.query = "";
        v.found = new HashSet<>();
        redraw(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        views.remove(e.getPlayer().getUniqueId());
        searching.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // the cached menu object is only reused while the window is open
        if (e.getInventory().getHolder() instanceof Menu m) {
            View v = views.get(e.getPlayer().getUniqueId());
            if (v != null && v.menu == m && !searching.contains(e.getPlayer().getUniqueId())) v.menu = null;
        }
    }

    // ------------------------------------------------------------------ respec + builds

    public void respecMenu(Player p) {
        SkillTree tree = st().tree(p);
        SkillAllocation a = st().allocation(p);
        if (tree == null || a == null) return;
        Menu m = new Menu(4, "Дахин тохируулах · Бүтэц", null);
        int orbs = OrbOfOblivion.count(p);
        long cd = st().respecCooldownMs();
        var pr = services.profiles().cached(p.getUniqueId()).orElseThrow();
        int wait = SkillEngine.respecWaitSeconds(pr, System.currentTimeMillis(), cd);
        List<String> common = new ArrayList<>();
        common.add(wait > 0 ? "Хүлээх хугацаа: " + wait + " сек" : "Хүлээлт алга");
        common.add("Мартагдлын Бөмбөрцөг: " + orbs + " ш (үнэгүй, хүлээлтгүй)");
        int all = a.spent();
        List<String> allLore = new ArrayList<>(List.of("Буцаах: " + all + " оноо", "Үнэ: " + st().respecCost(p, all) + " ₮"));
        allLore.addAll(common);
        m.set(10, bar(Material.TNT, "Бүх модыг буцаах", RED, allLore), (pl, c) -> confirmReset(pl, null));
        SkillCategory[] branches = {SkillCategory.DEFENSE, SkillCategory.SPELL, SkillCategory.OFFENSE};
        int[] slots = {12, 13, 14};
        for (int i = 0; i < branches.length; i++) {
            SkillCategory cat = branches[i];
            int refund = a.spent() - a.withoutCategory(cat).spent();
            List<String> lore = new ArrayList<>(List.of("Буцаах: " + refund + " оноо", "Үнэ: " + st().respecCost(p, refund) + " ₮"));
            lore.addAll(common);
            m.set(slots[i], bar(Material.REDSTONE, cat.label() + " салбарыг буцаах", ORANGE, lore), (pl, c) -> confirmReset(pl, cat));
        }
        m.set(16, bar(Material.HEART_OF_THE_SEA, "Мартагдлын Бөмбөрцөг: " + orbs, PURPLE,
                List.of("Бүх модыг үнэгүй буцаана", "Бөмбөрцөг: 1 алмаз + 4 эндер сувд", "Эсвэл администратороос")), (pl, c) -> {
            if (OrbOfOblivion.count(pl) > 0) confirmReset(pl, null);
            else pl.sendMessage(Messages.error("Танд Мартагдлын Бөмбөрцөг алга. Хийх: ✦ ердийн ширээн дээр ❖ 4 эндер сувд + 1 алмаз."));
        });
        // saved builds
        int slotsMax = st().buildSlots();
        List<String> names = pr.skillState().builds().keySet().stream().toList();
        for (int i = 0; i < Math.min(9, slotsMax); i++) {
            int slot = 18 + i;
            if (i < names.size()) {
                String name = names.get(i);
                SkillAllocation ba = SkillAllocation.decode(tree, pr.skillState().builds().get(name)).allocation();
                boolean active = name.equals(pr.skillState().activeBuild());
                ItemStack it = bar(Material.BOOK, "Бүтэц: " + name, active ? GOLD : TURQ, List.of(ba.spent() + " оноо · " + (ba.unlockedNodes().size() - 1) + " нод",
                        "Зүүн товш: ачаалах", "Shift+зүүн: одоогийн модоор дарж хадгалах", "Shift+баруун: устгах"));
                if (active) Menu.glow(it);
                m.set(slot, it, (pl, c) -> {
                    if (c.isShiftClick() && c.isRightClick()) say(pl, SkillText.build(st().deleteBuild(pl, name)));
                    else if (c.isShiftClick()) say(pl, SkillText.build(st().saveBuild(pl, name)));
                    else say(pl, SkillText.build(st().loadBuild(pl, name)));
                    respecMenu(pl);
                });
            } else {
                String auto = "build" + (i + 1);
                m.set(slot, bar(Material.GRAY_DYE, "Хоосон слот", GRAY, List.of("Товшиж одоогийн модыг «" + auto + "» нэрээр хадгална", "Эсвэл: /skills build save <нэр>")), (pl, c) -> {
                    say(pl, SkillText.build(st().saveBuild(pl, auto)));
                    respecMenu(pl);
                });
            }
        }
        m.set(31, bar(Material.COMPASS, "← Газрын зураг руу буцах", TURQ, List.of("Чадварын газрын зураг")), (pl, c) -> open(pl));
        m.open(p);
    }

    private void say(Player p, String text) {
        p.sendMessage(Messages.info(text));
    }

    /** The confirmation screen for every refund: shows what is refunded and what it costs. */
    public void confirmReset(Player p, SkillCategory category) {
        SkillAllocation a = st().allocation(p);
        if (a == null) return;
        int refund = category == null ? a.spent() : a.spent() - a.withoutCategory(category).spent();
        if (refund <= 0) {
            say(p, "Буцаах оноо алга.");
            return;
        }
        boolean orb = category == null && OrbOfOblivion.count(p) > 0;
        long coins = st().respecCost(p, refund);
        Menu m = new Menu(3, "Баталгаажуулах", null);
        List<String> lines = new ArrayList<>();
        lines.add((category == null ? "Бүх мод" : category.label() + " салбар") + ": " + refund + " оноо буцна");
        lines.add(orb ? "Мартагдлын Бөмбөрцөг 1 ширхэг зарцуулна (үнэгүй)" : "Үнэ: " + coins + " ₮");
        lines.add("Түвшин, EXP, мөнгө, хийсэн бүтцүүд хэвээр үлдэнэ");
        m.set(11, bar(Material.LIME_CONCRETE, "✔ Баталгаажуулах", GREEN, lines), (pl, c) -> {
            boolean used = false;
            if (orb) used = OrbOfOblivion.consumeOne(pl);
            SkillTreeService.ResetResult r = st().reset(pl, category, used);
            if (used && r.outcome() != SkillTreeService.ResetOutcome.OK) pl.getInventory().addItem(OrbOfOblivion.create(1));
            say(pl, SkillText.reset(r));
            pl.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> open(pl));
        });
        if (category == null && !orb && coins > 0 && OrbOfOblivion.count(p) == 0) {
            m.set(13, bar(Material.GRAY_DYE, "Бөмбөрцөггүй", GRAY, List.of("Бөмбөрцөгөөр үнэгүй буцаана")), null);
        }
        m.set(15, bar(Material.RED_CONCRETE, "✖ Болих", RED, List.of("Юу ч өөрчлөхгүй")), (pl, c) -> respecMenu(pl));
        m.open(p);
    }

    /** Right-clicking an orb opens the confirmation for a free full reset. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onOrbUse(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (!OrbOfOblivion.is(e.getPlayer().getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        if (st().tree(e.getPlayer()) == null) {
            e.getPlayer().sendMessage(Messages.error("Эхлээд ангиа сонгоно уу."));
            return;
        }
        confirmReset(e.getPlayer(), null);
    }
}
