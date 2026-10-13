package mn.suld.api.persistence;

import mn.suld.api.mastery.WeaponMastery;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Weapon mastery (V25, docs/MASTERY_AND_PROFESSIONS.md): per player and track, the mastery points ever earned. Writes
 * only add ({@link #add}), so batches written in any order, on any number of threads, end at the same total.
 */
public interface WeaponMasteryRepository {

    /** Every track the player has points in (absent tracks have none). */
    CompletableFuture<Map<WeaponMastery.Track, Double>> totals(UUID player);

    /** Adds {@code points} (> 0) to each track's total. */
    CompletableFuture<Void> add(UUID player, Map<WeaponMastery.Track, Double> points, long now);

    /** Staff/QA: sets a track's total. */
    CompletableFuture<Void> set(UUID player, WeaponMastery.Track track, double total, long now);
}
