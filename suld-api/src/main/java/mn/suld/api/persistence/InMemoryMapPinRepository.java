package mn.suld.api.persistence;

import mn.suld.api.map.MapPin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Map pins in memory (tests, and storage type "none"). */
public final class InMemoryMapPinRepository implements MapPinRepository {

    private final Map<UUID, List<MapPin>> pins = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<List<MapPin>> list(UUID owner) {
        List<MapPin> l = pins.getOrDefault(owner, List.of());
        synchronized (this) {
            List<MapPin> out = new ArrayList<>(l);
            out.sort(Comparator.comparingLong(MapPin::createdAt));
            return CompletableFuture.completedFuture(out);
        }
    }

    @Override
    public synchronized CompletableFuture<Boolean> add(MapPin pin, int max) {
        List<MapPin> l = pins.computeIfAbsent(pin.owner(), k -> new ArrayList<>());
        if (l.size() >= max || l.stream().anyMatch(p -> p.id().equals(pin.id()))) return CompletableFuture.completedFuture(false);
        l.add(pin);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Boolean> remove(UUID owner, UUID pin) {
        List<MapPin> l = pins.get(owner);
        return CompletableFuture.completedFuture(l != null && l.removeIf(p -> p.id().equals(pin)));
    }
}
