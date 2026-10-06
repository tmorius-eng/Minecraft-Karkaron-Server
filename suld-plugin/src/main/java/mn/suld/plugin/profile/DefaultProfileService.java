package mn.suld.plugin.profile;

import mn.suld.api.identity.KeyedSequencer;
import mn.suld.api.identity.PlayerIdentity;
import mn.suld.api.persistence.ProfileRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.service.ProfileLoad;
import mn.suld.api.service.ProfileService;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache-backed {@link ProfileService} and the single writer per player.
 *
 * <p>Every storage operation for a UUID (acquire, save, save-and-unload) runs through a
 * {@link KeyedSequencer}, so they execute strictly in order: a quick reconnect can never read
 * the database before the previous session's save has landed (stale read → rollback), and two
 * concurrent logins can never both create a profile.
 */
public final class DefaultProfileService implements ProfileService {

    private final ProfileRepository repository;
    private final ConcurrentHashMap<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final KeyedSequencer<UUID> sequencer = new KeyedSequencer<>();

    public DefaultProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompletableFuture<ProfileLoad> acquire(PlayerIdentity identity) {
        UUID id = identity.uuid();
        return sequencer.submit(id, () -> {
            Instant now = Instant.now();
            PlayerProfile cached = cache.get(id);
            if (cached != null) {
                // Already online in another session (duplicate login): share the live instance.
                return CompletableFuture.completedFuture(adopt(cached, identity, now, false));
            }
            return repository.find(id).thenCompose(found -> {
                if (found.isPresent()) {
                    PlayerProfile profile = found.get();
                    PlayerProfile winner = cache.putIfAbsent(id, profile);
                    return CompletableFuture.completedFuture(
                            adopt(winner != null ? winner : profile, identity, now, false));
                }
                // Storage positively said "unknown UUID": first join. Persist before admitting.
                PlayerProfile fresh = PlayerProfile.createNew(id, identity.name(), now);
                return repository.save(fresh).thenApply(saved -> {
                    PlayerProfile winner = cache.putIfAbsent(id, fresh);
                    return new ProfileLoad(winner != null ? winner : fresh, winner == null, null);
                });
            });
        });
    }

    /** Apply the authenticated name (renames update display only) and mark the session seen. */
    private static ProfileLoad adopt(PlayerProfile profile, PlayerIdentity identity, Instant now, boolean created) {
        String previous = identity.isRenameOf(profile.name()) ? profile.name() : null;
        profile.name(identity.name());
        profile.touch(now);
        return new ProfileLoad(profile, created, previous);
    }

    @Override
    public Optional<PlayerProfile> cached(UUID playerId) {
        return Optional.ofNullable(cache.get(playerId));
    }

    /** Snapshot of all currently cached profiles (for auto-save/shutdown). */
    public Collection<PlayerProfile> cachedProfiles() {
        return cache.values();
    }

    @Override
    public CompletableFuture<PlayerProfile> save(PlayerProfile profile) {
        return sequencer.submit(profile.playerId(), () -> repository.save(profile));
    }

    @Override
    public CompletableFuture<Void> saveAndUnload(UUID playerId) {
        return sequencer.submit(playerId, () -> {
            PlayerProfile profile = cache.get(playerId);
            if (profile == null) {
                return CompletableFuture.completedFuture(null);
            }
            // Evict only after the save succeeded: a failed save keeps the live copy for retry.
            return repository.save(profile).thenApply(saved -> {
                cache.remove(playerId, profile);
                return null;
            });
        });
    }

    /** Operations currently queued or running (diagnostics). */
    public int inFlight() {
        return sequencer.inFlight();
    }
}
