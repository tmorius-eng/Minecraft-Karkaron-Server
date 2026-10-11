package mn.suld.api.persistence;

import mn.suld.api.profile.PlayerProfile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ProfileRepository} for tests and for a no-database developer
 * mode. It stores independent snapshots so {@link #find(UUID)} returns a fresh
 * instance, emulating the round-trip semantics of a real database (a loaded
 * profile is not the same object the caller previously saved).
 *
 * <p>It does <em>not</em> enforce optimistic-lock conflicts; that behaviour
 * belongs to the JDBC implementation and its {@code version} column. Data is
 * not durable and is lost when the process exits.
 */
public final class InMemoryProfileRepository implements ProfileRepository {

    private final ConcurrentHashMap<UUID, PlayerProfile> store = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<PlayerProfile>> find(UUID playerId) {
        PlayerProfile stored = store.get(playerId);
        return CompletableFuture.completedFuture(Optional.ofNullable(stored).map(InMemoryProfileRepository::snapshot));
    }

    @Override
    public CompletableFuture<java.util.List<mn.suld.api.leaderboard.Leaderboard.Entry>> top(
            mn.suld.api.leaderboard.Leaderboard board, int limit) {
        java.util.List<mn.suld.api.leaderboard.Leaderboard.Entry> rows = new java.util.ArrayList<>();
        for (PlayerProfile p : store.values()) {
            if (p.playerClass().isEmpty()) continue;
            rows.add(new mn.suld.api.leaderboard.Leaderboard.Entry(p.playerId(), p.name(), p.progression().level(),
                    p.progression().expIntoLevel(), p.currency()));
        }
        return CompletableFuture.completedFuture(board.rank(rows, limit));
    }

    @Override
    public CompletableFuture<Boolean> exists(UUID playerId) {
        return CompletableFuture.completedFuture(store.containsKey(playerId));
    }

    @Override
    public CompletableFuture<PlayerProfile> save(PlayerProfile profile) {
        store.put(profile.playerId(), snapshot(profile));
        profile.markPersisted(profile.version());
        return CompletableFuture.completedFuture(profile);
    }

    @Override
    public CompletableFuture<Void> delete(UUID playerId) {
        store.remove(playerId);
        return CompletableFuture.completedFuture(null);
    }

    /** Number of stored profiles (test helper). */
    public int size() {
        return store.size();
    }

    private static PlayerProfile snapshot(PlayerProfile source) {
        return PlayerProfile.restore(
                source.playerId(),
                source.name(),
                source.playerClass().orElse(null),
                source.progression(),
                source.createdAt(),
                source.lastSeenAt(),
                source.version(),
                source.currency(),
                source.questState(),
                source.skillState(),
                source.equipment(),
                source.classGear(),
                source.activeMinutes(),
                source.endgame());
    }
}
