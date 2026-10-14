package mn.suld.api.persistence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** {@link AchievementRepository} in memory (tests, {@code database.type: memory}). */
public final class InMemoryAchievementRepository implements AchievementRepository {

    private final Map<UUID, Map<String, Long>> deeds = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> unlocked = new ConcurrentHashMap<>();
    private final Map<String, Long> points = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> claims = new ConcurrentHashMap<>();
    private final Set<String> heroic = ConcurrentHashMap.newKeySet();

    private static String k(UUID p, String season) {
        return p + "/" + season;
    }

    @Override
    public synchronized CompletableFuture<Stored> load(UUID player) {
        return CompletableFuture.completedFuture(new Stored(new HashMap<>(deeds.getOrDefault(player, Map.of())),
                new HashMap<>(unlocked.getOrDefault(player, Map.of()))));
    }

    @Override
    public synchronized CompletableFuture<Void> write(UUID player, Map<String, Long> counts, Map<String, Long> bests, Map<String, Long> unlocks, long now) {
        Map<String, Long> d = deeds.computeIfAbsent(player, x -> new HashMap<>());
        counts.forEach((key, v) -> {
            if (v != null && v > 0) d.merge(key, v, Long::sum);
        });
        bests.forEach((key, v) -> {
            if (v != null && v > 0) d.merge(key, v, Math::max);
        });
        Map<String, Long> u = unlocked.computeIfAbsent(player, x -> new HashMap<>());
        unlocks.forEach(u::putIfAbsent);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Pass> pass(UUID player, String season) {
        String key = k(player, season);
        return CompletableFuture.completedFuture(new Pass(points.getOrDefault(key, 0L), heroic.contains(key),
                new HashSet<>(claims.getOrDefault(key, Set.of()))));
    }

    @Override
    public synchronized CompletableFuture<Void> addPoints(UUID player, String season, long add, long now) {
        if (add > 0) points.merge(k(player, season), add, Long::sum);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Boolean> claim(UUID player, String season, String rewardKey, long now) {
        return CompletableFuture.completedFuture(claims.computeIfAbsent(k(player, season), x -> new HashSet<>()).add(rewardKey));
    }

    @Override
    public synchronized CompletableFuture<Boolean> openHeroic(UUID player, String season, String source, long now) {
        return CompletableFuture.completedFuture(heroic.add(k(player, season)));
    }

    @Override
    public synchronized CompletableFuture<Void> reset(UUID player) {
        deeds.remove(player);
        unlocked.remove(player);
        String prefix = player + "/";
        points.keySet().removeIf(x -> x.startsWith(prefix));
        claims.keySet().removeIf(x -> x.startsWith(prefix));
        heroic.removeIf(x -> x.startsWith(prefix));
        return CompletableFuture.completedFuture(null);
    }
}
