package mn.suld.api.campaign;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignRunTest {

    static CampaignDefinition wolves() {
        return new CampaignDefinition("campaign.chonyn_ayan", "Чонын Их Аян", "", new CampaignGoal(CampaignGoal.Type.KILL_MOBS,
                Set.of("mob.goviin_chono", "mob.orkhon_chono"), 3000), new int[]{30, 90, 150},
                List.of(new CampaignDefinition.TierReward(400, 80), new CampaignDefinition.TierReward(1200, 200), new CampaignDefinition.TierReward(3000, 450)),
                new double[]{1.5, 1.3, 1.2}, 2, 1500, WorldEffect.unlockEvent("event.chonyn_dovtolgoo"), 2);
    }

    static CampaignDefinition tomb() {
        return new CampaignDefinition("campaign.bulshnii_tsever", "Булшны Цэвэрлэгээ", "", new CampaignGoal(CampaignGoal.Type.CLEAR_DUNGEON,
                Set.of("dungeon.govi_bulsh"), 60), new int[]{1, 2, 3},
                List.of(new CampaignDefinition.TierReward(400, 80), new CampaignDefinition.TierReward(1200, 200), new CampaignDefinition.TierReward(3000, 450)),
                new double[]{1.5, 1.3, 1.2}, 50, 1500, null, 1);
    }

    private final Instant t0 = Instant.parse("2026-10-05T00:00:00Z");

    @Test
    void definitionGuardsItsNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new CampaignDefinition("campaign.x", "x", "", new CampaignGoal(CampaignGoal.Type.KILL_MOBS, Set.of("m"), 100),
                new int[]{30, 90, 150}, wolves().tierRewards(), new double[]{1.5, 1.3, 1.2}, 0, 0, null, 0), "gold above 5 % of the target");
        assertThrows(IllegalArgumentException.class, () -> new CampaignDefinition("campaign.x", "x", "", new CampaignGoal(CampaignGoal.Type.KILL_MOBS, Set.of("m"), 10000),
                new int[]{90, 30, 150}, wolves().tierRewards(), new double[]{1.5, 1.3, 1.2}, 0, 0, null, 0), "tiers must ascend");
        assertThrows(IllegalArgumentException.class, () -> new CampaignDefinition("wolves", "x", "", wolves().goal(), new int[]{30, 90, 150},
                wolves().tierRewards(), new double[]{1.5, 1.3, 1.2}, 0, 0, null, 0), "id prefix");
        assertEquals(2, wolves().tierOf(100));
        assertEquals(0, wolves().tierOf(29));
        assertEquals(3, wolves().tierOf(999));
        assertEquals(1.0, wolves().podiumMultiplier(4));
    }

    @Test
    void onlyMatchingUnitsCountAndSuccessFlipsOnce() {
        CampaignRun run = new CampaignRun(wolves(), "2026-W41", t0, t0.plusSeconds(6 * 86400));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), clan = UUID.randomUUID();
        assertTrue(run.record(a, "A", clan, CampaignGoal.Type.KILL_MOBS, "mob.goviin_chono", 1, null, t0));
        assertFalse(run.record(a, "A", clan, CampaignGoal.Type.KILL_MOBS, "mob.bear", 1, null, t0), "not a target mob");
        assertFalse(run.record(a, "A", clan, CampaignGoal.Type.CLEAR_DUNGEON, "mob.goviin_chono", 1, null, t0), "wrong source");
        assertTrue(run.record(b, "B", null, CampaignGoal.Type.KILL_MOBS, "mob.orkhon_chono", 2998, null, t0.plusSeconds(10)));
        assertTrue(run.isActive());
        assertEquals(2999, run.total());
        assertTrue(run.record(a, "A", clan, CampaignGoal.Type.KILL_MOBS, "mob.goviin_chono", 1, null, t0.plusSeconds(20)));
        assertEquals(CampaignState.SUCCEEDED, run.state());
        assertFalse(run.record(a, "A", clan, CampaignGoal.Type.KILL_MOBS, "mob.goviin_chono", 1, null, t0.plusSeconds(30)), "nothing counts after success");
        assertEquals(3000, run.total());
        assertEquals(1.0, run.progress());
        List<Contribution> rank = run.ranking();
        assertEquals("B", rank.get(0).name());
        assertEquals(2998, rank.get(0).units());
        assertEquals(Map.of(clan, 2L), run.clanUnits());
        assertEquals(1, run.participants(), "only B reached tier 1 (30 units)");
    }

    @Test
    void dedupeKeysCountOncePerPlayer() {
        CampaignRun run = new CampaignRun(tomb(), "2026-W41", t0, t0.plusSeconds(6 * 86400));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(run.record(a, "A", null, CampaignGoal.Type.CLEAR_DUNGEON, "dungeon.govi_bulsh", 1, "run-1", t0));
        assertFalse(run.record(a, "A", null, CampaignGoal.Type.CLEAR_DUNGEON, "dungeon.govi_bulsh", 1, "run-1", t0), "the same run credited twice");
        assertTrue(run.record(b, "B", null, CampaignGoal.Type.CLEAR_DUNGEON, "dungeon.govi_bulsh", 1, "run-1", t0), "each party member once");
        assertTrue(run.record(a, "A", null, CampaignGoal.Type.CLEAR_DUNGEON, "dungeon.govi_bulsh", 1, "run-2", t0));
        assertEquals(3, run.total());
        assertEquals(2, run.tierOf(a));
    }

    @Test
    void expiryAndRestore() {
        CampaignRun run = new CampaignRun(wolves(), "2026-W41", t0, t0.plusSeconds(100));
        UUID a = UUID.randomUUID();
        assertTrue(run.record(a, "A", null, CampaignGoal.Type.KILL_MOBS, "mob.goviin_chono", 40, null, t0));
        assertFalse(run.expireIfDue(t0.plusSeconds(99)));
        assertFalse(run.record(a, "A", null, CampaignGoal.Type.KILL_MOBS, "mob.goviin_chono", 1, null, t0.plusSeconds(100)), "expired on the dot");
        assertEquals(CampaignState.FAILED, run.state());
        assertFalse(run.expireIfDue(t0.plusSeconds(200)), "only the transition returns true");

        CampaignSnapshot row = run.snapshot(3, null, null);
        assertEquals(40, row.total());
        assertEquals(3, row.version());
        CampaignRun back = CampaignRun.restore(wolves(), new CampaignSnapshot("2026-W41", "campaign.chonyn_ayan", CampaignState.ACTIVE,
                t0.toEpochMilli(), t0.plusSeconds(100).toEpochMilli(), null, 3000, 40, null, 3),
                List.of(new Contribution(a, "A", null, 40, null, null)));
        assertTrue(back.isActive());
        assertEquals(40, back.unitsOf(a));
        assertEquals(1, back.tierOf(a));
        assertEquals(40, back.total());
    }
}
