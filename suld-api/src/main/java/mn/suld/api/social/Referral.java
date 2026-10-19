package mn.suld.api.social;

import java.util.UUID;

/**
 * One invitation (docs/REFERRAL.md): a new player ({@code invitee}) named the friend who brought them ({@code inviter})
 * with {@code /urih}. When the invitee reaches {@link ReferralSettings#rewardLevel()} both are paid: the invitee at
 * once ({@code rewardedAt}), the inviter when online, else at their next login ({@code inviterPaidAt}). One row per
 * invitee, ever.
 */
public record Referral(UUID invitee, UUID inviter, long createdAt, Long rewardedAt, Long inviterPaidAt) {

    public Referral {
        if (invitee == null || inviter == null) throw new IllegalArgumentException("invitee and inviter are required");
        if (invitee.equals(inviter)) throw new IllegalArgumentException("a player cannot invite themselves");
    }

    public boolean rewarded() {
        return rewardedAt != null;
    }

    public boolean inviterPaid() {
        return inviterPaidAt != null;
    }
}
