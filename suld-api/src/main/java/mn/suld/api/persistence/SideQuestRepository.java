package mn.suld.api.persistence;

import mn.suld.api.quest.SideQuestRecord;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The players' side quests (V32 {@code suld_side_quests}, docs/SIDE_QUESTS.md). Take and hand-in are compare-and-set. */
public interface SideQuestRepository {

    /** Every record of the player, by quest id. */
    CompletableFuture<Map<String, SideQuestRecord>> load(UUID player);

    /** @return false when the player already has a row for this quest (taken before, active or done) */
    CompletableFuture<Boolean> start(UUID player, String questId, long now);

    /** The objective count of an ACTIVE row (a DONE row keeps its own). */
    CompletableFuture<Void> progress(UUID player, String questId, int progress);

    /** ACTIVE → DONE: true once, whatever the clicks. */
    CompletableFuture<Boolean> complete(UUID player, String questId, int progress, long now);

    /** Deletes an ACTIVE row (a DONE row stays): true when there was one. */
    CompletableFuture<Boolean> abandon(UUID player, String questId);

    /** Staff: deletes the row whatever its state (docs/SIDE_QUESTS.md, /suld squest reset). */
    CompletableFuture<Boolean> reset(UUID player, String questId);
}
