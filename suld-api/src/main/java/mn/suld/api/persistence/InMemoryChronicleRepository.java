package mn.suld.api.persistence;

import mn.suld.api.chronicle.ChronicleEntry;
import mn.suld.api.chronicle.ChronicleKind;
import mn.suld.api.chronicle.ChronicleRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Volatile chronicle storage for MEMORY mode and tests. */
public final class InMemoryChronicleRepository implements ChronicleRepository {

    private final List<ChronicleEntry> log = new ArrayList<>();
    private final Map<String, ChronicleEntry> holders = new HashMap<>();
    private final Set<Long> hidden = new HashSet<>();
    private long nextId = 1;

    private static String key(ChronicleKind k, String subject) {
        return k + "|" + subject;
    }

    private ChronicleEntry write(ChronicleEntry e) {
        ChronicleEntry withId = e.withId(nextId++);
        log.add(withId);
        return withId;
    }

    @Override
    public synchronized CompletableFuture<Optional<ChronicleEntry>> claimFirst(ChronicleEntry entry) {
        String k = key(entry.kind(), entry.subjectKey());
        if (holders.containsKey(k)) return CompletableFuture.completedFuture(Optional.empty());
        ChronicleEntry w = write(entry);
        holders.put(k, w);
        return CompletableFuture.completedFuture(Optional.of(w));
    }

    @Override
    public synchronized CompletableFuture<Optional<ChronicleEntry>> recordIfBetter(ChronicleEntry entry) {
        String k = key(entry.kind(), entry.subjectKey());
        ChronicleEntry cur = holders.get(k);
        if (!ChronicleRules.better(entry.kind(), cur == null ? null : cur.value(), entry.value())) return CompletableFuture.completedFuture(Optional.empty());
        ChronicleEntry w = write(entry);
        holders.put(k, w);
        return CompletableFuture.completedFuture(Optional.of(w));
    }

    @Override
    public synchronized CompletableFuture<ChronicleEntry> append(ChronicleEntry entry) {
        return CompletableFuture.completedFuture(write(entry));
    }

    private List<ChronicleEntry> visible(java.util.function.Predicate<ChronicleEntry> f, int limit) {
        List<ChronicleEntry> out = new ArrayList<>();
        for (ChronicleEntry e : log) if (!hidden.contains(e.id()) && f.test(e)) out.add(e);
        out.sort(Comparator.comparingLong(ChronicleEntry::id).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }

    @Override
    public synchronized CompletableFuture<List<ChronicleEntry>> latest(int limit) {
        return CompletableFuture.completedFuture(visible(e -> true, limit));
    }

    @Override
    public synchronized CompletableFuture<List<ChronicleEntry>> byKind(ChronicleKind kind, int limit) {
        return CompletableFuture.completedFuture(visible(e -> e.kind() == kind, limit));
    }

    @Override
    public synchronized CompletableFuture<List<ChronicleEntry>> byPlayer(UUID player, int limit) {
        return CompletableFuture.completedFuture(visible(e -> player.equals(e.player()), limit));
    }

    @Override
    public synchronized CompletableFuture<Optional<ChronicleEntry>> holder(ChronicleKind kind, String subjectKey) {
        return CompletableFuture.completedFuture(Optional.ofNullable(holders.get(key(kind, subjectKey))));
    }

    @Override
    public synchronized CompletableFuture<Boolean> hide(long id) {
        boolean exists = log.stream().anyMatch(e -> e.id() == id);
        if (exists) hidden.add(id);
        return CompletableFuture.completedFuture(exists);
    }
}
