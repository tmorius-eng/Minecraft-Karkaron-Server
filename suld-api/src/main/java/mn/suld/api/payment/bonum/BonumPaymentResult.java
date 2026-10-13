package mn.suld.api.payment.bonum;

import mn.suld.api.payment.PaymentStatus;

import java.util.Locale;
import java.util.Set;

/**
 * A Bonum webhook event, as far as SÜLD reads it ({@code {type, status, message, body:{transactionId, invoiceId, amount,
 * currency, completedAt, status, invoiceStatus}}}, docs/PAYMENT_ARCHITECTURE.md §Bonum). The fields are parsed by the
 * plugin from the signed body; this type only decides what they mean, conservatively: an event is PAID only when the
 * invoice says so (or, without an invoice status, the transaction does). The exact words are to be confirmed against
 * the Bonum sandbox before production (the gateway is off by default).
 *
 * @param transactionId SÜLD's merchant reference (the intent id)
 * @param amount        whole MNT
 */
public record BonumPaymentResult(String type, String status, String transactionId, String invoiceId, long amount,
                                 String currency, String bodyStatus, String invoiceStatus, Long completedAt) {

    private static final Set<String> PAID = Set.of("PAID", "SUCCESS", "SUCCEEDED", "COMPLETED", "SETTLED");
    private static final Set<String> FAILED = Set.of("FAILED", "FAILURE", "ERROR", "CANCELLED", "CANCELED", "DECLINED", "REJECTED");
    private static final Set<String> EXPIRED = Set.of("EXPIRED", "TIMEOUT");

    /** Only payment events move an intent (card-token and other events are not purchases). */
    public boolean payment() {
        return type == null || "PAYMENT".equalsIgnoreCase(type.trim());
    }

    /** What this event says about the payment. */
    public PaymentStatus paymentStatus() {
        if (!payment()) return PaymentStatus.PENDING;
        String invoice = norm(invoiceStatus), body = norm(bodyStatus), top = norm(status);
        if (FAILED.contains(top) || FAILED.contains(body) || FAILED.contains(invoice)) return PaymentStatus.FAILED;
        if (EXPIRED.contains(invoice) || EXPIRED.contains(body)) return PaymentStatus.EXPIRED;
        if (!invoice.isEmpty()) return PAID.contains(invoice) ? PaymentStatus.PAID : PaymentStatus.PENDING;
        return PAID.contains(body) ? PaymentStatus.PAID : PaymentStatus.PENDING;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
