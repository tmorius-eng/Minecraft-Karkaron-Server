package mn.suld.api.persistence;

import mn.suld.api.death.DeathRecord;
import mn.suld.api.death.Wound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Memory-mode {@link DeathRepository} (and the reference behaviour the JDBC one must match). */
public final class InMemoryDeathRepository implements DeathRepository {

    private final Map<UUID, List<DeathRecord>> deaths = new ConcurrentHashMap<>();
    private final Map<UUID, Wound> wounds = new ConcurrentHashMap<>();

    @Override
    public synchronized CompletableFuture<DeathRecord> insert(DeathRecord d) {
        List<DeathRecord> list = deaths.computeIfAbsent(d.player(), k -> new ArrayList<>());
        if (list.stream().anyMatch(DeathRecord::locked) && d.locked()) {
            return CompletableFuture.failedFuture(new RepositoryException("player already locked: " + d.player(), null));
        }
        int seq = list.stream().mapToInt(DeathRecord::seq).max().orElse(0) + 1;
        DeathRecord stored = new DeathRecord(d.deathId(), d.player(), seq, d.diedAt(), d.lockedUntil(), d.world(), d.x(), d.y(),
                d.z(), d.cause(), d.level(), d.ascension(), d.woundAfter(), d.state(), d.recoveredAt(), 1);
        list.add(stored);
        return CompletableFuture.completedFuture(stored);
    }

    @Override
    public synchronized CompletableFuture<Boolean> update(DeathRecord r) {
        List<DeathRecord> list = deaths.get(r.player());
        if (list == null) return CompletableFuture.completedFuture(false);
        for (int i = 0; i < list.size(); i++) {
            DeathRecord cur = list.get(i);
            if (cur.deathId().equals(r.deathId())) {
                if (cur.version() != r.version() - 1) return CompletableFuture.completedFuture(false);
                list.set(i, r);
                return CompletableFuture.completedFuture(true);
            }
        }
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public synchronized CompletableFuture<Optional<DeathRecord>> openLock(UUID player) {
        return CompletableFuture.completedFuture(deaths.getOrDefault(player, List.of()).stream().filter(DeathRecord::locked).findFirst());
    }

    @Override
    public synchronized CompletableFuture<List<DeathRecord>> recent(UUID player, int limit) {
        List<DeathRecord> list = new ArrayList<>(deaths.getOrDefault(player, List.of()));
        list.sort(Comparator.comparingInt(DeathRecord::seq).reversed());
        return CompletableFuture.completedFuture(List.copyOf(list.subList(0, Math.min(limit, list.size()))));
    }

    @Override
    public CompletableFuture<Wound> wound(UUID player) {
        return CompletableFuture.completedFuture(wounds.getOrDefault(player, Wound.NONE));
    }

    @Override
    public CompletableFuture<Void> saveWound(UUID player, Wound wound) {
        wounds.put(player, wound);
        return CompletableFuture.completedFuture(null);
    }
}
