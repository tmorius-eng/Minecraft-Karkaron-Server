package mn.suld.api.payment;

/**
 * One provider call received ({@code suld_payment_events}, append-only, docs/PAYMENT_ARCHITECTURE.md §Data): never the
 * body, only its SHA-256, whether the signature held, what it named and what SÜLD did with it. For audits and replays.
 *
 * @param bodySha256 hex SHA-256 of the raw body as received
 * @param reference  the merchant reference the body names (an intent id), or null
 * @param status     the provider's status words, shortened
 * @param outcome    what was done: {@code CONFIRMED}, {@code DUPLICATE}, {@code BAD_SIGNATURE}, …
 */
public record PaymentEvent(long receivedAt, String provider, String bodySha256, boolean signatureOk, String reference,
                           String invoiceId, String status, String outcome) {

    public PaymentEvent {
        provider = cut(provider, 16);
        reference = cut(reference, 64);
        invoiceId = cut(invoiceId, 64);
        status = cut(status, 64);
        outcome = cut(outcome, 32);
    }

    private static String cut(String s, int n) {
        return s == null ? null : s.length() > n ? s.substring(0, n) : s;
    }
}
