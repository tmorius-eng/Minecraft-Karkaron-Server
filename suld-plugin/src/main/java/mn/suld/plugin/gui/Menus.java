package mn.suld.plugin.gui;

import mn.suld.api.item.ItemInstance;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.CosmeticCatalog;
import mn.suld.api.style.LevelRewards;
import mn.suld.api.style.PlayerStyle;
import mn.suld.api.style.Rank;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.style.StyleService;
import mn.suld.plugin.ui.Glyphs;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.StyleFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import mn.suld.api.clazz.PlayerClass;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every SÜLD menu: main menu (hotbar item, {@code /menu}), welcome, tutorial/help, profile, rank ladder
 * ({@code /rankup}), level rewards ({@code /lvlup}), cosmetics ({@code /cosmetics}), shop ({@code /shop}) and the
 * credit store ({@code /buy}). All text is bold white with coloured accents (readable on any background).
 */
public final class Menus {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor SKY = TextColor.fromHexString("#9FF3FF");
    private static final TextColor GREEN = TextColor.fromHexString("#7CE07C");
    private static final TextColor RED = TextColor.fromHexString("#FF6B6B");
    private static final TextColor PURPLE = TextColor.fromHexString("#C08BFF");

    private final Plugin plugin;
    private final SuldServices services;

    public Menus(Plugin plugin, SuldServices services) {
        this.questMenu = new QuestMenu(services);
        this.plugin = plugin;
        this.services = services;
        this.khuraldaiMenu = new KhuraldaiMenu(services, this);
        this.chronicleMenu = new ChronicleMenu(plugin, services, this);
    }

    private final KhuraldaiMenu khuraldaiMenu;
    private final ChronicleMenu chronicleMenu;

    /** Их Хуралдай: the week's campaign and the ballot (docs/KHURALDAI.md). */
    public void khuraldai(Player p) {
        khuraldaiMenu.open(p);
    }

    /** Баатрын Хроник, all kinds or one. */
    public void chronicle(Player p, mn.suld.api.chronicle.ChronicleKind kind) {
        chronicleMenu.open(p, kind);
    }

    /** «Миний түүх»: the player's own chronicle lines. */
    public void chronicleMine(Player p) {
        chronicleMenu.mine(p);
    }

    private StyleService styles() {
        return services.styles();
    }

    private static Component b(String s) {
        return Menu.line(s);
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }

    // ================================================================== main / welcome

    /** The tabs of every «Командын Өргөө» window (docs/UI_HUB.md), in the mock-up's order. */
    private List<HubMenu.Tab> hubTabs() {
        return List.of(
                new HubMenu.Tab("Тоглогч", "Таны баатар: түвшин, цол, шагнал, овог.", this::profile),
                new HubMenu.Tab("Даалгавар", "Эрэлийн ном: түүхийн бүлгүүд, газрууд.", this::quests),
                new HubMenu.Tab("Ур чадвар", "Чадварын мод, build, шившлэг.", this::skillsOrMap),
                new HubMenu.Tab("Дэлгүүр", "Хангамж, олз зарах, гоёл.", this::shop),
                new HubMenu.Tab("Rankup", "Ард → Хаан: цол ахих нөхцөл.", this::rankup),
                new HubMenu.Tab("Аялал", "Өртөө, түүхэн газрууд, овоо.", this::travel),
                new HubMenu.Tab("Тохиргоо", "Самбар, зөвлөмж, дуу, чат, урилга.", this::settings),
                new HubMenu.Tab("Тусламж", "Заавар, командууд, дүрэм.", this::help));
    }

    private void skillsOrMap(Player p) {
        if (skillMap != null) skillMap.open(p);
        else skills(p);
    }

