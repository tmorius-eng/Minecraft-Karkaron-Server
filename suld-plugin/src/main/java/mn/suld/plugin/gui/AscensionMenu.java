package mn.suld.plugin.gui;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.balance.Ascension;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.DungeonContent;
import mn.suld.plugin.content.QuestContent;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Тэнгэрийн Зэрэг (Ascension I–III, docs/ASCENSION_SPEC.md): {@code /ascend}, or the Тэнгэрийн Тахилч in
 * Kharkhorum at level 60. The window shows the rank, the Тэнгэрийн оноо (EXP earned at the cap) against the rank's
 * cost, the rite fee, every content gate with its progress, and what a rank gives and costs. The rite takes two
 * clicks on the same button; it is all-or-nothing ({@link Ascension#rite}): points and coins are spent only when every
 * gate is met.
 */
public final class AscensionMenu {

    private static final TextColor SKY = TextColor.fromHexString("#9FF3FF");
    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor GREEN = TextColor.fromHexString("#7CE07C");
    private static final TextColor RED = TextColor.fromHexString("#FF6B6B");
    private static final TextColor GREY = TextColor.fromHexString("#B8B8B8");

    private final SuldServices services;
    /** Players who clicked the rite once (the second click performs it). */
    private final Map<UUID, Long> armed = new ConcurrentHashMap<>();

    public AscensionMenu(SuldServices services) {
        this.services = services;
    }

    /** What the gates need to know about {@code p}. */
    public Ascension.Inputs inputs(Player p, PlayerProfile pr) {
        int clears = 0;
        for (var d : DungeonContent.ALL) if (services.dungeons().cleared(p, d.id())) clears++;
        var tree = services.skillTree();
        int chapters = tree == null ? 0 : tree.context(p).finishedChapters();
        double gp = services.dungeons().gearPower(p);
        return new Ascension.Inputs(pr.progression().level(), clears, DungeonContent.ALL.size(), pr.endgame().palaceClears(),
                gp == Double.MAX_VALUE ? 0 : gp, chapters, QuestContent.STORY.chapters().size(), pr.classGear().mastery());
    }

    public void open(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        var curve = services.curve();
        int rank = pr.ascension();
        Ascension.Inputs in = inputs(p, pr);
        Menu m = new Menu(5, "Тэнгэрийн Зэрэг", null);

        List<Component> now = new ArrayList<>();
        now.add(Menu.line("Мөнх Тэнгэрийн шат — 60-р түвшний"));
        now.add(Menu.line("дараах гурван зэрэг."));
        m.set(4, Menu.glow(Menu.item(Material.NETHER_STAR, Menu.title("Тэнгэрийн Зэрэг: " + Ascension.roman(rank), SKY), Menu.lore(SKY, now,
                List.of(Menu.kv("Одоогийн хүч:", "+" + Math.round(Ascension.powerBonus(rank) * 100) + " %", GREEN),
                        Menu.kv("Нэмэлт чадварын оноо:", "+" + rank, GREEN)), null))), null);

        if (rank >= Ascension.MAX_RANK) {
            m.set(22, Menu.glow(Menu.item(Material.BEACON, Menu.title("Тэнгэрийн Зэргийн дээд шат", GOLD), Menu.lore(GOLD,
                    List.of(Menu.line("Та Тэнгэрийн гурван зэргийг бүгдийг давлаа.")), List.of(), null))), null);
        } else {
            long cost = Ascension.cost(curve, rank);
            long pts = pr.endgame().tengeriPoints();
            long fee = Ascension.riteCoins(rank);
            m.set(20, Menu.item(Material.EXPERIENCE_BOTTLE, Menu.title("Тэнгэрийн оноо", SKY), Menu.lore(SKY,
                    List.of(Menu.line("60-р түвшинд олсон EXP бүхэн"), Menu.line("Тэнгэрийн оноо болно.")),
                    List.of(Menu.kv("Байгаа:", fmt(pts), pts >= cost ? GREEN : RED), Menu.kv("Хэрэгтэй:", fmt(cost), GOLD),
                            Component.text(bar(pts, cost), pts >= cost ? GREEN : SKY)), null)), null);
            List<Component> checklist = new ArrayList<>();
            for (Ascension.Gate g : Ascension.gates(rank, in)) {
                checklist.add(Component.text((g.met() ? "✔ " : "✘ ") + g.label(), g.met() ? GREEN : RED, TextDecoration.BOLD));
            }
            boolean gatesMet = Ascension.blocked(rank, in) == null;
            m.set(22, Menu.item(gatesMet ? Material.ENCHANTED_BOOK : Material.BOOK, Menu.title("Зэрэг " + Ascension.roman(rank + 1) + "-ийн нөхцөл", gatesMet ? GREEN : GOLD),
                    Menu.lore(GOLD, checklist, List.of(), null)), null);
            m.set(24, Menu.item(Material.GOLD_INGOT, Menu.title("Ёслолын тахил", GOLD), Menu.lore(GOLD,
                    List.of(Menu.line("Тахилчид өргөх зоос.")),
                    List.of(Menu.kv("Хэрэгтэй:", fmt(fee) + " ₮", GOLD), Menu.kv("Таны зоос:", fmt(pr.currency()) + " ₮", pr.currency() >= fee ? GREEN : RED)), null)), null);

            Ascension.Rite trial = Ascension.rite(curve, rank, pts, pr.currency(), in);
            boolean ready = trial.outcome() == Ascension.Outcome.DONE;
            boolean isArmed = ready && armed.containsKey(p.getUniqueId());
            List<Component> body = new ArrayList<>();
            if (ready) {
                body.add(Menu.line("Бүх нөхцөл биеллээ."));
                body.add(Component.text("⚠ Зэрэгтэй баатрын сүнс үхвэл хамгийн", RED, TextDecoration.BOLD));
                body.add(Component.text("   урт хугацаагаар түгжигдэнэ.", RED, TextDecoration.BOLD));
            } else {
                body.add(Component.text(trial.reason(), RED, TextDecoration.BOLD));
            }
            m.set(31, ready ? Menu.glow(Menu.item(isArmed ? Material.LIME_CONCRETE : Material.LIGHT_BLUE_CONCRETE,
                    Menu.title(isArmed ? "Дахин дарж батал — Зэрэг " + Ascension.roman(rank + 1) : "Зэрэг " + Ascension.roman(rank + 1) + " авах", isArmed ? GREEN : SKY),
                    Menu.lore(SKY, body, List.of(Menu.kv("Зарцуулна:", fmt(cost) + " оноо, " + fmt(fee) + " ₮", GOLD)), isArmed ? "Дарж ёслолыг эхлүүл" : "Дарж сонго")))
                    : Menu.item(Material.GRAY_CONCRETE, Menu.title("Зэрэг " + Ascension.roman(rank + 1) + " — хаалттай", GREY), Menu.lore(GREY, body, List.of(), null)),
                    ready ? (pl, c) -> click(pl) : null);
        }

        m.set(40, Menu.item(Material.WRITABLE_BOOK, Menu.title("Зэрэг бүр юу өгөх вэ", GOLD), Menu.lore(GOLD, List.of(
                Menu.line("+1 чадварын оноо (/skills)"),
                Menu.line("+1 % хүч (цохилт, ид шид, сум)"),
                Menu.line("Зэрэг III: T6 ангийн хуяг нээгдэнэ"),
                Component.text("Үхлийн түгжээ хамгийн урт болно", RED, TextDecoration.BOLD)), List.of(), null)), null);
        m.set(36, Menu.item(Material.ARROW, Menu.title("« Сүлд Цэс", GOLD), List.of()), (pl, c) -> pl.performCommand("menu"));
        m.set(44, Menu.item(Material.BARRIER, Menu.title("Хаах", RED), List.of()), (pl, c) -> pl.closeInventory());
        m.open(p);
    }

    private void click(Player p) {
        Long t = armed.get(p.getUniqueId());
        if (t == null || System.currentTimeMillis() - t > 15_000) {
            armed.put(p.getUniqueId(), System.currentTimeMillis());
            open(p);
            return;
        }
        armed.remove(p.getUniqueId());
        ascend(p);
        p.closeInventory();
    }

    /** The rite: re-checked from fresh state at the moment it happens. */
    public void ascend(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        if (services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс байхдаа ёслол хийх боломжгүй."));
            return;
        }
        int rank = pr.ascension();
        Ascension.Rite r = Ascension.rite(services.curve(), rank, pr.endgame().tengeriPoints(), pr.currency(), inputs(p, pr));
        if (r.outcome() != Ascension.Outcome.DONE) {
            p.sendMessage(Messages.error(r.reason()));
            return;
        }
        pr.endgame(pr.endgame().withAscension(r.rank()).withPoints(r.points()));
        pr.addCurrency(r.coins() - pr.currency());
        services.profiles().save(pr);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "ascension.rite", p.getUniqueId().toString(),
                "rank " + rank + " -> " + r.rank()));
        Presentation.banner(p, "ТЭНГЭРИЙН ЗЭРЭГ " + Ascension.roman(r.rank()), "Мөнх Тэнгэр таныг өргөмжиллөө", NamedTextColor.AQUA);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 120, 0.6, 1.6, 0.6, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        p.sendMessage(Messages.success("Тэнгэрийн Зэрэг " + Ascension.roman(r.rank()) + ": +1 чадварын оноо, хүч +" + Math.round(Ascension.powerBonus(r.rank()) * 100) + " %."));
        Bukkit.broadcast(Component.text("✦ " + p.getName() + " Тэнгэрийн Зэрэг " + Ascension.roman(r.rank()) + "-д хүрлээ!", Messages.BRAND, TextDecoration.BOLD));
        services.hud().update(p, pr);
    }

    private static String fmt(long v) {
        return String.format(java.util.Locale.ROOT, "%,d", v);
    }

    private static String bar(long have, long need) {
        int n = need <= 0 ? 20 : (int) Math.min(20, have * 20 / need);
        return "▮".repeat(n) + "▯".repeat(20 - n);
    }
}
