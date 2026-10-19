package mn.suld.plugin.social;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * SÜLD → Discord (docs/DISCORD.md). The moments worth seeing from outside the server go to a public channel's
 * webhook (chronicle firsts, the world boss call and its rise, Их Хуралдай opening and its result, the server coming
 * up and going down); {@code /report} goes to a staff channel's webhook. A webhook URL is a secret from
 * {@code config.yml} ({@code discord.webhook}, {@code discord.staff-webhook}; the deploy writes them from the
 * environment) and is never logged or shown. Lines are batched per channel ({@link DiscordQueue}) and one message per
 * channel leaves every two seconds, off the main thread; a webhook that fails is logged once a minute by status or
 * exception class, never with the URL or the body. Blank = that channel is off; both blank = nothing runs.
 */
public final class DiscordNotifier {

    public enum Channel { PUBLIC, STAFF }

    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long FLUSH_TICKS = 40;
    private static final long FAILURE_LOG_MS = 60_000;

    private final Plugin plugin;
    private final Logger log;
    private final Map<Channel, URI> urls = new EnumMap<>(Channel.class);
    private final Map<Channel, DiscordQueue> queues = new EnumMap<>(Channel.class);
    private final Map<Channel, Long> lastFailureLog = new EnumMap<>(Channel.class);
    private final AtomicInteger sent = new AtomicInteger(), failed = new AtomicInteger();
    private final ExecutorService executor;
    private final HttpClient http;
    private BukkitTask task;

    public DiscordNotifier(Plugin plugin, String publicUrl, String staffUrl) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        put(Channel.PUBLIC, publicUrl);
        put(Channel.STAFF, staffUrl);
        for (Channel c : Channel.values()) queues.put(c, new DiscordQueue());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "suld-discord");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).executor(executor).followRedirects(HttpClient.Redirect.NEVER).build();
        log.info("discord: public channel " + (enabled(Channel.PUBLIC) ? "on" : "off") + ", staff channel " + (enabled(Channel.STAFF) ? "on" : "off"));
    }

    private void put(Channel c, String url) {
        if (url == null || url.isBlank()) return;
        if (!acceptable(url)) {
            log.warning("discord: the " + c.name().toLowerCase(Locale.ROOT) + " webhook is not an https URL (or a loopback http one for a dev server): channel off");
            return;
        }
        try {
            urls.put(c, URI.create(url.trim()));
        } catch (IllegalArgumentException e) {
            log.warning("discord: the " + c.name().toLowerCase(Locale.ROOT) + " webhook is not a valid URL: channel off");
        }
    }

    /** https anywhere; plain http only to the loopback host (a dev server's local stand-in). The URL itself stays out of every log. */
    static boolean acceptable(String url) {
        String u = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        if (u.startsWith("https://")) return u.length() > 8;
        return u.startsWith("http://127.0.0.1/") || u.startsWith("http://127.0.0.1:") || u.startsWith("http://localhost/") || u.startsWith("http://localhost:");
    }

    public boolean enabled(Channel c) {
        return urls.containsKey(c);
    }

    public boolean enabled() {
        return !urls.isEmpty();
    }

    public void start() {
        if (!enabled() || task != null) return;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, FLUSH_TICKS, FLUSH_TICKS);
    }

    /** A line for the public channel (what the server is doing, for players outside it). */
    public void announce(String line) {
        offer(Channel.PUBLIC, line);
    }

    /** A line for the staff channel (reports). */
    public void staff(String line) {
        offer(Channel.STAFF, line);
    }

    private void offer(Channel c, String line) {
        if (enabled(c)) queues.get(c).offer(line);
    }

    private void flush() {
        for (Channel c : Channel.values()) {
            if (!enabled(c)) continue;
            Optional<String> text = queues.get(c).next();
            text.ifPresent(t -> post(c, t));
        }
    }

    private CompletableFuture<Boolean> post(Channel c, String text) {
        HttpRequest req = HttpRequest.newBuilder(urls.get(c)).timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json(text), StandardCharsets.UTF_8)).build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).handle((res, err) -> {
            if (err == null && res.statusCode() / 100 == 2) {
                sent.incrementAndGet();
                return true;
            }
            failed.incrementAndGet();
            failure(c, err == null ? "answered " + res.statusCode() : "failed: " + root(err).getClass().getSimpleName());
            return false;
        });
    }

    private void failure(Channel c, String what) {
        long now = System.currentTimeMillis();
        Long last = lastFailureLog.get(c);
        if (last != null && now - last < FAILURE_LOG_MS) return;
        lastFailureLog.put(c, now);
        log.warning("discord: the " + c.name().toLowerCase(Locale.ROOT) + " webhook " + what + " (the line is dropped; next warning in a minute at most)");
    }

    private static Throwable root(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t;
    }

    /** {@code {"content": …}} without a JSON library: the five escapes JSON needs and every other control character. */
    static String json(String content) {
        StringBuilder sb = new StringBuilder("{\"content\":\"");
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.append("\"}").toString();
    }

    /** For {@code /suld discord}: the state without a secret in it. */
    public String describe() {
        return "public " + (enabled(Channel.PUBLIC) ? "on" : "off") + ", staff " + (enabled(Channel.STAFF) ? "on" : "off")
                + ", sent " + sent.get() + ", failed " + failed.get()
                + ", waiting " + queues.get(Channel.PUBLIC).size() + "/" + queues.get(Channel.STAFF).size();
    }

    /**
     * On disable: the last public line (the server going down) and whatever still waits leave now, each post given
     * three seconds, then the thread stops. Failures are ignored: the server is shutting down.
     */
    public void shutdown(String lastLine) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!enabled()) return;
        announce(lastLine);
        for (Channel c : Channel.values()) {
            if (!enabled(c)) continue;
            Optional<String> text;
            while ((text = queues.get(c).next()).isPresent()) {
                try {
                    post(c, text.get()).get(3, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    break;
                }
            }
        }
        executor.shutdownNow();
    }
}
