package mn.suld.plugin.gui;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.balance.Ascension;
import mn.suld.api.balance.AscensionHonor;
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
 * Тэнгэрийн Зэрэг (Ascension I–III and the honours IV–X, docs/ASCENSION_SPEC.md): {@code /ascend}, or the Тэнгэрийн
 * Тахилч in Kharkhorum at level 60. Below III the window shows the Тэнгэрийн оноо (EXP earned at the cap) against the
 * rank's cost, the rite fee, every content gate with its progress, and what a rank gives. From III it shows the stars
 * and the next honour's gates (stars, a timed heroic key, a weapon mastery level; {@link AscensionHonor}). The rite
 * takes two clicks on the same button; it is all-or-nothing: nothing is spent unless every gate is met.
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

    /** Last seen (rank, gate steps or stars) per player: a rise is announced, the first look is not. */
    private final Map<UUID, long[]> seen = new ConcurrentHashMap<>();

    public AscensionMenu(SuldServices services) {
        this.services = services;
    }

    /**
     * Every 10 s: a level-60 player whose /ascend checklist moved (a dungeon, a palace clear, a mastery rank, the
     * gear) hears the step, and at the last rank each new «Тэнгэрийн Од» — so the days at the cap show progress.
     */
    public void watch(org.bukkit.plugin.Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    watch(p);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("[ascend] watch " + p.getName() + ": " + ex);
                }
            }
            seen.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        }, 200L, 200L);
    }

    private void watch(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || pr.progression().level() < mn.suld.api.balance.Balance.MAX_LEVEL) return;
        int rank = pr.ascension();
        boolean top = rank >= Ascension.MAX_RANK;
        Ascension.Inputs in = top ? null : inputs(p, pr);
        long now = top ? Ascension.stars(services.curve(), rank, pr.endgame().tengeriPoints()) : Ascension.steps(rank, in);
        long[] last = seen.put(p.getUniqueId(), new long[]{rank, now});
        if (last == null || last[0] != rank || now <= last[1]) return;
        Component line;
        if (top) {
            line = Component.text("✦ Тэнгэрийн Од " + now + " — 60-р түвшний EXP тань одоор гэрэлтэнэ.", SKY, TextDecoration.BOLD);
        } else {
            int total = Ascension.totalSteps(rank, in.ladderSize(), in.storySize());
            line = Component.text("✔ Тэнгэрийн Зэрэг " + Ascension.roman(rank + 1) + "-ийн нөхцөл: " + now + "/" + total + " (/ascend)", GREEN, TextDecoration.BOLD);
        }
        p.sendMessage(line);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
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

    /** What the honour gates (IV–X) need to know about {@code p}. */
    public AscensionHonor.Inputs honorInputs(Player p, PlayerProfile pr) {
        int rank = pr.ascension();
        long stars = Ascension.stars(services.curve(), Math.max(rank, Ascension.MAX_RANK), pr.endgame().tengeriPoints());
        int key = services.heroic == null ? 0 : services.heroic.bestKey(p.getUniqueId());
        int mastery = services.weaponMastery == null ? 0 : services.weaponMastery.best(p.getUniqueId());
        return new AscensionHonor.Inputs(rank, rank < Ascension.MAX_RANK ? 0 : stars, key, mastery);
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
        String honour = AscensionHonor.title(rank);
        m.set(4, Menu.glow(Menu.item(Material.NETHER_STAR, Menu.title("Тэнгэрийн Зэрэг: " + Ascension.roman(rank)
                + (honour == null ? "" : " · " + honour), SKY), Menu.lore(SKY, now,
                List.of(Menu.kv("Одоогийн хүч:", "+" + Math.round(Ascension.powerBonus(rank) * 100) + " %", GREEN),
                        Menu.kv("Нэмэлт чадварын оноо:", "+" + Math.min(Ascension.MAX_RANK, rank), GREEN)), null))), null);

        if (rank >= Ascension.MAX_RANK) {
            long pts = pr.endgame().tengeriPoints(), star = Ascension.starCost(curve);
            m.set(20, Menu.item(Material.NETHER_STAR, Menu.title("Тэнгэрийн Од: " + Ascension.stars(curve, rank, pts), SKY), Menu.lore(SKY,
                    List.of(Menu.line("III-ын дараа олсон EXP бүр од болж"), Menu.line("гэрэлтэнэ (зарцуулагдахгүй, хүч нэмэхгүй).")),
                    List.of(Menu.kv("Дараагийн од:", fmt(pts % star) + " / " + fmt(star), SKY), Component.text(bar(pts % star, star), SKY)), null)), null);
            honours(m, p, pr);
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
                    List.of(Menu.kv("Хэрэгтэй:", fmt(fee) + " зоос", GOLD), Menu.kv("Таны зоос:", fmt(pr.currency()) + " зоос", pr.currency() >= fee ? GREEN : RED)), null)), null);

            Ascension.Rite trial = Ascension.rite(curve, rank, pts, pr.currency(), in);
            boolean ready = trial.outcome() == Ascension.Outcome.DONE;
            boolean isArmed = ready && armed.containsKey(p.getUniqueId());
            List<Component> body = new ArrayList<>();
            if (ready) {
                body.add(Menu.line("Бүх нөхцөл биеллээ."));
                if (!services.config().death().untilRecovery()) { // a timed lock is longest for an ascended soul
                    body.add(Component.text("⚠ Зэрэгтэй баатрын сүнс үхвэл хамгийн", RED, TextDecoration.BOLD));
                    body.add(Component.text("   урт хугацаагаар түгжигдэнэ.", RED, TextDecoration.BOLD));
                }
            } else {
                body.add(Component.text(trial.reason(), RED, TextDecoration.BOLD));
            }
            m.set(31, ready ? Menu.glow(Menu.item(isArmed ? Material.LIME_CONCRETE : Material.LIGHT_BLUE_CONCRETE,
                    Menu.title(isArmed ? "Дахин дарж батал — Зэрэг " + Ascension.roman(rank + 1) : "Зэрэг " + Ascension.roman(rank + 1) + " авах", isArmed ? GREEN : SKY),
                    Menu.lore(SKY, body, List.of(Menu.kv("Зарцуулна:", fmt(cost) + " оноо, " + fmt(fee) + " зоос", GOLD)), isArmed ? "Дарж ёслолыг эхлүүл" : "Дарж сонго")))
                    : Menu.item(Material.GRAY_CONCRETE, Menu.title("Зэрэг " + Ascension.roman(rank + 1) + " — хаалттай", GREY), Menu.lore(GREY, body, List.of(), null)),
                    ready ? (pl, c) -> click(pl) : null);
        }

        List<Component> gives = new java.util.ArrayList<>(List.of(
                Menu.line("I–III: +1 чадварын оноо, +1 % хүч"),
                Menu.line("Зэрэг III: T6 ангийн хуяг нээгдэнэ"),
                Menu.line("IV: тулаанд тоглогчоос −3 % гэмтэл"),
                Menu.line("V: ховор олзны аз +3"),
                Menu.line("VI: цохилт бүрт 2 % аянга (+50 %)"),
                Menu.line("VII–IX: нурууны туяа, цол"),
                Menu.line("X: «Мөнх Тэнгэрийн Элч», хөшөө")));
        // a timed lock (death.lock.mode: TIMED) is longest for an ascended character; without a timer it is the same for all
        if (!services.config().death().untilRecovery()) gives.add(Component.text("Үхлийн түгжээ хамгийн урт болно", RED, TextDecoration.BOLD));
        m.set(40, Menu.item(Material.WRITABLE_BOOK, Menu.title("Зэрэг бүр юу өгөх вэ", GOLD), Menu.lore(GOLD, gives, List.of(), null)), null);
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
        if (rank >= Ascension.MAX_RANK) {
            honourRite(p, pr);
            return;
        }
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
        services.achievements.record(p, mn.suld.api.achievement.Deed.ASCENSION, "", r.rank());
        Presentation.banner(p, "ТЭНГЭРИЙН ЗЭРЭГ " + Ascension.roman(r.rank()), "Мөнх Тэнгэр таныг өргөмжиллөө", NamedTextColor.AQUA);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 120, 0.6, 1.6, 0.6, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        p.sendMessage(Messages.success("Тэнгэрийн Зэрэг " + Ascension.roman(r.rank()) + ": +1 чадварын оноо, хүч +" + Math.round(Ascension.powerBonus(r.rank()) * 100) + " %."));
        Bukkit.broadcast(Component.text("✦ " + p.getName() + " Тэнгэрийн Зэрэг " + Ascension.roman(r.rank()) + "-д хүрлээ!", Messages.BRAND, TextDecoration.BOLD));
        services.hud().update(p, pr);
    }

    /** From III: the next honour's gates, its fee and the two-click rite (slots 22, 24, 31), or the top at X. */
    private void honours(Menu m, Player p, PlayerProfile pr) {
        int rank = pr.ascension();
        AscensionHonor.Rank next = AscensionHonor.next(rank);
        if (next == null) {
            m.set(22, Menu.glow(Menu.item(Material.BEACON, Menu.title("Тэнгэрийн Зэргийн дээд шат", GOLD), Menu.lore(GOLD,
                    List.of(Menu.line("Мөнх Тэнгэрийн Элч: Тэнгэрийн аравдугаар зэрэг.")), List.of(), null))), null);
            return;
        }
        AscensionHonor.Inputs in = honorInputs(p, pr);
        List<Component> checklist = new ArrayList<>();
        for (AscensionHonor.Gate g : AscensionHonor.gates(in)) {
            checklist.add(Component.text((g.met() ? "✔ " : "✘ ") + g.label(), g.met() ? GREEN : RED, TextDecoration.BOLD));
        }
        boolean gatesMet = AscensionHonor.blocked(in) == null;
        m.set(22, Menu.item(gatesMet ? Material.ENCHANTED_BOOK : Material.BOOK,
                Menu.title("Зэрэг " + Ascension.roman(next.number()) + " «" + next.title() + "»", gatesMet ? GREEN : GOLD),
                Menu.lore(GOLD, checklist, List.of(Menu.line(perk(next))), null)), null);
        long fee = AscensionHonor.riteCoins(rank);
        m.set(24, Menu.item(Material.GOLD_INGOT, Menu.title("Ёслолын тахил", GOLD), Menu.lore(GOLD,
                List.of(Menu.line("Тахилчид өргөх зоос (оноо зарцуулахгүй).")),
                List.of(Menu.kv("Хэрэгтэй:", fmt(fee) + " зоос", GOLD), Menu.kv("Таны зоос:", fmt(pr.currency()) + " зоос", pr.currency() >= fee ? GREEN : RED)), null)), null);
        AscensionHonor.Rite trial = AscensionHonor.rite(in, pr.currency());
        boolean ready = trial.outcome() == Ascension.Outcome.DONE;
        boolean isArmed = ready && armed.containsKey(p.getUniqueId());
        List<Component> body = new ArrayList<>();
        body.add(ready ? Menu.line("Бүх нөхцөл биеллээ.") : Component.text(trial.reason(), RED, TextDecoration.BOLD));
        m.set(31, ready ? Menu.glow(Menu.item(isArmed ? Material.LIME_CONCRETE : Material.LIGHT_BLUE_CONCRETE,
                Menu.title(isArmed ? "Дахин дарж батал — Зэрэг " + Ascension.roman(next.number()) : "Зэрэг " + Ascension.roman(next.number()) + " авах", isArmed ? GREEN : SKY),
                Menu.lore(SKY, body, List.of(Menu.kv("Зарцуулна:", fmt(fee) + " зоос", GOLD)), isArmed ? "Дарж ёслолыг эхлүүл" : "Дарж сонго")))
                : Menu.item(Material.GRAY_CONCRETE, Menu.title("Зэрэг " + Ascension.roman(next.number()) + " — хаалттай", GREY), Menu.lore(GREY, body, List.of(), null)),
                ready ? (pl, c) -> click(pl) : null);
    }

    private static String perk(AscensionHonor.Rank r) {
        return switch (r.perk()) {
            case PVP_GUARD -> "Өгөх нь: зөвшөөрсөн тулаанд тоглогчоос −3 % гэмтэл";
            case FORTUNE -> "Өгөх нь: ховор олзны аз +3";
            case LIGHTNING -> "Өгөх нь: мангас цохиход 2 %-д Тэнгэрийн аянга (+50 %)";
            case AURA -> "Өгөх нь: нурууны туяа, «" + r.title() + "» цол";
            case MONUMENT -> "Өгөх нь: «Мөнх Тэнгэрийн Элч» цол, Хархорумд хөшөө";
        };
    }

    /** The honour rite (III → IV … IX → X): re-checked from fresh state; only the fee is spent. */
    private void honourRite(Player p, PlayerProfile pr) {
        int rank = pr.ascension();
        AscensionHonor.Rite r = AscensionHonor.rite(honorInputs(p, pr), pr.currency());
        if (r.outcome() != Ascension.Outcome.DONE) {
            p.sendMessage(Messages.error(r.reason()));
            return;
        }
        pr.endgame(pr.endgame().withAscension(r.rank()));
        pr.addCurrency(r.coins() - pr.currency());
        services.profiles().save(pr);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "ascension.honour_rite", p.getUniqueId().toString(),
                "rank " + rank + " -> " + r.rank()));
        services.achievements.record(p, mn.suld.api.achievement.Deed.ASCENSION, "", r.rank());
        String title = AscensionHonor.title(r.rank());
        Presentation.banner(p, "ТЭНГЭРИЙН ЗЭРЭГ " + Ascension.roman(r.rank()), title, NamedTextColor.AQUA);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 160, 0.6, 1.8, 0.6, 0.06);
        p.getWorld().playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        p.sendMessage(Messages.success("Тэнгэрийн Зэрэг " + Ascension.roman(r.rank()) + " «" + title + "»: " + perk(AscensionHonor.Rank.of(r.rank())).replace("Өгөх нь: ", "") + "."));
        if (r.rank() == AscensionHonor.LAST) {
            Bukkit.broadcast(Component.text("☼ " + p.getName() + " — «" + title + "», Тэнгэрийн аравдугаар зэрэг! Мөнх Тэнгэр түүнийг өргөмжиллөө.",
                    GOLD, TextDecoration.BOLD));
            // the name goes into the Chronicle and on Kharkhorum's monument for good (the perk the shrine promises)
            if (services.chronicle() != null) services.chronicle().heavenlyEnvoy(p, () -> {
                if (services.holograms != null) services.holograms.refreshEnvoys();
            });
            for (Player o : Bukkit.getOnlinePlayers()) o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 0.6f);
        } else {
            Bukkit.broadcast(Component.text("✦ " + p.getName() + " Тэнгэрийн Зэрэг " + Ascension.roman(r.rank()) + " «" + title + "»-д хүрлээ!",
                    Messages.BRAND, TextDecoration.BOLD));
        }
        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("ascension_honour", p.getUniqueId(),
                Map.<String, Object>of("rank", r.rank())));
        services.hud().update(p, pr);
        services.hud().teamChanged(p); // the ✧ mark after the name
    }

    private static String fmt(long v) {
        return String.format(java.util.Locale.ROOT, "%,d", v);
    }

    private static String bar(long have, long need) {
        int n = need <= 0 ? 20 : (int) Math.min(20, have * 20 / need);
        return "▮".repeat(n) + "▯".repeat(20 - n);
    }
}
