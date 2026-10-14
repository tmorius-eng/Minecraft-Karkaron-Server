package mn.suld.api.battlepass;

import mn.suld.api.achievement.Deed;
import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.CosmeticCatalog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignPassTest {

    @Test
    void tiersAreAThousandPointsEachUpToFifty() {
        assertEquals(0, CampaignPass.tier(999));
        assertEquals(1, CampaignPass.tier(1000));
        assertEquals(50, CampaignPass.tier(50_000));
        assertEquals(50, CampaignPass.tier(1_000_000), "no tier past the last");
        assertEquals(1, CampaignPass.toNext(999));
        assertEquals(1000, CampaignPass.toNext(1000));
        assertEquals(0, CampaignPass.toNext(50_000));
    }

    @Test
    void aRewardIsClaimedOnceWhenReachedAndTheHeroicOnesOnlyOnceOpen() {
        CampaignPass.Layout l = new CampaignPass.Layout(List.of(new CampaignPass.Reward(1, CampaignPass.Track.HEROIC, "tag.a"),
                new CampaignPass.Reward(5, CampaignPass.Track.FREE, "tag.b"), new CampaignPass.Reward(5, CampaignPass.Track.HEROIC, "tag.c")));
        assertEquals(List.of(), CampaignPass.claimable(l, 999, true, Set.of()));
        assertEquals(List.of(), CampaignPass.claimable(l, 4_999, false, Set.of()), "tier 4, the heroic track closed");
        assertEquals(List.of("tag.a"), CampaignPass.claimable(l, 4_999, true, Set.of()).stream().map(CampaignPass.Reward::cosmetic).toList());
        assertEquals(List.of("tag.b"), CampaignPass.claimable(l, 5_000, false, Set.of()).stream().map(CampaignPass.Reward::cosmetic).toList());
        assertEquals(List.of("tag.a", "tag.c"), CampaignPass.claimable(l, 5_000, true, Set.of("FREE/5")).stream().map(CampaignPass.Reward::cosmetic).toList());
        assertThrows(IllegalArgumentException.class, () -> new CampaignPass.Layout(List.of(new CampaignPass.Reward(5, CampaignPass.Track.FREE, "tag.b"),
                new CampaignPass.Reward(5, CampaignPass.Track.FREE, "tag.c"))), "two rewards on one tier of a track");
        assertThrows(IllegalArgumentException.class, () -> new CampaignPass.Reward(51, CampaignPass.Track.FREE, "tag.b"));
    }

    @Test
    void theBundledLayoutGivesOnlyThePasssOwnCosmetics() {
        CampaignPass.Layout l = PassLayout.bundled();
        Set<String> seen = new HashSet<>();
        for (CampaignPass.Reward r : l.rewards()) {
            Cosmetic c = CosmeticCatalog.byId(r.cosmetic()).orElse(null);
            assertTrue(c != null, r + ": no such cosmetic");
            assertEquals("pass", c.source(), r.cosmetic() + " is the pass's own, never sold");
            assertEquals(0, c.creditPrice());
            assertTrue(seen.add(r.cosmetic()), r.cosmetic() + " twice");
        }
        for (Cosmetic c : CosmeticCatalog.ALL) if ("pass".equals(c.source())) assertTrue(seen.contains(c.id()), c.id() + " is on no tier");
        assertTrue(l.at(CampaignPass.Track.FREE, 50).isPresent() && l.at(CampaignPass.Track.HEROIC, 50).isPresent(), "both tracks end with a reward");
    }

    @Test
    void deedsGivePointsUnderADailyCap() {
        assertEquals(120, CampaignPass.points(Deed.DUNGEON_CLEAR, 1));
        assertEquals(100, CampaignPass.points(Deed.LEVEL, 2));
        assertEquals(0, CampaignPass.points(Deed.MOB_KILL, 0));
        assertEquals(500, CampaignPass.underCap(0, 500));
        assertEquals(100, CampaignPass.underCap(CampaignPass.DAILY_CAP - 100, 500));
        assertEquals(0, CampaignPass.underCap(CampaignPass.DAILY_CAP, 500));
        for (Deed d : Deed.values()) assertTrue(CampaignPass.points(d, 1) >= 0, d.name());
    }

    @Test
    void aSeasonIsEightWeeksFromItsEpochMonday() {
        LocalDate epoch = LocalDate.of(2026, 10, 7); // a Wednesday: its week's Monday is the 5th
        Season s = Season.of(LocalDate.of(2026, 10, 13), epoch, 8);
        assertEquals(1, s.number());
        assertEquals(LocalDate.of(2026, 10, 5), s.start());
        assertEquals(LocalDate.of(2026, 11, 29), s.end());
        assertEquals("S1", s.id());
        Season next = Season.of(LocalDate.of(2026, 11, 30), epoch, 8);
        assertEquals(2, next.number());
        assertEquals(LocalDate.of(2026, 11, 30), next.start());
        assertEquals(0, Season.of(LocalDate.of(2026, 10, 4), epoch, 8).number(), "before the first season");
        assertEquals(0, s.daysLeft(LocalDate.of(2026, 11, 29)));
    }
}
