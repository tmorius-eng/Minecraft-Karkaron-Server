package mn.suld.api.persistence;

import mn.suld.api.campaign.CampaignSnapshot;
import mn.suld.api.campaign.Contribution;
import mn.suld.api.campaign.WorldFlag;
import mn.suld.api.campaign.vote.VoteCycle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage of Их Хуралдай (V18): vote cycles and ballots, the week's campaign and its contributions, and the world-state
 * flags. Every method is asynchronous; callers hop back to the main thread before touching the game.
 */
public interface CampaignRepository {

    /** The outcome of one ballot. */
    enum CastResult { CAST, ALREADY_VOTED, CLOSED, NO_SUCH_OPTION, NO_CYCLE }

    // ------------------------------------------------------------------ votes

    CompletableFuture<Optional<VoteCycle>> voteCycle(String cycleId);

    /** Open the vote of a cycle if none exists yet; returns the row either way. */
    CompletableFuture<VoteCycle> openVote(String cycleId, List<String> options, long opensAt, long closesAt);

    /** One ballot per player per cycle, enforced by the primary key. */
    CompletableFuture<CastResult> castVote(String cycleId, UUID player, String option, int level, long at);

    CompletableFuture<Map<String, Integer>> tally(String cycleId);

    /** Close the vote with its tally and winner; false when another server closed it first (CAS on version). */
    CompletableFuture<Boolean> closeVote(String cycleId, String winner, Map<String, Integer> tally, long closedAt, long expectedVersion);

    /** The most recent vote cycles, newest first. */
    CompletableFuture<List<VoteCycle>> recentVotes(int limit);

    // -------------------------------------------------------------- campaigns

    CompletableFuture<Optional<CampaignSnapshot>> campaign(String cycleId);

    /**
     * Insert a new campaign row (when {@code expectedVersion < 0}) or compare-and-set an existing one to
     * {@code snapshot} with {@code version + 1}. False when the row changed under the caller (or already exists).
     */
    CompletableFuture<Boolean> saveCampaign(CampaignSnapshot snapshot, long expectedVersion);

    /** Add contribution deltas: each player's units grow, the campaign total grows by the sum, and a ledger row is kept. */
    CompletableFuture<Void> addContributions(String cycleId, List<Contribution> deltas);

    /** Every contributor of a cycle, largest first. */
    CompletableFuture<List<Contribution>> contributions(String cycleId);

    /** Record the tier owed to a player at resolution ({@code claimedAt} null) or that it was paid. */
    CompletableFuture<Void> markReward(String cycleId, UUID player, int tier, Long claimedAt);

    /** The most recent campaigns, newest first. */
    CompletableFuture<List<CampaignSnapshot>> recentCampaigns(int limit);

    // ------------------------------------------------------------ world state

    CompletableFuture<Void> setFlag(WorldFlag flag);

    CompletableFuture<Void> clearFlag(String key);

    CompletableFuture<List<WorldFlag>> flags();
}
