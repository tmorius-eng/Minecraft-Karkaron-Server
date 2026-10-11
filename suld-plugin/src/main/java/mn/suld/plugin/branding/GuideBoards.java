package mn.suld.plugin.branding;

import mn.suld.plugin.worldbuild.WorldBuildService;
import net.kyori.adventure.text.Component;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Floating guide boards in front of the arrival point of Kharkhorum: how to play, how to earn coins, and the useful
 * commands. Plain text displays (no extra plugin), re-created whenever they are missing (chunk reload, {@code /kill}),
 * switched with {@code guide.boards} in config.yml.
 */
public final class GuideBoards {

    private static final String TAG = "suld_guide";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private record Board(double dx, double dy, double dz, String text) {
    }

    private static String title(String t) {
        return "<bold><gradient:#FFF0A0:#FFD24A:#FF9A3C>✦ " + t + " ✦</gradient></bold>\n";
    }

    private static String sub(String t) {
        return "<bold><white>" + t + "</white></bold>\n\n";
    }

    /** "label" in gold, then white text. */
    private static String bullet(String label, String rest) {
        return "<bold><#FFD24A>" + label + "</#FFD24A><white> " + rest + "</white></bold>\n";
    }

    /** White text with the commands (between « ») in aqua. */
    private static String line(String text) {
        return "<bold><white>" + text.replace("«", "</white><aqua>").replace("»", "</aqua><white>") + "</white></bold>\n";
    }

    private static final String GAP = "\n";

    private static final List<Board> BOARDS = List.of(
            new Board(-16, 4.8, -2, title("ТАВТАЙ МОРИЛ") + sub("SÜLD — Монгол Hardcore MMORPG")
                    + bullet("1.", "Ангиа сонго: «/class»")
                    + bullet("2.", "Эрлээ ав: «/quest» (18 бүлэг)")
                    + bullet("3.", "Хотын хаалгаар гараад")
                    + line("    тал нутагт мангас ан")
                    + bullet("4.", "Олзоо зарж зоос ол: «/shop»")
                    + bullet("5.", "Түвшин ахихад ур чадвар нээгдэнэ")
                    + GAP + line("Бүх цэс: «/menu» (9-р нүдний цаг)")
                    + line("Заавар: «/help»  «/tutorial»")),
            new Board(-8, 5.2, -6, title("ЗООС ХЭРХЭН ОЛОХ ВЭ?") + GAP
                    + bullet("• Мангас ан", "— олз унана (арьс, хуяг, эрдэнэ)")
                    + line("   Худалдаачинд зарна: «/shop»")
                    + bullet("• Эрэл дуусга", "— бүлэг бүр зоос өгнө")
                    + bullet("• Өдрийн даалгавар:", "«/tasks»")
                    + bullet("• Өдрийн шагнал:", "«/daily»")
                    + bullet("• Агуй:", "«/dungeon» — их шагнал")
                    + bullet("• Арилжаа:", "«/trade»  «/pay»")
                    + GAP + line("Зарцуул: «/rankup»  «/cosmetics»  Дархан")),
            new Board(0, 5.6, -9, title("АНГИ БА ТУЛАА") + GAP
                    + bullet("Баатар", "— тэсвэр, ойрын тулаан")
                    + bullet("Мэргэн", "— нум сум, холоос")
                    + bullet("Бөө", "— сүнсний ид шид, бүлэг хамгаална")
                    + bullet("Дархан", "— гал, дархны чадвар")
                    + bullet("Хүлэгчин", "— хурд, довтолгоо")
                    + GAP + line("Ангийн зэвсгээ барьж 3 товшилт:")
                    + line("   Баруун-Зүүн-Баруун = ид шид!")
                    + line("Хослол бүр: «/skills»")
                    + line("Ганц ангийн зэвсэг: 12, 24, 36, 48, 60-р")
                    + line("түвшинд өөрөө хувирч хүчирхэгжинэ")),
            new Board(8, 5.2, -6, title("НУТАГ, АЮУЛ") + GAP
                    + bullet("➜ Зүүн", "Хэрлэн (түвшин 1–8)")
                    + bullet("➜ Өмнө", "Говь (5–15)")
                    + bullet("➜ Хойд", "Хангай (10–20)")
                    + bullet("➜ Баруун", "Алтай (18–30)")
                    + line("Нутаг бүр 6 газартай (24 газар):")
                    + line("Туул, Онон, Орхон, Сэлэнгэ, Говь...")
                    + line("Шинэ газрыг анх нээхэд EXP!")
                    + GAP + "<bold><red>HARDCORE:</red><white> үхвэл юмныхаа</white></bold>\n"
                    + line("хагас, EXP-ийн 10 хувийг алдана.")
                    + line("Хот аюулгүй: PvP, мангас үгүй. «/spawn»")
                    + line("Агуй (бүлгээрээ): «/dungeon»")),
            new Board(16, 4.8, -2, title("КОМАНДУУД") + GAP
                    + line("«/menu» «/help» «/tutorial»")
                    + line("«/quest» «/tasks» «/daily»")
                    + line("«/class» «/skills» «/profile»")
                    + line("«/shop» «/rankup» «/lvlup»")
                    + line("«/cosmetics» «/buy» «/top»")
                    + line("«/party» «/clan» «/trade»")
                    + line("«/mori» (морь, 5-р түвшин)")
                    + line("«/spawn» «/balance» «/pay»")
                    + line("«/commands» — бүх жагсаалт")
                    + line("«/discord» «/website»")));

