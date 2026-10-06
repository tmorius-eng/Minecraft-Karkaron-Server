package mn.suld.api.persistence;

import mn.suld.api.style.PlayerStyle;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Volatile style storage for MEMORY mode and tests. */
public final class InMemoryStyleRepository implements StyleRepository {

    private final Map<UUID, PlayerStyle.Snapshot> store = new ConcurrentHashMap<>();
    private final Map<UUID, Long> credits = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<PlayerStyle.Snapshot>> load(UUID player) {
        PlayerStyle.Snapshot s = store.get(player);
        long c = credits.getOrDefault(player, 0L);
        if (s == null && c == 0) return CompletableFuture.completedFuture(Optional.empty());
        if (s == null) s = new PlayerStyle.Snapshot(player, mn.suld.api.style.Rank.ARD, java.util.Set.of(), null, null, null, null, 0, c, 0);
        return CompletableFuture.completedFuture(Optional.of(new PlayerStyle.Snapshot(s.player(), s.rank(), s.owned(), s.tag(),
                s.nameColor(), s.chatColor(), s.joinMessage(), s.claimedLevels(), c, s.discovered())));
    }

    @Override
    public CompletableFuture<Void> save(PlayerStyle.Snapshot style) {
        store.put(style.player(), style);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Long> addCredits(UUID player, long delta) {
        long now = credits.getOrDefault(player, 0L);
        if (now + delta < 0) return CompletableFuture.completedFuture(-1L);
        credits.put(player, now + delta);
        return CompletableFuture.completedFuture(now + delta);
    }
}
