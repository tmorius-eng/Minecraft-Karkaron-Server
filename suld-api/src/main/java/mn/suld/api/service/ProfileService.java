package mn.suld.api.service;

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

    /** Load the player's profile, creating a fresh one if none exists. */
    CompletableFuture<PlayerProfile> loadOrCreate(UUID playerId, String name);

    /** The cached profile for an online player, if present. */
    Optional<PlayerProfile> cached(UUID playerId);

    /** Persist the profile asynchronously. */
    CompletableFuture<PlayerProfile> save(PlayerProfile profile);

    /** Persist and remove the profile from the cache (used on quit). */
    CompletableFuture<Void> saveAndUnload(UUID playerId);
}
