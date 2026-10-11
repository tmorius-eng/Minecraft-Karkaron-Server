package mn.suld.api.persistence;

import mn.suld.api.campaign.CampaignSnapshot;
import mn.suld.api.campaign.CampaignState;
import mn.suld.api.campaign.Contribution;
import mn.suld.api.campaign.WorldFlag;
import mn.suld.api.campaign.vote.VoteCycle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The contract the JDBC repository is held to as well (JdbcCampaignRepositoryIT). */
class InMemoryCampaignRepositoryTest {

    @Test
    void oneBallotPerPlayerAndCasClose() {
        InMemoryCampaignRepository r = new InMemoryCampaignRepository();
        UUID p = UUID.randomUUID();
        assertEquals(CampaignRepository.CastResult.NO_CYCLE, r.castVote("2026-W41", p, "campaign.a", 10, 1).join());
        VoteCycle c = r.openVote("2026-W41", List.of("campaign.a", "campaign.b"), 0, 100).join();
        assertEquals(c, r.openVote("2026-W41", List.of("campaign.zzz"), 5, 6).join(), "opening twice keeps the first ballot");
        assertEquals(CampaignRepository.CastResult.NO_SUCH_OPTION, r.castVote("2026-W41", p, "campaign.c", 10, 1).join());
        assertEquals(CampaignRepository.CastResult.CAST, r.castVote("2026-W41", p, "campaign.a", 10, 1).join());
        assertEquals(CampaignRepository.CastResult.ALREADY_VOTED, r.castVote("2026-W41", p, "campaign.b", 10, 2).join());
        assertEquals(Map.of("campaign.a", 1), r.tally("2026-W41").join());
        assertFalse(r.closeVote("2026-W41", "campaign.a", Map.of("campaign.a", 1), 50, 7).join(), "wrong version");
        assertTrue(r.closeVote("2026-W41", "campaign.a", Map.of("campaign.a", 1), 50, 0).join());
        assertFalse(r.closeVote("2026-W41", "campaign.b", Map.of(), 51, 1).join(), "closed once");
        assertEquals(CampaignRepository.CastResult.CLOSED, r.castVote("2026-W41", UUID.randomUUID(), "campaign.a", 10, 3).join());
        VoteCycle closed = r.voteCycle("2026-W41").join().orElseThrow();
        assertEquals("campaign.a", closed.winner());
        assertEquals(List.of("campaign.b"), closed.losers());
        assertEquals(1, closed.voters());
        assertEquals("2026-W41", r.recentVotes(5).join().get(0).cycleId());
    }

    @Test
    void campaignRowsContributionsAndFlags() {
        InMemoryCampaignRepository r = new InMemoryCampaignRepository();
        CampaignSnapshot row = new CampaignSnapshot("2026-W41", "campaign.a", CampaignState.ACTIVE, 0, 100, null, 3000, 0, null, 0);
        assertTrue(r.saveCampaign(row, -1).join());
        assertFalse(r.saveCampaign(row, -1).join(), "insert once");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        r.addContributions("2026-W41", List.of(Contribution.delta(a, "A", null, 5), Contribution.delta(b, "B", null, 2))).join();
        r.addContributions("2026-W41", List.of(Contribution.delta(a, "A", null, 1))).join();
        assertEquals(8, r.campaign("2026-W41").join().orElseThrow().total());
        List<Contribution> t = r.contributions("2026-W41").join();
        assertEquals("A", t.get(0).name());
        assertEquals(6, t.get(0).units());
        assertFalse(r.saveCampaign(row.withState(CampaignState.SUCCEEDED, 90), 5).join(), "CAS");
        assertTrue(r.saveCampaign(row.withState(CampaignState.SUCCEEDED, 90), 0).join());
        CampaignSnapshot after = r.campaign("2026-W41").join().orElseThrow();
        assertEquals(CampaignState.SUCCEEDED, after.state());
        assertEquals(8, after.total(), "a state change never overwrites the flushed total");
        assertEquals(1, after.version());
        r.markReward("2026-W41", a, 2, null).join();
        assertEquals(2, r.contributions("2026-W41").join().get(0).rewardTier());
        r.setFlag(new WorldFlag("effect:campaign.a", "UNLOCK_EVENT|event.x||0|0|0.0", "2026-W41", "2026-W43", 1)).join();
        assertTrue(r.flags().join().get(0).liveIn("2026-W42"));
        assertFalse(r.flags().join().get(0).liveIn("2026-W43"));
        r.clearFlag("effect:campaign.a").join();
        assertTrue(r.flags().join().isEmpty());
    }
}
