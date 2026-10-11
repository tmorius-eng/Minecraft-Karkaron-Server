package mn.suld.api.persistence;

import mn.suld.api.progress.Mark;
import mn.suld.api.progress.MarkKind;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Volatile marks storage for MEMORY mode and tests. */
public final class InMemoryMarksRepository implements MarksRepository {

    private final Map<UUID, Map<MarkKind, Map<String, Long>>> store = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> put(Mark m) {
        store.computeIfAbsent(m.player(), p -> new ConcurrentHashMap<>())
                .computeIfAbsent(m.kind(), k -> new ConcurrentHashMap<>())
                .put(m.key(), m.value());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> putAll(List<Mark> marks) {
        marks.forEach(this::put);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<MarkKind, Map<String, Long>>> load(UUID player) {
        Map<MarkKind, Map<String, Long>> out = new EnumMap<>(MarkKind.class);
        store.getOrDefault(player, Map.of()).forEach((k, v) -> out.put(k, new HashMap<>(v)));
        return CompletableFuture.completedFuture(out);
    }

    @Override
    public CompletableFuture<Long> count(MarkKind kind, String key) {
        long n = store.values().stream().filter(m -> m.getOrDefault(kind, Map.of()).containsKey(key)).count();
        return CompletableFuture.completedFuture(n);
    }
}
