package mn.suld.api.payment;

import mn.suld.api.persistence.InMemoryPaymentRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentLedgerTest {

    private final InMemoryPaymentRepository repo = new InMemoryPaymentRepository();
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final Set<String> granted = ConcurrentHashMap.newKeySet();
    private final AtomicInteger failNext = new AtomicInteger();
    private final PaymentLedger ledger = new PaymentLedger(repo, intent -> {
        if (failNext.getAndUpdate(n -> Math.max(0, n - 1)) > 0) return CompletableFuture.failedFuture(new IllegalStateException("db down"));
        return CompletableFuture.completedFuture(granted.add(PaymentLedger.txid(intent))); // the txid ledger: once
    }, clock::get);

    private PaymentIntent intent(long credits, long amount) {
        PaymentIntent i = PaymentIntent.create(UUID.randomUUID(), credits, amount, "bonum", clock.get(), 15 * 60_000L);
        repo.create(i).join();
        return i;
    }

    private PaymentLedger.Result pay(PaymentIntent i, long amount) {
        return ledger.onEvent(PaymentLedger.merchantReference(i), PaymentStatus.PAID, amount, "MNT", "inv-" + i.intentId()).join();
    }

    private PaymentIntent stored(PaymentIntent i) {
        return repo.find(i.intentId()).join().orElseThrow();
    }

    @Test
    void aPaymentGrantsTheCreditsOnceAndClosesTheIntent() {
        PaymentIntent i = intent(250, 11_000);
        PaymentLedger.Result r = pay(i, 11_000);
        assertEquals(PaymentLedger.Outcome.CONFIRMED, r.outcome());
        assertTrue(r.granted());
        PaymentIntent s = stored(i);
        assertEquals(PaymentIntent.State.CONFIRMED, s.state());
        assertNotNull(s.fulfilledAt());
        assertFalse(s.owed());
        assertEquals("inv-" + i.intentId(), s.providerTxnId());

        PaymentLedger.Result again = pay(i, 11_000); // the provider retries, or someone replays the signed body
        assertEquals(PaymentLedger.Outcome.DUPLICATE, again.outcome());
        assertFalse(again.granted());
        assertEquals(1, granted.size());
    }

    @Test
    void identicalWebhooksAtOnceGrantOnce() throws Exception {
        PaymentIntent i = intent(600, 25_000);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<PaymentLedger.Result>> all = new ArrayList<>();
            for (int k = 0; k < 24; k++) all.add(CompletableFuture.supplyAsync(() -> pay(i, 25_000), pool));
            int grants = 0;
            for (CompletableFuture<PaymentLedger.Result> f : all) {
                PaymentLedger.Result r = f.get();
                assertTrue(r.outcome() == PaymentLedger.Outcome.CONFIRMED || r.outcome() == PaymentLedger.Outcome.DUPLICATE, r.outcome().name());
                if (r.granted()) grants++;
            }
            assertEquals(1, grants);
            assertEquals(1, granted.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aWrongAmountOrCurrencyGrantsNothing() {
        PaymentIntent i = intent(100, 5_000);
        assertEquals(PaymentLedger.Outcome.MISMATCH, pay(i, 500).outcome());
        assertEquals(PaymentLedger.Outcome.MISMATCH, ledger.onEvent(PaymentLedger.merchantReference(i), PaymentStatus.PAID, 5_000, "USD", "x").join().outcome());
        assertEquals(PaymentIntent.State.PENDING, stored(i).state());
        assertTrue(granted.isEmpty());
        assertEquals(PaymentLedger.Outcome.CONFIRMED, pay(i, 5_000).outcome()); // the right payment still counts
    }

    @Test
    void anUnknownOrMalformedReferenceMovesNothing() {
        assertEquals(PaymentLedger.Outcome.UNKNOWN, ledger.onEvent(UUID.randomUUID().toString(), PaymentStatus.PAID, 1, "MNT", "x").join().outcome());
        assertEquals(PaymentLedger.Outcome.UNKNOWN, ledger.onEvent("'; DROP TABLE x; --", PaymentStatus.PAID, 1, "MNT", "x").join().outcome());
        assertEquals(PaymentLedger.Outcome.UNKNOWN, ledger.onEvent(null, PaymentStatus.PAID, 1, "MNT", "x").join().outcome());
        assertTrue(granted.isEmpty());
    }

    @Test
    void bothFormsOfTheReferenceNameTheIntent() {
        UUID id = UUID.randomUUID();
        assertEquals(id, PaymentLedger.reference(id.toString()).orElseThrow());
        assertEquals(id, PaymentLedger.reference(id.toString().replace("-", "")).orElseThrow());
        assertEquals(id, PaymentLedger.reference(id.toString().toUpperCase()).orElseThrow());
        assertTrue(PaymentLedger.reference("1-1-1-1-1").isEmpty()); // UUID.fromString would accept it
        assertTrue(PaymentLedger.reference("zz" + id.toString().replace("-", "").substring(2)).isEmpty());
    }

    @Test
    void aFailureClosesTheIntentButALatePaymentIsStillHonoured() {
        PaymentIntent i = intent(100, 5_000);
        assertEquals(PaymentLedger.Outcome.FAILED,
                ledger.onEvent(PaymentLedger.merchantReference(i), PaymentStatus.FAILED, 0, null, null).join().outcome());
        assertEquals(PaymentIntent.State.FAILED, stored(i).state());
        assertEquals(PaymentLedger.Outcome.IGNORED, // a second failure, or a pending status, changes nothing
                ledger.onEvent(PaymentLedger.merchantReference(i), PaymentStatus.PENDING, 0, null, null).join().outcome());
        assertEquals(PaymentLedger.Outcome.CONFIRMED, pay(i, 5_000).outcome()); // the money was taken after all
        assertEquals(1, granted.size());
    }

    @Test
    void aGrantThatFailsIsFinishedByReconcile() {
        PaymentIntent i = intent(1300, 50_000);
        failNext.set(1);
        PaymentLedger.Result r = pay(i, 50_000);
        assertEquals(PaymentLedger.Outcome.OWED, r.outcome());
        assertTrue(stored(i).owed());
        assertTrue(granted.isEmpty());

        List<PaymentLedger.Result> fixed = ledger.reconcile().join();
        assertEquals(1, fixed.size());
        assertEquals(PaymentLedger.Outcome.CONFIRMED, fixed.get(0).outcome());
        assertTrue(fixed.get(0).granted());
        assertFalse(stored(i).owed());
        assertTrue(ledger.reconcile().join().isEmpty()); // nothing left to do
        assertEquals(1, granted.size());
    }

    @Test
    void aRestartAfterTheGrantButBeforeTheMarkGrantsNothingTwice() {
        PaymentIntent i = intent(2800, 100_000);
        PaymentIntent confirmed = i.confirmed("inv-1", clock.get());
        assertTrue(repo.replace(confirmed, i.version()).join());
        granted.add(PaymentLedger.txid(confirmed)); // the grant went through, then the server stopped
        List<PaymentLedger.Result> r = ledger.reconcile().join();
        assertEquals(PaymentLedger.Outcome.CONFIRMED, r.get(0).outcome());
        assertFalse(r.get(0).granted());
        assertFalse(stored(i).owed());
        assertEquals(1, granted.size());
    }

    @Test
    void reconcileExpiresOldIntentsAndAPaymentForOneStillCounts() {
        PaymentIntent i = intent(100, 5_000);
        clock.addAndGet(16 * 60_000L);
        List<PaymentLedger.Result> r = ledger.reconcile().join();
        assertEquals(PaymentLedger.Outcome.EXPIRED, r.get(0).outcome());
        assertEquals(PaymentIntent.State.EXPIRED, stored(i).state());
        assertEquals(PaymentLedger.Outcome.CONFIRMED, pay(i, 5_000).outcome());
    }

    @Test
    void anOpenIntentIsListedForItsPlayerUntilItsTimeRunsOut() {
        PaymentIntent i = intent(100, 5_000);
        assertEquals(1, repo.open(i.player(), clock.get()).join().size());
        assertTrue(repo.open(UUID.randomUUID(), clock.get()).join().isEmpty());
        assertTrue(repo.open(i.player(), i.expiresAt()).join().isEmpty());
    }
}
