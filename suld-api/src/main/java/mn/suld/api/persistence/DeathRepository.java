package mn.suld.api.persistence;

import mn.suld.api.death.DeathRecord;
import mn.suld.api.death.Wound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage of deaths and of each player's open wound (V12: {@code suld_death_state}, {@code suld_death_recovery}).
 * Every method is asynchronous; implementations order the writes of one player.
 */
public interface DeathRepository {

    /**
     * Store a new death. The repository assigns the per-player sequence number. Fails (exceptionally) if the player
     * already has a LOCKED death — the database enforces one open lock per player.
     */
    CompletableFuture<DeathRecord> insert(DeathRecord death);

    /** Compare-and-set update of a death's state: succeeds only if the stored version is {@code record.version() - 1}. */
    CompletableFuture<Boolean> update(DeathRecord record);

    /** The player's LOCKED death, if any. */
    CompletableFuture<Optional<DeathRecord>> openLock(UUID player);

    /** The player's most recent deaths, newest first. */
    CompletableFuture<List<DeathRecord>> recent(UUID player, int limit);

    CompletableFuture<Wound> wound(UUID player);

    CompletableFuture<Void> saveWound(UUID player, Wound wound);
}
