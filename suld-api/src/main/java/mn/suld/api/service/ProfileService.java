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

    /**
     * Persist and remove the profile from the cache. Only call when the <i>last</i> live
     * session for the UUID has ended (see {@code SessionRegistry}).
     */
    CompletableFuture<Void> saveAndUnload(UUID playerId);
}