    private final Plugin plugin;
    private final WorldBuildService city;
    private final List<UUID> spawned = new ArrayList<>();

    public GuideBoards(Plugin plugin, WorldBuildService city) {
        this.plugin = plugin;
        this.city = city;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("guide.boards", true)) return;
        Bukkit.getScheduler().runTaskTimer(plugin, this::ensure, 100L, 20L * 30);
        Bukkit.getScheduler().runTaskTimer(plugin, this::sparkle, 140L, 30L);
    }

    /** A little gold glimmer on every board so new players' eyes land on them. */
    private void sparkle() {
        for (UUID id : spawned) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) continue;
            boolean near = false;
            for (org.bukkit.entity.Player p : e.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(e.getLocation()) < 30 * 30) {
                    near = true;
                    break;
                }
            }
            if (near) {
                e.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, e.getLocation().add(0, 1.9, 0), 3, 1.6, 0.25, 0.15, 0.01);
                e.getWorld().spawnParticle(org.bukkit.Particle.WAX_OFF, e.getLocation().add(0, -2.0, 0), 2, 1.2, 0.2, 0.1, 0.0);
            }
        }
    }

    /** Place any board that is missing now (a player has just arrived and their chunk is loaded). */
    public void refresh() {
        if (plugin.getConfig().getBoolean("guide.boards", true)) ensure();
    }

    /** Remove the boards and place them again (e.g. after editing the text). */
    public int rebuild() {
        for (UUID id : spawned) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        spawned.clear();
        ensure();
        return spawned.size();
    }

    /** Boards at eye level (bottom edge 1.4 above the ground, about 3 blocks tall), in an arc around the arrival point. */
    static final float SCALE = 0.85f;
    static final double EYE = 1.4, RADIUS = 7.5;

    /**
     * Where a board goes: in its direction from the arrival point, at {@link #RADIUS} when that spot is clear of
     * buildings, else the nearest clear radius from 4 to 13 (so a board never stands inside a statue or a wall).
     * Null when no clear spot exists (the board is skipped rather than drawn through a build).
     */
    private static Location place(Location base, Board b) {
        double len = Math.hypot(b.dx(), b.dz());
        double ux = len == 0 ? 0 : b.dx() / len, uz = len == 0 ? -1 : b.dz() / len;
        for (int k = 0; k <= 18; k++) {
            double r = RADIUS + (k % 2 == 0 ? k / 4.0 : -(k + 1) / 4.0); // 7.5, 7.0, 8.0, 6.5, 8.5 ...
            if (r < 4 || r > 13) continue;
            Location at = base.clone().add(ux * r, 0, uz * r);
            at.setY(base.getY() + EYE);
            if (clear(at)) return at;
        }
        return null;
    }

    /** No solid block in the board's volume: 3 wide (it turns to face the viewer), 3.5 tall, from its bottom edge. */
    private static boolean clear(Location at) {
        if (!at.isChunkLoaded()) return true;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 3; dy++)
                    if (!at.clone().add(dx, dy, dz).getBlock().isPassable()) return false;
        return true;
    }

    private void ensure() {
        if (!city.isBuilt()) return;
        Location base = city.pointLocation("spawn");
        if (base == null || base.getWorld() == null) return;
        // never duplicate: drop tagged leftovers that this instance does not know about
        if (spawned.isEmpty()) {
            for (Entity e : base.getWorld().getEntitiesByClass(TextDisplay.class)) {
                if (e.getScoreboardTags().contains(TAG)) e.remove();
            }
        }
        for (int i = 0; i < BOARDS.size(); i++) {
            Board b = BOARDS.get(i);
            UUID known = i < spawned.size() ? spawned.get(i) : null;
            Entity existing = known == null ? null : Bukkit.getEntity(known);
            if (existing != null && existing.isValid()) continue;
            Location at = place(base, b);
            if (at == null || !at.isChunkLoaded()) continue;
            Component text = MM.deserialize(b.text());
            TextDisplay d = base.getWorld().spawn(at, TextDisplay.class, t -> {
                t.text(text);
                t.setBillboard(Display.Billboard.CENTER);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
                t.setViewRange(0.6f);
                t.setPersistent(false);
                t.addScoreboardTag(TAG);
                t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f()));
            });
            if (i < spawned.size()) spawned.set(i, d.getUniqueId());
            else spawned.add(d.getUniqueId());
        }
    }
}
