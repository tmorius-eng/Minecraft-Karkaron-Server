package mn.suld.api.payment;

import java.util.Objects;
import java.util.UUID;

/**
 * One purchase a player asked for (docs/PAYMENT_ARCHITECTURE.md §Intent): it exists before any money moves, its id is
 * the merchant reference sent to the provider, and only the provider's signed event moves it on. Pure value type with
 * the state rules; storage is the ledger's ({@code suld_payment_intent}).
 *
 * @param intentId      merchant reference (Bonum {@code transactionId}), also the credit grant's txid
 * @param product       e.g. {@code CREDITS_250}
 * @param credits       what fulfilment grants
 * @param amountMinor   the price in MNT (no minor unit in use: ₮5000 is 5000)
 * @param providerTxnId the provider's id for the paid invoice, unique once confirmed
 * @param version       optimistic lock of the stored row
 */
public record PaymentIntent(UUID intentId, UUID player, String product, long credits, long amountMinor, String currency,
                            String provider, State state, long createdAt, long expiresAt, String invoiceId,
                            String providerTxnId, Long confirmedAt, Long fulfilledAt, int version) {

    /** PENDING → CONFIRMED / FAILED / EXPIRED; CONFIRMED → REFUNDED (staff are alerted, nothing is reversed). */
    public enum State { PENDING, CONFIRMED, FAILED, EXPIRED, REFUNDED }

    public static final String CURRENCY = "MNT";

    public PaymentIntent {
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(state, "state");
        if (credits <= 0 || amountMinor <= 0) throw new IllegalArgumentException("credits and amount must be > 0");
        currency = currency == null ? CURRENCY : currency;
        product = product == null ? "CREDITS_" + credits : product;
    }

    /** A new intent for {@code credits} at {@code amountMnt}, open for {@code ttlMs}. */
    public static PaymentIntent create(UUID player, long credits, long amountMnt, String provider, long now, long ttlMs) {
        return new PaymentIntent(UUID.randomUUID(), player, "CREDITS_" + credits, credits, amountMnt, CURRENCY, provider,
                State.PENDING, now, now + Math.max(60_000L, ttlMs), null, null, null, null, 0);
    }

    public boolean expired(long now) {
        return state == State.PENDING && now >= expiresAt;
    }

    /** This intent with the provider's invoice id (still PENDING). */
    public PaymentIntent withInvoice(String invoice) {
        return new PaymentIntent(intentId, player, product, credits, amountMinor, currency, provider, state, createdAt, expiresAt,
                invoice, providerTxnId, confirmedAt, fulfilledAt, version + 1);
    }

    /** What a provider event does to an intent. */
    public enum Verdict {
        /** PENDING (or a late EXPIRED) → CONFIRMED: fulfil it. */
        CONFIRM,
        /** Already CONFIRMED: a replay, nothing to do (CONFIRMED → CONFIRMED is a no-op). */
        DUPLICATE,
        /** The paid amount or currency is not this intent's: refused, staff alerted. */
        MISMATCH,
        /** PENDING → FAILED (the provider says the payment failed). */
        FAIL,
        /** PENDING → EXPIRED (the provider says the invoice expired). */
        EXPIRE,
        /** Nothing to change (an event for a closed intent, or a status that moves nothing). */
        IGNORE
    }

    /**
     * The rule for a provider event with {@code status} paying {@code amount} in {@code payCurrency}. A payment that
     * arrives after the intent expired is still confirmed: the money was taken, so the credits are owed.
     */
    public Verdict judge(PaymentStatus status, long amount, String payCurrency) {
        if (status == PaymentStatus.PAID) {
            if (state == State.CONFIRMED || state == State.REFUNDED) return Verdict.DUPLICATE;
            if (amount != amountMinor || !currency.equalsIgnoreCase(payCurrency == null ? CURRENCY : payCurrency)) return Verdict.MISMATCH;
            return state == State.PENDING || state == State.EXPIRED || state == State.FAILED ? Verdict.CONFIRM : Verdict.IGNORE;
        }
        if (state != State.PENDING) return Verdict.IGNORE;
        return switch (status) {
            case FAILED -> Verdict.FAIL;
            case EXPIRED -> Verdict.EXPIRE;
            default -> Verdict.IGNORE;
        };
    }

    /** This intent CONFIRMED by provider transaction {@code txn} at {@code now}. */
    public PaymentIntent confirmed(String txn, long now) {
        return new PaymentIntent(intentId, player, product, credits, amountMinor, currency, provider, State.CONFIRMED, createdAt,
                expiresAt, invoiceId, txn, now, fulfilledAt, version + 1);
    }

    /** This intent moved to a closed state without fulfilment (FAILED or EXPIRED). */
    public PaymentIntent closed(State next) {
        if (next != State.FAILED && next != State.EXPIRED) throw new IllegalArgumentException("closing to " + next);
        return new PaymentIntent(intentId, player, product, credits, amountMinor, currency, provider, next, createdAt, expiresAt,
                invoiceId, providerTxnId, confirmedAt, fulfilledAt, version + 1);
    }

    /** This (CONFIRMED) intent fulfilled at {@code now}. */
    public PaymentIntent fulfilled(long now) {
        if (state != State.CONFIRMED) throw new IllegalStateException("fulfilling a " + state + " intent");
        return new PaymentIntent(intentId, player, product, credits, amountMinor, currency, provider, state, createdAt, expiresAt,
                invoiceId, providerTxnId, confirmedAt, now, version + 1);
    }

    /** Confirmed and not yet fulfilled (startup reconcile fulfils these). */
    public boolean owed() {
        return state == State.CONFIRMED && fulfilledAt == null;
    }
}
