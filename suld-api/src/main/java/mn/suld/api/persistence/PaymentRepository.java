package mn.suld.api.persistence;

import mn.suld.api.payment.PaymentEvent;
import mn.suld.api.payment.PaymentIntent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The payment ledger (V29, docs/PAYMENT_ARCHITECTURE.md §Data): intents and the provider calls received. An intent
 * moves only by compare-and-set on its {@code version}, so two copies of one webhook arriving at once move it once.
 */
public interface PaymentRepository {

    /** Stores a new intent (its version as given). */
    CompletableFuture<Void> create(PaymentIntent intent);

    CompletableFuture<Optional<PaymentIntent>> find(UUID intentId);

    /**
     * Writes {@code next} when the stored row's version is still {@code expectedVersion}: true when written, false when
     * another write came first (or a confirmed provider transaction id is already another intent's).
     */
    CompletableFuture<Boolean> replace(PaymentIntent next, int expectedVersion);

    /** Intents confirmed and not yet fulfilled (startup reconcile fulfils them). */
    CompletableFuture<List<PaymentIntent>> owed();

    /** PENDING intents whose time ran out before {@code now} (reconcile closes them as EXPIRED). */
    CompletableFuture<List<PaymentIntent>> expiredBy(long now);

    /** A player's PENDING intents still open at {@code now}. */
    CompletableFuture<List<PaymentIntent>> open(UUID player, long now);

    /** A player's latest intents, newest first (staff). */
    CompletableFuture<List<PaymentIntent>> recent(UUID player, int limit);

    /** Appends one provider call to the log. */
    CompletableFuture<Void> log(PaymentEvent event);
}
