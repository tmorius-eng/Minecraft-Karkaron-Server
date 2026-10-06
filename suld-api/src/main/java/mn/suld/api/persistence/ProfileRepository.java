package mn.suld.api.persistence;

import mn.suld.api.profile.PlayerProfile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous storage contract for {@link PlayerProfile}s.
 *
 * <p>Every method returns a {@link CompletableFuture}: implementations must
 * perform I/O off the server's main thread. Callers are expected to hop back
 * onto the main thread before touching Bukkit state. Failures complete the
 * future exceptionally with a {@link RepositoryException}.
 *
 * <p>{@link #save(PlayerProfile)} uses the profile's
 * {@link PlayerProfile#version()} for optimistic concurrency: a write whose
 * expected version no longer matches storage must fail rather than clobber a
 * newer record. This is a cornerstone of the anti-duplication strategy.
 */
public interface ProfileRepository {

    CompletableFuture<Optional<PlayerProfile>> find(UUID playerId);

    CompletableFuture<Boolean> exists(UUID playerId);

    /**
     * Persist the profile. On success the returned future yields a profile whose
     * version reflects the committed row (implementations call
     * {@link PlayerProfile#markPersisted(long)}).
     */
    CompletableFuture<PlayerProfile> save(PlayerProfile profile);

    CompletableFuture<Void> delete(UUID playerId);
}
