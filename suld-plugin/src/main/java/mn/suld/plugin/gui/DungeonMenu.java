package mn.suld.plugin.gui;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.DungeonRun;
import mn.suld.api.region.Navigation;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.DungeonContent;
import mn.suld.plugin.dungeon.DungeonGuide;
import mn.suld.plugin.ui.Glyphs;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Агуйн Шат: the dungeon window ({@code /dungeon}). Ten plinths on a stone stair (background art from
 * tools/pack/gen_ui.py {@code gui_dungeons}) climb from the steppe to the sky palace; each holds its dungeon, with
 * the level, party size, place, boss and whether it is open in the tooltip. A click at the gate enters; anywhere
 * else it starts the purple way-finder bar ({@link DungeonGuide}) to the gate. The bottom row: menu, party, the
 * player's progress, the current run or way-finder, close.
 */
public final class DungeonMenu {

    /** Slot of each ladder dungeon, bottom to top (row × 9 + column); mirrors gen_ui.DUNGEON_SLOTS. */
    static final int[] SLOTS = {4 * 9 + 1, 4 * 9 + 3, 4 * 9 + 5, 4 * 9 + 7, 2 * 9 + 7, 2 * 9 + 5, 2 * 9 + 3, 2 * 9 + 1, 2, 5};

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor SKY = TextColor.fromHexString("#9FF3FF");
    private static final TextColor GREEN = TextColor.fromHexString("#7CE07C");
    private static final TextColor RED = TextColor.fromHexString("#FF6B6B");
    private static final TextColor GREY = TextColor.fromHexString("#B8B8B8");
    private static final TextColor PURPLE = TextColor.fromHexString("#C08BFF");

    /** Within this many blocks of a gate a click enters instead of guiding. */
    private static final double AT_GATE = 16;

    private final SuldServices services;
    private final DungeonGuide guide;

    public DungeonMenu(SuldServices services, DungeonGuide guide) {
        this.services = services;
        this.guide = guide;
    }

    static Material icon(String id) {
        return switch (id) {
            case "dungeon.khasar_den" -> Material.MOSSY_COBBLESTONE;
            case "dungeon.govi_bulsh" -> Material.CHISELED_SANDSTONE;
            case "dungeon.baavgain_uur" -> Material.SPRUCE_LOG;
            case "dungeon.mosun_orgil" -> Material.PACKED_ICE;
            case "dungeon.dalain_gun" -> Material.PRISMARINE;
            case "dungeon.khar_khot" -> Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
            case "dungeon.ulaan_khad" -> Material.RED_SANDSTONE;
            case "dungeon.burkhan_agui" -> Material.MOSSY_STONE_BRICKS;
            case "dungeon.tengeriin_shat" -> Material.QUARTZ_PILLAR;
            case "dungeon.tengeriin_ordon" -> Material.LAPIS_BLOCK;
            default -> Material.STONE_BRICKS;
        };
    }

    public void open(Player p) {
        Menu m = new Menu(6, "Агуйн Шат", Glyphs.GUI_DUNGEONS);
        int level = services.profiles().cached(p.getUniqueId()).map(pr -> pr.progression().level()).orElse(1);
        List<DungeonDefinition> ladder = DungeonContent.ALL;
        int cleared = 0;
        for (int i = 0; i < ladder.size() && i < SLOTS.length; i++) {
            DungeonDefinition d = ladder.get(i);
            boolean done = services.dungeons().cleared(p, d.id());
            if (done) cleared++;
            m.set(SLOTS[i], rung(p, d, i, level, done), (pl, c) -> click(pl, d));
        }
        // toolbar (row 5)
        m.set(45, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> pl.performCommand("menu"));
        int size = services.parties().partyOf(p.getUniqueId()).map(x -> x.size()).orElse(1);
        m.set(47, Menu.item(Material.PLAYER_HEAD, Menu.title("Бүлэг", SKY), Menu.lore(SKY,
                List.of(Menu.kv("Одоо:", size == 1 ? "ганцаараа" : size + " хүн", SKY),
                        Menu.line("Найзаа урих: /party invite <нэр>")), List.of(), null)), null);
        m.set(49, Menu.item(Material.NETHER_STAR, Menu.title("Таны аян", GOLD), List.of(
                Menu.kv("Түвшин:", String.valueOf(level), GOLD),
                Menu.kv("Давсан агуй:", cleared + " / " + ladder.size(), cleared == ladder.size() ? GREEN : GOLD),
                Component.empty(),
                Menu.line("Агуй бүр өмнөхөө давсны дараа нээгдэнэ."))), null);
        Optional<DungeonRun> run = services.dungeons().runFor(p.getUniqueId());
        if (run.isPresent()) {
            m.set(51, Menu.item(Material.IRON_DOOR, Menu.title("Агуйгаас гарах", RED), Menu.lore(RED,
                    List.of(Menu.line(services.dungeons().statusLine(p.getUniqueId()).orElse("Агуйд байна"))), List.of(), "Дарж гарах")), (pl, c) -> {
                pl.closeInventory();
                services.parties().leave(pl, true);
            });
        } else if (guide.guiding(p)) {
            DungeonDefinition t = mn.suld.plugin.content.SuldContent.dungeonFor(guide.target(p));
            m.set(51, Menu.item(Material.COMPASS, Menu.title("Хөтөч зогсоох", PURPLE), Menu.lore(PURPLE,
                    List.of(Menu.line("Одоо: " + (t == null ? "—" : t.displayName()))), List.of(), "Дарж зогсоох")), (pl, c) -> {
                guide.stop(pl);
                open(pl);
            });
        }
        m.set(53, Menu.item(Material.BARRIER, Menu.title("Хаах", GREY), List.of()), (pl, c) -> pl.closeInventory());
        m.open(p);
    }

