package mn.suld.api.social;

/**
 * The referral loop's numbers ({@code referral:} in config.yml; the defaults are a proposal until the owner sets them,
 * docs/REFERRAL.md).
 *
 * @param rewardLevel    the invitee's level that pays both sides
 * @param maxLevelToName a player above this level is no newcomer and may not name an inviter
 * @param inviteeCoins   paid to the invitee at {@code rewardLevel}
 * @param inviterCoins   paid to the inviter for each invitee who reached {@code rewardLevel}
 * @param badgeAfter     paid invitations that earn the inviter the «Элч» badge (0 = never)
 */
public record ReferralSettings(boolean enabled, int rewardLevel, int maxLevelToName, long inviteeCoins, long inviterCoins, int badgeAfter) {

    public ReferralSettings {
        if (rewardLevel < 1 || maxLevelToName < 1 || inviteeCoins < 0 || inviterCoins < 0 || badgeAfter < 0) {
            throw new IllegalArgumentException("referral settings out of range");
        }
    }

    public static ReferralSettings defaults() {
        return new ReferralSettings(true, 10, 5, 100, 200, 3);
    }
}
