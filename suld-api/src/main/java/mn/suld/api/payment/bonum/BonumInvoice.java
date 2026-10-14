package mn.suld.api.payment.bonum;

import mn.suld.api.json.Json;
import mn.suld.api.payment.PaymentIntent;
import mn.suld.api.payment.PaymentLedger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The invoice SÜLD asks Bonum for, for one intent: the merchant reference ({@code transactionId}, the intent id), the
 * amount in whole tögrög, how long it stays open and, for the hosted page, the callback URL. Nothing about the player
 * but the intent id leaves the server.
 */
public record BonumInvoice(String transactionId, long amount, int expiresInSeconds, String callback) {

    public BonumInvoice {
        if (transactionId == null || transactionId.isBlank()) throw new IllegalArgumentException("no transactionId");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        expiresInSeconds = Math.max(60, expiresInSeconds);
    }

    /** The invoice for {@code intent} at {@code now}: open for the intent's remaining time (at least a minute). */
    public static BonumInvoice of(PaymentIntent intent, String callback, long now) {
        int left = (int) Math.min(Integer.MAX_VALUE, Math.max(60, (intent.expiresAt() - now) / 1000));
        return new BonumInvoice(PaymentLedger.merchantReference(intent), intent.amountMinor(), left, callback);
    }

    /** The body of a QR request. */
    public String qrJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", amount);
        m.put("transactionId", transactionId);
        m.put("expiresIn", expiresInSeconds);
        return Json.write(m);
    }

    /** The body of a hosted-invoice request. */
    public String invoiceJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", amount);
        if (callback != null && !callback.isBlank()) m.put("callback", callback);
        m.put("transactionId", transactionId);
        m.put("expiresIn", expiresInSeconds);
        return Json.write(m);
    }
}
