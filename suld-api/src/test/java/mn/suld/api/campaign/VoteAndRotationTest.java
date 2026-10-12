package mn.suld.api.campaign;

import mn.suld.api.campaign.vote.VoteRules;
import mn.suld.api.campaign.vote.VoteTally;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteAndRotationTest {

    private static CampaignDefinition arc(String id, String... requires) {
        return new CampaignDefinition(id, id, "", new CampaignGoal(CampaignGoal.Type.KILL_MOBS, Set.of("mob.x"), 10000), new int[]{10, 20, 30},
                List.of(new CampaignDefinition.TierReward(1, 1), new CampaignDefinition.TierReward(2, 2), new CampaignDefinition.TierReward(3, 3)),
                new double[]{1.5, 1.3, 1.2}, 0, 0, null, 1, List.of(requires));
    }

    @org.junit.jupiter.api.Test
    void anArcChapterStandsOnlyAfterItsPrerequisitesAndThenLeads() {
        List<CampaignDefinition> all = List.of(arc("campaign.a"), arc("campaign.b"), arc("campaign.c"),
                arc("campaign.next", "campaign.a"), arc("campaign.finale", "campaign.next", "campaign.b"));
        for (int w = 0; w < 30; w++) { // before anything succeeded: the chapters never stand
            List<String> ids = CampaignRotation.pick(all, List.of(), List.of(), 3, "2026-W" + w, Set.of()).stream().map(CampaignDefinition::id).toList();
            org.junit.jupiter.api.Assertions.assertFalse(ids.contains("campaign.next") || ids.contains("campaign.finale"), ids.toString());
        }
        // a won: next opens and leads (a is resting as the last winner)
        List<String> after = CampaignRotation.pick(all, List.of("campaign.a"), List.of(), 3, "2026-W40", Set.of("campaign.a"))
                .stream().map(CampaignDefinition::id).toList();
        org.junit.jupiter.api.Assertions.assertEquals("campaign.next", after.get(0), after.toString());
        org.junit.jupiter.api.Assertions.assertFalse(after.contains("campaign.finale"), "finale still needs next and b");
        // both prerequisites done: the finale stands
        List<String> end = CampaignRotation.pick(all, List.of("campaign.b"), List.of(), 3, "2026-W45", Set.of("campaign.a", "campaign.next", "campaign.b"))
                .stream().map(CampaignDefinition::id).toList();
        org.junit.jupiter.api.Assertions.assertEquals("campaign.finale", end.get(0), end.toString());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> arc("campaign.self", "campaign.self"));
    }

    private static CampaignDefinition def(String id, int cooldown) {
        return new CampaignDefinition(id, id, "", new CampaignGoal(CampaignGoal.Type.KILL_MOBS, Set.of("mob.x"), 10000), new int[]{10, 20, 30},
                List.of(new CampaignDefinition.TierReward(1, 1), new CampaignDefinition.TierReward(2, 2), new CampaignDefinition.TierReward(3, 3)),
                new double[]{1.5, 1.3, 1.2}, 0, 0, null, cooldown);
    }

    private static List<String> ids(List<CampaignDefinition> l) {
        return l.stream().map(CampaignDefinition::id).toList();
    }

    @Test
    void eligibilityGates() {
        assertEquals(Optional.of(VoteRules.Denial.NO_CLASS), VoteRules.eligible(false, 60, 5, 100, 1));
        assertEquals(Optional.of(VoteRules.Denial.LEVEL_TOO_LOW), VoteRules.eligible(true, 4, 5, 100, 1));
        assertEquals(Optional.of(VoteRules.Denial.ACCOUNT_TOO_NEW), VoteRules.eligible(true, 5, 5, 0, 1));
        assertTrue(VoteRules.eligible(true, 5, 5, 1, 1).isEmpty());
        assertFalse(VoteRules.reason(VoteRules.Denial.LEVEL_TOO_LOW, 5, 1).isBlank());
    }

    @Test
    void tallyPicksTheMostVotesAndBreaksTiesByFewestRecentWins() {
        List<String> ballot = List.of("campaign.a", "campaign.b", "campaign.c");
        assertEquals(Optional.of("campaign.b"), VoteTally.winner(ballot, Map.of("campaign.a", 2, "campaign.b", 5, "campaign.c", 1), 3, Map.of()));
        assertEquals(Optional.empty(), VoteTally.winner(ballot, Map.of("campaign.a", 2), 3, Map.of()), "below the quorum");
        assertEquals(Optional.of("campaign.a"), VoteTally.winner(ballot, Map.of("campaign.a", 3, "campaign.b", 3), 0, Map.of()), "tie: ballot order");
        assertEquals(Optional.of("campaign.b"), VoteTally.winner(ballot, Map.of("campaign.a", 3, "campaign.b", 3), 0, Map.of("campaign.a", 1)),
                "tie: the one that won less recently");
        assertEquals(6, VoteTally.voters(ballot, Map.of("campaign.a", 3, "campaign.b", 3, "campaign.zzz", 9)));
    }

    @Test
    void rotationRestsWinnersAndBringsLosersBack() {
        List<CampaignDefinition> all = List.of(def("campaign.a", 2), def("campaign.b", 1), def("campaign.c", 1), def("campaign.d", 1), def("campaign.e", 1));
        List<CampaignDefinition> b1 = CampaignRotation.pick(all, List.of(), List.of(), 3, "2026-W41");
        assertEquals(3, b1.size());
        assertEquals(b1, CampaignRotation.pick(all, List.of(), List.of(), 3, "2026-W41"), "deterministic for a cycle");
        assertNotEquals(ids(b1), ids(CampaignRotation.pick(all, List.of(), List.of(), 3, "2026-W42")), "another week shuffles differently");

        List<CampaignDefinition> b2 = CampaignRotation.pick(all, List.of("campaign.a"), List.of("campaign.b", "campaign.c"), 3, "2026-W42");
        assertFalse(ids(b2).contains("campaign.a"), "the last winner rests");
        assertEquals(List.of("campaign.b", "campaign.c"), ids(b2).subList(0, 2), "last week's losers lead the ballot");

        List<CampaignDefinition> b3 = CampaignRotation.pick(all, List.of("campaign.b", "campaign.a"), List.of(), 3, "2026-W43");
        assertFalse(ids(b3).contains("campaign.a"), "cooldown 2: a rests a second week");
        assertFalse(ids(b3).contains("campaign.b"));
        List<CampaignDefinition> b4 = CampaignRotation.pick(all, List.of("campaign.c", "campaign.b", "campaign.a"), List.of(), 3, "2026-W44");
        assertTrue(ids(b4).contains("campaign.a"), "after its cooldown a runs again");
        assertTrue(ids(b4).contains("campaign.b"), "cooldown 1: b is back after one week");
        assertFalse(ids(b4).contains("campaign.c"));
    }

    @Test
    void rotationRelaxesWhenThereAreFewCampaigns() {
        List<CampaignDefinition> three = List.of(def("campaign.a", 3), def("campaign.b", 3), def("campaign.c", 3));
        List<CampaignDefinition> b = CampaignRotation.pick(three, List.of("campaign.a", "campaign.b"), List.of(), 3, "2026-W41");
        assertEquals(2, b.size(), "with cooldowns relaxed only the last winner rests, leaving two");
        assertFalse(ids(b).contains("campaign.a"));
        List<CampaignDefinition> two = CampaignRotation.pick(three.subList(0, 2), List.of("campaign.a"), List.of(), 3, "2026-W41");
        assertEquals(2, two.size(), "two campaigns: both always run");
    }
}
