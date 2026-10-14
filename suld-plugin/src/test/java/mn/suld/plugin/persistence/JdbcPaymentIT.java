package mn.suld.plugin.persistence;

import mn.suld.api.payment.PaymentEvent;
import mn.suld.api.payment.PaymentIntent;
import mn.suld.api.payment.PaymentLedger;
import mn.suld.api.payment.PaymentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V29 payment ledger: an intent moves by compare-and-set; identical webhooks at once grant once; the log appends. */
class JdbcPaymentIT {

    private JdbcPaymentRepository repo;
    private JdbcStyleRepository styles;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        repo = new JdbcPaymentRepository(db.ds(), db.dialect(), pool);
        styles = new JdbcStyleRepository(db.ds(), db.dialect(), pool);
    }

    @AfterEach
    void stop() {
        pool.shutdownNow();
    }

    private PaymentIntent intent(UUID player, long now) {
        PaymentIntent i = PaymentIntent.create(player, 250, 11_000, "bonum", now, 15 * 60_000L);
        repo.create(i).join();
        return i;
    }

    @Test
    void anIntentRoundTripsAndMovesOnlyFromTheVersionItWasRead() {
        UUID p = UUID.randomUUID();
        PaymentIntent i = intent(p, 1_000);
        PaymentIntent read = repo.find(i.intentId()).join().orElseThrow();
        assertEquals(i, read);
        assertNull(read.confirmedAt());

        PaymentIntent invoiced = i.withInvoice("INV-1");
        assertTrue(repo.replace(invoiced, i.version()).join());
        assertFalse(repo.replace(i.confirmed("x", 2_000), i.version()).join(), "a stale version is refused");
        PaymentIntent confirmed = invoiced.confirmed("INV-1", 2_000);
        assertTrue(repo.replace(confirmed, invoiced.version()).join());
        assertEquals(List.of(confirmed), repo.owed().join());
        PaymentIntent done = confirmed.fulfilled(3_000);
        assertTrue(repo.replace(done, confirmed.version()).join());
        assertTrue(repo.owed().join().isEmpty());
        assertEquals(done, repo.find(i.intentId()).join().orElseThrow());
    }

    @Test
    void aProviderTransactionBelongsToOneIntent() {
        UUID p = UUID.randomUUID();
        PaymentIntent a = intent(p, 1_000), b = intent(p, 1_001);
        assertTrue(repo.replace(a.confirmed("INV-SAME", 2_000), a.version()).join());
        assertFalse(repo.replace(b.confirmed("INV-SAME", 2_000), b.version()).join(), "the unique provider id refuses a second intent");
        assertEquals(PaymentIntent.State.PENDING, repo.find(b.intentId()).join().orElseThrow().state());
    }

    @Test
    void openExpiredAndRecentAreThePlayersOwn() {
        UUID p = UUID.randomUUID(), q = UUID.randomUUID();
        PaymentIntent a = intent(p, 1_000);
        intent(p, 2_000);
        intent(q, 3_000);
        assertEquals(2, repo.open(p, 5_000).join().size());
        assertEquals(1, repo.open(q, 5_000).join().size());
        assertTrue(repo.open(p, a.expiresAt() + 1_000).join().isEmpty());
        assertEquals(3, repo.expiredBy(a.expiresAt() + 5_000).join().size());
        List<PaymentIntent> recent = repo.recent(p, 1).join();
        assertEquals(1, recent.size());
        assertEquals(2_000, recent.get(0).createdAt(), "newest first");
    }

    @Test
    void identicalWebhooksAtOnceGrantTheCreditsOnce() throws Exception {
        UUID p = UUID.randomUUID();
        PaymentIntent i = intent(p, System.currentTimeMillis());
        PaymentLedger ledger = new PaymentLedger(repo, intent -> styles.grantCredits(intent.player(), intent.credits(),
                PaymentLedger.txid(intent), "bonum").thenApply(g -> g.applied()), System::currentTimeMillis);
        // the callers are the webhook's own threads (as in BonumWebhookServer): they wait while the repository works on
        // its pool; sharing one pool for both would starve it
        ExecutorService webhook = Executors.newFixedThreadPool(16);
        int granted = 0;
        try {
            List<CompletableFuture<PaymentLedger.Result>> all = new ArrayList<>();
            for (int k = 0; k < 16; k++) {
                all.add(CompletableFuture.supplyAsync(() -> ledger.onEvent(PaymentLedger.merchantReference(i), PaymentStatus.PAID, 11_000, "MNT",
                        "INV-77").join(), webhook));
            }
            for (CompletableFuture<PaymentLedger.Result> f : all) if (f.get(60, java.util.concurrent.TimeUnit.SECONDS).granted()) granted++;
        } finally {
            webhook.shutdownNow();
        }
        assertEquals(1, granted);
        assertEquals(250, styles.addCredits(p, 0).join(), "250 credits, once");
        PaymentIntent s = repo.find(i.intentId()).join().orElseThrow();
        assertEquals(PaymentIntent.State.CONFIRMED, s.state());
        assertNotNull(s.fulfilledAt());
        assertEquals("INV-77", s.providerTxnId());
    }

    @Test
    void theLogAppendsAndNeverHoldsTheBody() {
        repo.log(new PaymentEvent(1, "bonum", "a".repeat(64), false, null, null, null, "BAD_SIGNATURE")).join();
        repo.log(new PaymentEvent(2, "bonum", "b".repeat(64), true, "ref", "INV-1", "SUCCESS/PAID", "CONFIRMED")).join();
        List<PaymentEvent> ev = repo.events(10).join();
        assertEquals(2, ev.size());
        assertEquals("CONFIRMED", ev.get(0).outcome());
        assertTrue(ev.get(0).signatureOk());
        assertFalse(ev.get(1).signatureOk());
    }
}
