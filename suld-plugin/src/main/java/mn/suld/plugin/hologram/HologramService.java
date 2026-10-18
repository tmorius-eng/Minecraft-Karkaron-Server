package mn.suld.plugin.hologram;

import mn.suld.api.campaign.CampaignDefinition;
import mn.suld.api.campaign.CampaignRun;
import mn.suld.api.campaign.Contribution;
import mn.suld.api.chronicle.ChronicleEntry;
import mn.suld.api.chronicle.ChronicleKind;
import mn.suld.api.hologram.HologramBoard;
import mn.suld.api.hologram.HologramLoader;
import mn.suld.api.hologram.HologramSet;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.chronicle.ChronicleService;
import mn.suld.plugin.worldbuild.WorldBuildService;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The boards of content/holograms.json in Kharkhorum: the guide boards before the arrival point, «Мөнх Тэнгэрийн Элч»
 * on top of «Анги ба тулаан», the Хуралдай notice board and the Hall of Fame on the plaza. Where each stands, how it
 * looks and every word on it come from the file; the envoys, the ballot, the campaign and the Chronicle only fill its
 * placeholders. Text displays, never blocks and never saved with the chunk: placed again whenever one is missing and
 * its chunk is loaded, and a board standing on another follows it. Main thread; the only database read (the envoys) is
 * asynchronous and cached.
 */
public final class HologramService {

    static final String TAG = "suld_hologram";
    /** The tags the boards carried before they were data: leftovers with them are cleared on start. */
    private static final Set<String> OLD_TAGS = Set.of("suld_guide", "suld_plaza_board", "suld_envoy_monument");

    /** What a source gives a board: which of its variants to show, its values and its lists. */
    record Data(String variant, Map<String, String> values, Map<String, List<Map<String, String>>> lists) {
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final WorldBuildService city;
    private final Map<String, UUID> spawned = new HashMap<>();
    private final Map<String, String> shown = new HashMap<>();
    private volatile List<String> envoys = List.of();
    private boolean swept;

    public HologramService(Plugin plugin, SuldServices services, WorldBuildService city) {
        this.plugin = plugin;
        this.services = services;
        this.city = city;
    }

    public void start() {
        refreshEnvoys();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::sparkle, 140L, 30L);
    }

    /** Places any board that is missing now (a player has just arrived and their chunk is loaded). */
    public void refresh() {
        tick();
    }

