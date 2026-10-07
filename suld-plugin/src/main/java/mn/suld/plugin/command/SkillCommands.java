package mn.suld.plugin.command;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.tree.EffectText;
import mn.suld.api.skill.tree.SkillAllocation;
import mn.suld.api.skill.tree.SkillBuild;
import mn.suld.api.skill.tree.SkillCategory;
import mn.suld.api.skill.tree.SkillEngine;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.SkillTree;
import mn.suld.api.skill.tree.SkillTreeLoader;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.gui.Menus;
import mn.suld.plugin.gui.SkillMapMenu;
import mn.suld.plugin.skill.OrbOfOblivion;
import mn.suld.plugin.skill.SkillText;
import mn.suld.plugin.skill.SkillTreeService;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /skills, /skill and /skillsadmin: every command checks permission, validates its input and is safe from the console. */
public final class SkillCommands {

    private final SuldServices services;
    private final SkillMapMenu map;
    private final Menus menus;
    private final mn.suld.plugin.skill.qa.SkillQa qa;

    public SkillCommands(SuldServices services, SkillMapMenu map, Menus menus, mn.suld.plugin.skill.qa.SkillQa qa) {
        this.services = services;
        this.map = map;
        this.menus = menus;
        this.qa = qa;
    }

    private SkillTreeService st() {
        return services.skillTree();
    }

    private static final List<String> CATEGORY_WORDS = List.of("all", "defense", "spell", "offense");

