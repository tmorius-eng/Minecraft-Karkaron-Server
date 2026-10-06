package mn.suld.api.relic;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Authoritative state of one relic as stored in the database. Immutable; every change goes
 * through a compare-and-set on {@link #version()}, which also serves as the physical copy's
 * <i>generation</i>: any item carrying an older generation is a stale copy and is destroyed.
 */
public record RelicRecord(
        String key,
        UUID itemUuid,
        RelicState state,
        @Nullable UUID owner,
        @Nullable String ownerName,
        @Nullable Instant acquiredAt,
        long version,
        @Nullable ShrineLocation shrine) {

    public boolean isOwnedBy(UUID player) {
        return state == RelicState.OWNED && player != null && player.equals(owner);
    }

    public boolean isAvailable() {
        return state == RelicState.UNCLAIMED;
    }
}
