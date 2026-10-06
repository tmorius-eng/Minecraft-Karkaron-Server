package mn.suld.api.relic;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A requested ownership change. Applied only if the stored version still equals
 * {@link #expectedVersion()} — one atomic compare-and-set, so two concurrent claims (or a
 * claim racing a seize) can never both succeed. The version is incremented on success.
 */
public record RelicTransition(
        String key,
        long expectedVersion,
        RelicState newState,
        @Nullable UUID newOwner,
        @Nullable String newOwnerName,
        RelicEvent event,
        String actor,
        String detail) {

    public RelicTransition {
        if ((newState == RelicState.OWNED) != (newOwner != null)) {
            throw new IllegalArgumentException("OWNED requires an owner; UNCLAIMED requires none");
        }
    }

    public static RelicTransition claim(RelicRecord r, UUID player, String name, RelicEvent event, String actor, String detail) {
        return new RelicTransition(r.key(), r.version(), RelicState.OWNED, player, name, event, actor, detail);
    }

    public static RelicTransition release(RelicRecord r, RelicEvent event, String actor, String detail) {
        return new RelicTransition(r.key(), r.version(), RelicState.UNCLAIMED, null, null, event, actor, detail);
    }

    /** Same owner, new generation: invalidates every existing physical copy. */
    public static RelicTransition regenerate(RelicRecord r, RelicEvent event, String actor, String detail) {
        return new RelicTransition(r.key(), r.version(), r.state(), r.owner(), r.ownerName(), event, actor, detail);
    }
}
