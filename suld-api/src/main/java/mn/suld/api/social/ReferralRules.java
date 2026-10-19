package mn.suld.api.social;

import java.util.UUID;

/** The pure rules of the referral loop (docs/REFERRAL.md); the service applies them, the tests pin them. */
public final class ReferralRules {

    /** Why an invitee may not name an inviter now; {@link #NONE} when they may. */
    public enum Refusal { NONE, OFF, SELF, TOO_HIGH, ALREADY_NAMED }

    private ReferralRules() {
    }

    public static Refusal refuse(ReferralSettings s, UUID invitee, UUID inviter, int inviteeLevel, boolean alreadyNamed) {
        if (!s.enabled()) return Refusal.OFF;
        if (invitee.equals(inviter)) return Refusal.SELF;
        if (alreadyNamed) return Refusal.ALREADY_NAMED;
        if (inviteeLevel > s.maxLevelToName()) return Refusal.TOO_HIGH;
        return Refusal.NONE;
    }

    /** The invitee reached the level that pays and nobody was paid yet. */
    public static boolean due(ReferralSettings s, Referral r, int inviteeLevel) {
        return s.enabled() && !r.rewarded() && inviteeLevel >= s.rewardLevel();
    }

    /** After {@code rewardedCount} paid invitations the inviter earns the «Элч» badge. */
    public static boolean earnsBadge(ReferralSettings s, int rewardedCount) {
        return s.badgeAfter() > 0 && rewardedCount >= s.badgeAfter();
    }
}
