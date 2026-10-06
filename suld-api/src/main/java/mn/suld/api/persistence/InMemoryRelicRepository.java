package mn.suld.api.persistence;

import mn.suld.api.relic.CasResult;
import mn.suld.api.relic.RelicEvent;
import mn.suld.api.relic.RelicHistoryEntry;
import mn.suld.api.relic.RelicRecord;
import mn.suld.api.relic.RelicState;
import mn.suld.api.relic.RelicTransition;
import mn.suld.api.relic.ShrineLocation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Volatile relic storage (MEMORY mode, tests). Same CAS semantics as the JDBC store. */
public final class InMemoryRelicRepository implements RelicRepository {

    private final Clock clock;
    private final Map<String, RelicRecord> rows = new HashMap<>();
    private final Map<String, List<RelicHistoryEntry>> history = new HashMap<>();

    public InMemoryRelicRepository(Clock clock) {
        this.clock = clock;
    }

    public InMemoryRelicRepository() {
        this(Clock.systemUTC());
    }

    @Override
    public synchronized CompletableFuture<RelicRecord> ensure(String key, UUID freshItemUuid) {
        RelicRecord r = rows.get(key);
        if (r == null) {
            for (RelicRecord other : rows.values()) {
                if (other.itemUuid().equals(freshItemUuid)) {
                    return CompletableFuture.failedFuture(new RepositoryException("item uuid already used"));
                }
            }
            r = new RelicRecord(key, freshItemUuid, RelicState.UNCLAIMED, null, null, null, 0, null);
            rows.put(key, r);
            log(key, RelicEvent.CREATED, null, "server", "");
        }
        return CompletableFuture.completedFuture(r);
    }

    @Override
    public synchronized CompletableFuture<List<RelicRecord>> loadAll() {
        return CompletableFuture.completedFuture(List.copyOf(rows.values()));
    }

    @Override
    public synchronized CompletableFuture<CasResult> apply(RelicTransition t) {
        RelicRecord cur = rows.get(t.key());
        if (cur == null) {
            return CompletableFuture.failedFuture(new RepositoryException("unknown relic " + t.key()));
        }
        if (cur.version() != t.expectedVersion()) {
            return CompletableFuture.completedFuture(new CasResult(false, cur));
        }
        Instant now = clock.instant();
        boolean ownerChanged = t.newOwner() == null ? cur.owner() != null : !t.newOwner().equals(cur.owner());
        RelicRecord next = new RelicRecord(cur.key(), cur.itemUuid(), t.newState(), t.newOwner(), t.newOwnerName(),
                t.newState() == RelicState.OWNED ? (ownerChanged ? now : cur.acquiredAt()) : null,
                cur.version() + 1, cur.shrine());
        rows.put(t.key(), next);
        log(t.key(), t.event(), t.newOwner(), t.actor(), t.detail());
        return CompletableFuture.completedFuture(new CasResult(true, next));
    }

    @Override
    public synchronized CompletableFuture<RelicRecord> setShrine(String key, ShrineLocation shrine, String actor) {
        RelicRecord cur = rows.get(key);
        if (cur == null) {
            return CompletableFuture.failedFuture(new RepositoryException("unknown relic " + key));
        }
        RelicRecord next = new RelicRecord(cur.key(), cur.itemUuid(), cur.state(), cur.owner(), cur.ownerName(),
                cur.acquiredAt(), cur.version(), shrine);
        rows.put(key, next);
        log(key, RelicEvent.SHRINE_SET, null, actor, shrine.world() + " " + shrine.x() + " " + shrine.y() + " " + shrine.z());
        return CompletableFuture.completedFuture(next);
    }

    @Override
    public synchronized CompletableFuture<List<RelicHistoryEntry>> history(String key, int limit) {
        List<RelicHistoryEntry> all = new ArrayList<>(history.getOrDefault(key, List.of()));
        java.util.Collections.reverse(all);
        return CompletableFuture.completedFuture(all.subList(0, Math.min(limit, all.size())));
    }

    private void log(String key, RelicEvent event, UUID owner, String actor, String detail) {
        history.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new RelicHistoryEntry(clock.instant(), event, owner, actor, detail));
    }
}
