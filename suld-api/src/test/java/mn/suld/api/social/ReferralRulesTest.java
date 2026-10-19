package mn.suld.api.social;

import mn.suld.api.persistence.InMemoryReferralRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferralRulesTest {

    private final ReferralSettings s = ReferralSettings.defaults();
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @Test
    void whoMayNameAnInviter() {
        assertEquals(ReferralRules.Refusal.NONE, ReferralRules.refuse(s, a, b, 1, false));
        assertEquals(ReferralRules.Refusal.NONE, ReferralRules.refuse(s, a, b, 5, false), "level 5 is still a newcomer");
        assertEquals(ReferralRules.Refusal.TOO_HIGH, ReferralRules.refuse(s, a, b, 6, false));
        assertEquals(ReferralRules.Refusal.SELF, ReferralRules.refuse(s, a, a, 1, false));
        assertEquals(ReferralRules.Refusal.ALREADY_NAMED, ReferralRules.refuse(s, a, b, 1, true), "the first invitation stands");
        assertEquals(ReferralRules.Refusal.OFF, ReferralRules.refuse(new ReferralSettings(false, 10, 5, 100, 200, 3), a, b, 1, false));
    }

    @Test
    void thePaymentIsDueOnceAtTheRewardLevel() {
        Referral r = new Referral(a, b, 1L, null, null);
        assertFalse(ReferralRules.due(s, r, 9));
        assertTrue(ReferralRules.due(s, r, 10));
        assertTrue(ReferralRules.due(s, r, 60), "a late check still pays");
        assertFalse(ReferralRules.due(s, new Referral(a, b, 1L, 5L, null), 60), "paid already");
    }

    @Test
    void theBadgeComesAfterThreePaidInvitationsByDefault() {
        assertFalse(ReferralRules.earnsBadge(s, 2));
        assertTrue(ReferralRules.earnsBadge(s, 3));
        assertFalse(ReferralRules.earnsBadge(new ReferralSettings(true, 10, 5, 100, 200, 0), 99), "0 = never");
    }

    @Test
    void aReferralNeedsTwoDifferentPlayers() {
        assertThrows(IllegalArgumentException.class, () -> new Referral(a, a, 1L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Referral(a, null, 1L, null, null));
    }

    @Test
    void theInMemoryStoreKeepsTheFirstRowAndPaysEachSideOnce() {
        InMemoryReferralRepository repo = new InMemoryReferralRepository();
        assertTrue(repo.create(new Referral(a, b, 1L, null, null)).join());
        assertFalse(repo.create(new Referral(a, UUID.randomUUID(), 2L, null, null)).join(), "one inviter per invitee, ever");
        assertFalse(repo.markInviterPaid(a, 3L).join(), "the inviter is paid only after the invitee was");
        assertTrue(repo.markRewarded(a, 3L).join());
        assertFalse(repo.markRewarded(a, 4L).join());
        assertTrue(repo.markInviterPaid(a, 5L).join());
        assertFalse(repo.markInviterPaid(a, 6L).join());
        Referral r = repo.byInvitee(a).join().orElseThrow();
        assertEquals(3L, r.rewardedAt());
        assertEquals(5L, r.inviterPaidAt());
        assertEquals(1, repo.byInviter(b).join().size());
    }
}
