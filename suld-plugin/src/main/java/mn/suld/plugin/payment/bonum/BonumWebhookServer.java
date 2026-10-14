package mn.suld.plugin.payment.bonum;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import mn.suld.api.payment.PaymentEvent;
import mn.suld.api.payment.PaymentLedger;
import mn.suld.api.payment.bonum.BonumPaymentResult;
import mn.suld.api.payment.bonum.BonumSignature;
import mn.suld.api.payment.bonum.BonumWebhook;
import mn.suld.api.web.RateLimiter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Bonum's webhook (docs/PAYMENT_ARCHITECTURE.md §Security model): a small HTTP listener off the main thread, meant to
 * sit behind the host's TLS proxy ({@code callback-url} → {@code bind:port/path}).
 *
 * <ol>
 *   <li>POST only, to the one path; at most {@link BonumWebhook#MAX_BODY} bytes; a token bucket per sender; an optional
 *   list of sender addresses.</li>
 *   <li>The raw body's signature ({@code signature-header}, HMAC-SHA256 under the checksum key) must hold, else 401.
 *   Nothing in an unsigned body is read.</li>
 *   <li>The signed event goes to the ledger; the answer is 200 once its result is stored (a duplicate, a mismatch or an
 *   unknown reference too: retrying would not change them), 503 when the store failed (the provider retries).</li>
 *   <li>Every call is appended to {@code suld_payment_events} with the body's SHA-256, never the body; the key and the
 *   header are never logged.</li>
 * </ol>
 */
public final class BonumWebhookServer {

    /** Settings of the listener ({@code payment.bonum.webhook}). */
    public record Settings(String bind, int port, String path, String signatureHeader, List<String> trustedProxies, List<String> allowedIps) {
        public Settings {
            path = path == null || path.isBlank() ? "/api/bonum/callback" : path;
            signatureHeader = signatureHeader == null || signatureHeader.isBlank() ? BonumSignature.HEADER : signatureHeader;
            trustedProxies = List.copyOf(trustedProxies);
            allowedIps = List.copyOf(allowedIps);
        }
    }

    private final Settings settings;
    private final byte[] key;
    private final Function<BonumPaymentResult, CompletableFuture<PaymentLedger.Result>> ledger;
    private final Consumer<PaymentEvent> log;
    private final Logger logger;
    private final RateLimiter limiter = new RateLimiter(30, 2.0, 1_000);
    private HttpServer server;
    private ThreadPoolExecutor pool;

    public BonumWebhookServer(Settings settings, byte[] checksumKey, Function<BonumPaymentResult, CompletableFuture<PaymentLedger.Result>> ledger,
                              Consumer<PaymentEvent> log, Logger logger) {
        this.settings = settings;
        this.key = checksumKey.clone();
        this.ledger = ledger;
        this.log = log;
        this.logger = logger;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(settings.bind(), settings.port()), 32);
        server.createContext("/", this::handle);
        pool = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64), r -> {
            Thread t = new Thread(r, "SULD-bonum-webhook");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(true);
        server.setExecutor(pool);
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    /** The port listened on (the configured one, or the one picked for port 0). */
    public int port() {
        return server == null ? settings.port() : server.getAddress().getPort();
    }

    /** The sender a call is counted against: the forwarded address behind a trusted proxy, else the peer. */
    String client(HttpExchange ex) {
        String peer = ex.getRemoteAddress() == null ? "?" : ex.getRemoteAddress().getAddress().getHostAddress();
        if (!settings.trustedProxies().contains(peer)) return peer;
        String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (fwd == null || fwd.isBlank()) return peer;
        String[] hops = fwd.split(",");
        return hops[hops.length - 1].trim();
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            if (!settings.path().equals(ex.getRequestURI().getPath())) {
                reply(ex, 404, "{\"ok\":false}");
                return;
            }
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.getResponseHeaders().set("Allow", "POST");
                reply(ex, 405, "{\"ok\":false}");
                return;
            }
            String client = client(ex);
            if (!limiter.allow(client, System.currentTimeMillis())) {
                ex.getResponseHeaders().set("Retry-After", "5");
                reply(ex, 429, "{\"ok\":false}");
                return;
            }
            if (!settings.allowedIps().isEmpty() && !settings.allowedIps().contains(client)) {
                reply(ex, 403, "{\"ok\":false}");
                return;
            }
            byte[] body = readBody(ex);
            if (body == null) {
                reply(ex, 413, "{\"ok\":false}");
                return;
            }
            long now = System.currentTimeMillis();
            String sha = sha256(body);
            if (!BonumSignature.verify(body, ex.getRequestHeaders().getFirst(settings.signatureHeader()), key)) {
                log.accept(new PaymentEvent(now, "bonum", sha, false, null, null, null, "BAD_SIGNATURE"));
                logger.warning("[payment] a Bonum webhook with a bad signature was refused (body sha256 " + sha.substring(0, 12) + "…)");
                reply(ex, 401, "{\"ok\":false}");
                return;
            }
            BonumPaymentResult r;
            try {
                r = BonumWebhook.parse(body);
            } catch (RuntimeException bad) {
                log.accept(new PaymentEvent(now, "bonum", sha, true, null, null, null, "BAD_BODY"));
                reply(ex, 400, "{\"ok\":false}");
                return;
            }
            String status = (r.status() == null ? "" : r.status()) + "/" + (r.invoiceStatus() != null ? r.invoiceStatus() : r.bodyStatus() == null ? "" : r.bodyStatus());
            PaymentLedger.Result result;
            try {
                result = ledger.apply(r).get(8, TimeUnit.SECONDS);
            } catch (Exception failed) {
                if (failed instanceof InterruptedException) Thread.currentThread().interrupt();
                log.accept(new PaymentEvent(now, "bonum", sha, true, r.transactionId(), r.invoiceId(), status, "ERROR"));
                logger.warning("[payment] a Bonum webhook for " + r.transactionId() + " could not be stored now (" + failed.getClass().getSimpleName()
                        + "); the provider will retry");
                reply(ex, 503, "{\"ok\":false}");
                return;
            }
            log.accept(new PaymentEvent(now, "bonum", sha, true, r.transactionId(), r.invoiceId(), status, result.outcome().name()));
            reply(ex, 200, "{\"ok\":true,\"outcome\":\"" + result.outcome().name() + "\"}");
        } catch (RuntimeException e) {
            logger.warning("[payment] webhook error: " + e.getClass().getSimpleName());
        }
    }

    /** The body, or null when it is larger than {@link BonumWebhook#MAX_BODY}. */
    private static byte[] readBody(HttpExchange ex) throws IOException {
        String len = ex.getRequestHeaders().getFirst("Content-Length");
        if (len != null) {
            try {
                if (Long.parseLong(len.trim()) > BonumWebhook.MAX_BODY) return null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try (InputStream in = ex.getRequestBody()) {
            byte[] b = in.readNBytes(BonumWebhook.MAX_BODY + 1);
            return b.length > BonumWebhook.MAX_BODY ? null : b;
        }
    }

    private static void reply(HttpExchange ex, int code, String json) throws IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
