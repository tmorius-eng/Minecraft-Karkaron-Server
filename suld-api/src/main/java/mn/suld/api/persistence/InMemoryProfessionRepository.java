package mn.suld.api.persistence;

import mn.suld.api.mastery.Profession;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** In-memory crafts (tests, and storage-less setups): the same add-only rule as the JDBC one. */
public final class InMemoryProfessionRepository implements ProfessionRepository {

    private final Map<UUID, Map<Profession, Long>> totals = new HashMap<>();

    @Override
    public synchronized CompletableFuture<Map<Profession, Long>> totals(UUID player) {
        Map<Profession, Long> m = totals.get(player);
        return CompletableFuture.completedFuture(m == null ? Map.of() : Map.copyOf(m));
    }

    @Override
    public synchronized CompletableFuture<Void> add(UUID player, Map<Profession, Long> points, long now) {
        Map<Profession, Long> m = totals.computeIfAbsent(player, k -> new EnumMap<>(Profession.class));
        points.forEach((p, n) -> {
            if (n != null && n > 0) m.merge(p, n, Long::sum);
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Void> set(UUID player, Profession craft, long total, long now) {
        totals.computeIfAbsent(player, k -> new EnumMap<>(Profession.class)).put(craft, Math.max(0, total));
        return CompletableFuture.completedFuture(null);
    }
}
