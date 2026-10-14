package mn.suld.plugin.payment.bonum;

import mn.suld.api.payment.bonum.BonumApi;
import mn.suld.api.payment.bonum.BonumInvoice;
import mn.suld.api.payment.bonum.BonumInvoiceReply;
import mn.suld.api.payment.bonum.BonumQr;
import mn.suld.api.payment.bonum.BonumToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Bonum's merchant API over {@code java.net.http} (docs/PAYMENT_ARCHITECTURE.md §Bonum, {@link BonumApi}): asynchronous
 * only, never on the main thread. The access token is fetched once and shared ({@link #token}), renewed before it
 * lapses, refreshed with the refresh token while that holds. The app secret, the terminal id and the tokens are never
 * logged; an error names the HTTP status, not the request.
 */
public final class BonumClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final URI base;
    private final String terminalId;
    private final String appSecret;
    private final String language;
    private volatile BonumToken token;
    private CompletableFuture<BonumToken> inflight;

    /**
     * @param base       {@link BonumApi#SANDBOX}, {@link BonumApi#PRODUCTION}, or a loopback test stub
     * @param terminalId the merchant's terminal ({@code X-TERMINAL-ID})
     * @param appSecret  the merchant's app secret ({@code Authorization: AppSecret …})
     */
    public BonumClient(URI base, String terminalId, String appSecret, String language, Executor executor) {
        this.base = base;
        this.terminalId = terminalId;
        this.appSecret = appSecret;
        this.language = language == null || language.isBlank() ? "mn" : language;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).executor(executor).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** True for an https base, or plain http to this machine only (a test stub): never secrets over the open network. */
    public static boolean safeBase(URI base) {
        if (base == null || base.getHost() == null) return false;
        if ("https".equalsIgnoreCase(base.getScheme())) return true;
        String h = base.getHost();
        return "http".equalsIgnoreCase(base.getScheme()) && (h.equals("127.0.0.1") || h.equals("localhost") || h.equals("[::1]") || h.equals("::1"));
    }

    /** A QR invoice for one intent. */
    public CompletableFuture<BonumQr> createQr(BonumInvoice invoice) {
        return post(BonumApi.QR, invoice.qrJson(), BonumQr::parse);
    }

    /** A hosted invoice (a payment page) for one intent. */
    public CompletableFuture<BonumInvoiceReply> createInvoice(BonumInvoice invoice) {
        return post(BonumApi.INVOICE, invoice.invoiceJson(), BonumInvoiceReply::parse);
    }

    /** The access token: the cached one while usable, else one request shared by every caller. */
    CompletableFuture<BonumToken> token() {
        long now = System.currentTimeMillis();
        BonumToken t = token;
        if (t != null && t.usable(now)) return CompletableFuture.completedFuture(t);
        synchronized (this) {
            if (inflight != null && !inflight.isDone()) return inflight;
            CompletableFuture<BonumToken> next = t != null && t.refreshable(now)
                    ? refresh(t).exceptionallyCompose(e -> create())
                    : create();
            inflight = next.thenApply(nt -> {
                token = nt;
                return nt;
            });
            return inflight;
        }
    }

    private CompletableFuture<BonumToken> create() {
        HttpRequest req = HttpRequest.newBuilder(base.resolve(BonumApi.TOKEN)).timeout(TIMEOUT).GET()
                .header("Authorization", "AppSecret " + appSecret)
                .header("X-TERMINAL-ID", terminalId)
                .header("Accept", "application/json")
                .build();
        return send(req, "token").thenApply(body -> BonumToken.parse(body, System.currentTimeMillis()));
    }

    private CompletableFuture<BonumToken> refresh(BonumToken t) {
        HttpRequest req = HttpRequest.newBuilder(base.resolve(BonumApi.REFRESH)).timeout(TIMEOUT).GET()
                .header("Authorization", "Bearer " + t.refreshToken())
                .header("Accept", "application/json")
                .build();
        return send(req, "token refresh").thenApply(body -> BonumToken.parse(body, System.currentTimeMillis()));
    }

    private <T> CompletableFuture<T> post(String path, String json, Function<String, T> parse) {
        return token().thenCompose(t -> {
            HttpRequest req = HttpRequest.newBuilder(base.resolve(path)).timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .header("Authorization", "Bearer " + t.accessToken())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Accept-Language", language)
                    .build();
            return send(req, path);
        }).thenApply(parse);
    }

    private CompletableFuture<String> send(HttpRequest req, String what) {
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(res -> {
            if (res.statusCode() == 401) token = null; // the token was refused: the next call fetches a new one
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("Bonum " + what + ": HTTP " + res.statusCode() + " " + brief(res.body()));
            }
            return res.body();
        });
    }

    /** At most 160 printable characters of a reply, for a log line (never a header). */
    static String brief(String body) {
        if (body == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < body.length() && sb.length() < 160; k++) {
            char c = body.charAt(k);
            sb.append(c < 0x20 ? ' ' : c);
        }
        return sb.toString();
    }
}
