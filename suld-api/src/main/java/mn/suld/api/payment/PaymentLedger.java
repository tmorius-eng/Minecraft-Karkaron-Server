package mn.suld.api.payment;

import mn.suld.api.persistence.PaymentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * What a provider's signed event does to the ledger (docs/PAYMENT_ARCHITECTURE.md §Security model), as one rule over
 * {@link PaymentRepository} and the credit grant, so the plugin's webhook and the startup reconcile share it:
 *
 * <ol>
 *   <li>the intent named by the event is read and judged ({@link PaymentIntent#judge});</li>
 *   <li>a payment moves it to CONFIRMED by compare-and-set: of two identical webhooks at once, one wins and the other
 *   reads it again and finds a duplicate;</li>
 *   <li>the credits are granted under the intent's own transaction id ({@code suld_credit_grants}), so a grant is made
 *   once however often it is tried;</li>
 *   <li>the intent is marked fulfilled. A crash between 2 and 4 leaves it {@link PaymentIntent#owed owed}; reconcile
 *   grants again (a no-op if it was granted) and marks it.</li>
 * </ol>
 *
 * Nothing here trusts a player: only a provider event (its signature checked by the caller) moves an intent.
 */
public final class PaymentLedger {

    /** Grants an intent's credits under {@link #txid}: true when this call granted them, false when they already were. */
    @FunctionalInterface
    public interface Grants extends Function<PaymentIntent, CompletableFuture<Boolean>> {
    }

    public enum Outcome {
        /** Paid now: credits granted. */
        CONFIRMED,
        /** Paid before (a replayed or repeated event): nothing changed. */
        DUPLICATE,
        /** The paid amount or currency is not the intent's: nothing granted, staff alerted. */
        MISMATCH,
        /** The provider says the payment failed. */
        FAILED,
        /** The invoice ran out (provider event, or reconcile). */
        EXPIRED,
        /** No intent of ours has this reference. */
        UNKNOWN,
        /** An event that moves nothing (a pending status, a closed intent). */
        IGNORED,
        /** Confirmed, but the grant did not go through: reconcile finishes it. */
        OWED
    }

    /** What an event or a reconcile step did; {@code intent} is the stored state after it (null for UNKNOWN). */
    public record Result(Outcome outcome, PaymentIntent intent, boolean granted) {
    }

    /** CAS attempts before giving up on a row that keeps changing under us. */
    private static final int TRIES = 4;

    private final PaymentRepository repo;
    private final Grants grants;
    private final LongSupplier clock;

    public PaymentLedger(PaymentRepository repo, Grants grants, LongSupplier clock) {
        this.repo = repo;
        this.grants = grants;
        this.clock = clock;
    }

    /** The credit grant's transaction id for an intent: one per intent, forever. */
    public static String txid(PaymentIntent intent) {
        return intent.provider() + ":" + intent.intentId();
    }

    /** The intent id a merchant reference names: a UUID with or without its dashes; empty for anything else. */
    public static Optional<UUID> reference(String ref) {
        if (ref == null) return Optional.empty();
        String s = ref.trim();
        if (s.length() == 32 && s.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20);
        }
        try {
            UUID id = UUID.fromString(s);
            return id.toString().equalsIgnoreCase(s) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The reference sent to the provider for an intent: its id without dashes (32 hex digits). */
    public static String merchantReference(PaymentIntent intent) {
        return intent.intentId().toString().replace("-", "");
    }

    /**
     * A provider event: {@code reference} (our intent), its {@code status}, the {@code amount} and {@code currency}
     * paid, and the provider's own id for the payment ({@code providerTxn}, unique once confirmed).
     */
    public CompletableFuture<Result> onEvent(String reference, PaymentStatus status, long amount, String currency, String providerTxn) {
        Optional<UUID> id = reference(reference);
        if (id.isEmpty()) return CompletableFuture.completedFuture(new Result(Outcome.UNKNOWN, null, false));
        return step(id.get(), status, amount, currency, providerTxn, 0);
    }

    private CompletableFuture<Result> step(UUID id, PaymentStatus status, long amount, String currency, String txn, int attempt) {
        return repo.find(id).thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.completedFuture(new Result(Outcome.UNKNOWN, null, false));
            PaymentIntent cur = found.get();
            long now = clock.getAsLong();
            return switch (cur.judge(status, amount, currency)) {
                case DUPLICATE -> cur.owed() ? fulfil(cur) : CompletableFuture.completedFuture(new Result(Outcome.DUPLICATE, cur, false));
                case MISMATCH -> CompletableFuture.completedFuture(new Result(Outcome.MISMATCH, cur, false));
                case IGNORE -> CompletableFuture.completedFuture(new Result(Outcome.IGNORED, cur, false));
                case CONFIRM -> {
                    PaymentIntent next = cur.confirmed(txn == null || txn.isBlank() ? merchantReference(cur) : txn, now);
                    yield repo.replace(next, cur.version()).thenCompose(ok -> ok ? fulfil(next)
                            : retry(id, status, amount, currency, txn, attempt));
                }
                case FAIL, EXPIRE -> {
                    PaymentIntent next = cur.closed(status == PaymentStatus.FAILED ? PaymentIntent.State.FAILED : PaymentIntent.State.EXPIRED);
                    yield repo.replace(next, cur.version()).thenCompose(ok -> ok
                            ? CompletableFuture.completedFuture(new Result(status == PaymentStatus.FAILED ? Outcome.FAILED : Outcome.EXPIRED, next, false))
                            : retry(id, status, amount, currency, txn, attempt));
                }
            };
        });
    }

    private CompletableFuture<Result> retry(UUID id, PaymentStatus status, long amount, String currency, String txn, int attempt) {
        if (attempt + 1 >= TRIES) {
            return CompletableFuture.failedFuture(new IllegalStateException("intent " + id + " kept changing (or its provider id is another intent's)"));
        }
        return step(id, status, amount, currency, txn, attempt + 1);
    }

    /** Grants a confirmed intent's credits (once, by txid) and marks it fulfilled. */
    private CompletableFuture<Result> fulfil(PaymentIntent confirmed) {
        return grants.apply(confirmed).handle((granted, err) -> err == null ? granted : null).thenCompose(granted -> {
            if (granted == null) return CompletableFuture.completedFuture(new Result(Outcome.OWED, confirmed, false));
            PaymentIntent done = confirmed.fulfilled(clock.getAsLong());
            return repo.replace(done, confirmed.version()).thenApply(ok -> new Result(Outcome.CONFIRMED, ok ? done : confirmed, granted));
        });
    }

    /**
     * Startup (and periodic) repair: every owed intent is granted and marked; every PENDING intent past its time is
     * closed as EXPIRED (a payment that still arrives for it is confirmed: the money was taken).
     */
    public CompletableFuture<List<Result>> reconcile() {
        long now = clock.getAsLong();
        return repo.owed().thenCompose(owed -> {
            List<CompletableFuture<Result>> steps = new ArrayList<>();
            for (PaymentIntent i : owed) steps.add(fulfil(i));
            return repo.expiredBy(now).thenCompose(stale -> {
                for (PaymentIntent i : stale) {
                    PaymentIntent next = i.closed(PaymentIntent.State.EXPIRED);
                    steps.add(repo.replace(next, i.version()).thenApply(ok -> new Result(ok ? Outcome.EXPIRED : Outcome.IGNORED, ok ? next : i, false)));
                }
                return CompletableFuture.allOf(steps.toArray(CompletableFuture[]::new))
                        .thenApply(v -> steps.stream().map(CompletableFuture::join).toList());
            });
        });
    }
}
