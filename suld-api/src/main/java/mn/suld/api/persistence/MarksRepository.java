package mn.suld.api.persistence;

import mn.suld.api.progress.Mark;
import mn.suld.api.progress.MarkKind;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage of per-player progress marks (V17 {@code suld_player_marks}): first dungeon clears, first site visits, the
 * last ovoo day. Writes are idempotent upserts, so a retried write or a backfill from the player file never duplicates.
 */
public interface MarksRepository {

    /** Write or replace one mark. */
    CompletableFuture<Void> put(Mark mark);

    /** Write or replace several marks of one player (a backfill from the player file). */
    CompletableFuture<Void> putAll(List<Mark> marks);

    /** Every mark of a player, as {@code kind → (key → value)}. */
    CompletableFuture<Map<MarkKind, Map<String, Long>>> load(UUID player);

    /** How many distinct players carry a mark of {@code kind} for {@code key} (e.g. clears of one dungeon). */
    CompletableFuture<Long> count(MarkKind kind, String key);
}
