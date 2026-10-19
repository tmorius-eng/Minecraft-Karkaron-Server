package mn.suld.api.persistence;

import mn.suld.api.social.Referral;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The referrals (V31 {@code suld_referrals}, docs/REFERRAL.md): one row per invitee; the two payments are compare-and-set. */
public interface ReferralRepository {

    /** @return false when the invitee already has a row (the first invitation stands) */
    CompletableFuture<Boolean> create(Referral referral);

    CompletableFuture<Optional<Referral>> byInvitee(UUID invitee);

    /** Every invitation this player made, oldest first. */
    CompletableFuture<List<Referral>> byInviter(UUID inviter);

    /** The invitee's payment: true once, when {@code rewarded_at} was still null. */
    CompletableFuture<Boolean> markRewarded(UUID invitee, long at);

    /** The inviter's payment: true once, after the invitee was rewarded and while {@code inviter_paid_at} was still null. */
    CompletableFuture<Boolean> markInviterPaid(UUID invitee, long at);
}