    private static SkillCategory category(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "defense", "def", "l", "хамгаалалт" -> SkillCategory.DEFENSE;
            case "spell", "spells", "m", "шид" -> SkillCategory.SPELL;
            case "offense", "off", "r", "довтолгоо" -> SkillCategory.OFFENSE;
            default -> null;
        };
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.WHITE, TextDecoration.BOLD);
    }

    // ================================================================== /skills

    public TabExecutor skills() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (!s.hasPermission("suld.skills")) {
                    s.sendMessage(Messages.error("Эрх алга."));
                    return true;
                }
                if (!(s instanceof Player p)) {
                    s.sendMessage(Messages.error("Энэ тушаалыг тоглогч л ашиглана. (Админ: /skillsadmin)"));
                    return true;
                }
                if (services.profiles().cached(p.getUniqueId()).isEmpty()) {
                    s.sendMessage(Messages.error("Профайл ачаалагдаагүй байна."));
                    return true;
                }
                String sub = a.length == 0 ? "tree" : a[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "tree", "map", "gazar" -> map.open(p);
                    case "chest", "grid" -> map.open(p, null); // the inventory map (no camera change)
                    case "spells", "shid" -> menus.skills(p);
                    case "info" -> info(p);
                    case "reset" -> reset(p, a);
                    case "build", "builds" -> build(p, a);
                    case "search", "haih" -> {
                        if (a.length < 2) s.sendMessage(Messages.error("Хэрэглээ: /skills search <үг>"));
                        else if (st().tree(p) == null) s.sendMessage(Messages.error("Эхлээд ангиа сонго."));
                        else map.search(p, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
                    }
                    default -> s.sendMessage(Messages.error("Хэрэглээ: /skills [tree|info|spells|reset [салбар]|build save|load|delete|list <нэр>|search <үг>]"));
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (!(s instanceof Player p)) return List.of();
                if (a.length == 1) return filter(List.of("tree", "info", "spells", "reset", "build", "search"), a[0]);
                if (a.length == 2 && a[0].equalsIgnoreCase("reset")) return filter(CATEGORY_WORDS, a[1]);
                if (a.length == 2 && a[0].equalsIgnoreCase("build")) return filter(List.of("save", "load", "delete", "list"), a[1]);
                if (a.length == 3 && a[0].equalsIgnoreCase("build") && !a[1].equalsIgnoreCase("save") && !a[1].equalsIgnoreCase("list")) {
                    return filter(services.profiles().cached(p.getUniqueId()).map(SkillEngine::buildNames).orElse(List.of()), a[2]);
                }
                return List.of();
            }
        };
    }

    private void info(Player p) {
        SkillTreeService st = st();
        SkillTree tree = st.tree(p);
        if (tree == null) {
            p.sendMessage(Messages.error("Эхлээд ангиа сонго: /class"));
            return;
        }
        SkillAllocation a = st.allocation(p);
        SkillBuild b = a.build();
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElseThrow();
        p.sendMessage(Component.text("━━ Чадварын мод · " + tree.clazz().displayName() + " ━━", TextColor.fromHexString("#F2B632"), TextDecoration.BOLD));
        p.sendMessage(line("Түвшин " + st.context(p).level() + " · Оноо: " + st.available(p) + " чөлөөтэй / " + a.spent() + " зарцуулсан / " + st.total(p) + " нийт"));
        p.sendMessage(line("Нээсэн нод: " + (a.unlockedNodes().size() - 1) + " / " + (tree.nodes().size() - 1)));
        p.sendMessage(line("Дуулал (F): " + (b.ultimate() == null ? "—" : b.ultimate().displayName() + (st.ultimateCooldownSeconds(p) > 0 ? " (" + st.ultimateCooldownSeconds(p) + " сек)" : " (бэлэн)"))));
        p.sendMessage(line("Түлхүүр чадвар: " + (b.keystones().isEmpty() ? "—" : b.keystones().iterator().next().displayName())));
        p.sendMessage(line("Бүтэц: " + (pr.skillState().activeBuild().isEmpty() ? "—" : pr.skillState().activeBuild())
                + " · хадгалсан " + pr.skillState().builds().size() + "/" + st.buildSlots()));
        p.sendMessage(line("Газрын зураг: /skills · Хайх: /skills search <үг> · Буцаах: /skills reset"));
    }

    private void reset(Player p, String[] a) {
        if (st().tree(p) == null) {
            p.sendMessage(Messages.error("Эхлээд ангиа сонго: /class"));
            return;
        }
        SkillCategory cat = null;
        if (a.length >= 2 && !a[1].equalsIgnoreCase("all")) {
            cat = category(a[1]);
            if (cat == null) {
                p.sendMessage(Messages.error("Салбар: all, defense, spell, offense"));
                return;
            }
        }
        map.confirmReset(p, cat);
    }

    private void build(Player p, String[] a) {
        if (st().tree(p) == null) {
            p.sendMessage(Messages.error("Эхлээд ангиа сонго: /class"));
            return;
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElseThrow();
        String sub = a.length < 2 ? "list" : a[1].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            p.sendMessage(Messages.info("Бүтцүүд (" + pr.skillState().builds().size() + "/" + st().buildSlots() + "): "
                    + (pr.skillState().builds().isEmpty() ? "—" : String.join(", ", pr.skillState().builds().keySet()))));
            return;
        }
        if (a.length < 3) {
            p.sendMessage(Messages.error("Хэрэглээ: /skills build save|load|delete <нэр>"));
            return;
        }
        String name = a[2];
        SkillEngine.Result r = switch (sub) {
            case "save" -> st().saveBuild(p, name);
            case "load" -> st().loadBuild(p, name);
            case "delete", "del" -> st().deleteBuild(p, name);
            default -> null;
        };
        if (r == null) p.sendMessage(Messages.error("Хэрэглээ: /skills build save|load|delete|list <нэр>"));
        else p.sendMessage(r.ok() ? Messages.success(SkillText.build(r)) : Messages.error(SkillText.build(r)));
    }

    // ================================================================== /skill <node>

    public TabExecutor skill() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (!s.hasPermission("suld.skills")) {
                    s.sendMessage(Messages.error("Эрх алга."));
                    return true;
                }
                if (!(s instanceof Player p)) {
                    s.sendMessage(Messages.error("Энэ тушаалыг тоглогч л ашиглана."));
                    return true;
                }
                SkillTree tree = st().tree(p);
                if (tree == null) {
                    s.sendMessage(Messages.error("Эхлээд ангиа сонго: /class"));
                    return true;
                }
                if (a.length == 0) {
                    s.sendMessage(Messages.error("Хэрэглээ: /skill <нод> [unlock|refund|info]"));
                    return true;
                }
                String action = "info";
                int end = a.length;
                String last = a[a.length - 1].toLowerCase(Locale.ROOT);
                if (a.length > 1 && List.of("unlock", "refund", "info", "open").contains(last)) {
                    action = last;
                    end = a.length - 1;
                }
                String query = String.join(" ", java.util.Arrays.copyOfRange(a, 0, end));
                SkillNode n = resolve(p, tree, query);
                if (n == null) {
                    s.sendMessage(Messages.error("«" + query + "» нод олдсонгүй. /skills search " + query));
                    return true;
                }
                switch (action) {
                    case "unlock" -> {
                        var res = st().unlock(p, n);
                        s.sendMessage(res.ok() ? Messages.success("✔ «" + n.name() + "» нээгдлээ") : Messages.error(SkillText.why(res)));
                    }
                    case "refund" -> {
                        var res = st().refund(p, n);
                        s.sendMessage(res.ok() ? Messages.success("↩ «" + n.name() + "» буцаагдлаа") : Messages.error(SkillText.why(res)));
                    }
                    case "open" -> map.open(p, n.id());
                    default -> detail(p, tree, n);
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (!(s instanceof Player p)) return List.of();
                SkillTree tree = st().tree(p);
                if (tree == null) return List.of();
                if (a.length == 1) {
                    SkillAllocation al = st().allocation(p);
                    List<String> ids = new ArrayList<>();
                    for (SkillNode n : tree.nodes()) if (!n.root() && al.visible(n)) ids.add(n.id());
                    return filter(ids, a[0]);
                }
                if (a.length == 2) return filter(List.of("info", "unlock", "refund", "open"), a[1]);
                return List.of();
            }
        };
    }

    /** A node by id, exact name, or a unique name fragment among the nodes the player can see. */
    private SkillNode resolve(Player p, SkillTree tree, String query) {
        SkillAllocation al = st().allocation(p);
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (SkillNode n : tree.nodes()) {
            if (!n.root() && al.visible(n) && n.id().equalsIgnoreCase(q)) return n;
        }
        for (SkillNode n : tree.nodes()) {
            if (!n.root() && al.visible(n) && n.name().toLowerCase(Locale.ROOT).equals(q)) return n;
        }
        List<SkillNode> part = new ArrayList<>();
        for (SkillNode n : tree.nodes()) {
            if (!n.root() && al.visible(n) && n.name().toLowerCase(Locale.ROOT).contains(q)) part.add(n);
        }
        return part.size() == 1 ? part.get(0) : null;
    }

    private void detail(Player p, SkillTree tree, SkillNode n) {
        SkillAllocation al = st().allocation(p);
        var state = al.state(n, st().context(p).level(), st().available(p));
        p.sendMessage(Component.text("━━ " + n.name() + " (" + n.category().label() + ") ━━", TextColor.fromHexString("#F2B632"), TextDecoration.BOLD));
        for (EffectText.Line l : EffectText.lines(n)) p.sendMessage(line("  " + l.text()));
        p.sendMessage(line("  Үнэ: " + n.cost() + " оноо" + (n.maxRank() > 1 ? " × " + n.maxRank() + " түвшин" : "") + " · Түвшин " + n.effectiveLevel()
                + " · Одоо: " + al.rank(n) + "/" + n.maxRank()));
        for (SkillNode.Req r : n.requires()) p.sendMessage(line("  Шаардлага: " + tree.node(r.node()).name()));
        List<String> rivals = new ArrayList<>();
        for (int ex : tree.exclusives(n.index())) rivals.add(tree.node(ex).name());
        if (!rivals.isEmpty()) p.sendMessage(line("  ✖ Хамт авч болохгүй: " + String.join(", ", rivals)));
        p.sendMessage(line("  Төлөв: " + state));
        p.sendMessage(Component.text("  [нээх]", NamedTextColor.GREEN, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/skill " + n.id() + " unlock"))
                .append(Component.text("  [буцаах]", NamedTextColor.GOLD, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/skill " + n.id() + " refund")))
                .append(Component.text("  [газраас харах]", NamedTextColor.AQUA, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/skill " + n.id() + " open"))));
    }

    // ================================================================== /skillsadmin

    public TabExecutor admin() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (!s.hasPermission("suld.admin.skills")) {
                    s.sendMessage(Messages.error("Эрх алга."));
                    return true;
                }
                if (a.length == 0) {
                    s.sendMessage(Messages.info("/skillsadmin inspect <тоглогч> | grantpoints <тоглогч> <тоо> | unlock <тоглогч> <нод> | reset <тоглогч> | orb <тоглогч> [тоо] | reload | validate"));
                    return true;
                }
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "reload" -> {
                        List<SkillTreeLoader.Issue> issues = st().reload();
                        s.sendMessage(issues.isEmpty() ? Messages.success("Чадварын өгөгдөл дахин ачаалагдлаа.")
                                : Messages.error(issues.size() + " алдаа — алдаатай анги өмнөх модоо хадгаллаа. /skillsadmin validate"));
                    }
                    case "validate" -> {
                        List<SkillTreeLoader.Issue> issues = st().validate();
                        if (issues.isEmpty()) s.sendMessage(Messages.success("Бүх өгөгдлийн файл зөв (" + st().dataDir() + ")"));
                        else {
                            s.sendMessage(Messages.error(issues.size() + " алдаа:"));
                            for (SkillTreeLoader.Issue i : issues.subList(0, Math.min(15, issues.size()))) s.sendMessage(line("  " + i));
                            if (issues.size() > 15) s.sendMessage(line("  … бусад алдаа консолд (сервер лог)"));
                        }
                    }
                    case "inspect", "grantpoints", "unlock", "reset", "orb", "fire", "qa", "qakit" -> player(s, a);
                    default -> s.sendMessage(Messages.error("Үл мэдэгдэх дэд тушаал."));
                }
                return true;
            }

            private void player(CommandSender s, String[] a) {
                if (a.length < 2) {
                    s.sendMessage(Messages.error("Тоглогчийн нэр хэрэгтэй."));
                    return;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    s.sendMessage(Messages.error("«" + a[1] + "» онлайн биш (профайл зөвхөн онлайн тоглогчид засагдана)."));
                    return;
                }
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "inspect" -> inspect(s, t);
                    case "grantpoints" -> {
                        Integer n = a.length < 3 ? null : parseInt(a[2]);
                        if (n == null || Math.abs(n) > 1000) {
                            s.sendMessage(Messages.error("Хэрэглээ: /skillsadmin grantpoints <тоглогч> <−1000..1000>"));
                            return;
                        }
                        st().adminGrant(t, n);
                        s.sendMessage(Messages.success(t.getName() + ": " + (n >= 0 ? "+" : "") + n + " оноо олголоо (чөлөөтэй " + st().available(t) + ")"));
                        t.sendMessage(Messages.info("Администратор танд " + n + " чадварын оноо олголоо."));
                    }
                    case "unlock" -> {
                        SkillTree tree = st().tree(t);
                        if (tree == null || a.length < 3) {
                            s.sendMessage(Messages.error(tree == null ? t.getName() + " анги сонгоогүй." : "Хэрэглээ: /skillsadmin unlock <тоглогч> <нод>"));
                            return;
                        }
                        SkillNode n = tree.find(a[2].toLowerCase(Locale.ROOT)).orElse(null);
                        if (n == null) {
                            s.sendMessage(Messages.error("Нод олдсонгүй: " + a[2]));
                            return;
                        }
                        var res = st().adminUnlock(t, n);
                        s.sendMessage(res.ok() ? Messages.success(t.getName() + ": «" + n.name() + "» нээгдлээ") : Messages.error(SkillText.why(res)));
                    }
                    case "fire" -> {
                        mn.suld.api.skill.tree.TriggerEvent ev = null;
                        if (a.length >= 3) {
                            try {
                                ev = mn.suld.api.skill.tree.TriggerEvent.valueOf(a[2].toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException ignored) {
                                // reported below
                            }
                        }
                        if (ev == null) {
                            s.sendMessage(Messages.error("Хэрэглээ: /skillsadmin fire <тоглогч> " + java.util.Arrays.toString(mn.suld.api.skill.tree.TriggerEvent.values())));
                            return;
                        }
                        int n = st().adminFire(t, ev);
                        s.sendMessage(Messages.success(t.getName() + ": " + ev + " — " + n + " идэвхгүй шид шалгагдлаа"));
                    }
                    case "qakit" -> {
                        String scenario = a.length < 3 ? "" : a[2].toLowerCase(Locale.ROOT);
                        String res = scenario.isEmpty() ? null : st().qaKit(t, scenario);
                        s.sendMessage(res == null ? Messages.error("Хэрэглээ: /skillsadmin qakit <тоглогч> fresh|rich|states|states0") : Messages.success(t.getName() + " → " + res));
                    }
                    case "qa" -> {
                        java.util.Set<String> suites = new java.util.HashSet<>();
                        for (int i = 2; i < a.length; i++) suites.add(a[i].toLowerCase(Locale.ROOT));
                        if (suites.isEmpty()) suites.add("all");
                        suites.removeIf(x -> !x.equals("all") && !x.startsWith("only=") && !mn.suld.plugin.skill.qa.SkillQa.SUITES.contains(x));
                        if (suites.stream().allMatch(x -> x.startsWith("only="))) suites.add("all");
                        if (suites.isEmpty()) {
                            s.sendMessage(Messages.error("Suites: all " + String.join(" ", mn.suld.plugin.skill.qa.SkillQa.SUITES)));
                            return;
                        }
                        s.sendMessage(Messages.info("Combat verification suite started for " + t.getName() + ": " + suites + " — the player is moved to a sky arena and restored afterwards. Report: plugins/SULD/qa/"));
                        qa.run(s, t, suites);
                    }
                    case "reset" -> s.sendMessage(st().adminReset(t) ? Messages.success(t.getName() + ": мод буцаагдлаа") : Messages.info(t.getName() + ": буцаах оноо алга"));
                    case "orb" -> {
                        int n = a.length < 3 ? 1 : (parseInt(a[2]) == null ? 1 : Math.max(1, Math.min(16, parseInt(a[2]))));
                        t.getInventory().addItem(OrbOfOblivion.create(n)).values().forEach(it -> t.getWorld().dropItemNaturally(t.getLocation(), it));
                        s.sendMessage(Messages.success(t.getName() + ": " + n + " Мартагдлын Бөмбөрцөг олголоо"));
                    }
                    default -> {
                    }
                }
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (!s.hasPermission("suld.admin.skills")) return List.of();
                if (a.length == 1) return filter(List.of("inspect", "grantpoints", "unlock", "reset", "orb", "fire", "qa", "qakit", "reload", "validate"), a[0]);
                if (a.length == 2 && !List.of("reload", "validate").contains(a[0].toLowerCase(Locale.ROOT))) {
                    return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), a[1]);
                }
                if (a.length == 3 && a[0].equalsIgnoreCase("qakit")) return filter(List.of("fresh", "rich", "states", "states0"), a[2]);
                if (a.length >= 3 && a[0].equalsIgnoreCase("qa")) {
                    java.util.List<String> all = new java.util.ArrayList<>(mn.suld.plugin.skill.qa.SkillQa.SUITES);
                    all.add(0, "all");
                    return filter(all, a[a.length - 1]);
                }
                if (a.length == 3 && a[0].equalsIgnoreCase("fire")) {
                    return filter(java.util.Arrays.stream(mn.suld.api.skill.tree.TriggerEvent.values()).map(Enum::name).toList(), a[2]);
                }
                if (a.length == 3 && a[0].equalsIgnoreCase("unlock")) {
                    Player t = Bukkit.getPlayerExact(a[1]);
                    SkillTree tree = t == null ? null : st().tree(t);
                    return tree == null ? List.of() : filter(tree.nodes().stream().filter(n -> !n.root()).map(SkillNode::id).toList(), a[2]);
                }
                return List.of();
            }
        };
    }

    private void inspect(CommandSender s, Player t) {
        SkillTree tree = st().tree(t);
        if (tree == null) {
            s.sendMessage(Messages.info(t.getName() + " (" + t.getUniqueId() + "): анги сонгоогүй"));
            return;
        }
        SkillAllocation a = st().allocation(t);
        PlayerProfile pr = services.profiles().cached(t.getUniqueId()).orElseThrow();
        s.sendMessage(Messages.info(t.getName() + " · " + t.getUniqueId() + " · " + tree.clazz().displayName() + " · түвшин " + st().context(t).level()));
        s.sendMessage(Messages.info("оноо: " + st().available(t) + " чөлөөтэй, " + a.spent() + " зарцуулсан, " + st().total(t) + " нийт (олгосон " + pr.skillState().granted() + ")"));
        s.sendMessage(Messages.info("нод: " + (a.encode().isEmpty() ? "—" : a.encode())));
        s.sendMessage(Messages.info("бүтэц: " + String.join(", ", pr.skillState().builds().keySet()) + " · идэвхтэй: " + pr.skillState().activeBuild()
                + " · дахин тохируулсан: " + pr.skillState().respecs().size() + " удаа"));
        SkillBuild b = a.build();
        s.sendMessage(Messages.info("дуулал: " + (b.ultimate() == null ? "—" : b.ultimate().name()) + " · түлхүүр: " + b.keystones()
                + " · консистент: " + a.consistent()));
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
