package mn.suld.api.clan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClanProgressionTest {

    @Test
    void curveIsMonotonicAndCapped() {
        assertEquals(0, ClanProgression.totalExpFor(1));
        assertEquals(400, ClanProgression.totalExpFor(2));
        for (int l = 2; l <= ClanProgression.MAX_LEVEL; l++) {
            assertTrue(ClanProgression.totalExpFor(l) > ClanProgression.totalExpFor(l - 1));
        }
        assertEquals(ClanProgression.MAX_LEVEL, ClanProgression.levelFor(Long.MAX_VALUE / 2));
        assertEquals(0, ClanProgression.expToNext(Long.MAX_VALUE / 2));
    }

    @Test
    void levelBoundaries() {
        assertEquals(1, ClanProgression.levelFor(399));
        assertEquals(2, ClanProgression.levelFor(400));
        assertEquals(1, ClanProgression.expToNext(399));
    }

    @Test
    void perks() {
        assertEquals(10, ClanProgression.capacity(1));
        assertEquals(28, ClanProgression.capacity(10));
        assertEquals(28, ClanProgression.capacity(99), "clamped");
        assertEquals(0.0, ClanProgression.expBonus(1));
        assertEquals(0.18, ClanProgression.expBonus(10), 1e-9);
    }
}
