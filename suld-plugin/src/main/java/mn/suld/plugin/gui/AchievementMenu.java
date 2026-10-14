package mn.suld.plugin.gui;

import mn.suld.api.achievement.Achievement;
import mn.suld.api.battlepass.CampaignPass;
import mn.suld.api.battlepass.Season;
import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.CosmeticCatalog;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.achievement.AchievementService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code /achievements} and {@code /pass} (docs/ACHIEVEMENTS_AND_PASS.md): the deeds the server honours, page by page,
 * and «Хуралдайн Зам», the season's pass, eight tiers a page with both tracks under them.
 */
public final class AchievementMenu {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A"), CREAM = TextColor.fromHexString("#F4E8C8"),
            SKY = TextColor.fromHexString("#9FD8FF"), PURPLE = TextColor.fromHexString("#B06BFF");
    private static final int[] SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    /** Tiers on a page: columns 1–8 (the first column names the tracks). */
    private static final int TIERS_A_PAGE = 8;

    private final SuldServices services;
    private final Menus menus;

    public AchievementMenu(SuldServices services, Menus menus) {
        this.services = services;
        this.menus = menus;
    }

    private static Material icon(Achievement.Category c) {
        return switch (c) {
            case STORY -> Material.WRITABLE_BOOK;
            case BATTLE -> Material.IRON_SWORD;
            case HEROIC -> Material.DIAMOND_SWORD;
            case LANDS -> Material.COMPASS;
            case CRAFTS -> Material.SMITHING_TABLE;
            case HONOUR -> Material.GOLDEN_HELMET;
            case SOCIAL -> Material.BELL;
        };
    }

    // ================================================================== achievements

    public void open(Player p, Achievement.Category category) {
        AchievementService ach = services.achievements;
        Achievement.Category cat = category == null ? Achievement.Category.STORY : category;
        Set<String> unlocked = ach.unlocked(p.getUniqueId());
        List<Achievement> all = ach.catalog().all();
        Menu m = new Menu(6, "Амжилт · " + cat.displayName(), null);
        int c = 0;
        for (Achievement.Category k : Achievement.Category.values()) {
            long got = all.stream().filter(a -> a.category() == k && unlocked.contains(a.id())).count();
            long of = all.stream().filter(a -> a.category() == k).count();
            ItemStack it = Menu.item(icon(k), Menu.title(k.displayName() + " " + got + "/" + of, k == cat ? GOLD : NamedTextColor.WHITE),
                    List.of(Component.text(k == cat ? "Одоо харж байна" : "Дарж нээх", NamedTextColor.GRAY)));
            m.set(c++, k == cat ? Menu.glow(it) : it, (pl, click) -> open(pl, k));
        }
        long got = all.stream().filter(a -> unlocked.contains(a.id())).count();
        m.set(8, Menu.item(Material.NETHER_STAR, Menu.title("Нийт " + got + "/" + all.size(), GOLD), List.of(
                Component.text("Амжилт бүр Хуралдайн Замд оноо өгнө.", CREAM),
                Component.text("Шагнал нь зөвхөн гоёл: хэзээ ч хүч биш.", NamedTextColor.GRAY))), null);
        int n = 0;
        for (Achievement a : all) {
            if (a.category() != cat || n >= SLOTS.length) continue;
            boolean done = unlocked.contains(a.id());
            if (a.hidden() && !done) {
                m.set(SLOTS[n++], Menu.item(Material.GRAY_DYE, Menu.title("???", NamedTextColor.DARK_GRAY),
                        List.of(Component.text("Нууц амжилт: гүйцэтгэхэд нээгдэнэ.", NamedTextColor.GRAY))), null);
                continue;
            }
            List<Component> lore = new ArrayList<>();
            for (String line : KhuraldaiMenu.wrap(a.description(), 40)) lore.add(Component.text(line, NamedTextColor.WHITE));
            lore.add(Component.empty());
            long have = Math.min(ach.progress(p.getUniqueId(), a), a.criterion().goal());
            lore.add(Menu.kv("Явц:", done ? "✔ Гүйцэтгэсэн" : have + "/" + a.criterion().goal(), done ? NamedTextColor.GREEN : SKY));
            if (a.points() > 0) lore.add(Menu.kv("Хуралдайн Зам:", "+" + a.points() + " оноо", GOLD));
            if (a.reward() != null) {
                CosmeticCatalog.byId(a.reward()).ifPresent(cos -> lore.add(Menu.kv("Шагнал:", cos.name() + " (" + cos.category().displayName() + ")", PURPLE)));
            }
            lore.add(Menu.kv("Нэр:", a.basis().displayName(), NamedTextColor.GRAY));
            if (!a.source().isBlank()) for (String line : KhuraldaiMenu.wrap(a.source(), 40)) lore.add(Component.text(line, NamedTextColor.DARK_GRAY));
            ItemStack it = Menu.item(done ? icon(a.category()) : Material.GRAY_DYE, Menu.title(a.name(), done ? GOLD : NamedTextColor.WHITE), lore);
            m.set(SLOTS[n++], done ? Menu.glow(it) : it, null);
        }
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Дүр", GOLD), List.of()), (pl, click) -> menus.profile(pl));
        m.set(53, Menu.item(Material.GOAT_HORN, Menu.title("Хуралдайн Зам · /pass", GOLD), List.of()), (pl, click) -> pass(pl, -1));
        m.open(p);
    }

