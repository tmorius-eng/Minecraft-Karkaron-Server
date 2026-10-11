package mn.suld.api.campaign;

import org.jetbrains.annotations.Nullable;

/**
 * The database row of one cycle's campaign ({@code suld_campaigns}). {@code version} is the compare-and-set guard of
 * every state change; {@code total} is bumped additively by the contribution flush and is not CAS-guarded.
 */
public record CampaignSnapshot(String cycleId, String campaignId, CampaignState state, long startsAt, long endsAt,
                               @Nullable Long resolvedAt, long target, long total, @Nullable Long effectAppliedAt, long version) {

    public CampaignSnapshot withState(CampaignState next, long at) {
        return new CampaignSnapshot(cycleId, campaignId, next, startsAt, endsAt, next.resolved() ? at : resolvedAt, target, total,
                effectAppliedAt, version);
    }

    public CampaignSnapshot withEffectApplied(long at) {
        return new CampaignSnapshot(cycleId, campaignId, state, startsAt, endsAt, resolvedAt, target, total, at, version);
    }

    public CampaignSnapshot withTotal(long newTotal) {
        return new CampaignSnapshot(cycleId, campaignId, state, startsAt, endsAt, resolvedAt, target, newTotal, effectAppliedAt, version);
    }
}
