package mn.suld.plugin.gui;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestState;
import mn.suld.api.region.Navigation;
import mn.suld.api.region.RegionDefinition;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.QuestContent;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.quest.QuestTracker;
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

/**
 * Сүлдний Зам, the story map ({@code /quest}). Act I: the owner's painted map of the four lands (background {@code
 * gui_quests}) with eighteen chapters on a road from the Хэрлэн steppe through the Говь, up to the Хангай and on to the
 * Алтай. Act II «Хөх Сүлдний Зам» (chapters 19–42, {@code gui_quests2}): the outer lands, the historic sites and the
 * ladder to the Тэнгэрийн Шат. It opens on the act of the current chapter; a button switches acts. Each card tells the chapter's story, what to do, where (direction and distance from the player), the
 * progress and the reward. The bottom row: menu, tracker on/off, dungeons, the journey, party, close.
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

    /** Opens the act of the player's current chapter. */
    public void open(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        int done = services.quests().chain().completedCount(pr.questState());
        open(p, done >= ACT_ONE && services.quests().chain().size() > ACT_ONE ? 2 : 1);
    }

    /** Act I «Сүлдний Зам» (1–18, the owner's painted map) or Act II «Хөх Сүлдний Зам» (19–42). */
    public void open(Player p, int act) {
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
            open(pl);
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
                            List.of(), "Дарж нээх")), (pl, c) -> open(pl, two ? 1 : 2));
        }
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
            body.add(Menu.kv("Шагнал:", def.expReward() + " EXP · " + def.currencyReward() + " ₮", GREEN));
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
