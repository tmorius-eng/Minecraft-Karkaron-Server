package mn.suld.api.persistence;

import mn.suld.api.map.MapPin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Players' map pins (V26, docs/WORLD_MAP.md). */
public interface MapPinRepository {

    /** The player's pins, oldest first. */
    CompletableFuture<List<MapPin>> list(UUID owner);

    /** Adds a pin unless the owner already has {@code max}; true when it was added. */
    CompletableFuture<Boolean> add(MapPin pin, int max);

    /** Removes one of the owner's pins; true when it was there. */
    CompletableFuture<Boolean> remove(UUID owner, UUID pin);
}
