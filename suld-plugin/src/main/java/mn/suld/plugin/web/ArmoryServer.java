package mn.suld.plugin.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import mn.suld.api.balance.AscensionHonor;
import mn.suld.api.campaign.CampaignDefinition;
import mn.suld.api.campaign.CampaignRun;
import mn.suld.api.campaign.vote.VoteCycle;
import mn.suld.api.clan.Clan;
import mn.suld.api.clan.ClanMember;
import mn.suld.api.item.EquipSlot;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.json.Json;
import mn.suld.api.mastery.Profession;
import mn.suld.api.mastery.WeaponMastery;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.web.Armory;
import mn.suld.api.web.RateLimiter;
import mn.suld.plugin.SuldServices;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The read-only armory (docs/ARMORY_API.md): {@code GET /api/player/{uuid}}, {@code /api/clans/leaderboard} and
 * {@code /api/khuraldai/active}, as cached JSON for a web page. Off by default ({@code web.armory.enabled}).
 *
 * <ul>
 *   <li>The game's state is read on the main thread only: a snapshot every {@code cache-seconds} (the clan board, the
 *   week's Хуралдай, the sheets of the players online and the clan of every member). The HTTP threads read the snapshot,
 *   so no request ever touches the world or blocks a tick.</li>
 *   <li>An offline player's sheet is read from the database on the io pool (profile, weapon mastery and crafts, never
 *   the gear: that lives in the player file), kept for {@code cache-seconds}.</li>
 *   <li>Each client address has a token bucket ({@code burst}, {@code per-minute}): over it, 429 with Retry-After.
 *   GET and HEAD only; nothing is written; nothing in a view is private (api {@code Armory.NEVER}).</li>
 * </ul>
 */
public final class ArmoryServer {

    private final Plugin plugin;
    private final SuldServices services;
    private final boolean enabled;
    private final String bind;
    private final int port;
    private final int cacheSeconds;
    private final RateLimiter limiter;
    private final String allowOrigin;
    private HttpServer server;
    private ExecutorService pool;

    /** Whether a snapshot was ever taken: until then every answer is 503. */
    private volatile boolean warm;
    private volatile String clansJson;
    private volatile String khuraldaiJson;
    private volatile Map<UUID, String> online = Map.of();
    private volatile Map<UUID, String[]> clanOf = Map.of(); // member → {tag, name}
    private final Map<UUID, Cached> offline = new ConcurrentHashMap<>();

    private record Cached(String json, long at) {
    }

    public ArmoryServer(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("web.armory");
        this.enabled = c != null && c.getBoolean("enabled", false);
        this.bind = c == null ? "127.0.0.1" : c.getString("bind", "127.0.0.1");
        this.port = c == null ? 8086 : c.getInt("port", 8086);
        this.cacheSeconds = Math.max(5, c == null ? 30 : c.getInt("cache-seconds", 30));
        int burst = Math.max(1, c == null ? 20 : c.getInt("burst", 20));
        double perMinute = Math.max(1, c == null ? 60 : c.getDouble("per-minute", 60));
        this.limiter = new RateLimiter(burst, perMinute / 60.0, 10_000);
        this.allowOrigin = c == null ? "*" : c.getString("allow-origin", "*");
        this.trustedProxies = c == null ? List.of() : c.getStringList("trusted-proxies");
    }

    /** Peers whose X-Forwarded-For names the real client (a reverse proxy on the host); any other peer is the client. */
    private final List<String> trustedProxies;

    /** The client a request is counted against: the forwarded address behind a trusted proxy, else the peer. */
    String client(HttpExchange ex) {
        String peer = ex.getRemoteAddress() == null ? "?" : ex.getRemoteAddress().getAddress().getHostAddress();
        if (!trustedProxies.contains(peer)) return peer;
        String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (fwd == null || fwd.isBlank()) return peer;
        String[] hops = fwd.split(",");
        return hops[hops.length - 1].trim(); // the hop the trusted proxy itself saw: earlier ones the client may forge
    }

    public boolean enabled() {
        return enabled;
    }