    // ================================================================== the pass

    /** The pass at {@code page} (−1: the page of the player's tier). */
    public void pass(Player p, int page) {
        AchievementService ach = services.achievements;
        Season season = ach.season();
        long points = ach.points(p.getUniqueId());
        int tier = CampaignPass.tier(points);
        int pages = (CampaignPass.TIERS + TIERS_A_PAGE - 1) / TIERS_A_PAGE;
        int pg = page < 0 ? Math.min(pages - 1, Math.max(0, tier - 1) / TIERS_A_PAGE) : Math.max(0, Math.min(pages - 1, page));
        boolean heroic = ach.heroic(p.getUniqueId());
        Menu m = new Menu(6, "Хуралдайн Зам · " + season.id() + " · " + (pg + 1) + "/" + pages, null);
        List<Component> head = new ArrayList<>();
        head.add(Menu.kv("Шат:", tier + "/" + CampaignPass.TIERS, GOLD));
        head.add(Menu.kv("Оноо:", points + (tier < CampaignPass.TIERS ? " (дараагийн шат хүртэл " + CampaignPass.toNext(points) + ")" : ""), CREAM));
        head.add(Menu.kv("Улирал:", season.start() + " — " + season.end() + " (" + season.daysLeft(LocalDate.now(services.zone())) + " өдөр үлдсэн)", NamedTextColor.GRAY));
        head.add(Component.text("Агуй, газар, ан, мэргэжил, даалгавар, амжилт оноо өгнө", NamedTextColor.GRAY));
        head.add(Component.text("(өдөрт " + CampaignPass.DAILY_CAP + " хүртэл; амжилтын оноо хязгааргүй).", NamedTextColor.GRAY));
        if (!ach.loaded(p.getUniqueId())) head.add(Component.text("Ачаалж байна… түр хүлээгээд дахин нээ.", NamedTextColor.RED));
        m.set(4, Menu.item(Material.GOAT_HORN, Menu.title("Хуралдайн Зам · " + season.id(), GOLD), head), null);
        m.set(18, Menu.item(Material.LIME_STAINED_GLASS_PANE, Menu.title(CampaignPass.Track.FREE.displayName(), NamedTextColor.GREEN),
                List.of(Component.text("Бүгдэд нээлттэй", NamedTextColor.GRAY))), null);
        m.set(27, Menu.item(heroic ? Material.ORANGE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                Menu.title(CampaignPass.Track.HEROIC.displayName(), heroic ? GOLD : NamedTextColor.GRAY),
                List.of(Component.text(heroic ? "Энэ улиралд нээлттэй" : "Хаалттай", NamedTextColor.GRAY))), null);
        for (int i = 0; i < TIERS_A_PAGE; i++) {
            int t = pg * TIERS_A_PAGE + i + 1;
            if (t > CampaignPass.TIERS) break;
            int col = i + 1;
            boolean reached = tier >= t;
            ItemStack num = Menu.item(reached ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE, Menu.title(t + "-р шат", reached ? GOLD : NamedTextColor.WHITE),
                    List.of(Component.text(reached ? "Хүрсэн" : (t * CampaignPass.TIER_POINTS) + " оноонд", NamedTextColor.GRAY)));
            m.set(9 + col, reached ? Menu.glow(num) : num, null);
            reward(p, m, 18 + col, t, CampaignPass.Track.FREE, reached, true, pg);
            reward(p, m, 27 + col, t, CampaignPass.Track.HEROIC, reached, heroic, pg);
        }
        if (pg > 0) m.set(45, Menu.item(Material.ARROW, Menu.title("« Өмнөх", GOLD), List.of()), (pl, c) -> pass(pl, pg - 1));
        if (pg < pages - 1) m.set(53, Menu.item(Material.ARROW, Menu.title("Дараах »", GOLD), List.of()), (pl, c) -> pass(pl, pg + 1));
        m.set(48, Menu.item(Material.NETHER_STAR, Menu.title("Амжилтууд · /achievements", GOLD), List.of()), (pl, c) -> open(pl, null));
        if (!heroic) {
            long price = ach.heroicCredits();
            m.set(50, Menu.item(Material.ORANGE_DYE, Menu.title("Баатарлаг зам", GOLD), List.of(
                    Component.text("Шат бүрт нэмэлт гоёл (зөвхөн гоёл, хүч биш).", CREAM),
                    Component.text(price > 0 ? "Үнэ: " + price + " ✦ Сүлд Кредит (дэлгүүр тун удахгүй)." : "Одоогоор зарагдахгүй: тун удахгүй.", NamedTextColor.GRAY))), null);
        }
        long claimable = ach.claimable(p).size();
        if (claimable > 0) {
            m.set(49, Menu.glow(Menu.item(Material.CHEST, Menu.title("Бүх шагналаа авах (" + claimable + ")", GOLD), List.of())), (pl, c) -> {
                for (CampaignPass.Reward r : ach.claimable(pl)) ach.claim(pl, r, () -> { });
                pass(pl, pg);
            });
        }
        m.open(p);
    }

