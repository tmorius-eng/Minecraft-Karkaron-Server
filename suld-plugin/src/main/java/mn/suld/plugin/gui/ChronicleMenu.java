package mn.suld.plugin.gui;

import mn.suld.api.chronicle.ChronicleEntry;
import mn.suld.api.chronicle.ChronicleKind;
import mn.suld.api.chronicle.ChronicleRules;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.chronicle.ChronicleService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Баатрын Хроник: the newest lines, one kind at a time, or «Миний түүх» (the player's own). The all-kinds page reads the
 * service's cache; a kind or a player's page asks the database and opens when the answer is back.
 */
public final class ChronicleMenu {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final int[] SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Plugin plugin;
    private final SuldServices services;
    private final Menus menus;

    public ChronicleMenu(Plugin plugin, SuldServices services, Menus menus) {
        this.plugin = plugin;
        this.services = services;
        this.menus = menus;
    }

    /** All kinds (cached), or one kind (asked from the database). */
    public void open(Player p, ChronicleKind kind) {
        if (kind == null) {
            show(p, "Баатрын Хроник", services.chronicle().latest(), kind, false);
            return;
        }
        services.chronicle().byKind(kind, SLOTS.length).whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            show(p, "Хроник · " + ChronicleRules.label(kind), err != null ? List.of() : list, kind, false);
        }));
    }

    /** «Миний түүх»: the lines where this player is the hero. */
    public void mine(Player p) {
        services.chronicle().byPlayer(p.getUniqueId(), SLOTS.length).whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            show(p, "Миний түүх · " + p.getName(), err != null ? List.of() : list, null, true);
        }));
    }

    private void show(Player p, String title, List<ChronicleEntry> entries, ChronicleKind kind, boolean mine) {
        ChronicleService chronicle = services.chronicle();
        Menu m = new Menu(6, title, null);
        ZoneId zone = services.zone();
        int n = 0;
        for (ChronicleEntry e : entries) {
            if (n >= SLOTS.length) break;
            List<Component> lore = new ArrayList<>();
            for (String line : KhuraldaiMenu.wrap(chronicle.headline(e).replace("✦ ", ""), 40)) lore.add(Component.text(line, NamedTextColor.WHITE));
            lore.add(Component.empty());
            lore.add(Menu.kv("Хэзээ:", DATE.format(Instant.ofEpochMilli(e.at()).atZone(zone)), NamedTextColor.GRAY));
            if (!e.cycleId().isBlank()) lore.add(Menu.kv("Долоо хоног:", e.cycleId(), NamedTextColor.GRAY));
            if (e.party().size() > 1) lore.add(Menu.kv("Баг:", String.join(", ", e.party()), NamedTextColor.GRAY));
            if (p.hasPermission("suld.admin.campaign")) lore.add(Menu.kv("id:", String.valueOf(e.id()), NamedTextColor.DARK_GRAY));
            m.set(SLOTS[n++], Menu.item(icon(e.kind()), Menu.title(ChronicleRules.label(e.kind()) + " · " + chronicle.subjectName(e), ChronicleService.colour(e.kind())), lore), null);
        }
        if (n == 0) {
            m.set(22, Menu.item(Material.PAPER, Menu.title(mine ? "Таны түүх хоосон хэвээр" : "Хроник хоосон хэвээр", NamedTextColor.GRAY), List.of(
                    Component.text(mine ? "Агуй анх цэвэрлэ, газар нээ, аянд оролц." : "Анхны амжилт энд бичигдэнэ.", NamedTextColor.WHITE))), null);
        }
        // tabs
        tab(m, 0, Material.BOOK, "Бүгд", kind == null && !mine, pl -> open(pl, null));
        tab(m, 1, Material.IRON_SWORD, ChronicleRules.label(ChronicleKind.FIRST_DUNGEON_CLEAR), kind == ChronicleKind.FIRST_DUNGEON_CLEAR, pl -> open(pl, ChronicleKind.FIRST_DUNGEON_CLEAR));
        tab(m, 2, Material.CLOCK, ChronicleRules.label(ChronicleKind.FASTEST_DUNGEON_CLEAR), kind == ChronicleKind.FASTEST_DUNGEON_CLEAR, pl -> open(pl, ChronicleKind.FASTEST_DUNGEON_CLEAR));
        tab(m, 3, Material.NETHER_STAR, ChronicleRules.label(ChronicleKind.RELIC_CLAIMED), kind == ChronicleKind.RELIC_CLAIMED, pl -> open(pl, ChronicleKind.RELIC_CLAIMED));
        tab(m, 4, Material.COMPASS, ChronicleRules.label(ChronicleKind.FIRST_SITE_DISCOVERY), kind == ChronicleKind.FIRST_SITE_DISCOVERY, pl -> open(pl, ChronicleKind.FIRST_SITE_DISCOVERY));
        tab(m, 5, Material.YELLOW_BANNER, ChronicleRules.label(ChronicleKind.CAMPAIGN_RESULT), kind == ChronicleKind.CAMPAIGN_RESULT, pl -> open(pl, ChronicleKind.CAMPAIGN_RESULT));
        tab(m, 7, Material.PLAYER_HEAD, "Миний түүх", mine, this::mine);
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Дүр", GOLD), List.of()), (pl, c) -> menus.profile(pl));
        m.set(53, Menu.item(Material.LECTERN, Menu.title("Их Хуралдай · /khuraldai", GOLD), List.of()), (pl, c) -> menus.khuraldai(pl));
        m.open(p);
    }

    private static void tab(Menu m, int slot, Material icon, String label, boolean active, java.util.function.Consumer<Player> open) {
        var it = Menu.item(icon, Menu.title(label, active ? GOLD : NamedTextColor.WHITE), List.of(Component.text(active ? "Одоо харж байна" : "Дарж нээх", NamedTextColor.GRAY)));
        m.set(slot, active ? Menu.glow(it) : it, (pl, c) -> open.accept(pl));
    }

    private static Material icon(ChronicleKind k) {
        return switch (k) {
            case FIRST_DUNGEON_CLEAR -> Material.IRON_SWORD;
            case FASTEST_DUNGEON_CLEAR -> Material.CLOCK;
            case RELIC_CLAIMED -> Material.NETHER_STAR;
            case FIRST_SITE_DISCOVERY -> Material.COMPASS;
            case CAMPAIGN_RESULT -> Material.YELLOW_BANNER;
            case KHURALDAI_RESULT -> Material.LECTERN;
        };
    }
}