    public void start() {
        if (!enabled) return;
        snapshot();
        Bukkit.getScheduler().runTaskTimer(plugin, this::snapshot, 20L * cacheSeconds, 20L * cacheSeconds);
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 32);
            server.createContext("/", this::handle);
            java.util.concurrent.ThreadPoolExecutor p = new java.util.concurrent.ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(256), r -> {
                Thread t = new Thread(r, "SULD-armory-http");
                t.setDaemon(true);
                return t;
            }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
            p.allowCoreThreadTimeOut(true);
            pool = p;
            server.setExecutor(pool);
            server.start();
            plugin.getLogger().info("[armory] read-only API on " + bind + ":" + port + " (cache " + cacheSeconds + " s)");
        } catch (IOException e) {
            plugin.getLogger().warning("[armory] cannot listen on " + bind + ":" + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    // ================================================================== the snapshot (main thread)

    private void snapshot() {
        long now = System.currentTimeMillis();
        try {
            Map<UUID, String[]> clans = new HashMap<>();
            List<Armory.ClanLine> lines = new ArrayList<>();
            List<Clan> all = new ArrayList<>(services.clans().registry().all());
            all.sort(Comparator.comparingLong(Clan::exp).reversed());
            int rank = 0;
            for (Clan c : all) {
                for (ClanMember m : c.members()) clans.put(m.playerId(), new String[]{c.tag(), c.name()});
                if (rank < 50) lines.add(new Armory.ClanLine(++rank, c.tag(), c.name(), c.level(), c.exp(), c.size()));
            }
            clanOf = Map.copyOf(clans);
            clansJson = Json.write(Armory.clans(lines, now));
            khuraldaiJson = Json.write(khuraldai(now));
            Map<UUID, String> sheets = new HashMap<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
                if (pr == null) continue;
                sheets.put(p.getUniqueId(), Json.write(sheet(p.getUniqueId(), pr, true, gear(p), masteries(p.getUniqueId()), crafts(p.getUniqueId()), now).json()));
            }
            online = Map.copyOf(sheets);
            warm = true;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[armory] snapshot failed (the last one stays): " + e);
        }
    }

    private Map<String, Object> khuraldai(long now) {
        var k = services.khuraldai();
        var cs = services.campaigns();
        String phase = k.cycle().phase(Instant.ofEpochMilli(now)).name();
        List<Armory.Option> ballot = new ArrayList<>();
        VoteCycle vc = k.current();
        if (vc != null) {
            Map<String, String> names = new HashMap<>();
            for (CampaignDefinition d : k.ballot()) names.put(d.id(), d.name());
            Map<String, Integer> tally = k.tally();
            for (String o : vc.options()) ballot.add(new Armory.Option(o, names.getOrDefault(o, o), tally.getOrDefault(o, 0)));
        }
        Armory.Campaign campaign = null;
        CampaignRun run = cs.active().orElse(null);
        if (run != null) {
            campaign = new Armory.Campaign(run.definition().id(), run.definition().name(), run.total(), run.definition().goal().target(),
                    run.state().name());
        }
        return Armory.khuraldai(cs.cycleId(), phase, ballot, campaign, cs.worldMarks(), now);
    }

    private List<Armory.Gear> gear(Player p) {
        List<Armory.Gear> out = new ArrayList<>();
        for (Map.Entry<EquipSlot, ItemInstance> e : services.equipment().worn(p).entrySet()) {
            ItemInstance i = e.getValue();
            var def = services.itemService().catalog().item(i.definitionId()).orElse(null);
            if (def == null) continue;
            out.add(new Armory.Gear(e.getKey().name(), mn.suld.api.item.ItemTooltip.name(services.itemService().catalog(), def, i),
                    i.rarity().id(), i.itemLevel()));
        }
        return out;
    }

    private Map<String, Integer> masteries(UUID id) {
        Map<String, Integer> m = new LinkedHashMap<>();
        var wm = services.weaponMastery;
        if (wm != null && wm.loaded(id)) for (WeaponMastery.Track t : WeaponMastery.Track.values()) m.put(t.name(), wm.level(id, t));
        return m;
    }

    private Map<String, Integer> crafts(UUID id) {
        Map<String, Integer> m = new LinkedHashMap<>();
        var ps = services.professions;
        if (ps != null && ps.loaded(id)) for (Profession c : Profession.values()) m.put(c.name(), ps.level(id, c));
        return m;
    }

    private Armory.PlayerSheet sheet(UUID id, PlayerProfile pr, boolean isOnline, List<Armory.Gear> gear,
                                     Map<String, Integer> mastery, Map<String, Integer> crafts, long now) {
        String[] clan = clanOf.get(id);
        int asc = pr.ascension();
        return new Armory.PlayerSheet(id.toString(), pr.name(), isOnline, pr.playerClass().map(c -> c.displayName()).orElse(null),
                pr.progression().level(), asc, title(asc), clan == null ? null : clan[0], clan == null ? null : clan[1], mastery, crafts, gear, now);
    }

    static String title(int rank) {
        if (rank <= 0) return null;
        if (rank <= 3) return "Тэнгэрийн Зэрэг " + new String[]{"", "I", "II", "III"}[rank];
        return AscensionHonor.title(rank);
    }

    // ================================================================== the HTTP side (its own threads)

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String method = ex.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                ex.getResponseHeaders().set("Allow", "GET, HEAD");
                send(ex, 405, Armory.error(405, "read-only: GET only"));
                return;
            }
            String client = client(ex);
            long now = System.currentTimeMillis();
            if (!limiter.allow(client, now)) {
                ex.getResponseHeaders().set("Retry-After", String.valueOf(Math.max(1, (limiter.retryAfterMs(client, now) + 999) / 1000)));
                send(ex, 429, Armory.error(429, "too many requests"));
                return;
            }
            if (!warm) {
                send(ex, 503, Armory.error(503, "warming up"));
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (path.equals("/api/clans/leaderboard")) {
                send(ex, 200, clansJson);
            } else if (path.equals("/api/khuraldai/active")) {
                send(ex, 200, khuraldaiJson);
            } else if (path.startsWith("/api/player/")) {
                String key = path.substring("/api/player/".length());
                if (!Armory.uuid(key)) {
                    send(ex, 400, Armory.error(400, "a player is named by their UUID"));
                    return;
                }
                UUID id = UUID.fromString(key);
                String json = online.get(id);
                if (json == null) {
                    try {
                        json = offlineSheet(id, now);
                    } catch (DatabaseLate e) {
                        send(ex, 504, Armory.error(504, "the database did not answer in " + DB_WAIT_MS / 1000 + " s"));
                        return;
                    }
                }
                if (json == null) send(ex, 404, Armory.error(404, "no such player"));
                else send(ex, 200, json);
            } else {
                send(ex, 404, Armory.error(404, "not found: /api/player/{uuid}, /api/clans/leaderboard, /api/khuraldai/active"));
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[armory] " + e);
            try {
                send(ex, 500, Armory.error(500, "internal error"));
            } catch (IOException | RuntimeException ignored) {
                // the answer had already begun: the client sees a cut reply
            }
        }
    }

    /** How long a request may wait on the database for an offline sheet, all its reads together. */
    static final long DB_WAIT_MS = 3_000;

    /** The database did not answer within {@link #DB_WAIT_MS}, or failed: the request is answered 504, nothing is cached. */
    private static final class DatabaseLate extends Exception {
        DatabaseLate(Throwable cause) {
            super(cause);
        }
    }

    /**
     * An offline player's sheet from the database (kept {@code cache-seconds}); null when there is none. The profile
     * is read first; then the weapon mastery and the crafts at once, all within one wait of {@link #DB_WAIT_MS}.
     */
    private String offlineSheet(UUID id, long now) throws DatabaseLate {
        Cached c = offline.get(id);
        if (c != null && now - c.at() < cacheSeconds * 1000L) return c.json().isEmpty() ? null : c.json();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DB_WAIT_MS);
        try {
            PlayerProfile pr = services.profileRepository().find(id).get(left(deadline), TimeUnit.NANOSECONDS).orElse(null);
            String json = "";
            if (pr != null) {
                var wm = services.weaponMastery;
                var ps = services.professions;
                CompletableFuture<Map<WeaponMastery.Track, Double>> fm = wm == null ? CompletableFuture.completedFuture(Map.of()) : wm.totalsOf(id);
                CompletableFuture<Map<Profession, Long>> fc = ps == null ? CompletableFuture.completedFuture(Map.of()) : ps.totalsOf(id);
                CompletableFuture.allOf(fm, fc).get(left(deadline), TimeUnit.NANOSECONDS);
                Map<String, Integer> mastery = new LinkedHashMap<>(), crafts = new LinkedHashMap<>();
                if (wm != null) {
                    Map<WeaponMastery.Track, Double> t = fm.join();
                    for (WeaponMastery.Track k : WeaponMastery.Track.values()) mastery.put(k.name(), WeaponMastery.fromTotal(t.getOrDefault(k, 0.0)).level());
                }
                if (ps != null) {
                    Map<Profession, Long> t = fc.join();
                    for (Profession k : Profession.values()) crafts.put(k.name(), Profession.fromTotal(t.getOrDefault(k, 0L)).level());
                }
                json = Json.write(sheet(id, pr, false, List.of(), mastery, crafts, now).json());
            }
            if (offline.size() > 5_000) offline.clear(); // bounded: a scan of random UUIDs keeps no memory
            offline.put(id, new Cached(json, now));
            return json.isEmpty() ? null : json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseLate(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new DatabaseLate(e);
        }
    }

    private static long left(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }

    private void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        var h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Cache-Control", status == 200 ? "public, max-age=" + cacheSeconds : "no-store");
        if (allowOrigin != null && !allowOrigin.isBlank()) h.set("Access-Control-Allow-Origin", allowOrigin);
        boolean head = "HEAD".equals(ex.getRequestMethod());
        ex.sendResponseHeaders(status, head ? -1 : b.length);
        if (!head) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(b);
            }
        }
    }

    /** The endpoints (docs/ARMORY_API.md). */
    static List<String> endpoints() {
        return List.of("/api/player/{uuid}", "/api/clans/leaderboard", "/api/khuraldai/active");
    }
}