    /** Reads the envoys of Тэнгэрийн Зэрэг X from the Chronicle again (a new one, or one hidden by staff). */
    public void refreshEnvoys() {
        ChronicleService c = services.chronicle();
        if (c == null) return;
        c.byKind(ChronicleKind.HEAVENLY_ENVOY, 24).whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().warning("[holograms] the envoys could not be read: " + err);
                return;
            }
            List<ChronicleEntry> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparingLong(ChronicleEntry::at));
            List<String> n = new ArrayList<>();
            for (ChronicleEntry e : sorted) n.add(e.playerName());
            envoys = List.copyOf(n);
            tick();
        }));
    }

    /** The envoys' names on the board now, the first envoy first. */
    public List<String> envoys() {
        return envoys;
    }

    /** Removes every board and places them again; returns how many stand. */
    public int rebuild() {
        removeAll();
        tick();
        return spawned.size();
    }

    /** Reads content/holograms.json again and rebuilds; returns the server file's problems (none: it is in use). */
    public List<HologramLoader.Issue> reload() {
        List<HologramLoader.Issue> issues = Holograms.install(plugin);
        rebuild();
        return issues;
    }

    /** One line a board: its id and where it stands, or why it does not. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (HologramBoard b : Holograms.set().boards()) {
            Entity e = live(b.id());
            String where = e == null ? (enabled(b) ? "байрлаагүй (бүс ачаалагдаагүй эсвэл зай алга)" : "унтраалттай (config)")
                    : e.getLocation().getBlockX() + " " + String.format(java.util.Locale.ROOT, "%.1f", e.getLocation().getY()) + " " + e.getLocation().getBlockZ();
            out.add(b.id() + " · " + where);
        }
        return out;
    }

    // ------------------------------------------------------------------ placing

    private void tick() {
        if (!city.isBuilt()) return;
        if (!swept) sweep();
        HologramSet set = Holograms.set();
        Map<String, Location> where = new HashMap<>();
        Map<String, String> texts = new HashMap<>();
        for (HologramBoard b : ordered(set)) {
            String text = textOf(b);
            texts.put(b.id(), text);
            Entity e = live(b.id());
            if (e instanceof TextDisplay d && b.placement() != HologramBoard.Placement.ABOVE) {
                where.put(b.id(), d.getLocation()); // where it was placed stays its place (its spot was checked then)
                show(b, d, text);
                continue;
            }
            Location at = place(b, where, texts, set);
            if (at == null) continue;
            where.put(b.id(), at);
            if (!enabled(b)) continue;
            if (e instanceof TextDisplay d) { // a board on another follows it, and its height
                if (d.getLocation().distanceSquared(at) > 0.0025) d.teleport(at);
                show(b, d, text);
                continue;
            }
            if (at.getWorld() == null || !at.isChunkLoaded()) continue;
            spawn(b, at, text);
        }
    }

    /** The boards in an order where a board comes after the one it stands on. */
    static List<HologramBoard> ordered(HologramSet set) {
        Map<String, HologramBoard> left = new LinkedHashMap<>();
        for (HologramBoard b : set.boards()) left.put(b.id(), b);
        List<HologramBoard> out = new ArrayList<>();
        while (!left.isEmpty()) {
            int before = out.size();
            for (var it = left.values().iterator(); it.hasNext(); ) {
                HologramBoard b = it.next();
                if (b.placement() != HologramBoard.Placement.ABOVE || out.stream().anyMatch(o -> o.id().equals(b.above()))) {
                    out.add(b);
                    it.remove();
                }
            }
            if (out.size() == before) break; // a ring or a missing board below: the loader refuses those
        }
        return out;
    }

    private Location place(HologramBoard b, Map<String, Location> where, Map<String, String> texts, HologramSet set) {
        if (b.placement() == HologramBoard.Placement.ABOVE) {
            Location below = where.get(b.above());
            HologramBoard under = set.board(b.above()).orElse(null);
            if (below == null || under == null) return null;
            return below.clone().add(0, Holograms.height(texts.get(under.id()), under.style()) + b.dy(), 0);
        }
        Location base = anchor(b.anchor());
        if (base == null || base.getWorld() == null) return null;
        return b.placement() == HologramBoard.Placement.ARC ? arc(base, b) : offset(base, b);
    }

    /** A city point, or {@code city}: the city's middle on the ground. */
    private Location anchor(String name) {
        if (!"city".equals(name)) return city.pointLocation(name);
        World w = Bukkit.getWorlds().get(0);
        int[] o = city.origin(w.getName()).orElse(null);
        if (o == null || !w.isChunkLoaded(o[0] >> 4, o[1] >> 4)) return null;
        return new Location(w, o[0] + 0.5, w.getHighestBlockYAt(o[0], o[1], HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, o[1] + 0.5);
    }

    /**
     * In the board's direction from the anchor, at its radius when that spot is clear of buildings, else the nearest
     * clear radius up to 4.5 blocks nearer or farther (a board never stands inside a statue or a wall); null when no
     * spot is clear.
     */
    private static Location arc(Location base, HologramBoard b) {
        double len = Math.hypot(b.dx(), b.dz());
        double ux = len == 0 ? 0 : b.dx() / len, uz = len == 0 ? -1 : b.dz() / len;
        for (int k = 0; k <= 18; k++) {
            double r = b.radius() + (k % 2 == 0 ? k / 4.0 : -(k + 1) / 4.0); // 7.5, 7.0, 8.0, 6.5, 8.5 …
            if (r < 1) continue;
            Location at = base.clone().add(ux * r, 0, uz * r);
            at.setY(base.getY() + b.dy());
            if (!b.clear() || clear(at)) return at;
        }
        return null;
    }

    /** The anchor plus the offset (half of it when the spot is in a build); null when neither spot is clear. */
    private static Location offset(Location base, HologramBoard b) {
        for (double f : new double[]{1, 0.5}) {
            Location at = base.clone().add(b.dx() * f, 0, b.dz() * f);
            at.setY(b.ground() && at.isChunkLoaded()
                    ? at.getWorld().getHighestBlockYAt(at.getBlockX(), at.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1 + b.dy()
                    : base.getY() + b.dy());
            if (!b.clear() || clear(at)) return at;
        }
        return null;
    }

    /** No solid block in a board's volume: 3 wide (it turns to face the viewer), 4 tall from its bottom edge. */
    private static boolean clear(Location at) {
        if (!at.isChunkLoaded()) return true;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 3; dy++)
                    if (!at.clone().add(dx, dy, dz).getBlock().isPassable()) return false;
        return true;
    }

    /** The config switches stay: {@code guide.boards} for the guide boards, {@code chronicle.hall-boards} for the plaza. */
    private boolean enabled(HologramBoard b) {
        if (b.id().startsWith("guide.")) return plugin.getConfig().getBoolean("guide.boards", true);
        if (b.id().startsWith("plaza.")) return plugin.getConfig().getBoolean("chronicle.hall-boards", true);
        return true;
    }

    private void spawn(HologramBoard b, Location at, String text) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            Holograms.style(t, b.style());
            t.text(Holograms.component(text));
            t.setPersistent(false);
            t.addScoreboardTag(TAG);
            t.addScoreboardTag(TAG + "_" + b.id().replace('.', '_'));
            if (b.tag() != null) t.addScoreboardTag(b.tag());
        });
        spawned.put(b.id(), d.getUniqueId());
        shown.put(b.id(), text);
    }

    private void show(HologramBoard b, TextDisplay d, String text) {
        if (text.equals(shown.get(b.id()))) return;
        d.text(Holograms.component(text));
        shown.put(b.id(), text);
    }

    private Entity live(String id) {
        UUID u = spawned.get(id);
        Entity e = u == null ? null : Bukkit.getEntity(u);
        return e != null && e.isValid() ? e : null;
    }

    private void removeAll() {
        for (UUID u : spawned.values()) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        spawned.clear();
        shown.clear();
    }

    /** Leftovers from before a reload (non-persistent displays outlive a plugin reload, not a restart). */
    private void sweep() {
        swept = true;
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay t : w.getEntitiesByClass(TextDisplay.class)) {
                Set<String> tags = t.getScoreboardTags();
                if (tags.contains(TAG) || tags.stream().anyMatch(OLD_TAGS::contains)) t.remove();
            }
        }
    }

    /** A few gold sparks on the boards that ask for them while a player is near, so new players' eyes land there. */
    private void sparkle() {
        for (HologramBoard b : Holograms.set().boards()) {
            if (!b.sparkle()) continue;
            Entity e = live(b.id());
            if (e == null) continue;
            boolean near = false;
            for (Player p : e.getWorld().getPlayers()) {
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

    // ------------------------------------------------------------------ text

    private String textOf(HologramBoard b) {
        Data d = b.source() == null ? new Data(HologramBoard.TEXT, Map.of(), Map.of()) : switch (b.source()) {
            case "envoys" -> envoyData(envoys);
            case "khuraldai" -> noticeData();
            case "chronicle" -> hallData();
            default -> new Data(HologramBoard.TEXT, Map.of(), Map.of());
        };
        return render(b, d);
    }

    /** A board's MiniMessage text for this data. */
    static String render(HologramBoard b, Data d) {
        return Holograms.mini(b.lines(d.variant()), d.values(), d.lists(), b.items(), b.headers());
    }

    private Data noticeData() {
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
            return ballotData(names, votes, left);
        }
        CampaignRun run = services.campaigns() == null ? null : services.campaigns().active().orElse(null);
        if (run == null) return new Data("idle", Map.of(), Map.of());
        List<String> top = new ArrayList<>();
        List<Long> units = new ArrayList<>();
        for (Contribution c : run.ranking()) {
            if (top.size() == 3) break;
            top.add(c.name());
            units.add(c.units());
        }
        CampaignDefinition def = run.definition();
        return campaignData(def.name(), def.goal().unitWord(), run.total(), def.goal().target(), run.remainingSeconds(Instant.now()), top, units);
    }

    private Data hallData() {
        ChronicleService chronicle = services.chronicle();
        List<String> lines = new ArrayList<>();
        if (chronicle != null) {
            int max = services.config().chronicle().hallEntries();
            for (var e : chronicle.latest()) {
                if (lines.size() >= Math.min(max, 8)) break;
                lines.add(chronicle.headline(e).replace("✦ ", "").replaceFirst("^Хроник: ", ""));
            }
        }
        return hallData(lines);
    }

    static Data envoyData(List<String> names) {
        if (names.isEmpty()) return new Data("empty", Map.of(), Map.of());
        List<Map<String, String>> l = new ArrayList<>();
        for (String n : names) l.add(Map.of("name", n));
        return new Data("names", Map.of(), Map.of("names", l));
    }

    static Data ballotData(List<String> names, List<Integer> votes, long secondsLeft) {
        List<Map<String, String>> options = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            options.add(Map.of("n", Integer.toString(i + 1), "name", names.get(i), "votes", Integer.toString(votes.get(i))));
        }
        return new Data("ballot", Map.of("count", Integer.toString(names.size()), "left", time(secondsLeft)), Map.of("options", options));
    }

    static Data campaignData(String name, String unit, long total, long target, long secondsLeft, List<String> top, List<Long> units) {
        int pct = target <= 0 ? 0 : (int) Math.min(100, total * 100 / target);
        int filled = pct / 10;
        List<Map<String, String>> leaders = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            leaders.add(Map.of("n", Integer.toString(i + 1), "name", top.get(i), "units", Long.toString(units.get(i))));
        }
        return new Data("campaign", Map.of("name", name, "unit", unit, "total", Long.toString(total), "target", Long.toString(target),
                "left", time(secondsLeft), "bar_full", "█".repeat(filled), "bar_empty", "█".repeat(10 - filled)), Map.of("top", leaders));
    }

    static Data hallData(List<String> lines) {
        if (lines.isEmpty()) return new Data("empty", Map.of(), Map.of());
        List<Map<String, String>> l = new ArrayList<>();
        for (String s : lines) l.add(Map.of("line", s));
        return new Data("lines", Map.of(), Map.of("lines", l));
    }

    static String time(long seconds) {
        if (seconds >= 86_400) return seconds / 86_400 + " өдөр";
        if (seconds >= 3_600) return seconds / 3_600 + " цаг";
        return Math.max(1, seconds / 60) + " минут";
    }
}
