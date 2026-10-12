package mn.suld.plugin.gui;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestState;
import mn.suld.api.region.Navigation;
import mn.suld.api.region.Area;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.world.site.HistoricSite;
import mn.suld.api.world.site.SiteKind;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.QuestContent;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.content.Content;
import mn.suld.plugin.quest.QuestTracker;
import mn.suld.plugin.region.HistoricSiteService;
import mn.suld.plugin.ui.StyleFormat;
import mn.suld.plugin.ui.Glyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Сүлдний Зам ({@code /quest}), a quest book in the akuma style with the Ikh Mongol colours: three tabs on top (Act I
 * «Сүлдний Зам» 1–18, Act II «Хөх Сүлдний Зам» 19–42, the historic sites), a grid of cards, and on every card the
 * navy-and-gold tooltip ({@code suld:mongol}) with the story, an «Мэдээлэл» block, the reward, a click hint and a
 * status badge. It opens on the tab of the current chapter. The painted maps of the two acts ({@code gui_quests},
 * {@code gui_quests2}) stay one button away ({@link #openMap}). The bottom row: menu, tracker on/off, dungeons, the
 * journey, party, map, close.
 */
public final class QuestMenu {

    /** Act I chapter slots (row × 9 + column), mirroring gen_ui.QUEST_SLOTS. */
    static final int[] SLOTS = {36, 28, 38, 30, 40, 32, 42, 34, 44, 17, 7, 15, 5, 13, 3, 11, 1, 9};
    /** Act II chapter slots (chapters 19–42), mirroring gen_ui.QUEST2_SLOTS. */
    static final int[] SLOTS2 = {36, 28, 38, 30, 40, 32, 42, 34, 44, 26, 16, 24, 14, 22, 12, 20, 10, 18, 0, 1, 2, 3, 4, 5};
    /** Chapters in Act I. */
    static final int ACT_ONE = 18;

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor SKY = TextColor.fromHexString("#9FF3FF");
    private static final TextColor GREEN = TextColor.fromHexString("#7CE07C");
    private static final TextColor STORY = TextColor.fromHexString("#E8DCC0");
    private static final TextColor GREY = TextColor.fromHexString("#B8B8B8");
    private static final TextColor GOLD_D = TextColor.fromHexString("#C08A2E");

    private final SuldServices services;
    private QuestTracker tracker;

    public QuestMenu(SuldServices services) {
        this.services = services;
    }

    public void tracker(QuestTracker tracker) {
        this.tracker = tracker;
    }

    private static Material icon(mn.suld.api.quest.QuestType t) {
        return switch (t) {
            case KILL_MOB -> Material.IRON_SWORD;
            case REACH_LEVEL -> Material.EXPERIENCE_BOTTLE;
            case COLLECT_ITEM -> Material.LEATHER;
            case DISCOVER_LOCATION -> Material.FILLED_MAP;
            case COMPLETE_DUNGEON -> Material.MOSSY_COBBLESTONE;
        };
    }

    /** The land a chapter belongs to (the maps' tints). */
    private static String land(int index) {
        if (index >= ACT_ONE) {
            int i = index - ACT_ONE;
            return i < 4 ? "Хөвсгөл" : i < 10 ? "Зүүнгар, Тангудын хил" : i < 15 ? "Алс дорнын тал" : i < 18 ? "Хэнтий" : i < 22 ? "Өндөр Алтай" : "Ариун уул";
        }
        return index < 5 ? "Хэрлэн" : index < 9 ? "Говь" : index < 14 ? "Хангай" : "Алтай";
    }

    /** Opens the quest book on the act of the player's current chapter. */
    public void open(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        int done = services.quests().chain().completedCount(pr.questState());
        open(p, done >= ACT_ONE && services.quests().chain().size() > ACT_ONE ? 2 : 1);
    }

    // ------------------------------------------------------------------------------------------------ the book

    /** Tab 1 Act I, tab 2 Act II, tab 3 the historic sites. */
    public void open(Player p, int tab) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        var chain = services.quests().chain();
        QuestState state = pr.questState();
        int done = chain.completedCount(state);
        boolean hasTwo = chain.size() > ACT_ONE;
        if (tab == 2 && !hasTwo) tab = 1;
        if (tab < 1 || tab > 3) tab = 1;
        final int shownTab = tab;
        // short: the painted top strip has knots at its ends and in the middle; the title sits between the first two
        String title = tab == 3 ? seenCount(p) + "/" + sites().size() + " газар" : done + "/" + chain.size() + " бүлэг";
        Menu m = new Menu(6, Component.text(title, TextColor.fromHexString("#F2D58A")),
                tab == 2 ? Glyphs.GUI_QUESTBOOK2 : tab == 3 ? Glyphs.GUI_QUESTBOOK3 : Glyphs.GUI_QUESTBOOK1, 14);

        // row 0: the tabs (the background draws them; invisible buttons on top)
        tab(m, new int[]{0, 1, 2}, "Акт I · Сүлдний Зам", "1–" + Math.min(ACT_ONE, chain.size()) + "-р бүлэг: Хархорумаас Алтай хүртэл",
                Math.min(done, ACT_ONE) + " / " + Math.min(ACT_ONE, chain.size()) + " бүлэг", Glyphs.BADGE_Q_OPEN, 1);
        if (hasTwo) {
            tab(m, new int[]{3, 4, 5}, "Акт II · Хөх Сүлдний Зам", "19–" + chain.size() + "-р бүлэг: гадна нутаг, Тэнгэрийн Шат",
                    Math.max(0, done - ACT_ONE) + " / " + (chain.size() - ACT_ONE) + " бүлэг",
                    done >= ACT_ONE ? Glyphs.BADGE_Q_OPEN : Glyphs.BADGE_Q_LOCKED, 2);
        }
        tab(m, new int[]{6, 7, 8}, "Түүхэн Газрууд", "Монголын эртний дурсгалт газрууд, тэдгээр рүү зам",
                seenCount(p) + " / " + sites().size() + " газар үзсэн", Glyphs.BADGE_Q_OPEN, 3);

        // rows 1–4: the cards
        if (tab == 3) {
            List<HistoricSite> list = sites();
            Set<String> seen = services.historicSites == null ? Set.of() : services.historicSites.seen(p);
            for (int i = 0; i < list.size() && i < 36; i++) {
                HistoricSite s = list.get(i);
                m.set(9 + i, siteCard(p, s, seen.contains(s.id())), (pl, c) -> {
                    String where = siteWhere(pl, s.id());
                    pl.sendMessage(Component.text("✦ " + s.name() + (where == null ? "" : " · " + where), GOLD, TextDecoration.BOLD));
                });
            }
        } else {
            int first = tab == 2 ? ACT_ONE : 0, last = tab == 2 ? chain.size() : Math.min(ACT_ONE, chain.size());
            for (int i = first; i < last && i - first < 36; i++) {
                QuestDefinition def = chain.chapters().get(i);
                boolean finished = i < done;
                boolean active = !finished && def.id().equals(state.questId());
                m.set(9 + i - first, bookCard(p, def, i, finished, active, state), active ? (pl, c) -> {
                    if (tracker != null && !tracker.shown(pl)) tracker.toggle(pl);
                    pl.closeInventory();
                    pl.sendActionBar(Component.text("✦ Эрлийн заагч дээд талд — зүг, зайг нь дага", GOLD, TextDecoration.BOLD));
                } : null);
            }
        }

        // row 5: the toolbar
        m.set(45, Menu.mongol(Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of())), (pl, c) -> pl.performCommand("menu"));
        boolean shown = tracker == null || tracker.shown(p);
        m.set(47, Menu.mongol(Menu.item(shown ? Material.COMPASS : Material.CLOCK, Menu.title("Эрлийн заагч: " + (shown ? "асаалттай" : "унтраалттай"), SKY),
                Menu.lore(SKY, List.of(Menu.line("Дээд талын мөр зорилго руу зүг, зайг заана.")), List.of(), "Дарж " + (shown ? "унтраах" : "асаах")))), (pl, c) -> {
            if (tracker != null) tracker.toggle(pl);
            open(pl, shownTab);
        });
        m.set(48, Menu.mongol(Menu.item(Material.MOSSY_COBBLESTONE, Menu.title("Агуйн Шат", SKY), Menu.lore(SKY,
                List.of(Menu.line("Арван агуй, хаалга руу нь зам заана.")), List.of(), "Нээх"))), (pl, c) -> pl.performCommand("dungeon"));
        m.set(49, Menu.mongol(Menu.item(Material.WRITABLE_BOOK, Menu.title("Сүлдний Зам", GOLD), List.of(
                Menu.line("Хархорумаас Тэнгэрийн Шат хүртэлх аян."),
                Menu.line("Бүлэг бүр дуусмагц дараагийнх нь эхэлнэ."),
                Component.empty(),
                Menu.kv("Явц:", done + " / " + chain.size() + " бүлэг", GREEN),
                Menu.kv("Одоо:", done < chain.size() ? land(Math.min(done, chain.size() - 1)) : "Бүх бүлэг дууссан", GOLD)))), null);
        m.set(50, Menu.mongol(Menu.item(Material.PLAYER_HEAD, Menu.title("Бүлэг", SKY), Menu.lore(SKY,
                List.of(Menu.line("Найзаа урих: /party invite <нэр>")), List.of(), null))), null);
        if (tab != 3) {
            m.set(51, Menu.mongol(Menu.item(Material.FILLED_MAP, Menu.title("Газрын зураг", GOLD), Menu.lore(GOLD,
                    List.of(Menu.line("Энэ актын бүлгүүд зурган газрын зураг дээр.")), List.of(), "Дарж нээх"))), (pl, c) -> openMap(pl, shownTab));
        }
        m.set(53, Menu.mongol(Menu.item(Material.BARRIER, Menu.title("Хаах", GREY), List.of())), (pl, c) -> pl.closeInventory());
        m.open(p);
    }

    private void tab(Menu m, int[] slots, String name, String about, String progress, String badge, int tab) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(about, STORY));
        lore.add(Component.empty());
        lore.add(Component.text("Мэдээлэл", GOLD, TextDecoration.BOLD));
        lore.add(info("Явц:", progress, GREEN));
        lore.add(Component.empty());
        lore.add(clickHint("ДАРЖ НЭЭХ"));
        lore.add(StyleFormat.glyph(badge));
        ItemStack it = Menu.mongol(Menu.invisible(Component.text(name, GOLD, TextDecoration.BOLD), lore));
        for (int s : slots) m.set(s, it, (pl, c) -> open(pl, tab));
    }

    /** A chapter card: story, type, «Мэдээлэл», «Шагнал», click hint, status badge. */
    private ItemStack bookCard(Player p, QuestDefinition def, int index, boolean finished, boolean active, QuestState state) {
        TextColor c = finished ? GREEN : active ? GOLD : GREY;
        List<Component> body = new ArrayList<>();
        String badge;
        if (finished || active) {
            QuestContent.Lore lore = QuestContent.lore(def.id());
            for (String line : wrap(QuestContent.story(def.id()), 38)) body.add(Component.text(line, STORY));
            body.add(Component.empty());
            body.add(Menu.kv("Төрөл:", kind(def.type()), c));
            body.add(Component.empty());
            body.add(Component.text("Мэдээлэл", c, TextDecoration.BOLD));
            body.add(info("Юу хийх:", objective(def), NamedTextColor.WHITE));
            String where = where(p, def);
            if (where != null) body.add(info("Хаана:", where, SKY));
            if (active) body.add(info("Явц:", bar(state.progress(), def.requiredCount()) + " " + state.progress() + "/" + def.requiredCount(), GOLD));
            body.add(info("Өгсөн:", lore.giver(), SKY));
            body.add(info("Нутаг:", land(index), SKY));
            body.add(Component.empty());
            body.add(Component.text("Шагнал", c, TextDecoration.BOLD));
            body.add(info("+" + def.expReward(), "EXP", GREEN));
            body.add(info("+" + def.currencyReward(), "зоос", GOLD));
            if (active && !lore.hint().isEmpty()) {
                body.add(Component.empty());
                for (String line : wrap("➜ " + lore.hint(), 38)) body.add(Component.text(line, NamedTextColor.WHITE, TextDecoration.BOLD));
            }
            if (active) {
                body.add(Component.empty());
                body.add(clickHint("ДАРЖ ЗААГЧИЙГ АСААХ"));
            }
            badge = finished ? Glyphs.BADGE_Q_DONE : Glyphs.BADGE_Q_ACTIVE;
        } else {
            body.add(Component.text("Энэ бүлэг хараахан нээгдээгүй.", GREY));
            body.add(Component.empty());
            body.add(Component.text("Мэдээлэл", GREY, TextDecoration.BOLD));
            body.add(info("Нээгдэх:", index + "-р бүлгийг дуусгахад", NamedTextColor.WHITE));
            body.add(info("Нутаг:", land(index), GREY));
            badge = Glyphs.BADGE_Q_LOCKED;
        }
        body.add(Component.empty());
        body.add(StyleFormat.glyph(badge));
        String title = (index + 1) + ". " + (finished || active ? def.title() : "???");
        ItemStack it = Menu.item(finished || active ? icon(def.type()) : Material.GRAY_DYE, Component.text(title, c, TextDecoration.BOLD), body);
        it.setAmount(index >= ACT_ONE ? index - ACT_ONE + 1 : index + 1); // the chapter number within its act
        Menu.mongol(it);
        return active ? Menu.glow(it) : it;
    }

    /** A historic site card: kind, history label, story, area, direction, seen badge. */
    private ItemStack siteCard(Player p, HistoricSite s, boolean seen) {
        TextColor c = seen ? GREEN : GOLD;
        List<Component> body = new ArrayList<>();
        body.add(Component.text(s.kind().label() + " · " + HistoricSiteService.label(s.history()), SKY));
        body.add(Component.empty());
        for (String line : wrap(s.description(), 38)) body.add(Component.text(line, STORY));
        body.add(Component.empty());
        body.add(Component.text("Мэдээлэл", c, TextDecoration.BOLD));
        String area = Content.pack().areas().stream().filter(a -> a.id().equals(s.areaId())).findFirst().map(Area::name).orElse(null);
        if (area != null) body.add(info("Нутаг:", area, SKY));
        String where = siteWhere(p, s.id());
        if (where != null) body.add(info("Хаана:", where, SKY));
        body.add(info("Түүх:", HistoricSiteService.label(s.history()), NamedTextColor.WHITE));
        body.add(Component.empty());
        body.add(clickHint("ДАРЖ ЗАМ ЗААЛГАХ"));
        body.add(StyleFormat.glyph(seen ? Glyphs.BADGE_Q_SEEN : Glyphs.BADGE_Q_UNSEEN));
        ItemStack it = Menu.item(siteIcon(s.kind()), Component.text(s.name(), c, TextDecoration.BOLD), body);
        return Menu.mongol(it);
    }

    private String siteWhere(Player p, String id) {
        if (services.historicSites == null) return null;
        Location site = services.historicSites.where(id).orElse(null);
        if (site == null || !site.getWorld().equals(p.getWorld())) return null;
        double dx = site.getX() - p.getLocation().getX(), dz = site.getZ() - p.getLocation().getZ();
        return Navigation.compass(Navigation.bearing(dx, dz)) + " · " + Math.round(Math.hypot(dx, dz)) + "м";
    }

    private static List<HistoricSite> sites() {
        return Content.pack().historicSites();
    }

    private int seenCount(Player p) {
        if (services.historicSites == null) return 0;
        Set<String> seen = services.historicSites.seen(p);
        int n = 0;
        for (HistoricSite s : sites()) if (seen.contains(s.id())) n++;
        return n;
    }

    /** «┃ key value», the information bar of the tooltip. */
    private static Component info(String key, String value, TextColor color) {
        return Component.text("┃ ", GOLD_D).append(Menu.kv(key, value, color));
    }

    /** The mouse icon and a click hint, e.g. «ДАРЖ НЭЭХ». */
    private static Component clickHint(String text) {
        return StyleFormat.join(StyleFormat.glyph(Glyphs.MOUSE_LEFT), Component.text(" " + text, NamedTextColor.WHITE, TextDecoration.BOLD));
    }

    private static String kind(mn.suld.api.quest.QuestType t) {
        return switch (t) {
            case KILL_MOB -> "Ан";
            case REACH_LEVEL -> "Өсөлт";
            case COLLECT_ITEM -> "Цуглуулга";
            case DISCOVER_LOCATION -> "Аялал";
            case COMPLETE_DUNGEON -> "Агуй";
        };
    }

    private static Material siteIcon(SiteKind k) {
        return switch (k) {
            case DEER_STONES -> Material.CHISELED_STONE_BRICKS;
            case STONE_MEN -> Material.COBBLESTONE_WALL;
            case YAM_STATION -> Material.SADDLE;
            case NINE_BANNERS -> Material.WHITE_BANNER;
            case GREAT_OVOO -> Material.COBBLESTONE;
            case ORDO_RUINS -> Material.MUD_BRICKS;
            case RUIN_WALLS -> Material.PACKED_MUD;
            case STELE -> Material.POLISHED_ANDESITE;
            case BIRTH_STONE -> Material.MOSSY_COBBLESTONE;
            case EARTH_WALL -> Material.COARSE_DIRT;
            case PETROGLYPHS -> Material.TUFF_BRICKS;
            case WATCHTOWER_RUIN -> Material.STONE_BRICKS;
            case STONE_WALL_RING -> Material.STONE;
        };
    }

    // ------------------------------------------------------------------------------------------------ the painted maps

    /** Act I «Сүлдний Зам» (1–18, the owner's painted map) or Act II «Хөх Сүлдний Зам» (19–42). */
    public void openMap(Player p, int act) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        var chain = services.quests().chain();
        QuestState state = pr.questState();
        int done = chain.completedCount(state);
        boolean two = act == 2 && chain.size() > ACT_ONE;
        int[] slots = two ? SLOTS2 : SLOTS;
        int first = two ? ACT_ONE : 0;
        Menu m = new Menu(6, (two ? "Хөх Сүлдний Зам" : "Сүлдний Зам") + " · " + done + "/" + chain.size(), two ? Glyphs.GUI_QUESTS2 : Glyphs.GUI_QUESTS);
        for (int i = first; i < chain.size() && i - first < slots.length; i++) {
            QuestDefinition def = chain.chapters().get(i);
            boolean finished = i < done;
            boolean active = !finished && def.id().equals(state.questId());
            m.set(slots[i - first], card(p, def, i, finished, active, state), active ? (pl, c) -> {
                if (tracker != null && !tracker.shown(pl)) tracker.toggle(pl);
                pl.closeInventory();
                pl.sendActionBar(Component.text("✦ Эрлийн заагч дээд талд — зүг, зайг нь дага", GOLD, TextDecoration.BOLD));
            } : null);
        }
        // toolbar (row 5)
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> pl.performCommand("menu"));
        boolean shown = tracker == null || tracker.shown(p);
        m.set(47, Menu.item(shown ? Material.COMPASS : Material.CLOCK, Menu.title("Эрлийн заагч: " + (shown ? "асаалттай" : "унтраалттай"), SKY),
                Menu.lore(SKY, List.of(Menu.line("Дээд талын мөр зорилго руу зүг, зайг заана.")), List.of(), "Дарж " + (shown ? "унтраах" : "асаах"))), (pl, c) -> {
            if (tracker != null) tracker.toggle(pl);
            openMap(pl, two ? 2 : 1);
        });
        m.set(48, Menu.item(Material.MOSSY_COBBLESTONE, Menu.title("Агуйн Шат", SKY), Menu.lore(SKY,
                List.of(Menu.line("Арван агуй, хаалга руу нь зам заана.")), List.of(), "Нээх")), (pl, c) -> pl.performCommand("dungeon"));
        m.set(49, Menu.item(Material.WRITABLE_BOOK, Menu.title("Сүлдний Зам", GOLD), List.of(
                Menu.line(two ? "Гадна нутгаас Тэнгэрийн Шат хүртэл." : "Хархорумаас Алтай хүртэлх аян."),
                Menu.line("Бүлэг бүр дуусмагц дараагийнх нь эхэлнэ."),
                Component.empty(),
                Menu.kv("Явц:", done + " / " + chain.size() + " бүлэг", GREEN),
                Menu.kv("Одоо:", done < chain.size() ? land(Math.min(done, chain.size() - 1)) : "Бүх бүлэг дууссан", GOLD))), null);
        m.set(50, Menu.item(Material.PLAYER_HEAD, Menu.title("Бүлэг", SKY), Menu.lore(SKY,
                List.of(Menu.line("Найзаа урих: /party invite <нэр>")), List.of(), null)), null);
        if (chain.size() > ACT_ONE) {
            // the other act: Act I (painted map) ⇄ Act II
            m.set(51, Menu.item(two ? Material.FILLED_MAP : Material.MAP, Menu.title(two ? "« Act I · Сүлдний Зам" : "Act II · Хөх Сүлдний Зам »", GOLD),
                    Menu.lore(GOLD, List.of(Menu.line(two ? "1–18-р бүлэг: Хархорумаас Алтай хүртэл" : "19–" + chain.size() + "-р бүлэг: гадна нутаг, Тэнгэрийн Шат")),
                            List.of(), "Дарж нээх")), (pl, c) -> openMap(pl, two ? 1 : 2));
        }
        m.set(52, Menu.item(Material.BOOK, Menu.title("« Эрлийн Ном", GOLD), Menu.lore(GOLD, List.of(Menu.line("Бүлгүүдийг картаар харах.")), List.of(), "Дарж нээх")),
                (pl, c) -> open(pl, two ? 2 : 1));
        m.set(53, Menu.item(Material.BARRIER, Menu.title("Хаах", GREY), List.of()), (pl, c) -> pl.closeInventory());
        m.open(p);
    }

    private ItemStack card(Player p, QuestDefinition def, int index, boolean finished, boolean active, QuestState state) {
        TextColor c = finished ? GREEN : active ? GOLD : GREY;
        QuestContent.Lore lore = QuestContent.lore(def.id());
        List<Component> body = new ArrayList<>();
        if (finished || active) {
            for (String line : wrap(QuestContent.story(def.id()), 38)) body.add(Component.text(line, STORY));
            body.add(Component.empty());
            body.add(Menu.kv("Юу хийх:", objective(def), NamedTextColor.WHITE));
            String where = where(p, def);
            if (where != null) body.add(Menu.kv("Хаана:", where, SKY));
            if (active) body.add(Menu.kv("Явц:", bar(state.progress(), def.requiredCount()) + " " + state.progress() + "/" + def.requiredCount(), GOLD));
            body.add(Menu.kv("Өгсөн:", lore.giver(), SKY));
            body.add(Menu.kv("Шагнал:", def.expReward() + " EXP · " + def.currencyReward() + " зоос", GREEN));
            if (active && !lore.hint().isEmpty()) body.add(Component.text("➜ " + lore.hint(), NamedTextColor.WHITE, TextDecoration.BOLD));
        } else {
            body.add(Component.text("Өмнөх бүлгүүдээ дуусгахад нээгдэнэ.", GREY));
            body.add(Menu.kv("Нутаг:", land(index), GREY));
        }
        String title = (index + 1) + ". " + (finished || active ? def.title() : "Түгжээтэй") + (finished ? " ✔" : "");
        int stack = index >= ACT_ONE ? index - ACT_ONE + 1 : index + 1;
        ItemStack it = Menu.item(finished || active ? icon(def.type()) : Material.GRAY_DYE, Component.text(title, c, TextDecoration.BOLD),
                Menu.lore(c, body, List.of(), finished ? "Дууссан" : active ? "Дарж заагчийг асаах" : null));
        it.setAmount(stack); // the chapter number within its act
        return active ? Menu.glow(it) : it;
    }

    private static String objective(QuestDefinition d) {
        return switch (d.type()) {
            case KILL_MOB -> {
                var mob = SuldContent.mobFor(d.targetId());
                yield d.requiredCount() + " × " + (mob == null ? d.targetId() : mob.displayName()) + " устга";
            }
            case COLLECT_ITEM -> {
                var item = SuldContent.definitionFor(d.targetId());
                yield d.requiredCount() + " × " + (item == null ? d.targetId() : item.displayName()) + " цуглуул";
            }
            case REACH_LEVEL -> "Түвшин " + d.requiredCount() + "-д хүр";
            case DISCOVER_LOCATION -> {
                if (d.targetId().startsWith("site.")) {
                    yield mn.suld.plugin.content.Content.pack().historicSites().stream().filter(x -> x.id().equals(d.targetId()))
                            .findFirst().map(x -> x.name()).orElse("Түүхэн газар") + "-д оч";
                }
                RegionDefinition r = QuestTracker.targetRegion(d).orElse(null);
                yield (r == null ? "Шинэ газар" : r.displayName()) + "-д оч";
            }
            case COMPLETE_DUNGEON -> {
                var dg = SuldContent.dungeonFor(d.targetId());
                yield (dg == null ? "Агуй" : dg.displayName()) + "-г дав";
            }
        };
    }

    /** Region, compass direction and distance from the player, or null for goals without a place. */
    private String where(Player p, QuestDefinition d) {
        if (d.type() == mn.suld.api.quest.QuestType.COMPLETE_DUNGEON) {
            Location gate = services.dungeons().halls().flatMap(h -> h.gate(d.targetId())).orElse(null);
            if (gate != null && gate.getWorld().equals(p.getWorld())) {
                double dx = gate.getX() - p.getLocation().getX(), dz = gate.getZ() - p.getLocation().getZ();
                return "агуйн хаалга · " + Navigation.compass(Navigation.bearing(dx, dz)) + " · " + Math.round(Math.hypot(dx, dz)) + "м";
            }
        }
        if (d.type() == mn.suld.api.quest.QuestType.DISCOVER_LOCATION && d.targetId().startsWith("site.") && services.historicSites != null) {
            Location site = services.historicSites.where(d.targetId()).orElse(null);
            if (site != null && site.getWorld().equals(p.getWorld())) {
                double dx = site.getX() - p.getLocation().getX(), dz = site.getZ() - p.getLocation().getZ();
                return "түүхэн газар · " + Navigation.compass(Navigation.bearing(dx, dz)) + " · " + Math.round(Math.hypot(dx, dz)) + "м";
            }
        }
        RegionDefinition r = QuestTracker.targetRegion(d).orElse(null);
        if (r == null) return null;
        Location spawn = p.getWorld().getSpawnLocation();
        double[] w = Navigation.waypoint(r.shape());
        double dx = spawn.getX() + w[0] - p.getLocation().getX(), dz = spawn.getZ() + w[1] - p.getLocation().getZ();
        return r.displayName() + " · " + Navigation.compass(Navigation.bearing(dx, dz)) + " · " + Math.round(Math.hypot(dx, dz)) + "м";
    }

    private static String bar(int have, int need) {
        int n = 10, f = need <= 0 ? n : Math.min(n, Math.round((float) have * n / need));
        return "▰".repeat(f) + "▱".repeat(n - f);
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + w.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
