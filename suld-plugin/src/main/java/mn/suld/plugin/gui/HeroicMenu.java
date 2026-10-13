package mn.suld.plugin.gui;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.heroic.HeroicLadder;
import mn.suld.api.dungeon.heroic.SteppeAffix;
import mn.suld.api.persistence.HeroicRepository;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.DungeonContent;
import mn.suld.plugin.dungeon.DungeonGuide;
import mn.suld.plugin.dungeon.heroic.HeroicDungeonService;
import mn.suld.plugin.ui.Glyphs;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * «Тэнгэрийн Гэсгээлт Агуй»: the heroic keys window ({@code /dungeon heroic}, docs/HEROIC_DUNGEONS.md). The same stair as
 * {@link DungeonMenu}; each plinth shows the player's open key, best key and time, and this week's affixes at the open
 * key. A click at the gate enters at the highest open key (right click: one key lower), anywhere else it starts the
 * way-finder to the gate; any key: {@code /dungeon heroic <агуй> <түлхүүр>}.
 */
public final class HeroicMenu {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor SKY = TextColor.fromHexString("#9FF3FF");
    private static final TextColor GREEN = TextColor.fromHexString("#7CE07C");
    private static final TextColor RED = TextColor.fromHexString("#FF6B6B");
    private static final TextColor GREY = TextColor.fromHexString("#B8B8B8");
    private static final TextColor PURPLE = TextColor.fromHexString("#C08BFF");
    private static final double AT_GATE = 16;

    private final SuldServices services;
    private final DungeonGuide guide;
    private final DungeonMenu ladder;

    public HeroicMenu(SuldServices services, DungeonGuide guide, DungeonMenu ladder) {
        this.services = services;
        this.guide = guide;
        this.ladder = ladder;
    }

    public void open(Player p) {
        HeroicDungeonService heroic = services.heroic;
        int level = services.profiles().cached(p.getUniqueId()).map(pr -> pr.progression().level()).orElse(1);
        Menu m = new Menu(6, "Баатарлаг Агуй", Glyphs.GUI_DUNGEONS);
        List<DungeonDefinition> all = DungeonContent.ALL;
        for (int i = 0; i < all.size() && i < DungeonMenu.SLOTS.length; i++) {
            DungeonDefinition d = all.get(i);
            m.set(DungeonMenu.SLOTS[i], plinth(p, heroic, d, level), (pl, c) -> click(pl, d, c));
        }
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Агуйн Шат", GOLD), List.of()), (pl, c) -> ladder.open(pl));
        List<Component> week = new ArrayList<>();
        week.add(Menu.kv("Долоо хоног:", heroic.weekId(), SKY));
        for (int key : new int[]{2, 4, 7, 10}) week.add(Menu.kv("+" + key + ":", names(heroic.affixes(key)), SKY));
        week.add(Component.empty());
        week.add(Menu.line("Нөхцөлүүд долоо хоног бүр солигдоно (бүгдэд адил)."));
        m.set(49, Menu.item(Material.CLOCK, Menu.title("Энэ долоо хоногийн нөхцөл", GOLD), week), null);
        m.set(53, Menu.item(Material.BARRIER, Menu.title("Хаах", GREY), List.of()), (pl, c) -> pl.closeInventory());
        m.open(p);
    }

    private ItemStack plinth(Player p, HeroicDungeonService heroic, DungeonDefinition d, int level) {
        boolean cleared = services.dungeons().cleared(p, d.id());
        boolean ready = level >= HeroicLadder.PLAYER_LEVEL && cleared && heroic.loaded(p.getUniqueId());
        HeroicRepository.Key k = heroic.key(p.getUniqueId(), d.id());
        TextColor c = ready ? GOLD : GREY;
        List<Component> body = new ArrayList<>();
        body.add(Menu.kv("Нээлттэй:", "+" + k.open(), ready ? GREEN : GREY));
        body.add(Menu.kv("Шилдэг:", k.best() == 0 ? "—" : "+" + k.best() + " · " + HeroicDungeonService.format(k.bestSeconds()), GOLD));
        body.add(Menu.kv("Хугацаа:", HeroicDungeonService.format(HeroicLadder.timeLimitSeconds(d.totalWaves(), d.bossDefinition().enrageSeconds())), SKY));
        body.add(Menu.kv("+" + k.open() + " нөхцөл:", names(heroic.affixes(k.open())), PURPLE));
        body.add(Menu.kv("Мангас:", "Lv " + HeroicLadder.MOB_LEVEL + " · амь ×" + round(HeroicLadder.healthMultiplier(k.open()))
                + " · цохилт ×" + round(HeroicLadder.damageMultiplier(k.open())), RED));
        List<Component> info = new ArrayList<>();
        if (level < HeroicLadder.PLAYER_LEVEL) info.add(Component.text(HeroicLadder.PLAYER_LEVEL + "-р түвшнээс нээгдэнэ", RED));
        else if (!cleared) info.add(Component.text("Эхлээд энгийнээр нэг удаа дав", RED));
        else if (!heroic.loaded(p.getUniqueId())) info.add(Component.text("Түлхүүр ачаалж байна…", GREY));
        else {
            info.add(Component.text("Зүүн товч: +" + k.open() + " · баруун: +" + Math.max(HeroicLadder.MIN_KEY, k.open() - 1), GREEN));
            info.add(Component.text("Өөр түлхүүр: /dungeon heroic " + shortId(d) + " <1–" + k.open() + ">", GREY));
        }
        ItemStack it = Menu.item(DungeonMenu.icon(d.id()), Menu.title((ready ? "✦ " : "🔒 ") + d.displayName() + " +" + k.open(), c),
                Menu.lore(c, body, info, ready ? "Хаалган дээр дарж орох" : null));
        it.setAmount(Math.max(1, Math.min(64, k.open())));
        return k.best() > 0 ? Menu.glow(it) : it;
    }

    private void click(Player p, DungeonDefinition d, ClickType type) {
        HeroicDungeonService heroic = services.heroic;
        int open = heroic.key(p.getUniqueId(), d.id()).open();
        int key = type != null && type.isRightClick() ? Math.max(HeroicLadder.MIN_KEY, open - 1) : open;
        if (services.dungeons().isInAnyRun(p.getUniqueId())) {
            p.sendMessage(Messages.error("Та одоо агуйд байна."));
            return;
        }
        p.closeInventory();
        Location gate = services.dungeons().halls().flatMap(h -> h.gate(d.id())).orElse(null);
        if (gate != null && gate.getWorld().equals(p.getWorld()) && gate.distance(p.getLocation()) <= AT_GATE) {
            Component error = services.dungeons().start(p, d, key);
            if (error != null) p.sendMessage(error);
            return;
        }
        guide.guide(p, d.id());
        p.sendMessage(Messages.info("Хөтөч асаалаа: «" + d.displayName() + "»-ийн хаалган дээр дахин дарж баатарлаг +" + key + "-аар орно."));
    }

    static String names(List<SteppeAffix> affixes) {
        if (affixes.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        for (SteppeAffix a : affixes) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(a.displayName());
        }
        return sb.toString();
    }

    private static String round(double x) {
        return String.valueOf(Math.round(x * 100) / 100.0);
    }

    private static String shortId(DungeonDefinition d) {
        return d.id().startsWith("dungeon.") ? d.id().substring("dungeon.".length()) : d.id();
    }
}
