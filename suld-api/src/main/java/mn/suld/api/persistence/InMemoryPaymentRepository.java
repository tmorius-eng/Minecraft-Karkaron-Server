package mn.suld.api.persistence;

import mn.suld.api.payment.PaymentEvent;
import mn.suld.api.payment.PaymentIntent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** {@link PaymentRepository} in memory (tests, {@code database.type: memory}). */
public final class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<UUID, PaymentIntent> intents = new HashMap<>();
    private final List<PaymentEvent> events = new ArrayList<>();

    @Override
    public synchronized CompletableFuture<Void> create(PaymentIntent intent) {
        if (intents.putIfAbsent(intent.intentId(), intent) != null) {
            return CompletableFuture.failedFuture(new RepositoryException("intent " + intent.intentId() + " exists"));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Optional<PaymentIntent>> find(UUID intentId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(intents.get(intentId)));
    }

    @Override
    public synchronized CompletableFuture<Boolean> replace(PaymentIntent next, int expectedVersion) {
        PaymentIntent cur = intents.get(next.intentId());
        if (cur == null || cur.version() != expectedVersion) return CompletableFuture.completedFuture(false);
        if (next.providerTxnId() != null) {
            for (PaymentIntent o : intents.values()) {
                if (!o.intentId().equals(next.intentId()) && next.providerTxnId().equals(o.providerTxnId())) {
                    return CompletableFuture.completedFuture(false); // the unique provider transaction id
                }
            }
        }
        intents.put(next.intentId(), next);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<List<PaymentIntent>> owed() {
        return CompletableFuture.completedFuture(intents.values().stream().filter(PaymentIntent::owed).toList());
    }

    @Override
    public synchronized CompletableFuture<List<PaymentIntent>> expiredBy(long now) {
        return CompletableFuture.completedFuture(intents.values().stream().filter(i -> i.expired(now)).toList());
    }

    @Override
    public synchronized CompletableFuture<List<PaymentIntent>> open(UUID player, long now) {
        return CompletableFuture.completedFuture(intents.values().stream()
                .filter(i -> i.player().equals(player) && i.state() == PaymentIntent.State.PENDING && !i.expired(now)).toList());
    }

    @Override
    public synchronized CompletableFuture<List<PaymentIntent>> recent(UUID player, int limit) {
        return CompletableFuture.completedFuture(intents.values().stream().filter(i -> i.player().equals(player))
                .sorted(Comparator.comparingLong(PaymentIntent::createdAt).reversed()).limit(Math.max(0, limit)).toList());
    }

    @Override
    public synchronized CompletableFuture<Void> log(PaymentEvent event) {
        events.add(event);
        return CompletableFuture.completedFuture(null);
    }

    /** The log so far (tests). */
    public synchronized List<PaymentEvent> events() {
        return List.copyOf(events);
    }
}
