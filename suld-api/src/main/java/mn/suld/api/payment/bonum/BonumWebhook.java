package mn.suld.api.payment.bonum;

import mn.suld.api.json.Json;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Reads a Bonum webhook body ({@code {type, status, message, body:{transactionId, invoiceId, amount, currency,
 * completedAt, status, invoiceStatus}}}) into a {@link BonumPaymentResult}. Called only after the signature held
 * ({@link BonumSignature#verify}); a body that is not JSON, or names no transaction, is refused.
 */
public final class BonumWebhook {

    /** The most a webhook body may be: anything larger is refused before it is read. */
    public static final int MAX_BODY = 64 * 1024;

    private BonumWebhook() {
    }

    public static BonumPaymentResult parse(byte[] raw) {
        if (raw == null || raw.length == 0 || raw.length > MAX_BODY) throw new IllegalArgumentException("empty or oversized body");
        Map<String, Object> top = Json.object(Json.parse(new String(raw, StandardCharsets.UTF_8)));
        Map<String, Object> body = top.get("body") instanceof Map<?, ?> b ? Json.object(b) : top;
        String txn = BonumApi.text(body, "transactionId");
        if (txn == null || txn.isBlank()) throw new IllegalArgumentException("no transactionId");
        return new BonumPaymentResult(BonumApi.text(top, "type"), BonumApi.text(top, "status"), txn.trim(),
                BonumApi.text(body, "invoiceId"), BonumApi.number(body, "amount"), BonumApi.text(body, "currency"),
                body == top ? null : BonumApi.text(body, "status"), BonumApi.text(body, "invoiceStatus"), time(body.get("completedAt")));
    }

    /** Epoch ms of a completion time given as a number (ms, or s when small) or an ISO-8601 instant; null otherwise. */
    static Long time(Object v) {
        if (v instanceof Number n) {
            long t = n.longValue();
            return t < 100_000_000_000L ? t * 1000 : t;
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Instant.parse(s.trim()).toEpochMilli();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }
}
