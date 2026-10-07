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
        this.plugin = plugin;
        this.services = services;
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

    public void main(Player p) {
        Menu m = new Menu(6, "Сүлд Цэс", Glyphs.GUI_MAIN);
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
            new Step(Material.WRITABLE_BOOK, "3. Анхны эрэл", GOLD, List.of("«Сүлдний Зам»: 18 бүлэг эрэл.", "«Анхны Ан»: Говийн 3 чоныг ан.", "Хотын хаалгаар гараад тал руу."), "/quest"),
            new Step(Material.LEATHER, "4. Олз ба зоос", GREEN, List.of("Чонын арьс, баавгайн арьсаа", "дэлгүүрт зарж зоос ол."), "/shop"),
            new Step(Material.EXPERIENCE_BOTTLE, "5. Түвшин ба шагнал", GREEN, List.of("EXP цуглуулж түвшин ахи.", "Түвшин бүрийн шагналаа /lvlup-аар ав.", "Өдөр бүр /daily — өдрийн шагнал."), "/lvlup"),
            new Step(Material.GOLDEN_HELMET, "6. Цол ахиулах", PURPLE, List.of("Ард → Цэрэг → Аравт → ... → Хаан.", "Түвшин + зоос шаардлагатай."), "/rankup"),
            new Step(Material.MOSSY_COBBLESTONE, "7. Агуй ба бүлэг", NamedTextColor.GRAY, List.of("Бүлэг байгуулж агуйн аянд яв.", "/party invite <нэр>, /dungeon list"), "/dungeon list"),
            new Step(Material.WHITE_BANNER, "8. Овог", NamedTextColor.AQUA, List.of("Овог байгуулж эсвэл нэгдэж", "EXP нэмэгдэл ав."), "/clan top"),
            new Step(Material.NETHER_STAR, "9. Реликс", GOLD, List.of("Дэлхийд ганц домогт эд зүйлс.", "Эзэмшигч хаана ч халдлагад өртөнө!"), "/relic hint"));

    public void tutorial(Player p) {
        Menu m = new Menu(3, "Заавар · Tutorial", null);
        for (int i = 0; i < STEPS.size(); i++) {
            Step s = STEPS.get(i);
            List<Component> body = new ArrayList<>();
            for (String t : s.text()) body.add(b(t));
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

    public void help(Player p) {
        Menu m = new Menu(3, "Тусламж · Help", null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < HELP.size(); i++) {
            HelpEntry h = HELP.get(i);
            List<Component> body = new ArrayList<>();
            for (String l : h.lines()) body.add(b(l));
            m.set(slots[i], Menu.item(h.icon(), Menu.title(h.title(), h.color()), Menu.lore(h.color(), body, List.of(), "Дарж унших")),
                    (pl, c) -> {
                        pl.closeInventory();
                        pl.performCommand("help " + h.page());
                    });
        }
        m.set(18, Menu.item(Material.ARROW, Menu.title("« Заавар", GREEN), List.of()), (pl, c) -> tutorial(pl));
        m.set(26, Menu.item(Material.CLOCK, Menu.title("Сүлд Цэс", GOLD), List.of()), (pl, c) -> main(pl));
        m.open(p);
    }

    // ================================================================== profile / quests

    public void profile(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        PlayerStyle s = styles().of(p.getUniqueId());
        Progression prog = pr.progression();
        Menu m = new Menu(5, "Дүр · " + p.getName(), null);
        ItemStack head = Menu.item(Material.PLAYER_HEAD, StyleFormat.name(p, s).decoration(TextDecoration.BOLD, true), List.of(
                Menu.kv("Анги:", pr.playerClass().map(c -> c.displayName()).orElse("сонгоогүй"), RED),
                Menu.kv("Цол:", s.rank().displayName(), StyleFormat.hex(s.rank().color())),
                Menu.kv("Түвшин:", prog.level() + " (" + Math.round(services.progression().engine().progressFraction(prog) * 100) + "%)", GREEN),
                Menu.kv("Зоос:", fmt(pr.currency()) + " ₮", GOLD),
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
        };
    }

    /** The storyline: every chapter as a card — done ✔, active (glowing, with progress) or locked. */
    /** Told whenever the quest screen opens (the tutorial's step); set by the plugin. */
    public static volatile java.util.function.Consumer<Player> onQuests = p -> { };

    public void quests(Player p) {
        onQuests.accept(p);
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        var chain = services.quests().chain();
        var state = pr.questState();
        int done = chain.completedCount(state);
        Menu m = new Menu(6, "Сүлдний Зам · " + done + "/" + chain.size(), null);
        m.set(4, Menu.item(Material.WRITABLE_BOOK, Menu.title("Сүлдний Зам", GOLD), List.of(
                b("Хархорумаас Алтай хүртэлх үйл явдал."),
                b("Бүлэг бүр дуусмагц дараагийнх нь эхэлнэ."),
                Menu.kv("Явц:", done + "/" + chain.size() + " бүлэг", GREEN))), null);
        for (int i = 0; i < chain.size() && i < CHAPTER_SLOTS.length; i++) {
            var def = chain.chapters().get(i);
            var lore = mn.suld.plugin.content.QuestContent.lore(def.id());
            boolean finished = i < done;
            boolean active = !finished && def.id().equals(state.questId());
            TextColor c = finished ? GREEN : active ? GOLD : NamedTextColor.GRAY;
            List<Component> info = new ArrayList<>();
            info.add(Menu.kv("Өгсөн:", lore.giver(), SKY));
            if (active) info.add(Menu.kv("Явц:", state.progress() + "/" + def.requiredCount(), GOLD));
            info.add(Menu.kv("Шагнал:", def.expReward() + " EXP · " + def.currencyReward() + " ₮", GREEN));
            List<Component> body = new ArrayList<>();
            body.add(b(finished || active ? def.description() : "???"));
            if (active && !lore.hint().isEmpty()) body.add(b("➜ " + lore.hint()));
            ItemStack it = Menu.item(finished ? Material.LIME_DYE : active ? questIcon(def.type()) : Material.GRAY_DYE,
                    Component.text((i + 1) + ". " + (finished || active ? def.title() : "Түгжээтэй"), c, TextDecoration.BOLD),
                    Menu.lore(c, body, info, finished ? "Дууссан ✔" : active ? "Идэвхтэй эрэл" : "Өмнөх бүлгээ дуусга"));
            if (active) Menu.glow(it);
            m.set(CHAPTER_SLOTS[i], it, active ? (pl, cl) -> {
                pl.closeInventory();
                pl.performCommand("quest info");
            } : null);
        }
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> main(pl));
        m.set(48, Menu.item(Material.MOSSY_COBBLESTONE, Menu.title("Агуйнууд", SKY), Menu.lore(SKY,
                List.of(b("Агуйн аян: бүлгээрээ яв."), b("Тал нутагт /dungeon enter.")), List.of(), "Жагсаалт")), (pl, c) -> {
            pl.closeInventory();
            pl.performCommand("dungeon list");
        });
        m.set(50, Menu.item(Material.PLAYER_HEAD, Menu.title("Бүлэг", SKY), Menu.lore(SKY, List.of(b("/party invite <нэр>")), List.of(), "Мэдээлэл")),
                (pl, c) -> {
                    pl.closeInventory();
                    pl.performCommand("party info");
                });
        m.open(p);
    }

    // ================================================================== skills

    private static final Material[] SPELL_ICON = {Material.BLAZE_POWDER, Material.FIRE_CHARGE, Material.FEATHER, Material.NETHER_STAR};

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
                b("Нөөц цаг ба тулаанаар нөхөгдөнө."))), null);
        int slot = 10;
        for (mn.suld.api.skill.Spell sp : mn.suld.api.skill.Spell.of(clazz)) {
            boolean open = level >= sp.unlockLevel();
            TextColor c = open ? GREEN : RED;
            String combo = String.join("-", sp.combo().split(""));
            List<Component> info = new ArrayList<>();
            info.add(Menu.kv("Хослол:", combo, GOLD));
            info.add(Menu.kv("Нөөц:", String.valueOf(sp.cost()), SKY));
            info.add(Menu.kv("Хүлээлт:", sp.cooldownSeconds() + " сек", SKY));
            if (sp.damageMultiplier() > 0) info.add(Menu.kv("Хүч:", "x" + sp.damageMultiplier() + " ATK", RED));
            info.add(Menu.kv("Түвшин:", String.valueOf(sp.unlockLevel()), c));
            ItemStack it = Menu.item(open ? SPELL_ICON[sp.slot() - 1] : Material.GRAY_DYE,
                    Component.text(sp.slot() + ". " + sp.displayName(), open ? GOLD : NamedTextColor.GRAY, TextDecoration.BOLD),
                    Menu.lore(c, List.of(b(sp.description())), info, open ? "Хослолоор хэрэглэ" : "Түвшин " + sp.unlockLevel() + " хүрэх"));
            if (open) Menu.glow(it);
            m.set(slot, it, null);
            slot += 2;
        }
        m.set(18, Menu.item(Material.ARROW, Menu.title("« Дүр", GOLD), List.of()), (pl, c) -> profile(pl));
        if (skillMap != null) {
            m.set(22, Menu.item(Material.FILLED_MAP, Menu.title("Чадварын газрын зураг", TextColor.fromHexString("#2AC4B4")),
                    List.of(b("Оноо зарцуулж шившлэгээ хөгжүүл."))), (pl, c) -> skillMap.open(pl));
        }
        m.open(p);
    }

    // ================================================================== rank ladder

    public void rankup(Player p) {
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
            info.add(Menu.kv("Үнэ:", fmt(r.cost()) + " ₮", pr.currency() >= r.cost() ? GREEN : RED));
            String hint = have ? "Танд байгаа ✔" : next ? "Дарж цол ахиулах" : "Өмнөх цолоо ав";
            ItemStack it = Menu.item(icons[r.ordinal()], Component.text(r.displayName(), c, TextDecoration.BOLD),
                    Menu.lore(c, List.of(b(r.lore())), info, hint));
            if (have || next) Menu.glow(it);
            m.set(slots[r.ordinal()], it, next ? (pl, ct) -> {
                if (styles().rankUp(pl) == Rank.Check.OK) rankup(pl);
            } : null);
        }
        m.set(27, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> main(pl));
        m.open(p);
    }

    // ================================================================== level rewards

    public void lvlup(Player p) {
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
            info.add(Menu.kv("Зоос:", "+" + fmt(r.coins()) + " ₮", GOLD));
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
                Menu.kv("Зоос:", fmt(pr == null ? 0 : pr.currency()) + " ₮", GOLD), Menu.kv("Кредит:", fmt(s.credits()) + " ✦", SKY))), null);
        m.set(26, Menu.item(Material.EMERALD, Menu.title("Кредит · /buy", SKY), Menu.lore(SKY,
                List.of(b("Сүлд Кредитээр гоёл авна.")), List.of(), "Дарж нээх")), (pl, c) -> buy(pl));
        m.set(35, Menu.item(Material.CHEST, Menu.title("Дэлгүүр · /shop", GREEN), List.of()), (pl, c) -> shop(pl));
        m.set(44, Menu.item(Material.BARRIER, Menu.title("Бүгдийг тайлах", RED), List.of(b("Зүүсэн бүх гоёлыг тайлна."))), (pl, c) -> {
            PlayerStyle st = styles().of(pl.getUniqueId());
            for (Cosmetic.Category cat : Cosmetic.Category.values()) st.equip(cat, null);
            styles().onEquipChanged(pl);
            cosmetics(pl);
        });
        m.open(p);
    }

    /** A sample of what the cosmetic looks like, sent to the viewer's chat (effects play around them instead). */
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
                if (c.sold()) info.add(Menu.kv("Зоос:", fmt(c.price()) + " ₮", coins >= c.price() ? GREEN : RED));
                if (c.creditPrice() > 0) info.add(Menu.kv("Кредит:", fmt(c.creditPrice()) + " ✦", s.credits() >= c.creditPrice() ? GREEN : RED));
                if (c.source().startsWith("level:")) info.add(Menu.kv("Авах:", "Түвшин " + c.source().substring(6) + " · /lvlup", PURPLE));
                hint = c.sold() ? "Зүүн: зоосоор · Баруун: кредитээр" : c.creditPrice() > 0 ? "Дарж кредитээр авах" : "Түвшний шагнал";
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
                StyleService.Currency cur = (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT || !c.sold())
                        ? StyleService.Currency.CREDITS : StyleService.Currency.COINS;
                if (!owned && !c.sold() && c.creditPrice() == 0) return;
                styles().buyOrEquip(pl, c, cur, () -> category(pl, cat));
            });
        }
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Гоёл", GOLD), List.of()), (pl, c) -> cosmetics(pl));
        m.set(49, Menu.item(Material.GOLD_INGOT, Menu.title("Хэтэвч", GOLD), List.of(Menu.kv("Зоос:", fmt(coins) + " ₮", GOLD),
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
            new Supply(Material.ARROW, 32, 30, "Сум x32"),
            new Supply(Material.TORCH, 16, 12, "Бамбар x16"),
            new Supply(Material.SHIELD, 1, 120, "Бамбай"),
            new Supply(Material.GOLDEN_CARROT, 4, 90, "Алтан лууван x4"),
            new Supply(Material.GOLDEN_APPLE, 1, 300, "Алтан алим"),
            new Supply(Material.SADDLE, 1, 250, "Эмээл"),
            new Supply(Material.LEAD, 2, 40, "Чөдөр x2"));

    /** Sell prices of SÜLD loot (definition id → coins each). */

    public void shop(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        Menu m = new Menu(5, "Дэлгүүр · Shop", null);
        m.set(4, Menu.item(Material.GOLD_INGOT, Menu.title("Хэтэвч", GOLD), List.of(Menu.kv("Зоос:", fmt(pr.currency()) + " ₮", GOLD),
                Menu.kv("Кредит:", fmt(styles().of(p.getUniqueId()).credits()) + " ✦", SKY))), null);
        int slot = 10;
        for (Supply sup : SUPPLIES) {
            if (slot == 17) slot = 19;
            ItemStack it = Menu.item(sup.mat(), Menu.title(sup.name(), GREEN), Menu.lore(GREEN, List.of(),
                    List.of(Menu.kv("Үнэ:", fmt(sup.price()) + " ₮", pr.currency() >= sup.price() ? GREEN : RED)), "Дарж авах"));
            it.setAmount(Math.min(sup.amount(), it.getMaxStackSize()));
            m.set(slot++, it, (pl, c) -> buySupply(pl, sup));
        }
        m.set(29, Menu.item(Material.LEATHER, Menu.title("Олз зарах", GOLD), Menu.lore(GOLD,
                List.of(b("Цүнхэн дэх чонын арьс, баавгайн арьс,"), b("хор, мөсөн чулуу... бүгдийг зарна.")),
                List.of(Menu.kv("Чонын арьс:", "8 ₮", GOLD), Menu.kv("Баавгайн арьс:", "25 ₮", GOLD)), "Дарж бүгдийг зарах")),
                (pl, c) -> {
                    sellAll(pl);
                    shop(pl);
                });
        m.set(31, Menu.item(Material.ARMOR_STAND, Menu.title("Гоёл", PURPLE), Menu.lore(PURPLE,
                List.of(b("Цол, нэрийн өнгө, чатын өнгө...")), List.of(), "Дарж нээх")), (pl, c) -> cosmetics(pl));
        m.set(33, Menu.item(Material.EMERALD, Menu.title("Кредит дэлгүүр · /buy", SKY), Menu.lore(SKY,
                List.of(b("Сүлд Кредит — зөвхөн гоёлд.")), List.of(), "Дарж нээх")), (pl, c) -> buy(pl));
        m.set(36, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> main(pl));
        m.open(p);
    }

    private void buySupply(Player p, Supply sup) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        if (pr.currency() < sup.price()) {
            p.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + sup.price() + " ₮)."));
            return;
        }
        ItemStack give = new ItemStack(sup.mat(), sup.amount());
        if (!fits(p, give)) { // checked on a copy first: nothing is added, so nothing of the player's own is ever removed
            p.sendMessage(Messages.error("Цүнх дүүрэн байна."));
            return;
        }
        p.getInventory().addItem(give.clone());
        pr.addCurrency(-sup.price());
        services.profiles().save(pr);
        p.sendMessage(Messages.success("Авлаа: " + sup.name() + " (-" + sup.price() + " ₮)"));
        shop(p);
    }

    /** Whether the whole stack fits into the player's storage, tested on a copy. */
    private static boolean fits(Player p, ItemStack stack) {
        org.bukkit.inventory.Inventory sim = org.bukkit.Bukkit.createInventory(null, 36);
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (int i = 0; i < storage.length && i < 36; i++) sim.setItem(i, storage[i] == null ? null : storage[i].clone());
        return sim.addItem(stack.clone()).isEmpty();
    }

    private void sellAll(Player p) {
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
        services.profiles().save(pr);
        mn.suld.plugin.item.PlayerDataSaves.soon(plugin, p); // coins and the emptied slots reach disk together (no crash dupe)
        p.sendMessage(Messages.success(count + " олз зарж +" + fmt(total) + " ₮ авлаа."));
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