    private void reward(Player p, Menu m, int slot, int tier, CampaignPass.Track track, boolean reached, boolean open, int page) {
        AchievementService ach = services.achievements;
        CampaignPass.Reward r = ach.layout().at(track, tier).orElse(null);
        if (r == null) return;
        Cosmetic c = CosmeticCatalog.byId(r.cosmetic()).orElse(null);
        if (c == null) return;
        boolean claimed = ach.claimed(p.getUniqueId(), r);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(c.category().displayName() + " · " + c.rarity().displayName(), TextColor.fromHexString(c.rarity().color())));
        lore.add(Component.text(claimed ? "✔ Авсан" : !open ? "Баатарлаг зам хаалттай" : reached ? "Дарж авах" : tier + "-р шатанд хүрэхэд", claimed ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        Material icon = claimed ? Material.LIME_DYE : reached && open ? Material.CHEST : Material.PAPER;
        ItemStack it = Menu.item(icon, Menu.title(c.name(), claimed ? NamedTextColor.GREEN : open ? GOLD : NamedTextColor.GRAY), lore);
        m.set(slot, reached && open && !claimed ? Menu.glow(it) : it, (pl, click) -> {
            if (claimed || !reached || !open) return;
            ach.claim(pl, r, () -> pass(pl, page));
        });
    }
}
