package mn.suld.plugin.chronicle;

import mn.suld.api.campaign.CampaignDefinition;
import mn.suld.api.campaign.CampaignRun;
import mn.suld.api.campaign.Contribution;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.worldbuild.WorldBuildService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two boards on the palace plaza (docs/KHURALDAI.md §5): the Хуралдай notice board (this week's campaign with its
 * progress and top three, or the open ballot with its tally) and the Hall of Fame (the newest lines of Баатрын Хроник).
 * <p>
 * They are entities, never blocks: the city is hand-built, so the boards stand on free air next to the plaza point and
 * are found again by their scoreboard tag. Text is rebuilt every few seconds from main-thread caches only
 * ({@link mn.suld.plugin.campaign.CampaignService#active()}, {@link mn.suld.plugin.campaign.KhuraldaiService#tally()},
 * {@link ChronicleService#latest()}); no database call happens here.
 */
public final class PlazaBoards {

    static final String TAG = "suld_plaza_board";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Where the boards stand: offsets (x, z) from the plaza point, notice board first. */
    private static final double[][] SPOTS = {{-6, 4}, {6, 4}};
    private static final double EYE = 1.6;

    private final Plugin plugin;
    private final SuldServices services;
    private final WorldBuildService city;
    private final String anchor;
    private final UUID[] spawned = new UUID[2];

    public PlazaBoards(Plugin plugin, SuldServices services, WorldBuildService city) {
        this.plugin = plugin;
        this.services = services;
        this.city = city;
        this.anchor = plugin.getConfig().getString("chronicle.hall-anchor", "event.plaza");
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("chronicle.hall-boards", true)) return;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 120L, 20L * 5);
    }

    private void tick() {
        Location base = city.isBuilt() ? city.pointLocation(anchor) : null;
        if (base == null || base.getWorld() == null || !base.isChunkLoaded()) return;
        if (spawned[0] == null && spawned[1] == null) {
            for (Entity e : base.getWorld().getEntitiesByClass(TextDisplay.class)) {
                if (e.getScoreboardTags().contains(TAG)) e.remove(); // leftovers from before a restart
            }
        }
        String[] texts = {noticeText(), hallText()};
        for (int i = 0; i < SPOTS.length; i++) {
            Entity existing = spawned[i] == null ? null : Bukkit.getEntity(spawned[i]);
            if (existing instanceof TextDisplay d && d.isValid()) {
                d.text(MM.deserialize(texts[i]));
                continue;
            }
            Location at = base.clone().add(SPOTS[i][0], EYE, SPOTS[i][1]);
            if (!clear(at)) at = base.clone().add(SPOTS[i][0] * 0.5, EYE, SPOTS[i][1] * 0.5);
            if (!clear(at)) continue; // never draw through a build
            String text = texts[i];
            TextDisplay d = base.getWorld().spawn(at, TextDisplay.class, t -> {
                t.text(MM.deserialize(text));
                t.setBillboard(Display.Billboard.CENTER);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(160, 20, 12, 4));
                t.setLineWidth(260);
                t.setViewRange(0.5f);
                t.setPersistent(false);
                t.addScoreboardTag(TAG);
                t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
            });
            spawned[i] = d.getUniqueId();
        }
    }

    private static boolean clear(Location at) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 3; dy++)
                    if (!at.clone().add(dx, dy, dz).getBlock().isPassable()) return false;
        return true;
    }

    // ------------------------------------------------------------------ text

    private String noticeText() {
        var khuraldai = services.khuraldai();
        if (khuraldai != null && khuraldai.open()) {
            List<String> names = new ArrayList<>();
            List<Integer> votes = new ArrayList<>();
            Map<String, Integer> tally = khuraldai.tally();
            for (CampaignDefinition d : khuraldai.ballot()) {
                names.add(d.name());
                votes.add(tally == null ? 0 : tally.getOrDefault(d.id(), 0));
            }
            long left = Math.max(0, (khuraldai.current().closesAt() - Instant.now().toEpochMilli()) / 1000);
            return ballotText(names, votes, left);
        }
        CampaignRun run = services.campaigns() == null ? null : services.campaigns().active().orElse(null);
        if (run == null) return idleText();
        List<String> top = new ArrayList<>();
        List<Long> units = new ArrayList<>();
        for (Contribution c : run.ranking()) {
            if (top.size() == 3) break;
            top.add(c.name());
            units.add(c.units());
        }
        CampaignDefinition def = run.definition();
        return campaignText(def.name(), def.goal().unitWord(), run.total(), def.goal().target(), run.remainingSeconds(Instant.now()), top, units);
    }

    private String hallText() {
        ChronicleService chronicle = services.chronicle();
        List<String> lines = new ArrayList<>();
        if (chronicle != null) {
            int max = services.config().chronicle().hallEntries();
            for (var e : chronicle.latest()) {
                if (lines.size() >= Math.min(max, 8)) break;
                lines.add(chronicle.headline(e).replace("✦ ", "").replaceFirst("^Хроник: ", ""));
            }
        }
        return hallText(lines);
    }

    static String ballotText(List<String> names, List<Integer> votes, long secondsLeft) {
        StringBuilder b = new StringBuilder("<bold><gold>✦ ИХ ХУРАЛДАЙ ✦</gold></bold>\n<white>Дараагийн долоо хоногийн аяныг сонго!</white>\n\n");
        for (int i = 0; i < names.size(); i++) {
            b.append("<yellow>").append(i + 1).append(".</yellow> <white>").append(esc(names.get(i))).append("</white> <gray>— ")
                    .append(votes.get(i)).append(" санал</gray>\n");
        }
        return b.append("\n<aqua>/khuraldai vote <1-").append(names.size()).append("></aqua>  <gray>· ").append(time(secondsLeft))
                .append(" үлдлээ</gray>").toString();
    }

    static String campaignText(String name, String unit, long total, long target, long secondsLeft, List<String> top, List<Long> units) {
        int pct = target <= 0 ? 0 : (int) Math.min(100, total * 100 / target);
        int filled = pct / 10;
        StringBuilder b = new StringBuilder("<bold><gold>✦ ЭНЭ ДОЛОО ХОНОГИЙН АЯН ✦</gold></bold>\n<white><bold>")
                .append(esc(name)).append("</bold></white>\n")
                .append("<green>").append("█".repeat(filled)).append("</green><dark_gray>").append("█".repeat(10 - filled)).append("</dark_gray> ")
                .append("<white>").append(total).append(" / ").append(target).append(" ").append(esc(unit)).append("</white>\n");
        if (!top.isEmpty()) {
            b.append("\n<yellow>Тэргүүлэгчид:</yellow>\n");
            for (int i = 0; i < top.size(); i++) {
                b.append("<white>").append(i + 1).append(". ").append(esc(top.get(i))).append("</white> <gray>").append(units.get(i)).append("</gray>\n");
            }
        }
        return b.append("\n<aqua>/khuraldai</aqua> <gray>· ").append(time(secondsLeft)).append(" үлдлээ</gray>").toString();
    }

    static String idleText() {
        return "<bold><gold>✦ ИХ ХУРАЛДАЙ ✦</gold></bold>\n<white>Аян Даваа гарагт эхэлнэ.</white>\n<white>Бямба 20:00-оос санал хураана.</white>\n\n<aqua>/khuraldai</aqua>";
    }

    static String hallText(List<String> lines) {
        StringBuilder b = new StringBuilder("<bold><gold>✦ БААТРЫН ХРОНИК ✦</gold></bold>\n<gray>Хроник эхэлснээс хойш</gray>\n\n");
        if (lines.isEmpty()) b.append("<white>Хроник хоосон байна.</white>\n<white>Анхны түүхийг та бич!</white>\n");
        for (String l : lines) b.append("<white>• ").append(esc(l)).append("</white>\n");
        return b.append("\n<aqua>/chronicle</aqua>").toString();
    }

    static String time(long seconds) {
        if (seconds >= 86_400) return seconds / 86_400 + " өдөр";
        if (seconds >= 3_600) return seconds / 3_600 + " цаг";
        return Math.max(1, seconds / 60) + " минут";
    }

    /** Player names and content text must not inject MiniMessage tags. */
    static String esc(String s) {
        return MM.escapeTags(s == null ? "" : s);
    }
}
