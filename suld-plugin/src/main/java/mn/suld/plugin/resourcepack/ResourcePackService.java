package mn.suld.plugin.resourcepack;

import com.sun.net.httpserver.HttpServer;
import mn.suld.api.config.ResourcePackSettings;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

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
            httpPool = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "SULD-pack-http");
                t.setDaemon(true);
                return t;
            });
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
        byte[] hash = decodeSha1(sha1());
        Component prompt = Component.text(settings.prompt(), Messages.BRAND);
        try {
            player.setResourcePack(PACK_ID, urlFor(player), hash, prompt, settings.required());
            player.sendMessage(Messages.info("Дүрс багц илгээж байна…"));
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to send resource pack to " + player.getName() + ": " + ex.getMessage());
        }
    }

    public PlayerResourcePackStatusEvent.Status statusOf(UUID player) {
        return status.get(player);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        status.put(player.getUniqueId(), event.getStatus());
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> player.sendMessage(Messages.success("Дүрс багц амжилттай ачаалагдлаа."));
            case DECLINED -> player.sendMessage(Messages.info("Дүрс багцгүйгээр тоглож байна. (Server list → Edit → Server Resource Packs: Enabled)"));
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> player.sendMessage(Messages.error(
                    "Дүрс багц татаж чадсангүй (" + event.getStatus() + "). /suldpack force дахин оролдоно уу."));
            default -> {
                // ACCEPTED / DOWNLOADED / other intermediate states: no message.
            }
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
