package mn.suld.api.balance;

import mn.suld.api.mastery.WeaponMastery;
import mn.suld.api.dungeon.heroic.HeroicLadder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Тэнгэрийн Зэрэг IV–X: prestige after III, small power (owner 2026-10-13: «бага хүч + нэр хүнд»). */
class AscensionHonorTest {

    @Test
    void theRanksClimbFromFourToTenAndEveryGateOnlyGrows() {
        assertEquals(4, AscensionHonor.FIRST);
        assertEquals(7, AscensionHonor.Rank.values().length);
        AscensionHonor.Rank prev = null;
        for (AscensionHonor.Rank r : AscensionHonor.Rank.values()) {
            assertEquals(r, AscensionHonor.Rank.of(r.number()));
            assertNotNull(r.title());
            if (prev != null) {
                assertEquals(prev.number() + 1, r.number());
                assertTrue(r.stars() > prev.stars() && r.heroicKey() > prev.heroicKey() && r.weaponMastery() > prev.weaponMastery(), r.name());
            }
            assertTrue(r.heroicKey() <= HeroicLadder.MAX_KEY && r.weaponMastery() <= WeaponMastery.MAX_LEVEL, r.name());
            prev = r;
        }
        assertEquals(10, prev.number());
    }

    @Test
    void theStarsFollowTheOnooTheProposedDesignChargedForIvToX() {
        // proposed: rank r cost Ascension.cost(c, r - 1) = 0.6 · need(59) · r; a star is Ascension.starCost(c), the cost of IV
        var c = Balance.curve();
        long spent = 0;
        for (AscensionHonor.Rank r : AscensionHonor.Rank.values()) {
            spent += Ascension.cost(c, r.number() - 1);
            long stars = (spent + Ascension.starCost(c) - 1) / Ascension.starCost(c);
            assertEquals(stars, r.stars(), r.name() + ": " + spent + " оноо");
        }
        assertEquals(13, AscensionHonor.Rank.X.stars());
    }

    @Test
    void onlyAfterThreeAndNoHigherThanTen() {
        AscensionHonor.Inputs all = new AscensionHonor.Inputs(2, 99, 20, 50);
        assertEquals("Эхлээд Тэнгэрийн Зэрэг III.", AscensionHonor.blocked(all));
        assertNull(AscensionHonor.next(2));
        assertEquals(AscensionHonor.Rank.IV, AscensionHonor.next(3));
        assertNull(AscensionHonor.next(10));
        assertEquals(Ascension.Outcome.MAX_RANK, AscensionHonor.rite(new AscensionHonor.Inputs(10, 99, 20, 50), 10_000_000).outcome());
    }

    @Test
    void theRiteNeedsEveryGateThenTakesOnlyTheFee() {
        AscensionHonor.Inputs short1 = new AscensionHonor.Inputs(3, 0, 4, 10);
        AscensionHonor.Rite r = AscensionHonor.rite(short1, 1_000_000);
        assertEquals(Ascension.Outcome.GATED, r.outcome());
        assertTrue(r.reason().startsWith("Тэнгэрийн Од 1"), r.reason());
        assertEquals(1_000_000, r.coins(), "nothing is spent on a refusal");

        AscensionHonor.Inputs ready = new AscensionHonor.Inputs(3, 1, 4, 10);
        assertNull(AscensionHonor.blocked(ready));
        assertEquals(Ascension.Outcome.NOT_ENOUGH_COINS, AscensionHonor.rite(ready, 99_999).outcome());
        AscensionHonor.Rite done = AscensionHonor.rite(ready, 150_000);
        assertEquals(Ascension.Outcome.DONE, done.outcome());
        assertEquals(4, done.rank());
        assertEquals(50_000, done.coins(), "IV costs 100 000, the I–III rule continued");
        assertEquals(250_000, AscensionHonor.riteCoins(9), "X costs 250 000");

        // each gate alone holds the rank
        assertFalse(AscensionHonor.gates(new AscensionHonor.Inputs(3, 1, 3, 10)).stream().allMatch(AscensionHonor.Gate::met));
        assertFalse(AscensionHonor.gates(new AscensionHonor.Inputs(3, 1, 4, 9)).stream().allMatch(AscensionHonor.Gate::met));
    }

    @Test
    void thePerksAddUpAndStaySmall() {
        assertEquals(1.0, AscensionHonor.pvpTaken(3));
        assertEquals(0.97, AscensionHonor.pvpTaken(4), 1e-12);
        assertEquals(0, AscensionHonor.fortune(4));
        assertEquals(0.03, AscensionHonor.fortune(5), 1e-12);
        assertEquals(0, AscensionHonor.lightningChance(5));
        assertEquals(0.02, AscensionHonor.lightningChance(6), 1e-12);
        // a rank keeps every perk below it
        assertEquals(0.97, AscensionHonor.pvpTaken(10), 1e-12);
        assertEquals(0.02, AscensionHonor.lightningChance(10), 1e-12);
        assertTrue(AscensionHonor.has(7, AscensionHonor.Perk.AURA) && !AscensionHonor.has(6, AscensionHonor.Perk.AURA));
        assertTrue(AscensionHonor.has(10, AscensionHonor.Perk.MONUMENT) && !AscensionHonor.has(9, AscensionHonor.Perk.MONUMENT));
        // the back aura brightens with VII, VIII and IX, and X keeps it
        assertEquals(0, AscensionHonor.auraTier(6));
        assertEquals(1, AscensionHonor.auraTier(7));
        assertEquals(2, AscensionHonor.auraTier(8));
        assertEquals(3, AscensionHonor.auraTier(9));
        assertEquals(3, AscensionHonor.auraTier(10));
        // the owner's line: IV–X add at most 3 % of power against mobs (here 1 %), and no skill point or flat power
        assertTrue(AscensionHonor.MAX_PVE_POWER <= 0.03, "" + AscensionHonor.MAX_PVE_POWER);
        assertEquals(Ascension.powerBonus(3), Ascension.powerBonus(10), 1e-12, "the +1 % a rank stops at III");
    }

    @Test
    void romanNumeralsReachTen() {
        assertEquals("IV", Ascension.roman(4));
        assertEquals("IX", Ascension.roman(9));
        assertEquals("X", Ascension.roman(10));
        assertEquals("—", Ascension.roman(11));
    }
}
