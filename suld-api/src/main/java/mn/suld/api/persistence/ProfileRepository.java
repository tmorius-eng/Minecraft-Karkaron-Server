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

    /**
     * Persist two profiles in one storage transaction: both rows are written or neither (a coin transfer between
     * two players). On success both profiles are {@link PlayerProfile#markPersisted(long) marked persisted}; on
     * failure neither is, so both stay dirty for the next save.
     */
    CompletableFuture<Void> saveTogether(PlayerProfile first, PlayerProfile second);

    CompletableFuture<Void> delete(UUID playerId);

    /** The stored top {@code limit} rows of a leaderboard (players who have picked a class). */
    default CompletableFuture<java.util.List<mn.suld.api.leaderboard.Leaderboard.Entry>> top(
            mn.suld.api.leaderboard.Leaderboard board, int limit) {
        return CompletableFuture.completedFuture(java.util.List.of());
    }
}
