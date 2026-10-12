package mn.suld.plugin.resourcepack;

import com.sun.net.httpserver.HttpServer;
import mn.suld.api.config.ResourcePackSettings;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends the SÜLD resource pack (custom HUD font, item skins) on join and tracks per-player status.
 * <p>
 * Two delivery modes:
 * <ul>
 *   <li><b>external</b>: {@code resource-pack.url} (+ {@code sha1}) points at a hosted ZIP (the VPS deploy
 *       builds one with deploy/build-pack.sh);</li>
 *   <li><b>self-host</b> (default when no url is set): the pack bundled in the plugin jar
 *       ({@code suld-resourcepack.zip}, built reproducibly by Gradle from {@code resourcepack/}) is served by a
 *       tiny built-in HTTP server. Players get {@code http://<host they connected with>:<port>/suld-<sha1>.zip},
 *       so a local test server works with no setup and the URL changes whenever the pack does.</li>
 * </ul>
 * Pure custom code over the Paper resource-pack API — no external pack plugin.
 */
public final class ResourcePackService implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("suld:resourcepack".getBytes(StandardCharsets.UTF_8));

    private final Plugin plugin;
    private volatile ResourcePackSettings settings;
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> status = new ConcurrentHashMap<>();
    private byte[] bundled;
    private String bundledSha1 = "";
    private HttpServer server;
    private ExecutorService httpPool;
    /** First-join screens wait here until the pack prompt is answered (ONB-001). */
    private final PackGate gate = new PackGate(PROMPT_WAIT_MS, DOWNLOAD_WAIT_MS);
    private BukkitTask gateTicker;

    /** No answer yet: the prompt may still be on screen. Give the player this long to read it. */
    static final long PROMPT_WAIT_MS = 30_000;
    /** Accepted: the download is running; wait at most this long for "loaded" / "failed". */
    static final long DOWNLOAD_WAIT_MS = 90_000;

    public ResourcePackService(Plugin plugin, ResourcePackSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /** Start the built-in pack server if self-hosting applies. */
    public void start() {
        if (!settings.enabled() || !settings.url().isBlank() || !settings.selfHost()) return;
        try (InputStream in = plugin.getResource("suld-resourcepack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("Resource pack: no bundled suld-resourcepack.zip in the plugin jar; not self-hosting.");
                return;
            }
            bundled = in.readAllBytes();
            bundledSha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bundled));
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().warning("Resource pack: cannot read the bundled pack: " + e.getMessage());
            return;
        }
        try {
            // OPS-004: a stalled reader must not hold a worker forever (read in by the JDK server at its first use)
            if (System.getProperty("sun.net.httpserver.maxReqTime") == null) System.setProperty("sun.net.httpserver.maxReqTime", "30");
            if (System.getProperty("sun.net.httpserver.maxRspTime") == null) System.setProperty("sun.net.httpserver.maxRspTime", "120");
            server = HttpServer.create(new InetSocketAddress(settings.hostBind(), settings.hostPort()), 16);
            String path = "/suld-" + bundledSha1 + ".zip";
            server.createContext("/", exchange -> {
                try (exchange) {
                    String p = exchange.getRequestURI().getPath();
                    if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }
                    if (!p.equals(path) && !p.equals("/suld-resourcepack.zip")) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
                    boolean head = "HEAD".equals(exchange.getRequestMethod());
                    exchange.sendResponseHeaders(200, head ? -1 : bundled.length);
                    if (!head) {
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(bundled);
                        }
                    }
                }
            });
            // up to 8 downloads at once (a wave of new players), idle workers end after 30 s; production uses nginx
            java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(8, 8, 30,
                    java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "SULD-pack-http");
                t.setDaemon(true);
                return t;
            });
            pool.allowCoreThreadTimeOut(true);
            httpPool = pool;
            server.setExecutor(httpPool);
            server.start();
            plugin.getLogger().info("Resource pack: self-hosting " + bundled.length / 1024 + " KiB (sha1 " + bundledSha1
                    + ") on " + settings.hostBind() + ":" + settings.hostPort());
        } catch (IOException e) {
            plugin.getLogger().warning("Resource pack: cannot start the pack server on port " + settings.hostPort() + ": " + e.getMessage());
            server = null;
        }
    }

    public void stop() {
        if (gateTicker != null) gateTicker.cancel();
        gateTicker = null;
        if (server != null) server.stop(0);
        if (httpPool != null) httpPool.shutdownNow();
        server = null;
    }

    /** Re-read settings after a config reload. */
    public void updateSettings(ResourcePackSettings settings) {
        this.settings = settings;
    }

    public boolean enabled() {
        return settings.enabled() && (!settings.url().isBlank() || server != null);
    }

    public boolean selfHosting() {
        return server != null;
    }

    public String sha1() {
        return server != null ? bundledSha1 : settings.sha1();
    }

    /** The URL this player should download the pack from. */
    public String urlFor(Player player) {
        if (!settings.url().isBlank()) return settings.url();
        String base = settings.publicUrl();
        if (base.isBlank()) {
            InetSocketAddress vh = player.getVirtualHost();
            String host = vh != null && vh.getHostString() != null && !vh.getHostString().isBlank() ? vh.getHostString() : "127.0.0.1";
            // the pack server listens on IPv4 (0.0.0.0): "localhost" resolves to ::1 first on Windows and the
            // client's download can hang there, so a local player gets the IPv4 loopback
            if (host.equalsIgnoreCase("localhost") || host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) host = "127.0.0.1";
            if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]"; // IPv6 literal
            base = "http://" + host + ":" + settings.hostPort();
        }
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/suld-" + bundledSha1 + ".zip";
    }

    /** Send the pack to a player (if enabled). Safe to call on the main thread. */
    public void send(Player player) {
        if (!enabled()) {
            return;
        }
        sentAt.put(player.getUniqueId(), System.currentTimeMillis());
        byte[] hash = decodeSha1(sha1());
        Component prompt = Component.text(settings.prompt(), Messages.BRAND);
        try {
            String url = urlFor(player);
            plugin.getLogger().info("Resource pack: sending " + url + " to " + player.getName());
            player.setResourcePack(PACK_ID, url, hash, prompt, settings.required());
            player.sendMessage(Messages.info("Дүрс багц илгээж байна…"));
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to send resource pack to " + player.getName() + ": " + ex.getMessage());
        }
    }

    public PlayerResourcePackStatusEvent.Status statusOf(UUID player) {
        return status.get(player);
    }

    /**
     * Run {@code action} (on the main thread) once this player's pack prompt is settled: loaded, declined or failed,
     * or after a bounded wait when the client stays silent. Opening an inventory while the vanilla pack prompt is on
     * screen replaces the prompt and the pack request is lost, so first-join screens go through here.
     * Runs at once when no pack is sent or the player already answered this session.
     */
    public void afterPack(Player player, Runnable action) {
        PlayerResourcePackStatusEvent.Status known = status.get(player.getUniqueId());
        if (!enabled() || (known != null && answer(known) == PackGate.Answer.TERMINAL)) {
            action.run();
            return;
        }
        gate.await(player.getUniqueId(), System.currentTimeMillis(), action);
        if (gateTicker == null) {
            gateTicker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                for (Runnable r : gate.due(System.currentTimeMillis())) run(r);
            }, 20L, 20L);
        }
    }

    static PackGate.Answer answer(PlayerResourcePackStatusEvent.Status s) {
        return switch (s) {
            case SUCCESSFULLY_LOADED, DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> PackGate.Answer.TERMINAL;
            case ACCEPTED, DOWNLOADED -> PackGate.Answer.IN_PROGRESS;
        };
    }

    private void run(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Resource pack: a deferred join action failed: " + ex);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        gate.forget(id);
        status.remove(id);
    }

    /** When the pack was sent to each online player, for the time-to-load of the funnel event. */
    private final Map<UUID, Long> sentAt = new ConcurrentHashMap<>();
    /** Set by SuldServices: the analytics sink. */
    public volatile java.util.function.Consumer<mn.suld.api.analytics.AnalyticsEvent> analytics = e -> { };

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        // only SÜLD's pack decides the HUD and the onboarding gate: another plugin's pack must not overwrite it
        if (event.getID() != null && !PACK_ID.equals(event.getID())) {
            plugin.getLogger().info("Resource pack: " + player.getName() + " -> " + event.getStatus() + " for another pack " + event.getID());
            return;
        }
        status.put(player.getUniqueId(), event.getStatus());
        Long t0 = sentAt.get(player.getUniqueId());
        plugin.getLogger().info("Resource pack: " + player.getName() + " -> " + event.getStatus()
                + (t0 == null ? "" : " (" + (System.currentTimeMillis() - t0) + " ms after sending)"));
        // the funnel's second step (ANA-001): every status with the time since the pack was sent; a decline on a
        // required pack is where a player is lost before the class menu
        Long sent = sentAt.get(player.getUniqueId());
        analytics.accept(mn.suld.api.analytics.AnalyticsEvent.of(mn.suld.api.analytics.AnalyticsEventType.RESOURCE_PACK_STATUS,
                player.getUniqueId(), java.util.Map.of("status", event.getStatus().name(), "required", settings.required(),
                        "ms", sent == null ? -1L : System.currentTimeMillis() - sent)));
        Runnable ready = gate.answer(player.getUniqueId(), answer(event.getStatus()), System.currentTimeMillis());
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> player.sendMessage(Messages.success("Дүрс багц амжилттай ачаалагдлаа."));
            case DECLINED -> {
                if (settings.required()) {
                    // ONB-008: the client would drop the player with a bare vanilla line; say what SÜLD needs and how
                    player.kick(Component.text("SÜLD-д дүрс багц заавал хэрэгтэй (цэс, тэмдэг, зэвсгийн дүрс).\n\n", Messages.BRAND)
                            .append(Component.text("Дахин орохдоо «Yes / Тийм» дарна уу.\nЭсвэл: Multiplayer → SÜLD → Edit → Server Resource Packs: Enabled",
                                    net.kyori.adventure.text.format.NamedTextColor.WHITE)));
                } else {
                    player.sendMessage(Messages.info("Дүрс багцгүйгээр тоглож байна. (Server list → Edit → Server Resource Packs: Enabled)"));
                }
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> player.sendMessage(Messages.error(
                    "Дүрс багц татаж чадсангүй (" + event.getStatus() + "). Дахин оролдох: /suldpack"));
            default -> {
                // ACCEPTED / DOWNLOADED / other intermediate states: no message.
            }
        }
        if (ready != null) {
            // the status event can arrive off the main thread; screens must open on it
            if (plugin.getServer().isPrimaryThread()) run(ready);
            else plugin.getServer().getScheduler().runTask(plugin, () -> run(ready));
        }
    }

    private static byte[] decodeSha1(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        String clean = hex.trim();
        if (clean.length() != 40) {
            return null;
        }
        return HexFormat.of().parseHex(clean);
    }
}
