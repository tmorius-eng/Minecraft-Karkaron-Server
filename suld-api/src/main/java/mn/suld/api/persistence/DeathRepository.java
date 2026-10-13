package mn.suld.api.persistence;

import mn.suld.api.death.DeathRecord;
import mn.suld.api.death.SoulBeacon;
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

    /** Store a new «Сүнсний Гэрэлт» (V23 {@code suld_soul_beacons}); a second one for the same death is a no-op. */
    CompletableFuture<Void> saveBeacon(SoulBeacon beacon);

    /** The player's newest beacon that is unclaimed and still standing at {@code now}, if any. */
    CompletableFuture<Optional<SoulBeacon>> openBeacon(UUID player, long now);

    /**
     * Claim the beacon of {@code deathId} at {@code now}: true for exactly one caller, and only while it is unclaimed
     * and standing (a compare-and-set in storage; the refund is paid only after it).
     */
    CompletableFuture<Boolean> claimBeacon(UUID deathId, long now);
}
