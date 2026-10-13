package mn.suld.api.mastery;

import mn.suld.api.item.ItemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponMasteryTest {

    @Test
    void theFiveClassWeaponKindsHaveATrackAndNothingElseDoes() {
        assertEquals(WeaponMastery.Track.SCIMITAR, WeaponMastery.Track.of(ItemType.SWORD).orElseThrow());
        assertEquals(WeaponMastery.Track.COMPOSITE_BOW, WeaponMastery.Track.of(ItemType.BOW).orElseThrow());
        assertEquals(WeaponMastery.Track.SHAMANIC_STAFF, WeaponMastery.Track.of(ItemType.STAFF).orElseThrow());
        assertEquals(WeaponMastery.Track.HAMMER, WeaponMastery.Track.of(ItemType.AXE).orElseThrow());
        assertEquals(WeaponMastery.Track.LANCE, WeaponMastery.Track.of(ItemType.SPEAR).orElseThrow());
        for (ItemType t : ItemType.values()) {
            if (t.category() != ItemType.Category.WEAPON) assertTrue(WeaponMastery.Track.of(t).isEmpty(), t + " has no track");
        }
    }

    @Test
    void damageCountsInTheirOwnBasicHitsSoEveryLevelWeighsTheSame() {
        assertEquals(3.0, WeaponMastery.points(300, 100), 1e-9);
        assertEquals(WeaponMastery.points(30, 10), WeaponMastery.points(3000, 1000), 1e-9);
        assertEquals(0, WeaponMastery.points(-5, 100));
        assertEquals(0, WeaponMastery.points(50, 0));
    }

    @Test
    void levelsClimbToFiftyAndStopThere() {
        WeaponMastery.State s = WeaponMastery.State.START.add(WeaponMastery.need(1));
        assertEquals(2, s.level());
        assertEquals(0, s.points(), 1e-9);
        WeaponMastery.State many = WeaponMastery.State.START.add(WeaponMastery.need(1) + WeaponMastery.need(2) + 5);
        assertEquals(3, many.level(), "several levels at once");
        assertEquals(5, many.points(), 1e-9);
        double all = 0;
        for (int l = 1; l < WeaponMastery.MAX_LEVEL; l++) all += WeaponMastery.need(l);
        assertTrue(all > 200_000 && all < 350_000, "a long road: " + all + " basic hits to 50");
        WeaponMastery.State top = WeaponMastery.State.START.add(all * 2);
        assertEquals(WeaponMastery.MAX_LEVEL, top.level());
        assertEquals(0, top.points());
        assertSame(top, top.add(1000), "no points past 50");
        assertEquals(1.0, top.progress());
    }

    @Test
    void theStoredTotalAndTheStateAreTheSame() {
        for (double total : new double[]{0, 1, 39.5, 40, 41, 1234.5, 99_999, 10_000_000}) {
            WeaponMastery.State s = WeaponMastery.fromTotal(total);
            if (s.level() < WeaponMastery.MAX_LEVEL) assertEquals(total, WeaponMastery.totalOf(s), 1e-6, "total " + total);
            else assertEquals(WeaponMastery.MAX_LEVEL, s.level());
        }
        // adding in pieces or at once ends at the same state (the storage adds deltas in any order)
        WeaponMastery.State pieces = WeaponMastery.State.START;
        for (int i = 0; i < 500; i++) pieces = pieces.add(7.25);
        assertEquals(WeaponMastery.fromTotal(500 * 7.25), pieces);
    }

    @Test
    void theInMemoryStoreOnlyAdds() {
        var repo = new mn.suld.api.persistence.InMemoryWeaponMasteryRepository();
        java.util.UUID p = java.util.UUID.randomUUID();
        repo.add(p, java.util.Map.of(WeaponMastery.Track.SCIMITAR, 10.0), 1).join();
        repo.add(p, java.util.Map.of(WeaponMastery.Track.SCIMITAR, 2.5, WeaponMastery.Track.LANCE, 4.0), 2).join();
        repo.add(p, java.util.Map.of(WeaponMastery.Track.SCIMITAR, -3.0), 3).join(); // nothing is taken away
        assertEquals(12.5, repo.totals(p).join().get(WeaponMastery.Track.SCIMITAR), 1e-9);
        assertEquals(4.0, repo.totals(p).join().get(WeaponMastery.Track.LANCE), 1e-9);
    }

    @Test
    void thePowerIsSmallAndTheTitlesComeAtTenTwentyFiveFifty() {
        assertEquals(0.025, WeaponMastery.damageBonus(50), 1e-12);
        assertEquals(0.025, WeaponMastery.damageBonus(99), 1e-12, "capped at 50");
        assertEquals("Илдний Шавь", WeaponMastery.title(WeaponMastery.Track.SCIMITAR, 10));
        assertEquals("Нумны Мастер", WeaponMastery.title(WeaponMastery.Track.COMPOSITE_BOW, 50));
        assertNull(WeaponMastery.title(WeaponMastery.Track.LANCE, 11));
    }
}
