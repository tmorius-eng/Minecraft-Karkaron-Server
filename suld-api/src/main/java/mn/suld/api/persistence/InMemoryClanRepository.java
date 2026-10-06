package mn.suld.api.persistence;

import mn.suld.api.clan.Clan;
import mn.suld.api.clan.ClanSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Volatile clan storage for MEMORY mode and tests. */
public final class InMemoryClanRepository implements ClanRepository {

    private final Map<UUID, ClanSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<List<Clan>> loadAll() {
        List<Clan> out = new ArrayList<>();
        for (ClanSnapshot s : store.values()) {
            out.add(Clan.restore(s.id(), s.name(), s.tag(), s.createdAt(), s.exp(), s.version(), s.members()));
        }
        return CompletableFuture.completedFuture(out);
    }

    @Override
    public CompletableFuture<Void> save(ClanSnapshot clan) {
        store.put(clan.id(), clan);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> delete(UUID clanId) {
        store.remove(clanId);
        return CompletableFuture.completedFuture(null);
    }
}
