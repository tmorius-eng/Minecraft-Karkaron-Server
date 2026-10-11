package mn.suld.plugin.persistence;

import mn.suld.api.campaign.CampaignSnapshot;
import mn.suld.api.campaign.CampaignState;
import mn.suld.api.campaign.Contribution;
import mn.suld.api.campaign.WorldFlag;
import mn.suld.api.campaign.vote.VoteCycle;
import mn.suld.api.persistence.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V18: one ballot per player under contention, exact concurrent sums, one CAS winner, a restart's restore. */
class JdbcCampaignRepositoryIT {

    private JdbcCampaignRepository repo;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        repo = new JdbcCampaignRepository(db.ds(), db.dialect(), pool);
    }

    @Test
    void oneBallotPerPlayerEvenUnderContention() {
        VoteCycle c = repo.openVote("2026-W41", List.of("campaign.a", "campaign.b"), 1, 100).join();
        assertEquals(List.of("campaign.a", "campaign.b"), c.options());
        assertEquals(c.options(), repo.openVote("2026-W41", List.of("campaign.zzz"), 5, 6).join().options(), "opening twice keeps the ballot");
        UUID p = UUID.randomUUID();
        List<CompletableFuture<CampaignRepository.CastResult>> all = new ArrayList<>();
        for (int i = 0; i < 24; i++) all.add(repo.castVote("2026-W41", p, i % 2 == 0 ? "campaign.a" : "campaign.b", 10, i));
        long cast = all.stream().map(CompletableFuture::join).filter(r -> r == CampaignRepository.CastResult.CAST).count();
        assertEquals(1, cast, "exactly one of 24 concurrent ballots counts");
        assertEquals(CampaignRepository.CastResult.NO_SUCH_OPTION, repo.castVote("2026-W41", UUID.randomUUID(), "campaign.c", 1, 1).join());
        assertEquals(CampaignRepository.CastResult.NO_CYCLE, repo.castVote("2026-W40", UUID.randomUUID(), "campaign.a", 1, 1).join());
        for (int i = 0; i < 5; i++) assertEquals(CampaignRepository.CastResult.CAST, repo.castVote("2026-W41", UUID.randomUUID(), "campaign.b", 7, 1).join());
        Map<String, Integer> tally = repo.tally("2026-W41").join();
        assertEquals(6, tally.values().stream().mapToInt(Integer::intValue).sum(), "5 voters for b plus p's one accepted ballot");
        assertTrue(tally.get("campaign.b") >= 5, "p's one ballot may have landed on either option");

        List<CompletableFuture<Boolean>> closes = new ArrayList<>();
        for (int i = 0; i < 8; i++) closes.add(repo.closeVote("2026-W41", "campaign.b", tally, 200, 0));
        assertEquals(1, closes.stream().map(CompletableFuture::join).filter(b -> b).count(), "one CAS close wins");
        VoteCycle closed = repo.voteCycle("2026-W41").join().orElseThrow();
        assertTrue(closed.closed());
        assertEquals("campaign.b", closed.winner());
        assertEquals(6, closed.voters());
        assertEquals(tally, closed.tally());
        assertEquals(1, closed.version());
        assertEquals(CampaignRepository.CastResult.CLOSED, repo.castVote("2026-W41", UUID.randomUUID(), "campaign.a", 1, 1).join());
        repo.openVote("2026-W42", List.of("campaign.a"), 1, 2).join();
        assertEquals("2026-W42", repo.recentVotes(5).join().get(0).cycleId(), "newest first");
    }

    @Test
    void contributionsSumExactlyUnderContentionAndStateIsCas() {
        CampaignSnapshot row = new CampaignSnapshot("2026-W41", "campaign.a", CampaignState.ACTIVE, 0, 100, null, 3000, 0, null, 0);
        assertTrue(repo.saveCampaign(row, -1).join());
        assertFalse(repo.saveCampaign(row, -1).join(), "the row is inserted once");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), clan = UUID.randomUUID();
        List<CompletableFuture<Void>> adds = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            adds.add(repo.addContributions("2026-W41", List.of(Contribution.delta(a, "A", clan, 3), Contribution.delta(b, "B", null, 1))));
        }
        adds.forEach(CompletableFuture::join);
        assertEquals(96, repo.campaign("2026-W41").join().orElseThrow().total(), "24 × (3 + 1)");
        List<Contribution> t = repo.contributions("2026-W41").join();
        assertEquals("A", t.get(0).name());
        assertEquals(72, t.get(0).units());
        assertEquals(clan, t.get(0).clan());
        assertEquals(24, t.get(1).units());
        assertNull(t.get(1).clan());

        List<CompletableFuture<Boolean>> resolves = new ArrayList<>();
        for (int i = 0; i < 8; i++) resolves.add(repo.saveCampaign(row.withState(CampaignState.SUCCEEDED, 90), 0));
        assertEquals(1, resolves.stream().map(CompletableFuture::join).filter(x -> x).count(), "one CAS resolve wins");
        CampaignSnapshot after = repo.campaign("2026-W41").join().orElseThrow();
        assertEquals(CampaignState.SUCCEEDED, after.state());
        assertEquals(90, after.resolvedAt());
        assertEquals(96, after.total(), "a state change never overwrites the flushed total");
        assertEquals(1, after.version());
        assertTrue(repo.saveCampaign(after.withEffectApplied(95), 1).join());
        assertEquals(95, repo.campaign("2026-W41").join().orElseThrow().effectAppliedAt());

        repo.markReward("2026-W41", a, 3, null).join();
        assertEquals(3, repo.contributions("2026-W41").join().get(0).rewardTier());
        assertNull(repo.contributions("2026-W41").join().get(0).claimedAt());
        repo.markReward("2026-W41", a, 3, 99L).join();
        assertEquals(99L, repo.contributions("2026-W41").join().get(0).claimedAt());
        assertEquals("2026-W41", repo.recentCampaigns(3).join().get(0).cycleId());
    }

    @Test
    void worldFlagsUpsertAndClear() {
        repo.setFlag(new WorldFlag("effect:campaign.a", "UNLOCK_EVENT|event.x||0|0|0.0", "2026-W41", "2026-W43", 1)).join();
        repo.setFlag(new WorldFlag("effect:campaign.a", "UNLOCK_EVENT|event.y||0|0|0.0", "2026-W42", null, 2)).join();
        List<WorldFlag> flags = repo.flags().join();
        assertEquals(1, flags.size());
        assertEquals("UNLOCK_EVENT|event.y||0|0|0.0", flags.get(0).value());
        assertNull(flags.get(0).expiresCycle());
        repo.clearFlag("effect:campaign.a").join();
        assertTrue(repo.flags().join().isEmpty());
    }

    @Test
    void tallyEncodingRoundTrips() {
        Map<String, Integer> t = Map.of("campaign.a", 3, "campaign.b", 0);
        assertEquals(t, JdbcCampaignRepository.decodeTally(JdbcCampaignRepository.encodeTally(t)));
        assertTrue(JdbcCampaignRepository.decodeTally("").isEmpty());
    }
}
