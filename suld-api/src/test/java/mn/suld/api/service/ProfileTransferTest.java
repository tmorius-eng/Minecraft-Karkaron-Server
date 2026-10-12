package mn.suld.api.service;

import mn.suld.api.identity.PlayerIdentity;
import mn.suld.api.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERS-004: a coin transfer whose transaction fails never creates coins, even if the payee spent them meanwhile. */
class ProfileTransferTest {

    /** Storage whose two-row save fails once the test releases it. */
    private static final class FailingStore implements ProfileService {
        final CompletableFuture<Void> pending = new CompletableFuture<>();
        public CompletableFuture<ProfileLoad> acquire(PlayerIdentity identity) { throw new UnsupportedOperationException(); }
        public Optional<PlayerProfile> cached(UUID playerId) { return Optional.empty(); }
        public CompletableFuture<PlayerProfile> save(PlayerProfile profile) { return CompletableFuture.completedFuture(profile); }
        public CompletableFuture<Void> saveTogether(PlayerProfile first, PlayerProfile second) { return pending; }
        public CompletableFuture<Void> saveAndUnload(UUID playerId) { return CompletableFuture.completedFuture(null); }
    }

    private static PlayerProfile profile(long coins) {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "P", Instant.EPOCH);
        p.addCurrency(coins - p.currency());
        return p;
    }

    @Test
    void aFailedTransferRestoresBothSides() {
        FailingStore store = new FailingStore();
        PlayerProfile payer = profile(1000), payee = profile(50);
        CompletableFuture<Void> f = store.transfer(payer, payee, 400);
        assertEquals(600, payer.currency());
        assertEquals(450, payee.currency());
        store.pending.completeExceptionally(new RuntimeException("db down"));
        assertTrue(f.isCompletedExceptionally());
        assertEquals(1000, payer.currency());
        assertEquals(50, payee.currency());
    }

    @Test
    void aFailedTransferNeverMintsWhenThePayeeAlreadySpent() {
        FailingStore store = new FailingStore();
        PlayerProfile payer = profile(1000), payee = profile(0);
        store.transfer(payer, payee, 400);
        payee.addCurrency(-350); // spent most of it while the transaction was in flight
        long before = payer.currency() + payee.currency();
        store.pending.completeExceptionally(new RuntimeException("db down"));
        assertEquals(before, payer.currency() + payee.currency(), "the revert moves coins, it never creates them");
        assertEquals(0, payee.currency());
        assertEquals(650, payer.currency());
    }
}
