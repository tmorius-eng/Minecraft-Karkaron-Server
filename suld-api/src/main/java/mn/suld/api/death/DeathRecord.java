package mn.suld.api.death;

import java.util.Objects;
import java.util.UUID;

/**
 * One death, persisted (table {@code suld_death_state}). Times are real-world epoch milliseconds; the lock ends at
 * {@code lockedUntil} whatever the server did in between. At most one record per player is {@link State#LOCKED}.
 */
public record DeathRecord(UUID deathId, UUID player, int seq, long diedAt, long lockedUntil, String world, int x, int y,
                          int z, String cause, int level, int ascension, int woundAfter, State state, Long recoveredAt,
                          int version) {

    /** What became of the death. {@link #INSTANT_REVIVED} is reserved for the deferred paid revive and never set. */
    public enum State { LOCKED, RECOVERED, ADMIN_REVIVED, RESET, INSTANT_REVIVED }

    public DeathRecord {
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(state, "state");
        cause = cause == null ? "" : cause.length() > 64 ? cause.substring(0, 64) : cause;
        world = world == null ? "" : world.length() > 64 ? world.substring(0, 64) : world;
    }

    public boolean locked() {
        return state == State.LOCKED;
    }

    /** Time left of the lock at {@code now} (0 once over or not locked). */
    public long remaining(long now) {
        return locked() ? Math.max(0, lockedUntil - now) : 0;
    }

    /** This record moved to {@code next} at {@code when} (the wound recorded with it). */
    public DeathRecord closed(State next, long when, int wound) {
        if (next == State.LOCKED) throw new IllegalArgumentException("closing to LOCKED");
        return new DeathRecord(deathId, player, seq, diedAt, lockedUntil, world, x, y, z, cause, level, ascension, wound,
                next, when, version + 1);
    }
}
