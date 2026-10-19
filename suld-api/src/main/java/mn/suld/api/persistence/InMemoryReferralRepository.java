package mn.suld.api.persistence;

import mn.suld.api.social.Referral;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Referrals in memory (tests, and storage type "memory"). */
public final class InMemoryReferralRepository implements ReferralRepository {

    private final Map<UUID, Referral> rows = new LinkedHashMap<>();

    @Override
    public synchronized CompletableFuture<Boolean> create(Referral referral) {
        if (rows.containsKey(referral.invitee())) return CompletableFuture.completedFuture(false);
        rows.put(referral.invitee(), referral);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Optional<Referral>> byInvitee(UUID invitee) {
        return CompletableFuture.completedFuture(Optional.ofNullable(rows.get(invitee)));
    }

    @Override
    public synchronized CompletableFuture<List<Referral>> byInviter(UUID inviter) {
        List<Referral> out = new ArrayList<>();
        for (Referral r : rows.values()) if (r.inviter().equals(inviter)) out.add(r);
        out.sort(Comparator.comparingLong(Referral::createdAt));
        return CompletableFuture.completedFuture(out);
    }

    @Override
    public synchronized CompletableFuture<Boolean> markRewarded(UUID invitee, long at) {
        Referral r = rows.get(invitee);
        if (r == null || r.rewarded()) return CompletableFuture.completedFuture(false);
        rows.put(invitee, new Referral(r.invitee(), r.inviter(), r.createdAt(), at, null));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Boolean> markInviterPaid(UUID invitee, long at) {
        Referral r = rows.get(invitee);
        if (r == null || !r.rewarded() || r.inviterPaid()) return CompletableFuture.completedFuture(false);
        rows.put(invitee, new Referral(r.invitee(), r.inviter(), r.createdAt(), r.rewardedAt(), at));
        return CompletableFuture.completedFuture(true);
    }
}
