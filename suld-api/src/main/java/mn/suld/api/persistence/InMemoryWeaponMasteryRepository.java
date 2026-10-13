package mn.suld.api.persistence;

import mn.suld.api.mastery.WeaponMastery;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** In-memory weapon mastery (tests, and storage-less setups): the same add-only rule as the JDBC one. */
public final class InMemoryWeaponMasteryRepository implements WeaponMasteryRepository {

    private final Map<UUID, Map<WeaponMastery.Track, Double>> totals = new HashMap<>();

    @Override
    public synchronized CompletableFuture<Map<WeaponMastery.Track, Double>> totals(UUID player) {
        Map<WeaponMastery.Track, Double> m = totals.get(player);
        return CompletableFuture.completedFuture(m == null ? Map.of() : Map.copyOf(m));
    }

    @Override
    public synchronized CompletableFuture<Void> add(UUID player, Map<WeaponMastery.Track, Double> points, long now) {
        Map<WeaponMastery.Track, Double> m = totals.computeIfAbsent(player, k -> new EnumMap<>(WeaponMastery.Track.class));
        points.forEach((t, p) -> {
            if (p != null && p > 0) m.merge(t, p, Double::sum);
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Void> set(UUID player, WeaponMastery.Track track, double total, long now) {
        totals.computeIfAbsent(player, k -> new EnumMap<>(WeaponMastery.Track.class)).put(track, Math.max(0, total));
        return CompletableFuture.completedFuture(null);
    }
}
