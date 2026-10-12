package mn.suld.api.service;

import mn.suld.api.identity.PlayerIdentity;

import mn.suld.api.profile.PlayerProfile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the lifecycle and in-memory cache of {@link PlayerProfile}s.
 *
 * <p>Implementations are the single writer per player: load-or-create on join,
 * serve a cached instance while the player is online, auto-save periodically,
 * and save-and-evict on quit. All storage I/O is asynchronous; the returned
 * futures complete off the main thread.
 */
public interface ProfileService {

    /**
     * Acquire the profile for an <b>authenticated</b> identity: the cached instance if the
     * player is already online (duplicate session), else load it from storage, else create it.
     * Keyed strictly by UUID. A storage failure fails the future — it never creates a blank
     * profile that could overwrite real progress.
     */
    CompletableFuture<ProfileLoad> acquire(PlayerIdentity identity);

    /** The cached profile for an online player, if present. */
    Optional<PlayerProfile> cached(UUID playerId);

    /** Persist the profile asynchronously. */
    CompletableFuture<PlayerProfile> save(PlayerProfile profile);

    /** Persist two profiles atomically (both rows or neither), sequenced on both players. */
    CompletableFuture<Void> saveTogether(PlayerProfile first, PlayerProfile second);

    /**
     * Move {@code amount} coins from one player to another: the in-memory balances change at once (the caller has
     * checked {@code from} can pay, on the main thread), then both rows are written in one transaction. If storage
     * fails, the in-memory move is undone and the future fails, so neither side keeps coins the other lost.
     */
    default CompletableFuture<Void> transfer(PlayerProfile from, PlayerProfile to, long amount) {
        if (amount <= 0) return CompletableFuture.completedFuture(null);
        from.addCurrency(-amount);
        to.addCurrency(amount);
        return saveTogether(from, to).whenComplete((v, err) -> {
            if (err != null) {
                // PERS-004: take back only what the payee still has (balances clamp at 0); never mint the difference
                long back = Math.min(amount, Math.max(0, to.currency()));
                to.addCurrency(-back);
                from.addCurrency(back);
            }
        });
    }

    /**
     * Persist and remove the profile from the cache. Only call when the <i>last</i> live
     * session for the UUID has ended (see {@code SessionRegistry}).
     */
    CompletableFuture<Void> saveAndUnload(UUID playerId);
}
