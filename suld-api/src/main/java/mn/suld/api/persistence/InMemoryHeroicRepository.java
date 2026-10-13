package mn.suld.api.persistence;

import mn.suld.api.dungeon.heroic.HeroicLadder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory heroic keys (tests, and storage-less setups): the same never-lower rule as the JDBC one. */
public final class InMemoryHeroicRepository implements HeroicRepository {

    private final Map<UUID, Map<String, Key>> keys = new ConcurrentHashMap<>();
    private final List<Run> runs = new ArrayList<>();

    @Override
    public synchronized CompletableFuture<Map<String, Key>> keys(UUID player) {
        return CompletableFuture.completedFuture(Map.copyOf(keys.getOrDefault(player, Map.of())));
    }

    @Override
    public synchronized CompletableFuture<Void> raise(UUID player, String dungeonId, int open, int best, int bestSeconds, long now) {
        Map<String, Key> m = keys.computeIfAbsent(player, k -> new HashMap<>());
        Key k = m.getOrDefault(dungeonId, Key.fresh(dungeonId));
        int seconds = best > k.best() ? bestSeconds
                : best == k.best() && best > 0 && (k.bestSeconds() == 0 || bestSeconds < k.bestSeconds()) ? bestSeconds : k.bestSeconds();
        m.put(dungeonId, new Key(dungeonId, Math.max(k.open(), HeroicLadder.clamp(open)), Math.max(k.best(), best), seconds));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Void> logRun(Run run) {
        runs.add(run);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<List<Run>> board(String dungeonId, int limit) {
        return CompletableFuture.completedFuture(runs.stream()
                .filter(r -> r.dungeonId().equals(dungeonId) && r.outcome() == HeroicLadder.Outcome.TIMED)
                .sorted(Comparator.comparingInt(Run::key).reversed().thenComparingInt(Run::clockSeconds))
                .limit(Math.max(1, limit)).toList());
    }
}
