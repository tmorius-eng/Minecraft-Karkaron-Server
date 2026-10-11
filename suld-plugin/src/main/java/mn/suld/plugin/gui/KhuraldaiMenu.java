package mn.suld.plugin.gui;

import mn.suld.api.campaign.CampaignDefinition;
import mn.suld.api.campaign.CampaignRun;
import mn.suld.api.campaign.Contribution;
import mn.suld.api.campaign.vote.VoteCycle;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.campaign.KhuraldaiService;
import mn.suld.plugin.content.Content;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Их Хуралдай screen: this week's campaign (progress, your tier, the top ten) and the ballot for next week (one
 * click votes). Reads only cached state, so it opens without touching the database.
 */
public final class KhuraldaiMenu {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor SKY = TextColor.fromHexString("#9FF3FF");
    private static final TextColor GREEN = TextColor.fromHexString("#7CE07C");
    private static final int[] BALLOT_SLOTS = {38, 40, 42};
    private static final int[] TOP_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private final SuldServices services;
    private final Menus menus;

    public KhuraldaiMenu(SuldServices services, Menus menus) {
        this.services = services;
        this.menus = menus;
    }

    public void open(Player p) {
        KhuraldaiService k = services.khuraldai();
        Optional<CampaignRun> run = services.campaigns().active();
        Menu m = new Menu(6, "Их Хуралдай · " + services.weeklyCycle().cycleId(Instant.now()), null);

        // --- the week's campaign
        if (run.isPresent()) {
            CampaignRun r = run.get();
            CampaignDefinition d = r.definition();
            long mine = r.unitsOf(p.getUniqueId());
            int tier = r.tierOf(p.getUniqueId());
            String[] tierName = {"—", "хүрэл", "мөнгөн", "алтан"};
            List<Component> body = new ArrayList<>();
            for (String line : wrap(d.description(), 38)) body.add(Component.text(line, NamedTextColor.WHITE));
            m.set(4, Menu.glow(Menu.item(Material.YELLOW_BANNER, Menu.title(d.name(), GOLD), Menu.lore(GOLD, body, List.of(
                    Menu.kv("Нийт:", r.total() + " / " + d.goal().target() + " " + d.goal().unitWord() + " (" + Math.round(r.progress() * 100) + "%)", GOLD),
                    Menu.kv("Таны хувь:", mine + " · " + tierName[tier] + " шат", tier > 0 ? GREEN : NamedTextColor.GRAY),
                    Menu.kv("Шат:", d.tiers()[0] + " / " + d.tiers()[1] + " / " + d.tiers()[2], SKY),
                    Menu.kv("Шагнал:", d.reward(1).exp() + " / " + d.reward(2).exp() + " / " + d.reward(3).exp() + " EXP", SKY),
                    Menu.kv("Үлдсэн:", hours(r.remainingSeconds(Instant.now())), NamedTextColor.WHITE)), null))), null);
            List<Contribution> rank = r.ranking();
            for (int i = 0; i < TOP_SLOTS.length && i < rank.size(); i++) {
                Contribution c = rank.get(i);
                TextColor col = i == 0 ? GOLD : i < 3 ? SKY : NamedTextColor.WHITE;
                m.set(TOP_SLOTS[i], Menu.item(i < 3 ? Material.GOLD_INGOT : Material.IRON_INGOT, Menu.title("#" + (i + 1) + " " + c.name(), col),
                        List.of(Menu.kv("Хувь:", String.valueOf(c.units()), col),
                                Menu.kv("Шат:", tierName[d.tierOf(c.units())], NamedTextColor.WHITE))), null);
            }
            if (rank.isEmpty()) m.set(22, Menu.item(Material.PAPER, Menu.title("Хараахан хэн ч оролцоогүй", NamedTextColor.GRAY),
                    List.of(Component.text("Эхний хувь нэмрийг чи оруул!", NamedTextColor.WHITE))), null);
        } else {
            var last = services.campaigns().lastOutcome();
            m.set(4, Menu.item(Material.GRAY_BANNER, Menu.title("Энэ долоо хоногт аян алга", NamedTextColor.GRAY), Menu.lore(NamedTextColor.GRAY,
                    List.of(Component.text("Аян " + k.settings().campaignStarts() + "-д эхэлнэ.", NamedTextColor.WHITE)),
                    last == null ? List.of() : List.of(Menu.kv("Сүүлийн:", last.campaign().name() + (last.success() ? " ✔" : " ✘"), NamedTextColor.WHITE)), null)), null);
        }

        // --- the ballot
        VoteCycle c = k.current();
        List<CampaignDefinition> ballot = k.ballot();
        if (k.open() && !ballot.isEmpty()) {
            m.set(31, Menu.item(Material.LECTERN, Menu.title("Санал хураалт нээлттэй", GREEN), List.of(
                    Component.text("Дараагийн долоо хоногийн аяныг сонго.", NamedTextColor.WHITE),
                    Component.text("Хаагдах: " + k.settings().voteCloses(), NamedTextColor.GRAY))), null);
            for (int i = 0; i < ballot.size() && i < BALLOT_SLOTS.length; i++) {
                CampaignDefinition d = ballot.get(i);
                List<Component> body = new ArrayList<>();
                for (String line : wrap(d.description(), 38)) body.add(Component.text(line, NamedTextColor.WHITE));
                int index = i + 1;
                m.set(BALLOT_SLOTS[i], Menu.item(ballotIcon(i), Menu.title(index + ". " + d.name(), SKY), Menu.lore(SKY, body, List.of(
                        Menu.kv("Зорилго:", d.goal().target() + " " + d.goal().unitWord(), SKY),
                        Menu.kv("Санал:", String.valueOf(k.votesFor(d.id())), GREEN)), "Дарж санал өгөх")), (pl, click) -> {
                    pl.closeInventory();
                    k.vote(pl, String.valueOf(index));
                });
            }
        } else if (c != null && c.closed()) {
            CampaignDefinition w = Content.pack().campaign(c.winner() == null ? "" : c.winner());
            m.set(31, Menu.item(Material.WRITTEN_BOOK, Menu.title("Хуралдайн шийдвэр", GOLD), List.of(
                    Menu.kv("Дараагийн аян:", w == null ? String.valueOf(c.winner()) : w.name(), GOLD),
                    Menu.kv("Санал:", String.valueOf(c.voters()), NamedTextColor.WHITE))), null);
        } else {
            m.set(31, Menu.item(Material.CLOCK, Menu.title("Дараагийн Хуралдай", NamedTextColor.GRAY), List.of(
                    Component.text(k.settings().voteOpens() + " – " + k.settings().voteCloses(), NamedTextColor.WHITE),
                    Component.text("(" + services.zone().getId() + ")", NamedTextColor.GRAY))), null);
        }

        m.set(45, Menu.item(Material.ARROW, Menu.title("« Дүр", GOLD), List.of()), (pl, click) -> menus.profile(pl));
        m.set(53, Menu.item(Material.WRITABLE_BOOK, Menu.title("Баатрын Хроник · /chronicle", GOLD), List.of()), (pl, click) -> menus.chronicle(pl, null));
        m.open(p);
    }

    private static Material ballotIcon(int i) {
        return switch (i) {
            case 0 -> Material.BOW;
            case 1 -> Material.IRON_PICKAXE;
            default -> Material.COMPASS;
        };
    }

    private static String hours(long seconds) {
        if (seconds >= 86_400) return (seconds / 86_400) + " өдөр " + (seconds % 86_400) / 3600 + " цаг";
        if (seconds >= 3600) return (seconds / 3600) + " цаг";
        return (seconds / 60) + " мин";
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split("\\s+")) {
            if (line.length() + w.length() + 1 > width && line.length() > 0) {
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