    /** The «Командын Өргөө» command hub: {@code /menu}, the hotbar item, the welcome screen. */
    public void main(Player p) {
        List<HubMenu.Card> cards = List.of(
                HubMenu.card("/menu", "Үндсэн цэсийг нээнэ (Сүлд Цэс).", (pl, c) -> classic(pl)),
                HubMenu.card("/shop", "Дэлгүүрийн цэс.", (pl, c) -> shop(pl)),
                HubMenu.card("/quest", "Даалгаврын цэс.", (pl, c) -> quests(pl)),
                HubMenu.card("/rankup", "Ранк ахих нөхцөл.", (pl, c) -> rankup(pl)),
                HubMenu.card("/class", "Ангийн Академи: анги харах, солих.", (pl, c) -> academy(pl)),
                HubMenu.card("/travel", "Аялал, бүсүүд рүү.", (pl, c) -> travel(pl)),
                HubMenu.card("/skills", "Ур чадварын цэс.", (pl, c) -> skillsOrMap(pl)),
                HubMenu.card("/help", "Бүх командын жагсаалт.", (pl, c) -> help(pl)));
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_HUB, -1, null, cards, null, null, null, null), hubTabs());
    }

    /** The painted «Сүлд Цэс» (six cards): the hub's «/menu» card and the older entry points. */
    public void classic(Player p) {
        // the title is painted into the background (resource pack gui/main.png): no text on top of it
        Menu m = new Menu(6, net.kyori.adventure.text.Component.empty(), Glyphs.GUI_MAIN, 0);
        m.area(Menu.card(0), Menu.title("Дүр", SKY), Menu.lore(SKY, List.of(b("Түвшин, ур чадвар, шагнал, статистик.")),
                List.of(), "Дарж нээх"), (pl, c) -> profile(pl));
        m.area(Menu.card(1), Menu.title("Анги", RED), Menu.lore(RED, List.of(b("Таван баатрын зам — ангиа сонго.")),
                List.of(), "Дарж нээх"), (pl, c) -> pl.performCommand("class"));
        m.area(Menu.card(2), Menu.title("Эрэл ба Агуй", GOLD), Menu.lore(GOLD, List.of(b("Идэвхтэй эрэл, агуйн аян.")),
                List.of(), "Дарж нээх"), (pl, c) -> quests(pl));
        m.area(Menu.card(3), Menu.title("Цол", PURPLE), Menu.lore(PURPLE, List.of(b("Ард → Хаан: цолоо ахиул.")),
                List.of(), "Дарж нээх"), (pl, c) -> rankup(pl));
        m.area(Menu.card(4), Menu.title("Зах · Дэлгүүр", GREEN), Menu.lore(GREEN, List.of(b("Хангамж, олз зарах, гоёл.")),
                List.of(), "Дарж нээх"), (pl, c) -> shop(pl));
        m.area(Menu.card(5), Menu.title("Заавар", GREEN), Menu.lore(GREEN, List.of(b("Сервертэй танилцах алхмууд.")),
                List.of(), "Дарж нээх"), (pl, c) -> tutorial(pl));
        m.open(p);
    }

    public void welcome(Player p) {
        Menu m = new Menu(3, "Тавтай морил, " + p.getName() + "!", Glyphs.GUI_WELCOME);
        m.area(Menu.card(0), Menu.title("Заавар · /tutorial", GREEN), Menu.lore(GREEN,
                List.of(b("Шинэ бол эндээс эхэл: алхам алхмаар."), b("Анги, эрэл, хот, агуй, овог.")), List.of(), "Дарж эхлэх"),
                (pl, c) -> tutorial(pl));
        m.area(Menu.card(1), Menu.title("Сүлд Цэс · /menu", GOLD), Menu.lore(GOLD,
                List.of(b("Бүх цэс нэг дор."), b("Хотбарын 9-р нүдний Сүлд Цэс.")), List.of(), "Дарж нээх"), (pl, c) -> main(pl));
        m.area(Menu.card(2), Menu.title("Тоглох!", SKY), Menu.lore(SKY,
                List.of(b("Тусламж хэрэгтэй бол /help."), b("Амжилт! Мөнх тэнгэр ивээг.")), List.of(), "Хаах"), (pl, c) -> pl.closeInventory());
        m.open(p);
    }

    // ================================================================== tutorial / help

    private record Step(Material icon, String title, TextColor color, List<String> text, String command) {
    }

    private static final List<Step> STEPS = List.of(
            new Step(Material.COMPASS, "1. Хархорум", SKY, List.of("Та Монголын нийслэлд байна.", "Хот бол аюулгүй бүс: PvP, мангас,", "барих/нураах байхгүй."), "/spawn"),
            new Step(Material.IRON_SWORD, "2. Ангиа сонго", RED, List.of("Баатар, Мэргэн, Бөө, Дархан, Хүлэгчин.", "Анги нэг л удаа сонгогдоно!"), "/class"),
            new Step(Material.WRITABLE_BOOK, "3. Анхны эрэл", GOLD, List.of("«Сүлдний Зам»: {chapters} бүлэг эрэл.", "«Анхны Ан»: Говийн 3 чоныг ан.", "Хотын хаалгаар гараад тал руу."), "/quest"),
            new Step(Material.LEATHER, "4. Олз ба зоос", GREEN, List.of("Чонын арьс, баавгайн арьсаа", "дэлгүүрт зарж зоос ол."), "/shop"),
            new Step(Material.EXPERIENCE_BOTTLE, "5. Түвшин ба шагнал", GREEN, List.of("EXP цуглуулж түвшин ахи.", "Түвшин бүрийн шагналаа /lvlup-аар ав.", "Өдөр бүр /daily — өдрийн шагнал."), "/lvlup"),
            new Step(Material.GOLDEN_HELMET, "6. Цол ахиулах", PURPLE, List.of("Ард → Цэрэг → Аравт → ... → Хаан.", "Түвшин + зоос шаардлагатай."), "/rankup"),
            new Step(Material.MOSSY_COBBLESTONE, "7. Агуй ба бүлэг", NamedTextColor.GRAY, List.of("Бүлэг байгуулж агуйн аянд яв.", "/party invite <нэр>, /dungeon"), "/dungeon"),
            new Step(Material.WHITE_BANNER, "8. Овог", NamedTextColor.AQUA, List.of("Овог байгуулж эсвэл нэгдэж", "EXP нэмэгдэл ав."), "/clan top"),
            new Step(Material.NETHER_STAR, "9. Реликс", GOLD, List.of("Дэлхийд ганц домогт эд зүйлс.", "Эзэмшигч хаана ч халдлагад өртөнө!"), "/relic hint"));

    public void tutorial(Player p) {
        Menu m = new Menu(3, "Заавар · Tutorial", null);
        for (int i = 0; i < STEPS.size(); i++) {
            Step s = STEPS.get(i);
            List<Component> body = new ArrayList<>();
            for (String t : s.text()) body.add(b(mn.suld.plugin.branding.ContentCounts.fill(t)));
            ItemStack it = Menu.item(s.icon(), Menu.title(s.title(), s.color()),
                    Menu.lore(s.color(), body, List.of(Menu.kv("Команд:", s.command(), s.color())), "Дарж ажиллуулах"));
            m.set(9 + i, it, (pl, c) -> {
                pl.closeInventory();
                pl.performCommand(s.command().substring(1));
            });
        }
        m.set(0, Menu.item(Material.BOOK, Menu.title("Тусламж · /help", GOLD), Menu.lore(GOLD,
                List.of(b("Бүх командуудын жагсаалт.")), List.of(), "Дарж нээх")), (pl, c) -> help(pl));
        m.set(8, Menu.item(Material.CLOCK, Menu.title("Сүлд Цэс", GOLD), List.of()), (pl, c) -> main(pl));
        m.open(p);
    }

    private record HelpEntry(Material icon, String title, TextColor color, List<String> lines, String page) {
    }

    private static final List<HelpEntry> HELP = List.of(
            new HelpEntry(Material.COMPASS, "Эхлэх", SKY, List.of("Шинэ тоглогчийн алхмууд"), "start"),
            new HelpEntry(Material.IRON_SWORD, "Анги", RED, List.of("/class /profile /exp"), "class"),
            new HelpEntry(Material.BELL, "Хархорум", GOLD, List.of("Хотын үйлчилгээ, аюулгүй бүс"), "city"),
            new HelpEntry(Material.MOSSY_COBBLESTONE, "Агуй ба бүлэг", NamedTextColor.GRAY, List.of("/party /dungeon"), "dungeon"),
            new HelpEntry(Material.WHITE_BANNER, "Овог", NamedTextColor.AQUA, List.of("/clan /cc"), "clan"),
            new HelpEntry(Material.NETHER_STAR, "Реликс", GOLD, List.of("/relic list|hint|history"), "relic"),
            new HelpEntry(Material.OAK_SIGN, "Командууд", GREEN, List.of("Бүх команд нэг дор"), "commands"),
            new HelpEntry(Material.PAPER, "Дүрэм", RED, List.of("Серверийн дүрэм"), "rules"));

    /** The Тусламж page of the hub: eight topics, each a text page ({@code /help <page>}); the ornament tile = the tutorial. */
    public void help(Player p) {
        List<HubMenu.Card> cards = new ArrayList<>();
        for (HelpEntry h : HELP) {
            cards.add(HubMenu.card(h.title(), String.join(" · ", h.lines()), (pl, c) -> {
                pl.closeInventory();
                pl.performCommand("help " + h.page());
            }));
        }
        HubMenu.Card tut = new HubMenu.Card(Menu.title("Заавар · /tutorial", GREEN), List.of(Menu.line("Алхам алхмаар танилцуулга."),
                Component.empty(), Menu.line("« Дарж нээх »")), (pl, c) -> tutorial(pl));
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_HELP, 7, null, cards, tut, this::main, null, null), hubTabs());
    }

    // ================================================================== profile / quests

    /** The Тоглогч page of the hub: the player's stats on the ninth tile, eight cards, the row under the title. */
    // ------------------------------------------------------------------ class academy (docs/CLASS_CHANGE.md)

    /** «Ангийн Академи»: the five classes, what each plays like, and what a change costs this player now. */
    public void academy(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        PlayerClass current = pr.playerClass().orElse(null);
        if (current == null) {
            services.classSelectionGui().open(p);
            return;
        }
        var cc = services.classChange;
        List<HubMenu.Card> cards = new ArrayList<>();
        int mask = 0;
        PlayerClass[] all = PlayerClass.values();
        for (int i = 0; i < all.length; i++) {
            PlayerClass c = all[i];
            boolean mine = c == current;
            if (mine) mask |= 1 << i;
            var saved = pr.classMasteries().of(c).orElse(null);
            List<Component> lore = new ArrayList<>();
            lore.add(Menu.line("Үүрэг: " + ClassSelectionGui.role(c)));
            lore.add(Menu.line("Хүндрэл: " + "★".repeat(c.difficulty()) + "☆".repeat(Math.max(0, 5 - c.difficulty())) + " · Нөөц: " + c.resourceName()));
            lore.add(Menu.line("Эхлэл ур: " + ClassSelectionGui.STARTER_SKILL.getOrDefault(c, "—")));
            lore.add(Menu.line(ClassSelectionGui.lore(c)));
            lore.add(Component.empty());
            var gear = mine ? pr.classGear() : saved == null ? null : saved.gear();
            if (gear != null) {
                lore.add(Menu.line((mn.suld.api.clazz.ClassMastery.isMaster(gear) ? "✦ Мастер · " : "")
                        + "Ангийн Мастер: " + mn.suld.api.clazz.ClassMastery.level(gear) + "/" + mn.suld.api.clazz.ClassMastery.MASTER_LEVEL
                        + " · хуяг " + gear.tier().displayName() + " · ур чадвар " + gear.mastery()));
            }
            if (mine) {
                lore.add(Menu.line("« Таны одоогийн анги »"));
            } else {
                lore.add(Menu.line(saved == null ? "Анх удаа: эхлэлийн зэвсэг, хуяг өгнө; Мастер 1-ээс (EXP ×2 гүйцэх хүртэл)."
                        : "Мод, хуяг, зэвсэг хадгалагдсан."));
                var v = cc == null ? null : cc.verdict(p, c);
                lore.add(Menu.line(v == null ? "" : v.allowed() ? price(v) : "✖ " + v.refusal().text
                        + (v.refusal() == mn.suld.api.clazz.ClassChangeRules.Refusal.COOLDOWN ? " (" + mn.suld.api.clazz.ClassChangeRules.format(v.cooldownLeftMs()) + ")" : "")));
                lore.add(Menu.line("« Дарж сонгоно »"));
            }
            cards.add(HubMenu.card(Menu.title((gear != null && mn.suld.api.clazz.ClassMastery.isMaster(gear) ? "✦ " : "") + c.displayName(),
                    mine ? HubMenu.GOLD : HubMenu.CYAN), lore,
                    new ItemStack(ClassSelectionGui.ICONS.getOrDefault(c, Material.PAPER)), mine ? null : (pl, k) -> academyConfirm(pl, c)));
        }
        boolean grace = mn.suld.api.clazz.ClassChangeRules.grace(pr.progression().level(), pr.activeMinutes().total());
        long left = mn.suld.api.clazz.ClassChangeRules.cooldownLeft(System.currentTimeMillis(), pr.lastClassChange());
        String row = "Одоо: " + current.displayName() + " · " + (grace ? "шинэ тоглогч: солилт үнэгүй"
                : left > 0 ? "дараагийн солилт " + mn.suld.api.clazz.ClassChangeRules.format(left) + "-ын дараа"
                : "солилт " + mn.suld.api.clazz.ClassChangeRules.cost(pr.progression().level()) + " зоос")
                + " · тамга: " + mn.suld.plugin.clazz.ClassSeal.count(p)
                + " · мастер анги: " + mn.suld.api.clazz.ClassMastery.masters(pr) + "/" + PlayerClass.values().length;
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_PROFILE, 0, Menu.line(row), cards, null, this::profile, null, null, mask, "Ангийн Академи"), hubTabs());
    }

    /** «Баатар 60✦ · Мэргэн 34» — every class played, the mastered ones starred, the five-class title when earned. */
    private static String masteryLine(PlayerProfile pr) {
        StringBuilder sb = new StringBuilder();
        mn.suld.api.clazz.ClassMastery.levels(pr).forEach((c, l) -> {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(c.displayName()).append(' ').append(l).append(l >= mn.suld.api.clazz.ClassMastery.MASTER_LEVEL ? "✦" : "");
        });
        String title = mn.suld.api.clazz.ClassMastery.title(mn.suld.api.clazz.ClassMastery.masters(pr));
        return sb.length() == 0 ? "—" : title == null ? sb.toString() : title + " · " + sb;
    }

    private static String price(mn.suld.api.clazz.ClassChangeRules.Verdict v) {
        return switch (v.payment()) {
            case GRACE -> "Үнэгүй (шинэ тоглогч)";
            case SEAL -> "1 Хувь Төөргийн Тамга";
            case COINS -> v.price() + " зоос · дараа нь 6 цаг хүлээнэ";
        };
    }

    /** The confirm page: one misclick must not change a class. */
    void academyConfirm(Player p, PlayerClass to) {
        var cc = services.classChange;
        if (cc == null) return;
        var v = cc.verdict(p, to);
        List<HubMenu.Card> cards = new ArrayList<>();
        if (v.allowed()) {
            cards.add(HubMenu.card(Menu.title("✔ Батлах: " + to.displayName(), HubMenu.CYAN), List.of(Menu.line(price(v)),
                    Menu.line("Одоогийн ангийн ур чадвар, хуяг хадгалагдана."), Menu.line("Амь нь хувиараа үлдэнэ, ид шид 10 с хүлээнэ.")), null, (pl, k) -> {
                var done = cc.change(pl, to);
                if (!done.allowed()) pl.sendMessage(Messages.error(done.refusal().text));
                else academy(pl);
            }));
        } else {
            cards.add(HubMenu.card(Menu.title("✖ Одоо болохгүй", HubMenu.GOLD), List.of(Menu.line(v.refusal().text
                    + (v.refusal() == mn.suld.api.clazz.ClassChangeRules.Refusal.COOLDOWN ? " (" + mn.suld.api.clazz.ClassChangeRules.format(v.cooldownLeftMs()) + ")" : ""))), null, null));
        }
        var trial = services.classTrial;
        if (trial != null) {
            String why = trial.blocked(p, to);
            cards.add(HubMenu.card(Menu.title("⚔ Турших: " + to.displayName(), HubMenu.CYAN), List.of(Menu.line(why == null
                    ? "5 минут дамми дээр турш. Юу ч үлдэхгүй, зардалгүй." : "✖ " + why), Menu.line("Дараа нь өөрийн ангидаа буцна.")), null,
                    why == null ? (pl, k) -> trial.start(pl, to) : null));
        }
        cards.add(HubMenu.card("‹ Буцах", "Ангийн Академи руу.", (pl, k) -> academy(pl)));
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_PROFILE, 0, Menu.line(to.displayName() + " руу шилжих уу?"), cards, null, this::academy, null, null, 0,
                "Анги солих"), hubTabs());
    }

    public void profile(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        PlayerStyle s = styles().of(p.getUniqueId());
        Progression prog = pr.progression();
        String clan = services.clans().tagOf(p.getUniqueId()).map(t -> " · [" + t + "]").orElse("");
        Component row = Component.text("Lv " + prog.level() + " · " + fmt(pr.currency()) + " зоос · " + s.rank().displayName() + clan,
                TextColor.fromHexString("#A4B0C8"));
        int claimable = LevelRewards.claimable(prog.level(), s.claimedLevels()).size();
        List<Component> stats = List.of(
                Menu.kv("Анги:", pr.playerClass().map(c -> c.displayName()).orElse("сонгоогүй"), RED),
                Menu.kv("Цол:", s.rank().displayName(), StyleFormat.hex(s.rank().color())),
                Menu.kv("Түвшин:", prog.level() + " (" + Math.round(services.progression().engine().progressFraction(prog) * 100) + "%)", GREEN),
                Menu.kv("Зоос:", fmt(pr.currency()), GOLD),
                Menu.kv("Кредит:", fmt(s.credits()) + " ✦", SKY),
                Menu.kv("Овог:", services.clans().tagOf(p.getUniqueId()).map(t -> "[" + t + "]").orElse("—"), NamedTextColor.AQUA),
                Menu.kv("Ангийн Мастер:", masteryLine(pr), GOLD),
                Component.empty(),
                Menu.line("« Дарж: морь, Хуралдай, хроник »"));
        HubMenu.Card head = new HubMenu.Card(StyleFormat.name(p, s).decoration(TextDecoration.BOLD, true), stats, (pl, c) -> profileClassic(pl));
        List<HubMenu.Card> cards = List.of(
                HubMenu.card("Түвшний шагнал", claimable > 0 ? "Авах боломжтой: " + claimable : "Түвшин бүрийн шагнал авах.", (pl, c) -> lvlup(pl)),
                HubMenu.card("Цол", "Одоо: " + s.rank().displayName() + " · ранк ахих нөхцөл.", (pl, c) -> rankup(pl)),
                HubMenu.card("Гоёл", "Гоёл чимэглэл, цол, нэрийн өнгө.", (pl, c) -> cosmetics(pl)),
                HubMenu.card("Эрэл", "Түүхийн бүлгүүд, явц.", (pl, c) -> quests(pl)),
                HubMenu.card("Ур чадвар", "Чадварын мод, build.", (pl, c) -> skillsOrMap(pl)),
                HubMenu.card("Овог", "Овгийн мэдээлэл, гишүүд.", (pl, c) -> {
                    pl.closeInventory();
                    pl.performCommand("clan info");
                }),
                HubMenu.card("Өдрийн шагнал", "Өдөр бүрийн шагнал, цуваа.", (pl, c) -> {
                    pl.closeInventory();
                    pl.performCommand("daily");
                }),
                HubMenu.card("Даалгавар", "Өдрийн даалгаврууд.", (pl, c) -> {
                    pl.closeInventory();
                    pl.performCommand("tasks");
                }));
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_PROFILE, 0, row, cards, head, this::main, null, null), hubTabs());
    }

    /** The Тохиргоо page of the hub ({@code /settings}): the on/off settings, eight a page; the ones that are on glow. */
    public void settings(Player p) {
        settings(p, 0);
    }

    private void settings(Player p, int page) {
        PlayerStyle s = styles().of(p.getUniqueId());
        mn.suld.api.style.PlayerSetting[] all = mn.suld.api.style.PlayerSetting.values();
        int pages = (all.length + HubLayout.CARDS - 1) / HubLayout.CARDS, pg = Math.max(0, Math.min(pages - 1, page));
        List<HubMenu.Card> cards = new ArrayList<>();
        int mask = 0, onAll = 0;
        for (mn.suld.api.style.PlayerSetting st : all) if (s.setting(st)) onAll++;
        for (int i = pg * HubLayout.CARDS; i < Math.min(all.length, (pg + 1) * HubLayout.CARDS); i++) {
            mn.suld.api.style.PlayerSetting st = all[i];
            boolean on = s.setting(st);
            if (on) mask |= 1 << cards.size();
            cards.add(new HubMenu.Card(Menu.title(st.label(), on ? GREEN : NamedTextColor.GRAY), List.of(Menu.line(st.description()),
                    Component.empty(), Menu.kv("Одоо:", on ? "асаалттай ✔" : "унтраалттай ✘", on ? GREEN : RED),
                    Menu.line("« Дарж солих »")), (pl, c) -> {
                styles().toggleSetting(pl, st);
                settings(pl, pg);
            }));
        }
        Component row = Component.text(onAll + " / " + all.length + " асаалттай · гэрэлтсэн карт = асаалттай" + (pages > 1 ? " · " + (pg + 1) + "/" + pages : ""),
                TextColor.fromHexString("#A4B0C8"));
        java.util.function.Consumer<Player> prev = pg > 0 ? pl -> settings(pl, pg - 1) : null;
        java.util.function.Consumer<Player> next = pg < pages - 1 ? pl -> settings(pl, pg + 1) : null;
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_SETTINGS, 6, row, cards, null, this::main, prev, next, mask), hubTabs());
    }

    /** The older five-row profile (horse, Их Хуралдай, «Миний түүх»): behind the hub profile's head tile. */
    public void profileClassic(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        PlayerStyle s = styles().of(p.getUniqueId());
        Progression prog = pr.progression();
        Menu m = new Menu(5, "Дүр · " + p.getName(), null);
        ItemStack head = Menu.item(Material.PLAYER_HEAD, StyleFormat.name(p, s).decoration(TextDecoration.BOLD, true), List.of(
                Menu.kv("Анги:", pr.playerClass().map(c -> c.displayName()).orElse("сонгоогүй"), RED),
                Menu.kv("Цол:", s.rank().displayName(), StyleFormat.hex(s.rank().color())),
                Menu.kv("Түвшин:", prog.level() + " (" + Math.round(services.progression().engine().progressFraction(prog) * 100) + "%)", GREEN),
                Menu.kv("Зоос:", fmt(pr.currency()), GOLD),
                Menu.kv("Кредит:", fmt(s.credits()) + " ✦", SKY),
                Menu.kv("Овог:", services.clans().tagOf(p.getUniqueId()).map(t -> "[" + t + "]").orElse("—"), NamedTextColor.AQUA)));
        if (head.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(p);
            head.setItemMeta(sm);
        }
        m.set(13, head, null);
        int claimable = LevelRewards.claimable(prog.level(), s.claimedLevels()).size();
        m.set(29, Menu.item(Material.EXPERIENCE_BOTTLE, Menu.title("Түвшний шагнал · /lvlup", GREEN), Menu.lore(GREEN,
                List.of(b("Түвшин бүрт зоос, цол, өнгө.")), List.of(Menu.kv("Авах боломжтой:", String.valueOf(claimable), GREEN)), "Дарж нээх")),
                (pl, c) -> lvlup(pl));
        m.set(30, Menu.item(Material.GOLDEN_HELMET, Menu.title("Цол · /rankup", PURPLE), Menu.lore(PURPLE,
                List.of(b("Ард → Хаан.")), List.of(Menu.kv("Одоо:", s.rank().displayName(), StyleFormat.hex(s.rank().color()))), "Дарж нээх")),
                (pl, c) -> rankup(pl));
        m.set(31, Menu.item(Material.ARMOR_STAND, Menu.title("Гоёл · /cosmetics", GOLD), Menu.lore(GOLD,
                List.of(b("Цол, нэрийн өнгө, чатын өнгө, мэндчилгээ, эможи.")), List.of(), "Дарж нээх")), (pl, c) -> cosmetics(pl));
        m.set(32, Menu.item(Material.WRITABLE_BOOK, Menu.title("Эрэл · /quest", GOLD), List.of()), (pl, c) -> quests(pl));
        m.set(28, Menu.item(Material.BLAZE_POWDER, Menu.title("Ур чадвар · /skills", RED), Menu.lore(RED,
                List.of(b("Чадварын мод: идэвхтэй ба идэвхгүй шившлэг, өөрчлөлт.")), List.of(), "Дарж нээх")), (pl, c) -> {
            if (skillMap != null) skillMap.open(pl);
            else skills(pl);
        });
        m.set(33, Menu.item(Material.WHITE_BANNER, Menu.title("Овог · /clan info", NamedTextColor.AQUA), List.of()), (pl, c) -> {
            pl.closeInventory();
            pl.performCommand("clan info");
        });
        m.set(34, Menu.item(Material.CHEST, Menu.title("Өдрийн шагнал · /daily", GOLD), Menu.lore(GOLD,
                List.of(b("7 хоногийн дараалсан шагнал: зоос, EXP.")), List.of(), "Дарж нээх")), (pl, c) -> {
            pl.closeInventory();
            pl.performCommand("daily");
        });
        m.set(35, Menu.item(Material.IRON_SWORD, Menu.title("Өдрийн даалгавар · /tasks", GOLD), Menu.lore(GOLD,
                List.of(b("Өдөр бүр 3 анчны даалгавар.")), List.of(), "Дарж нээх")), (pl, c) -> {
            pl.closeInventory();
            pl.performCommand("tasks");
        });
        boolean horse = prog.level() >= mn.suld.plugin.mount.HorseService.MIN_LEVEL;
        m.set(22, Menu.item(horse ? Material.SADDLE : Material.LEAD, Menu.title("Монгол морь · /mori", horse ? GREEN : NamedTextColor.GRAY), Menu.lore(horse ? GREEN : NamedTextColor.GRAY,
                List.of(b("Цол ахих тусам хурдан, гоё морь.")), List.of(Menu.kv("Нээгдэх:", "Түвшин " + mn.suld.plugin.mount.HorseService.MIN_LEVEL, horse ? GREEN : RED)),
                horse ? "Дарж дуудах" : "Түвшин ахиул")), horse ? (pl, c) -> {
            pl.closeInventory();
            pl.performCommand("mori");
        } : null);
        m.set(36, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> main(pl));
        var campaign = services.campaigns().active();
        m.set(42, Menu.item(Material.LECTERN, Menu.title("Их Хуралдай · /khuraldai", GOLD), Menu.lore(GOLD,
                List.of(b("Долоо хоногийн аян ба санал хураалт.")),
                campaign.map(r -> List.of(Menu.kv("Аян:", r.definition().name(), GOLD),
                        Menu.kv("Таны хувь:", String.valueOf(r.unitsOf(p.getUniqueId())), GREEN))).orElse(List.of()), "Дарж нээх")), (pl, c) -> khuraldai(pl));
        m.set(43, Menu.item(Material.WRITABLE_BOOK, Menu.title("Миний түүх · /chronicle mine", GOLD), Menu.lore(GOLD,
                List.of(b("Хроникт бичигдсэн таны амжилтууд.")), List.of(), "Дарж нээх")), (pl, c) -> chronicleMine(pl));
        m.open(p);
    }

    private static final int[] CHAPTER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private static Material questIcon(mn.suld.api.quest.QuestType t) {
        return switch (t) {
            case KILL_MOB -> Material.IRON_SWORD;
            case REACH_LEVEL -> Material.EXPERIENCE_BOTTLE;
            case COLLECT_ITEM -> Material.LEATHER;
            case DISCOVER_LOCATION -> Material.FILLED_MAP;
            case COMPLETE_DUNGEON -> Material.MOSSY_COBBLESTONE;
            case INSPECT_CLUE -> Material.SPYGLASS;
            case CRAFT_ITEM -> Material.SMITHING_TABLE;
            case OVOO_RITUAL -> Material.COBBLESTONE_WALL;
        };
    }

    /** The storyline: every chapter as a card — done ✔, active (glowing, with progress) or locked. */
    /** Told whenever the quest screen opens (the tutorial's step); set by the plugin. */
    public static volatile java.util.function.Consumer<Player> onQuests = p -> { };

    private final QuestMenu questMenu;

    public QuestMenu questMenu() {
        return questMenu;
    }

    /** The story map (gui/QuestMenu). */
    public void quests(Player p) {
        onQuests.accept(p);
        questMenu.open(p);
    }

    // ================================================================== skills

    private static final Material[] SPELL_ICON = {Material.BLAZE_POWDER, Material.FIRE_CHARGE, Material.FEATHER, Material.NETHER_STAR, Material.HEART_OF_THE_SEA};

    private SkillMapMenu skillMap;

    public void skillMap(SkillMapMenu map) {
        this.skillMap = map;
    }

    public void skills(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        var clazz = pr.playerClass().orElse(null);
        Menu m = new Menu(3, "Ур чадвар · Skills", null);
        if (clazz == null) {
            m.set(13, Menu.item(Material.BARRIER, Menu.title("Анги сонгоогүй", RED), List.of(b("Эхлээд ангиа сонго: /class"))),
                    (pl, c) -> pl.performCommand("class"));
            m.open(p);
            return;
        }
        int level = pr.progression().level();
        m.set(4, Menu.item(Material.BOOK, Menu.title(clazz.displayName() + " · " + clazz.resourceName(), GOLD), List.of(
                b("Ангийн зэвсгээ барьж гурван товшилт:"),
                b(mn.suld.api.skill.Spell.firstClick(clazz) == 'R' ? "Баруун-Зүүн-Баруун гэх мэт (R-L-R)." : "Зүүн-Баруун-Зүүн гэх мэт (L-R-L)."),
                b("5-р ид шид (50-р түвшин): Shift+F."),
                b("Зэрэг II 40-р, зэрэг III 55-р түвшинд."),
                b("Нөөц цаг ба тулаанаар нөхөгдөнө."))), null);
        int slot = 11; // five spells: 11..15 (18 is the back button)
        for (mn.suld.api.skill.Spell sp : mn.suld.api.skill.Spell.of(clazz)) {
            boolean open = level >= sp.unlockLevel();
            TextColor c = open ? GREEN : RED;
            int rank = sp.rankAt(level);
            List<Component> info = new ArrayList<>();
            info.add(Menu.kv("Хослол:", sp.keys(), GOLD));
            if (open) info.add(Menu.kv("Зэрэг:", mn.suld.api.skill.Spell.roman(rank) + (rank > 1 ? " (+" + Math.round((mn.suld.api.skill.Spell.rankPower(rank) - 1) * 100) + "% хүч)" : ""), GOLD));
            info.add(Menu.kv("Нөөц:", String.valueOf(sp.cost()), SKY));
            info.add(Menu.kv("Хүлээлт:", String.format(java.util.Locale.ROOT, "%.1f", sp.cooldownSeconds() * mn.suld.api.skill.Spell.rankCooldown(rank)) + " сек", SKY));
            if (sp.damageMultiplier() > 0) info.add(Menu.kv("Хүч:", "x" + sp.damageMultiplier() + " ATK", RED));
            info.add(Menu.kv("Түвшин:", String.valueOf(sp.unlockLevel()), c));
            ItemStack it = Menu.item(open ? SPELL_ICON[sp.slot() - 1] : Material.GRAY_DYE,
                    Component.text(sp.slot() + ". " + sp.displayName() + (open && rank > 1 ? " " + mn.suld.api.skill.Spell.roman(rank) : ""), open ? GOLD : NamedTextColor.GRAY, TextDecoration.BOLD),
                    Menu.lore(c, List.of(b(sp.description())), info, open ? (sp.slot() == mn.suld.api.skill.Spell.SHIFT_F_SLOT ? "Shift+F дарж хэрэглэ" : "Хослолоор хэрэглэ") : "Түвшин " + sp.unlockLevel() + " хүрэх"));
            if (open) Menu.glow(it);
            m.set(slot, it, null);
            slot++;
        }
        m.set(18, Menu.item(Material.ARROW, Menu.title("« Дүр", GOLD), List.of()), (pl, c) -> profile(pl));
        if (skillMap != null) {
            m.set(22, Menu.item(Material.FILLED_MAP, Menu.title("Чадварын газрын зураг", TextColor.fromHexString("#2AC4B4")),
                    List.of(b("Оноо зарцуулж шившлэгээ хөгжүүл."))), (pl, c) -> skillMap.open(pl));
        }
        m.open(p);
    }

    // ================================================================== rank ladder

    /** The Rankup page of the hub: the eight ranks as cards (owned ones glow), the next one climbs on a click. */
    public void rankup(Player p) {
        if (services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс байхдаа цол ахиулах боломжгүй."));
            return;
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        PlayerStyle s = styles().of(p.getUniqueId());
        List<HubMenu.Card> cards = new ArrayList<>();
        int mask = 0;
        for (Rank r : Rank.LADDER) {
            boolean have = r.ordinal() <= s.rank().ordinal();
            boolean next = s.rank().next().map(n -> n == r).orElse(false);
            if (have) mask |= 1 << r.ordinal();
            TextColor c = StyleFormat.hex(r.color());
            List<Component> info = new ArrayList<>();
            info.add(Menu.kv("Түвшин:", String.valueOf(r.requiredLevel()), pr.progression().level() >= r.requiredLevel() ? GREEN : RED));
            info.add(Menu.kv("Үнэ:", fmt(r.cost()) + " зоос", pr.currency() >= r.cost() ? GREEN : RED));
            String hint = have ? "Танд байгаа ✔" : next ? "Дарж цол ахиулах" : "Өмнөх цолоо ав";
            List<Component> body = List.of(StyleFormat.rankBadge(r, true), Component.empty(), b(r.lore()));
            cards.add(HubMenu.card(Component.text(r.displayName(), c, TextDecoration.BOLD).font(StyleFormat.HD), Menu.lore(c, body, info, hint), null,
                    next ? (pl, ct) -> {
                        if (styles().rankUp(pl) == Rank.Check.OK) rankup(pl);
                    } : null));
        }
        Component row = Component.text("Таны цол: ", TextColor.fromHexString("#A4B0C8"))
                .append(Component.text(s.rank().displayName(), StyleFormat.hex(s.rank().color())))
                .append(Component.text(" · Lv " + pr.progression().level() + " · " + fmt(pr.currency()) + " зоос", TextColor.fromHexString("#A4B0C8")));
        HubMenu.Card badges = new HubMenu.Card(Menu.title("Бүх тэмдэг", GOLD), List.of(StyleFormat.rankBadge(s.rank(), true), Component.empty(),
                b("Энэ тэмдэг нэрийн өмнө TAB, чат,"), b("толгой дээрх нэрэнд харагдана."), Component.empty(), Menu.line("« Дарж: тэмдэг бүр, яаж авах вэ »")),
                (pl, c) -> badgeBook(pl));
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_RANKUP, 4, row, cards, badges, this::main, null, null, mask), hubTabs());
    }

    /** The older four-row rank ladder (kept for the badge book's «« Цол» link). */
    public void rankupClassic(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        PlayerStyle s = styles().of(p.getUniqueId());
        Menu m = new Menu(4, "Цол · Rank", null);
        Material[] icons = {Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET, Material.GOLDEN_HELMET,
                Material.DIAMOND_HELMET, Material.NETHERITE_HELMET, Material.TURTLE_HELMET, Material.DRAGON_HEAD};
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        for (Rank r : Rank.LADDER) {
            boolean have = r.ordinal() <= s.rank().ordinal();
            boolean next = s.rank().next().map(n -> n == r).orElse(false);
            TextColor c = StyleFormat.hex(r.color());
            List<Component> info = new ArrayList<>();
            info.add(Menu.kv("Түвшин:", String.valueOf(r.requiredLevel()), pr.progression().level() >= r.requiredLevel() ? GREEN : RED));
            info.add(Menu.kv("Үнэ:", fmt(r.cost()) + " зоос", pr.currency() >= r.cost() ? GREEN : RED));
            String hint = have ? "Танд байгаа ✔" : next ? "Дарж цол ахиулах" : "Өмнөх цолоо ав";
            // the rank's badge (the one shown before the name in TAB, chat and above the head)
            List<Component> body = List.of(StyleFormat.rankBadge(r, true), Component.empty(), b(r.lore()));
            ItemStack it = Menu.item(icons[r.ordinal()], Component.text(r.displayName(), c, TextDecoration.BOLD),
                    Menu.lore(c, body, info, hint));
            if (have || next) Menu.glow(it);
            m.set(slots[r.ordinal()], it, next ? (pl, ct) -> {
                if (styles().rankUp(pl) == Rank.Check.OK) rankup(pl);
            } : null);
        }
        m.set(4, Menu.item(Material.NAME_TAG, Menu.title("Таны цол", StyleFormat.hex(s.rank().color())), List.of(
                StyleFormat.rankBadge(s.rank(), true), Component.empty(),
                b("Энэ тэмдэг нэрийн өмнө TAB, чат,"), b("толгой дээрх нэрэнд харагдана."))), null);
        m.set(27, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> main(pl));
        m.set(31, Menu.item(Material.PAPER, Menu.title("Бүх тэмдэг", GOLD), List.of(b("Тэмдэг бүр, яаж авах вэ."))), (pl, c) -> badgeBook(pl));
        m.open(p);
    }

    // ================================================================== badges

    /**
     * Every badge (the owner's HD sheet) and how to get it: ranks with /rankup, staff and supporter badges from an
     * admin (/suld badge or LuckPerms suld.badge.<id>), the class badge with /class, ОВОГ with a clan, and the
     * city/wild badges the HUD shows by itself.
     */
    public void badgeBook(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        PlayerStyle s = styles().of(p.getUniqueId());
        Menu m = new Menu(6, "Тэмдгүүд · Badges", Glyphs.GUI_FRAME);
        int slot = 0;
        for (Rank r : Rank.LADDER) {
            boolean have = r.ordinal() <= s.rank().ordinal();
            m.set(slot++, badgeItem(StyleFormat.rankBadge(r, true), r.displayName(), StyleFormat.hex(r.color()), have,
                    List.of(b("Цол: /rankup"), Menu.kv("Түвшин:", String.valueOf(r.requiredLevel()), SKY), Menu.kv("Үнэ:", fmt(r.cost()) + " зоос", GOLD))), null);
        }
        slot = 9;
        var staff = StyleFormat.staffOf(p).orElse(null);
        for (mn.suld.api.style.StaffBadge sb : mn.suld.api.style.StaffBadge.values()) {
            m.set(slot++, badgeItem(StyleFormat.staffBadge(sb, true), sb.displayName(), StyleFormat.hex(sb.color()), staff == sb,
                    List.of(b(sb.staff() ? "Серверийн ажилтан." : "Серверийг дэмжигч."), b("Админ олгоно: /suld badge"))), null);
        }
        slot = 18;
        var mine = pr == null ? null : pr.playerClass().orElse(null);
        for (mn.suld.api.clazz.PlayerClass c : mn.suld.api.clazz.PlayerClass.values()) {
            m.set(slot++, badgeItem(StyleFormat.classBadge(c, true), c.displayName(), GOLD, c == mine,
                    List.of(b("Таны анги: /class"), b("Нэрийн дараа харагдана."))), null);
        }
        boolean clan = services.clans() != null && services.clans().tagOf(p.getUniqueId()).isPresent();
        m.set(27, badgeItem(StyleFormat.glyph(Glyphs.BADGE_CLAN), "Овог", SKY, clan,
                List.of(b("Овогт элсэх: /clan accept"), b("Овог байгуулах: /clan create"), b("Нэрийн дараа овгийн тагтай."))), null);
        m.set(28, badgeItem(StyleFormat.glyph(Glyphs.BADGE_SAFE), "Аюулгүй", GREEN, false,
                List.of(b("Хархорум хотод HUD дээр."))), null);
        m.set(29, badgeItem(StyleFormat.glyph(Glyphs.BADGE_DANGER), "Аюултай", RED, false,
                List.of(b("Хотоос гадна HUD дээр."))), null);
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Буцах", GOLD), List.of()), (pl, c) -> cosmetics(pl));
        m.set(49, Menu.item(Material.NAME_TAG, Menu.title("Цол ахиулах · /rankup", GOLD), List.of()), (pl, c) -> rankup(pl));
        m.open(p);
    }

    private ItemStack badgeItem(Component badge, String name, TextColor color, boolean have, List<Component> how) {
        List<Component> lore = new ArrayList<>();
        lore.add(badge);
        lore.add(Component.empty());
        lore.addAll(how);
        lore.add(Component.empty());
        lore.add(have ? Component.text("Танд байгаа ✔", GREEN, TextDecoration.BOLD) : Component.text("Танд алга", NamedTextColor.GRAY));
        ItemStack it = Menu.item(have ? Material.NAME_TAG : Material.PAPER, Component.text(name, color, TextDecoration.BOLD), lore);
        return have ? Menu.glow(it) : it;
    }

    // ================================================================== level rewards

    public void lvlup(Player p) {
        if (services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс байхдаа шагнал авах боломжгүй."));
            return;
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        PlayerStyle s = styles().of(p.getUniqueId());
        int level = pr.progression().level();
        Menu m = new Menu(4, "Түвшний шагнал · Lv " + level, null);
        int slot = 0;
        for (LevelRewards.Reward r : LevelRewards.ALL) {
            if (slot >= 27) break;
            boolean claimed = LevelRewards.claimed(s.claimedLevels(), r.level());
            boolean ready = !claimed && level >= r.level();
            Material mat = claimed ? Material.MINECART : ready ? Material.CHEST_MINECART : Material.GRAY_DYE;
            TextColor c = claimed ? NamedTextColor.GRAY : ready ? GREEN : RED;
            List<Component> info = new ArrayList<>();
            info.add(Menu.kv("Зоос:", "+" + fmt(r.coins()), GOLD));
            if (r.cosmeticId() != null) {
                CosmeticCatalog.byId(r.cosmeticId()).ifPresent(cos -> info.add(Menu.kv(cos.category().displayName() + ":", cos.name(), PURPLE)));
            }
            ItemStack it = Menu.item(mat, Component.text("Түвшин " + r.level() + " · " + r.label(), c, TextDecoration.BOLD),
                    Menu.lore(c, List.of(), info, claimed ? "Авсан ✔" : ready ? "Дарж авах" : "Түвшин " + r.level() + " хүрэх"));
            if (ready) Menu.glow(it);
            m.set(slot++, it, ready ? (pl, ct) -> {
                styles().claimLevel(pl, r.level());
                lvlup(pl);
            } : null);
        }
        m.set(27, Menu.item(Material.ARROW, Menu.title("« Дүр", GOLD), List.of()), (pl, c) -> profile(pl));
        m.set(31, Menu.item(Material.EXPERIENCE_BOTTLE, Menu.title("Бүгдийг авах", GREEN), List.of()), (pl, c) -> {
            for (LevelRewards.Reward r : LevelRewards.claimable(level, s.claimedLevels())) styles().claimLevel(pl, r.level());
            lvlup(pl);
        });
        m.open(p);
    }

    // ================================================================== cosmetics

    private static final Map<Cosmetic.Category, Material> CAT_ICON = Map.of(
            Cosmetic.Category.TAG, Material.NAME_TAG, Cosmetic.Category.NAME_COLOR, Material.ORANGE_DYE,
            Cosmetic.Category.CHAT_COLOR, Material.WRITABLE_BOOK, Cosmetic.Category.JOIN_MESSAGE, Material.GOAT_HORN,
            Cosmetic.Category.EMOJI, Material.SUNFLOWER, Cosmetic.Category.AURA, Material.NETHER_STAR,
            Cosmetic.Category.TRAIL, Material.LEATHER_BOOTS, Cosmetic.Category.KILL_EFFECT, Material.FIREWORK_ROCKET);
    private static final Map<Cosmetic.Category, TextColor> CAT_COLOR = Map.of(
            Cosmetic.Category.TAG, GOLD, Cosmetic.Category.NAME_COLOR, RED, Cosmetic.Category.CHAT_COLOR, SKY,
            Cosmetic.Category.JOIN_MESSAGE, PURPLE, Cosmetic.Category.EMOJI, TextColor.fromHexString("#FF6BB0"),
            Cosmetic.Category.AURA, TextColor.fromHexString("#5FE0E0"), Cosmetic.Category.TRAIL, GREEN,
            Cosmetic.Category.KILL_EFFECT, TextColor.fromHexString("#FF9A3C"));

    /** The card grid: eight categories on art cards (the order matches COSMETICS in tools/pack/gen_ui.py). */
    public void cosmetics(Player p) {
        PlayerStyle s = styles().of(p.getUniqueId());
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        Menu m = new Menu(6, "Гоёл · Cosmetics", Glyphs.GUI_COSMETICS);
        Cosmetic.Category[] order = {Cosmetic.Category.TAG, Cosmetic.Category.NAME_COLOR, Cosmetic.Category.CHAT_COLOR,
                Cosmetic.Category.JOIN_MESSAGE, Cosmetic.Category.EMOJI, Cosmetic.Category.AURA, Cosmetic.Category.TRAIL,
                Cosmetic.Category.KILL_EFFECT};
        for (int i = 0; i < order.length; i++) {
            Cosmetic.Category cat = order[i];
            TextColor c = CAT_COLOR.get(cat);
            long total = CosmeticCatalog.of(cat).size();
            Component equipped = s.equipped(cat).map(x -> Menu.kv("Зүүсэн:", x.name(), c)).orElse(Menu.kv("Зүүсэн:", "—", NamedTextColor.GRAY));
            m.area(Menu.card4(i), Menu.title(cat.displayName(), c), Menu.lore(c, List.of(b(cat.description() + ".")),
                    List.of(Menu.kv("Авсан:", s.ownedIn(cat) + "/" + total, c), equipped), "Ангилал үзэх"), (pl, ct) -> category(pl, cat));
        }
        m.set(8, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> main(pl));
        m.set(17, Menu.item(Material.GOLD_INGOT, Menu.title("Хэтэвч", GOLD), List.of(
                Menu.kv("Зоос:", fmt(pr == null ? 0 : pr.currency()), GOLD), Menu.kv("Кредит:", fmt(s.credits()) + " ✦", SKY))), null);
        m.set(26, Menu.item(Material.EMERALD, Menu.title("Кредит · /buy", SKY), Menu.lore(SKY,
                List.of(b("Сүлд Кредитээр гоёл авна.")), List.of(), "Дарж нээх")), (pl, c) -> buy(pl));
        m.set(35, Menu.item(Material.CHEST, Menu.title("Дэлгүүр · /shop", GREEN), List.of()), (pl, c) -> shop(pl));
        m.set(53, Menu.item(Material.NAME_TAG, Menu.title("Тэмдгүүд", GOLD), Menu.lore(GOLD, List.of(
                b("Цол, анги, овог, ажилтны тэмдэг."), StyleFormat.rankBadge(s.rank(), true)), List.of(), "Дарж үзэх")), (pl, c) -> badgeBook(pl));
        m.set(44, Menu.item(Material.BARRIER, Menu.title("Бүгдийг тайлах", RED), List.of(b("Зүүсэн бүх гоёлыг тайлна."))), (pl, c) -> {
            PlayerStyle st = styles().of(pl.getUniqueId());
            for (Cosmetic.Category cat : Cosmetic.Category.values()) st.equip(cat, null);
            styles().onEquipChanged(pl);
            cosmetics(pl);
        });
        m.open(p);
    }

    /** A sample of what the cosmetic looks like, sent to the viewer's chat (effects play around them instead). */
    /** Which achievement or tier of the pass gives an earned cosmetic (null for the others). */
    private String earnedBy(Cosmetic c) {
        if ("referral".equals(c.source())) return "Урилга: " + services.referrals.settings().badgeAfter() + " найзаа авчрах · /urih";
        var ach = services.achievements;
        if (ach == null) return null;
        if ("achievement".equals(c.source())) {
            for (mn.suld.api.achievement.Achievement a : ach.catalog().all()) if (c.id().equals(a.reward())) return "Амжилт «" + a.name() + "»";
        }
        if ("pass".equals(c.source())) {
            for (mn.suld.api.battlepass.CampaignPass.Reward r : ach.layout().rewards()) {
                if (c.id().equals(r.cosmetic())) return "Хуралдайн Зам · " + r.track().displayName() + " · " + r.tier() + "-р шат";
            }
        }
        return null;
    }

    private void previewCosmetic(Player p, Cosmetic c) {
        PlayerStyle s = styles().of(p.getUniqueId());
        Component hello = Component.text("Сайн байна уу!", NamedTextColor.WHITE, TextDecoration.BOLD);
        Component line = switch (c.category()) {
            case TAG -> StyleFormat.name(p, s).append(Component.text(" ")).append(StyleFormat.mini(c.style()))
                    .append(Component.text(" » ", NamedTextColor.GRAY)).append(hello);
            case NAME_COLOR -> StyleFormat.mini(c.style().replace("{}", p.getName())).append(Component.text(" » ", NamedTextColor.GRAY)).append(hello);
            case CHAT_COLOR -> StyleFormat.name(p, s).append(Component.text(" » ", NamedTextColor.GRAY))
                    .append(StyleFormat.mini(c.style().replace("{}", "Сайн байна уу!")));
            case JOIN_MESSAGE -> StyleFormat.joinMessage(c, p.getName());
            case EMOJI -> Component.text(c.style() + "  ←  чатад ингэж бичнэ", NamedTextColor.WHITE, TextDecoration.BOLD);
            default -> null;
        };
        if (line != null) {
            p.sendMessage(Component.text("Урьдчилан харах › ", GOLD, TextDecoration.BOLD).append(line));
            return;
        }
        if (plugin instanceof mn.suld.plugin.SuldPlugin sp && sp.effects() != null) {
            sp.effects().preview(p, c);
            p.sendMessage(Messages.info("Урьдчилан харж байна: " + c.name()));
        }
    }

    public void category(Player p, Cosmetic.Category cat) {
        PlayerStyle s = styles().of(p.getUniqueId());
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        long coins = pr == null ? 0 : pr.currency();
        TextColor accent = CAT_COLOR.get(cat);
        Menu m = new Menu(6, cat.displayName() + " · " + s.ownedIn(cat) + "/" + CosmeticCatalog.of(cat).size(), Glyphs.GUI_FRAME);
        int slot = 0;
        for (Cosmetic c : CosmeticCatalog.of(cat)) {
            if (slot >= 45) break;
            boolean owned = s.owns(c.id());
            if (c.withheld() && !owned) continue; // not obtainable for now (CULT-001 review)
            boolean on = s.equipped(cat).map(x -> x.id().equals(c.id())).orElse(false);
            TextColor rc = StyleFormat.hex(c.rarity().color());
            Component name = switch (cat) {
                case TAG -> StyleFormat.mini(c.style()).decoration(TextDecoration.BOLD, true);
                case NAME_COLOR -> StyleFormat.mini(c.style().replace("{}", p.getName())).decoration(TextDecoration.BOLD, true);
                case CHAT_COLOR -> StyleFormat.mini(c.style().replace("{}", c.name() + " чат")).decoration(TextDecoration.BOLD, true);
                case JOIN_MESSAGE, AURA, TRAIL, KILL_EFFECT -> Component.text(c.name(), rc, TextDecoration.BOLD);
                case EMOJI -> Component.text(c.name() + "  " + c.style(), rc, TextDecoration.BOLD);
            };
            List<Component> body = new ArrayList<>();
            body.add(Component.text(c.rarity().displayName(), rc, TextDecoration.BOLD));
            if (cat == Cosmetic.Category.JOIN_MESSAGE) body.add(StyleFormat.joinMessage(c, p.getName()));
            List<Component> info = new ArrayList<>();
            String hint;
            if (owned) {
                info.add(Menu.kv("Төлөв:", on ? "Зүүсэн ✔" : "Танд байгаа", GREEN));
                hint = cat == Cosmetic.Category.EMOJI ? "Чатад бичиж ашигла" : on ? "Дарж тайлах" : "Дарж зүүх";
            } else {
                boolean forCoins = styles().coinBuyable(c);
                if (forCoins) info.add(Menu.kv("Зоос:", fmt(c.price()), coins >= c.price() ? GREEN : RED));
                if (c.creditPrice() > 0) info.add(Menu.kv("Кредит:", fmt(c.creditPrice()) + " ✦", s.credits() >= c.creditPrice() ? GREEN : RED));
                if (c.creditPrice() > 0 && !forCoins) info.add(Menu.kv("Авах:", "Зөвхөн кредитээр · /buy", SKY));
                if (c.source().startsWith("level:")) info.add(Menu.kv("Авах:", "Түвшин " + c.source().substring(6) + " · /lvlup", PURPLE));
                String earnedBy = earnedBy(c);
                if (earnedBy != null) info.add(Menu.kv("Авах:", earnedBy, PURPLE));
                hint = forCoins ? "Зүүн: " + fmt(c.price()) + " зоос зоосоор · Баруун: " + fmt(c.creditPrice()) + " ✦ кредитээр"
                        : c.creditPrice() > 0 ? "Дарж " + fmt(c.creditPrice()) + " ✦ кредитээр авах"
                        : "achievement".equals(c.source()) ? "Амжилтын шагнал · /achievements" : "pass".equals(c.source()) ? "Хуралдайн Замын шагнал · /pass"
                        : "referral".equals(c.source()) ? "Урилгын шагнал · /urih" : "Түвшний шагнал";
            }
            body.add(Component.text("Shift + зүүн дарж урьдчилан харна", NamedTextColor.GRAY, TextDecoration.BOLD));
            Material icon = owned ? CAT_ICON.get(cat) : Material.GRAY_DYE;
            ItemStack it = Menu.item(icon, name, Menu.lore(accent, body, info, hint));
            if (on) Menu.glow(it);
            m.set(slot++, it, (pl, click) -> {
                if (click == ClickType.SHIFT_LEFT) {
                    previewCosmetic(pl, c);
                    return;
                }
                boolean forCoins = styles().coinBuyable(c);
                StyleService.Currency cur = (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT || !forCoins)
                        ? StyleService.Currency.CREDITS : StyleService.Currency.COINS;
                if (!owned && !forCoins && c.creditPrice() == 0) return;
                styles().buyOrEquip(pl, c, cur, () -> category(pl, cat));
            });
        }
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Гоёл", GOLD), List.of()), (pl, c) -> cosmetics(pl));
        m.set(49, Menu.item(Material.GOLD_INGOT, Menu.title("Хэтэвч", GOLD), List.of(Menu.kv("Зоос:", fmt(coins), GOLD),
                Menu.kv("Кредит:", fmt(s.credits()) + " ✦", SKY))), null);
        if (cat != Cosmetic.Category.EMOJI) {
            m.set(53, Menu.item(Material.BARRIER, Menu.title("Тайлах", RED), List.of(b("Энэ ангиллын гоёлыг тайлна."))), (pl, c) -> {
                styles().of(pl.getUniqueId()).equip(cat, null);
                styles().onEquipChanged(pl);
                category(pl, cat);
            });
        }
        m.open(p);
    }

    // ================================================================== shop

    private record Supply(Material mat, int amount, long price, String name) {
    }

    private static final List<Supply> SUPPLIES = List.of(
            new Supply(Material.BREAD, 8, 20, "Боов x8"),
            new Supply(Material.COOKED_MUTTON, 8, 40, "Шарсан хонины мах x8"),
            new Supply(Material.COOKED_BEEF, 8, 45, "Шарсан үхрийн мах x8"), // the class bow needs no arrows
            new Supply(Material.TORCH, 16, 12, "Бамбар x16"),
            new Supply(Material.SHIELD, 1, 120, "Бамбай"),
            new Supply(Material.GOLDEN_CARROT, 4, 90, "Алтан лууван x4"),
            new Supply(Material.GOLDEN_APPLE, 1, 300, "Алтан алим"),
            new Supply(Material.SADDLE, 1, 250, "Эмээл"),
            new Supply(Material.LEAD, 2, 40, "Чөдөр x2"));

    /** Sell prices of SÜLD loot (definition id → coins each). */

    private Component wallet(Player p, PlayerProfile pr) {
        return Component.text(fmt(pr.currency()) + " зоос · " + fmt(styles().of(p.getUniqueId()).credits()) + " ✦", TextColor.fromHexString("#A4B0C8"));
    }

    private HubMenu.Card supplyCard(PlayerProfile pr, Supply sup) {
        ItemStack it = new ItemStack(sup.mat(), Math.min(sup.amount(), sup.mat().getMaxStackSize()));
        return HubMenu.card(Menu.title(sup.name(), GREEN), Menu.lore(GREEN, List.of(),
                List.of(Menu.kv("Үнэ:", fmt(sup.price()) + " зоос", pr.currency() >= sup.price() ? GREEN : RED)), "Дарж авах"), it,
                (pl, c) -> {
                    buySupply(pl, sup);
                    if (sup == SUPPLIES.get(SUPPLIES.size() - 1)) shopMore(pl);
                    else shop(pl);
                });
    }

    /** The Дэлгүүр page of the hub: eight supplies, their items in the wells; the second page holds the rest. */
    public void shop(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        List<HubMenu.Card> cards = new ArrayList<>();
        for (Supply sup : SUPPLIES.subList(0, 8)) cards.add(supplyCard(pr, sup));
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_SHOP, 3, wallet(p, pr), cards, null, this::main, null, this::shopMore), hubTabs());
    }

    /** Page two: the last supply, selling, cosmetics, the credit store, the smith's book and board, the wallet. */
    public void shopMore(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        List<HubMenu.Card> cards = new ArrayList<>();
        cards.add(supplyCard(pr, SUPPLIES.get(SUPPLIES.size() - 1)));
        cards.add(HubMenu.card(Menu.title("Олз зарах", GOLD), Menu.lore(GOLD,
                List.of(b("Цүнхэн дэх чонын арьс, баавгайн арьс,"), b("хор, мөсөн чулуу... бүгдийг зарна.")),
                List.of(Menu.kv("Үнэ:", "зэрэг, түвшнээс хамаарна", GOLD)), "Дарж бүгдийг зарах"), null, (pl, c) -> {
            sellAll(pl);
            shopMore(pl);
        }));
        cards.add(HubMenu.card("Зарах цонх", "Олз, зэвсэг, хуяг үнэтэйгээ — сонгоод зарна.", (pl, c) -> sell(pl)));
        cards.add(HubMenu.card("Гоёл", "Цол, нэрийн өнгө, чатын өнгө...", (pl, c) -> cosmetics(pl)));
        cards.add(HubMenu.card("Кредит дэлгүүр", "Сүлд Кредит — зөвхөн гоёлд (/buy).", (pl, c) -> buy(pl)));
        cards.add(HubMenu.card("Жорын ном", "Дархны жорууд, дарж урлана (/item recipes).", (pl, c) -> {
            pl.closeInventory();
            pl.performCommand("item recipes");
        }));
        cards.add(HubMenu.card("Захиалгын самбар", "Бусдаар урлуулах, хөлс (/order).", (pl, c) -> {
            pl.closeInventory();
            pl.performCommand("order");
        }));
        cards.add(new HubMenu.Card(Menu.title("Хэтэвч", GOLD), List.of(Menu.kv("Зоос:", fmt(pr.currency()), GOLD),
                Menu.kv("Кредит:", fmt(styles().of(p.getUniqueId()).credits()) + " ✦", SKY)), null));
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_SHOP2, 3, wallet(p, pr), cards, null, this::main, this::shop, null), hubTabs());
    }

    /**
     * The Аялал page of the hub ({@code /travel}): the five relays (a trip only from a relay, GAME-001), the
     * historic sites, the nearest ovoo and the way home.
     */
    public void travel(Player p) {
        var npcs = services.npcs;
        String here = npcs == null ? null : npcs.relayHere(p);
        List<HubMenu.Card> cards = new ArrayList<>();
        for (Map.Entry<String, String> r : mn.suld.plugin.npc.NpcService.relays().entrySet()) {
            boolean current = r.getKey().equals(here);
            List<Component> lore = new ArrayList<>();
            if (current) lore.add(Menu.line("Та энд байна."));
            else {
                lore.add(Menu.kv("Үнэ:", mn.suld.plugin.npc.NpcService.TRAVEL_COST + " зоос", GOLD));
                lore.add(here == null ? Component.text("Аялахын тулд өртөөнд зогс.", NamedTextColor.GRAY).font(StyleFormat.HD) : Menu.line("« Дарж аялах »"));
            }
            cards.add(new HubMenu.Card(Menu.title(r.getValue(), current ? GREEN : HubMenu.CYAN), lore, current || npcs == null ? null : (pl, c) -> {
                String at = npcs.relayHere(pl);
                if (at == null) {
                    pl.closeInventory();
                    pl.sendMessage(Messages.info("Өртөөнд очоод аял: хотын талбай эсвэл дөрвөн хаалга."));
                    return;
                }
                pl.closeInventory();
                npcs.travelTo(pl, r.getKey());
            }));
        }
        cards.add(HubMenu.card("Түүхэн газрууд", "Олсон газрууд, тэдний түүх.", (pl, c) -> questMenu.open(pl, 3)));
        var ovoo = services.ovoos == null ? null : services.ovoos.nearest(p.getLocation());
        cards.add(new HubMenu.Card(Menu.title("Овоо", GOLD), ovoo == null ? List.of(Menu.line("Ойролцоо овоо алга."))
                : List.of(Menu.kv("Хамгийн ойр:", ovoo[0] + ", " + ovoo[1], GOLD), Menu.kv("Зай:", Math.round(p.getLocation().distance(
                new org.bukkit.Location(p.getWorld(), ovoo[0], p.getLocation().getY(), ovoo[1]))) + " блок", SKY), Component.empty(),
                Menu.line("« Дарж луужин заана »")), ovoo == null ? null : (pl, c) -> {
            pl.setCompassTarget(new org.bukkit.Location(pl.getWorld(), ovoo[0], pl.getLocation().getY(), ovoo[1]));
            pl.closeInventory();
            pl.sendMessage(Messages.success("Луужин овоо руу заалаа."));
        }));
        cards.add(HubMenu.card("Хархорум руу", "/spawn — хотын талбай руу буцах.", (pl, c) -> {
            pl.closeInventory();
            pl.performCommand("spawn");
        }));
        String where = services.inCity(p.getLocation()) ? "Хархорум" : "Тал нутаг";
        Component row = Component.text(where + (here != null ? " · өртөө: " + mn.suld.plugin.npc.NpcService.relays().get(here) : " · өртөөнөөс хол"),
                TextColor.fromHexString("#A4B0C8"));
        HubMenu.open(p, new HubMenu.Spec(Glyphs.PAGE_TRAVEL, 5, row, cards, null, this::main, null, null), hubTabs());
    }

    /** The older shop window. */
    public void shopClassic(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        Menu m = new Menu(5, "Дэлгүүр · Shop", null);
        m.set(4, Menu.item(Material.GOLD_INGOT, Menu.title("Хэтэвч", GOLD), List.of(Menu.kv("Зоос:", fmt(pr.currency()), GOLD),
                Menu.kv("Кредит:", fmt(styles().of(p.getUniqueId()).credits()) + " ✦", SKY))), null);
        int slot = 10;
        for (Supply sup : SUPPLIES) {
            if (slot == 17) slot = 19;
            ItemStack it = Menu.item(sup.mat(), Menu.title(sup.name(), GREEN), Menu.lore(GREEN, List.of(),
                    List.of(Menu.kv("Үнэ:", fmt(sup.price()) + " зоос", pr.currency() >= sup.price() ? GREEN : RED)), "Дарж авах"));
            it.setAmount(Math.min(sup.amount(), it.getMaxStackSize()));
            m.set(slot++, it, (pl, c) -> buySupply(pl, sup));
        }
        m.set(29, Menu.item(Material.LEATHER, Menu.title("Олз зарах", GOLD), Menu.lore(GOLD,
                List.of(b("Цүнхэн дэх чонын арьс, баавгайн арьс,"), b("хор, мөсөн чулуу... бүгдийг зарна.")),
                List.of(Menu.kv("Үнэ:", "зэрэг, түвшнээс хамаарна", GOLD), Menu.kv("Яг үнэ:", "«Зарах цонх»", GOLD)), "Дарж бүгдийг зарах")),
                (pl, c) -> {
                    sellAll(pl);
                    shop(pl);
                });
        m.set(30, Menu.item(Material.GOLD_NUGGET, Menu.title("Зарах цонх", GOLD), Menu.lore(GOLD,
                List.of(b("Цүнхэн дэх олз, зэвсэг, хуяг, тушиг"), b("бүгд үнэтэйгээ харагдана — сонгоод зарна.")),
                List.of(), "Дарж нээх")), (pl, c) -> sell(pl));
        m.set(31, Menu.item(Material.ARMOR_STAND, Menu.title("Гоёл", PURPLE), Menu.lore(PURPLE,
                List.of(b("Цол, нэрийн өнгө, чатын өнгө...")), List.of(), "Дарж нээх")), (pl, c) -> cosmetics(pl));
        m.set(33, Menu.item(Material.EMERALD, Menu.title("Кредит дэлгүүр · /buy", SKY), Menu.lore(SKY,
                List.of(b("Сүлд Кредит — зөвхөн гоёлд.")), List.of(), "Дарж нээх")), (pl, c) -> buy(pl));
        m.set(36, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> main(pl));
        m.open(p);
    }

    private void buySupply(Player p, Supply sup) {
        if (services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс байхдаа худалдан авалт хийх боломжгүй."));
            return;
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        if (pr.currency() < sup.price()) {
            p.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + sup.price() + " зоос)."));
            return;
        }
        ItemStack give = new ItemStack(sup.mat(), sup.amount());
        if (!fits(p, give)) { // checked on a copy first: nothing is added, so nothing of the player's own is ever removed
            p.sendMessage(Messages.error("Цүнх дүүрэн байна."));
            return;
        }
        // COINS → ITEMS (PlayerDataSaves): charge first, give only once the coins are in the database
        pr.addCurrency(-sup.price());
        mn.suld.plugin.item.PlayerDataSaves.coinsForItem(plugin, services, p, pr, sup.price(), "buy " + sup.name(), pl -> {
            for (ItemStack left : pl.getInventory().addItem(give.clone()).values()) pl.getWorld().dropItemNaturally(pl.getLocation(), left);
            pl.sendMessage(Messages.success("Авлаа: " + sup.name() + " (-" + sup.price() + " зоос)"));
            shop(pl);
        });
    }

    /** Whether the whole stack fits into the player's storage, tested on a copy. */
    private static boolean fits(Player p, ItemStack stack) {
        org.bukkit.inventory.Inventory sim = org.bukkit.Bukkit.createInventory(null, 36);
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (int i = 0; i < storage.length && i < 36; i++) sim.setItem(i, storage[i] == null ? null : storage[i].clone());
        return sim.addItem(stack.clone()).isEmpty();
    }

    /** Bag slot a player clicked once in the sell window (gear sells on the second click). */
    private final Map<java.util.UUID, Integer> sellArmed = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The sell window (/shop → Зарах цонх): every item in the bag a merchant buys, with its price ("Үнэ" on the
     * tooltip is what it pays). Loot materials sell on a click; gear asks for a second click on the same item.
     * Class gear, relics, forged items and Legendary-or-better gear never appear (they cannot be sold: salvage them).
     */
    public void sell(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        Integer armed = sellArmed.get(p.getUniqueId());
        Menu m = new Menu(6, "Зарах · Sell", null);
        ItemStack[] inv = p.getInventory().getStorageContents();
        int shown = 0;
        long all = 0;
        for (int i = 0; i < inv.length && shown < 45; i++) {
            ItemStack it = inv[i];
            long unit = unitPrice(it);
            if (unit <= 0) continue;
            final int bag = i;
            final ItemStack seen = it.clone();
            long price = unit * it.getAmount();
            if (material(it)) all += price;
            ItemStack icon = it.clone();
            var meta = icon.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Menu.kv("Зарах үнэ:", fmt(price) + " зоос" + (it.getAmount() > 1 ? "  (" + fmt(unit) + " × " + it.getAmount() + ")" : ""), GOLD));
            lore.add(b(armed != null && armed == bag ? "▶ Дахин дарж зарахаа батал" : material(it) ? "Дарж зарах" : "Дарж сонгоод, дахин дарж зарна"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            m.set(shown++, icon, (pl, c) -> sellSlot(pl, bag, seen));
        }
        if (shown == 0) m.set(22, Menu.item(Material.BARRIER, Menu.title("Зарах зүйл алга", RED), Menu.lore(RED,
                List.of(b("Мангасаас унасан олз, хэрэгсэл энд гарна."), b("Ангийн эд, дурсгалыг зарахгүй.")), List.of(), null)), null);
        final long allMaterials = all;
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Дэлгүүр", GOLD), List.of()), (pl, c) -> shop(pl));
        m.set(49, Menu.item(Material.LEATHER, Menu.title("Бүх олзыг зарах", GOLD), Menu.lore(GOLD,
                List.of(b("Зөвхөн материал (арьс, хор, чулуу...)."), b("Хэрэгслийг нэг нэгээр нь сонгож зарна.")),
                List.of(Menu.kv("Нийт:", fmt(allMaterials) + " зоос", GOLD)), "Дарж бүгдийг зарах")), (pl, c) -> {
            sellAll(pl);
            sell(pl);
        });
        m.set(53, Menu.item(Material.GOLD_INGOT, Menu.title("Хэтэвч", GOLD), List.of(Menu.kv("Зоос:", fmt(pr.currency()), GOLD))), null);
        m.open(p);
    }

    /** What a merchant pays for one of this stack, 0 when it is not for sale. */
    private long unitPrice(ItemStack it) {
        if (it == null || it.getType().isAir()) return 0;
        ItemInstance ii = services.items().read(it).orElse(null);
        if (ii == null || mn.suld.plugin.item.SoulboundGuard.soulbound(ii)) return 0;
        if (services.relics().items().isRelic(it)) return 0;
        mn.suld.api.item.ItemDefinition idef = mn.suld.plugin.content.SuldContent.definitionFor(ii.definitionId());
        if (idef == null || services.itemService().check(it).verdict() == mn.suld.plugin.item.ItemService.Verdict.FORGED) return 0;
        return mn.suld.api.item.ItemEconomy.sellPrice(idef, ii);
    }

    private boolean material(ItemStack it) {
        ItemInstance ii = services.items().read(it).orElse(null);
        mn.suld.api.item.ItemDefinition idef = ii == null ? null : mn.suld.plugin.content.SuldContent.definitionFor(ii.definitionId());
        return idef != null && idef.type() == mn.suld.api.item.ItemType.MATERIAL;
    }

    private void sellSlot(Player p, int bag, ItemStack seen) {
        if (services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс байхдаа эд зүйл зарах боломжгүй."));
            return;
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        ItemStack now = p.getInventory().getItem(bag);
        // the bag changed since the window was drawn (moved, split, swapped): redraw, never sell something else
        if (pr == null || now == null || !now.isSimilar(seen) || now.getAmount() != seen.getAmount()) {
            sellArmed.remove(p.getUniqueId());
            sell(p);
            return;
        }
        long unit = unitPrice(now);
        if (unit <= 0) {
            sell(p);
            return;
        }
        Integer armed = sellArmed.get(p.getUniqueId());
        if (!material(now) && (armed == null || armed != bag)) {
            sellArmed.put(p.getUniqueId(), bag);
            sell(p);
            return;
        }
        sellArmed.remove(p.getUniqueId());
        long price = unit * now.getAmount();
        ItemInstance ii = services.items().read(now).orElse(null);
        p.getInventory().setItem(bag, null);
        pr.addCurrency(price);
        mn.suld.plugin.item.PlayerDataSaves.itemsForCoins(plugin, services, p, pr, "sell " + (ii == null ? "?" : ii.definitionId())); // item off disk, then the coins
        services.audit().record(mn.suld.api.audit.AuditEvent.of(p.getUniqueId().toString(), "item.sell",
                (ii == null ? "?" : ii.definitionId() + "-" + ii.uuid()), now.getAmount() + " for " + price));
        p.sendMessage(Messages.success("Зарагдлаа: +" + fmt(price) + " зоос"));
        services.hud().update(p, pr);
        sell(p);
    }

    private void sellAll(Player p) {
        if (services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс байхдаа эд зүйл зарах боломжгүй."));
            return;
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        long total = 0;
        int count = 0;
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (it == null) continue;
            ItemInstance ii = services.items().read(it).orElse(null);
            // "sell all" sells loot materials only: gear is sold one by one (/item sell), never by accident
            if (ii == null || mn.suld.plugin.item.SoulboundGuard.soulbound(ii)) continue;
            mn.suld.api.item.ItemDefinition idef = mn.suld.plugin.content.SuldContent.definitionFor(ii.definitionId());
            if (idef == null || idef.type() != mn.suld.api.item.ItemType.MATERIAL) continue;
            if (services.itemService().check(it).verdict() == mn.suld.plugin.item.ItemService.Verdict.FORGED) continue;
            long price = mn.suld.api.item.ItemEconomy.sellPrice(idef, ii);
            if (price <= 0) continue;
            total += price * it.getAmount();
            count += it.getAmount();
            p.getInventory().setItem(i, null);
        }
        if (count == 0) {
            p.sendMessage(Messages.info("Зарах олз алга."));
            return;
        }
        pr.addCurrency(total);
        mn.suld.plugin.item.PlayerDataSaves.itemsForCoins(plugin, services, p, pr, "sell all"); // emptied slots off disk, then the coins
        p.sendMessage(Messages.success(count + " олз зарж +" + fmt(total) + " зоос авлаа."));
    }

    // ================================================================== credit store

    public void buy(Player p) {
        PlayerStyle s = styles().of(p.getUniqueId());
        Menu m = new Menu(4, "Кредит дэлгүүр · Store", null);
        m.set(4, Menu.item(Material.EMERALD, Menu.title("Таны кредит", SKY), List.of(Menu.kv("Үлдэгдэл:", fmt(s.credits()) + " ✦", SKY))), null);
        List<Map<?, ?>> packs = plugin.getConfig().getMapList("store.packages");
        Material[] icons = {Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.RAW_GOLD, Material.GOLD_BLOCK, Material.CHEST, Material.ENDER_CHEST};
        String url = plugin.getConfig().getString("branding.store-url", "");
        int slot = 10;
        int i = 0;
        mn.suld.plugin.payment.PaymentService pay = services.payments;
        if (pay != null && pay.enabled()) {
            // the Bonum gateway is on: a click asks Bonum for a QR invoice (docs/PAYMENT_ARCHITECTURE.md)
            for (mn.suld.api.payment.StorePackage pkg : pay.packages()) {
                if (slot > 16) break;
                m.set(slot++, Menu.item(icons[Math.min(i++, icons.length - 1)], Menu.title(pkg.credits() + " Сүлд Кредит", GOLD), Menu.lore(GOLD,
                        List.of(b("Зөвхөн гоёлд зарцуулагдана.")), List.of(Menu.kv("Үнэ:", pkg.price(), GREEN)), "Дарж QR авах (Bonum)")),
                        (pl, c) -> pay.buy(pl, pkg));
            }
            packs = List.of();
        }
        for (Map<?, ?> pack : packs) {
            if (slot > 16) break;
            String credits = String.valueOf(pack.get("credits"));
            String price = String.valueOf(pack.get("price"));
            m.set(slot++, Menu.item(icons[Math.min(i++, icons.length - 1)], Menu.title(credits + " Сүлд Кредит", GOLD), Menu.lore(GOLD,
                    List.of(b("Зөвхөн гоёлд зарцуулагдана.")), List.of(Menu.kv("Үнэ:", price, GREEN)), url.isBlank() ? "Тун удахгүй" : "Дарж дэлгүүр нээх")),
                    (pl, c) -> storeLink(pl, url));
        }
        m.set(22, Menu.item(Material.BOOK, Menu.title("Кредит гэж юу вэ?", SKY), List.of(
                b("Сүлд Кредит нь серверийг дэмжих"), b("бодит мөнгөөр авах валют."),
                b("Зөвхөн гоёл (цол, өнгө, мэндчилгээ) авна —"), b("тоглоомын хүч, эд зүйл худалдахгүй."))), null);
        m.set(27, Menu.item(Material.ARROW, Menu.title("« Гоёл", GOLD), List.of()), (pl, c) -> cosmetics(pl));
        m.open(p);
    }

    private void storeLink(Player p, String url) {
        p.closeInventory();
        if (url.isBlank()) {
            p.sendMessage(Messages.info("Кредит дэлгүүр тун удахгүй нээгдэнэ."));
            return;
        }
        p.sendMessage(Messages.accent("Дэлгүүр: ").append(Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))));
    }
}
