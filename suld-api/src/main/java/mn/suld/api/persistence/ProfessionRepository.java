package mn.suld.api.persistence;

import mn.suld.api.mastery.Profession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The crafts (V27, docs/MASTERY_AND_PROFESSIONS.md): per player and craft, the points ever earned by gathering. Writes
 * only add ({@link #add}), so batches written in any order, on any number of threads, end at the same total.
 */
public interface ProfessionRepository {

    /** Every craft the player has points in (absent crafts have none). */
    CompletableFuture<Map<Profession, Long>> totals(UUID player);

    /** Adds {@code points} (> 0) to each craft's total. */
    CompletableFuture<Void> add(UUID player, Map<Profession, Long> points, long now);

    /** Staff/QA: sets a craft's total. */
    CompletableFuture<Void> set(UUID player, Profession craft, long total, long now);
}
