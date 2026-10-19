package mn.suld.plugin.persistence;

import mn.suld.api.social.Referral;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V31 referrals on H2 (MySQL mode) or PostgreSQL: one row per invitee, each payment once under concurrency. */
class JdbcReferralIT {

    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @Test
    void oneInviterPerInviteeAndEachSidePaidOnce() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        JdbcReferralRepository repo = new JdbcReferralRepository(db.ds(), db.dialect(), pool);
        UUID invitee = UUID.randomUUID(), inviter = UUID.randomUUID();
        assertTrue(repo.create(new Referral(invitee, inviter, 1_000L, null, null)).get());
        assertFalse(repo.create(new Referral(invitee, UUID.randomUUID(), 2_000L, null, null)).get(), "the first invitation stands");
        assertEquals(inviter, repo.byInvitee(invitee).get().orElseThrow().inviter());
        assertFalse(repo.markInviterPaid(invitee, 5L).get(), "the inviter is paid only after the invitee was");

        List<CompletableFuture<Boolean>> all = new ArrayList<>();
        for (int i = 0; i < 24; i++) all.add(repo.markRewarded(invitee, 3_000L + i));
        long rewarded = all.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
        assertEquals(1, rewarded, "24 concurrent level-up checks pay the invitee once");

        all.clear();
        for (int i = 0; i < 24; i++) all.add(repo.markInviterPaid(invitee, 4_000L + i));
        assertEquals(1, all.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count(), "24 concurrent logins pay the inviter once");

        Referral r = repo.byInvitee(invitee).get().orElseThrow();
        assertTrue(r.rewarded() && r.inviterPaid());
        assertEquals(1, repo.byInviter(inviter).get().size());
        assertTrue(repo.byInviter(UUID.randomUUID()).get().isEmpty());
    }
}