    private ItemStack rung(Player p, DungeonDefinition d, int index, int level, boolean done) {
        DungeonDefinition prev = DungeonContent.previous(d.id());
        boolean prevDone = prev == null || services.dungeons().cleared(p, prev.id());
        boolean open = level >= d.minLevel() && prevDone;
        TextColor c = done ? GREEN : open ? GOLD : GREY;
        String mark = done ? "✔ " : open ? "▶ " : "🔒 ";
        List<Component> body = new ArrayList<>();
        body.add(Menu.kv("Хаана:", DungeonContent.where(d.id()), SKY));
        body.add(Menu.kv("Түвшин:", d.minLevel() + "+", level >= d.minLevel() ? GREEN : RED));
        body.add(Menu.kv("Бүлэг:", d.minPartySize() + "–" + d.maxPartySize() + " тоглогч", SKY));
        body.add(Menu.kv("Давалгаа:", d.totalWaves() + " + босс", SKY));
        body.add(Menu.kv("Босс:", d.bossDefinition().displayName(), PURPLE));
        List<Component> info = new ArrayList<>();
        if (done) info.add(Component.text("Давсан ✔", GREEN));
        else if (!open) info.add(Component.text(level < d.minLevel() ? "Түвшин " + d.minLevel() + " хэрэгтэй" : "Эхлээд «" + prev.displayName() + "»-г дав", RED));
        else info.add(Component.text("Нээлттэй — бэлэн бол яв!", GREEN));
        Location gate = services.dungeons().halls().flatMap(h -> h.gate(d.id())).orElse(null);
        String hint = "Дарж хаалга руу зам заах";
        if (gate != null && gate.getWorld().equals(p.getWorld())) {
            double dx = gate.getX() - p.getLocation().getX(), dz = gate.getZ() - p.getLocation().getZ();
            long dist = Math.round(Math.hypot(dx, dz));
            info.add(Component.text("Хаалга: " + Navigation.compass(Navigation.bearing(dx, dz)) + " · " + dist + "м", NamedTextColor.WHITE));
            if (dist <= AT_GATE) hint = "Дарж орох";
        }
        ItemStack it = Menu.item(icon(d.id()), Menu.title(mark + (index + 1) + ". " + d.displayName(), c), Menu.lore(c, body, info, hint));
        it.setAmount(index + 1);
        return done ? Menu.glow(it) : it;
    }

    private void click(Player p, DungeonDefinition d) {
        if (services.dungeons().isInAnyRun(p.getUniqueId())) {
            p.sendMessage(Messages.error("Та одоо агуйд байна."));
            return;
        }
        Location gate = services.dungeons().halls().flatMap(h -> h.gate(d.id())).orElse(null);
        p.closeInventory();
        if (gate != null && gate.getWorld().equals(p.getWorld()) && gate.distance(p.getLocation()) <= AT_GATE) {
            Component error = services.dungeons().start(p, d);
            if (error != null) p.sendMessage(error);
            return;
        }
        guide.guide(p, d.id());
        p.sendMessage(Messages.info("Хөтөч асаалаа: дээд талын нил ягаан мөр «" + d.displayName() + "»-ийн хаалга руу заана."));
    }
}
