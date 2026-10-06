package mn.suld.plugin.profile;

import mn.suld.api.persistence.ProfileRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.service.ProfileService;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache-backed {@link ProfileService}. It is the single writer per player: a
 * profile is loaded (or created) once on join, served from the cache while the
 * player is online, and saved-and-evicted on quit. Persistence I/O is delegated
 * to the asynchronous {@link ProfileRepository}; cache operations are cheap and
 * thread-safe.
 */
public final class DefaultProfileService implements ProfileService {

    private final ProfileRepository repository;
    private final ConcurrentHashMap<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public DefaultProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompletableFuture<PlayerProfile> loadOrCreate(UUID playerId, String name) {
        PlayerProfile cached = cache.get(playerId);
        if (cached != null) {
            cached.name(name);
            cached.touch(Instant.now());
            return CompletableFuture.completedFuture(cached);
        }
        return repository.find(playerId).thenCompose(found -> {
            Instant now = Instant.now();
            if (found.isPresent()) {
                PlayerProfile profile = found.get();
                profile.name(name);
                profile.touch(now);
                PlayerProfile existing = cache.putIfAbsent(playerId, profile);
                return CompletableFuture.completedFuture(existing != null ? existing : profile);
            }
            PlayerProfile fresh = PlayerProfile.createNew(playerId, name, now);
            PlayerProfile existing = cache.putIfAbsent(playerId, fresh);
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }
            // Brand-new character: persist the initial row before returning.
            return repository.save(fresh).thenApply(saved -> fresh);
        });
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
        return repository.save(profile);
    }

    @Override
    public CompletableFuture<Void> saveAndUnload(UUID playerId) {
        PlayerProfile profile = cache.remove(playerId);
        if (profile == null) {
            return CompletableFuture.completedFuture(null);
        }
        return repository.save(profile).thenApply(saved -> null);
    }
}
