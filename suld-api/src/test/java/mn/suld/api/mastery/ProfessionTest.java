package mn.suld.api.mastery;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionTest {

    @Test
    void eachCraftHasItsOwnKindOfNode() {
        EnumSet<Profession.Source> seen = EnumSet.noneOf(Profession.Source.class);
        for (Profession p : Profession.values()) {
            assertTrue(seen.add(p.source()), "one craft per source: " + p);
            assertSame(p, Profession.of(p.source()));
        }
        assertEquals(EnumSet.allOf(Profession.Source.class), seen);
    }

    @Test
    void tiersOpenAtOneThirteenTwentyFiveThirtySeven() {
        assertEquals(1, Profession.tierLevel(1));
        assertEquals(13, Profession.tierLevel(2));
        assertEquals(25, Profession.tierLevel(3));
        assertEquals(37, Profession.tierLevel(4));
        assertTrue(Profession.canGather(13, 2));
        assertFalse(Profession.canGather(12, 2));
        assertFalse(Profession.canGather(36, 4));
    }

    @Test
    void lowTiersPayLessOnceTheCraftHasOutgrownThem() {
        assertEquals(10, Profession.points(1, 1));
        assertEquals(10, Profession.points(24, 1));
        assertEquals(5, Profession.points(25, 1), "two tiers past it: half");
        assertEquals(40, Profession.points(37, 4));
        assertEquals(1, Profession.yield(1));
        assertEquals(2, Profession.yield(20));
        assertEquals(3, Profession.yield(50));
    }

    @Test
    void aTotalMakesItsLevelAndBack() {
        assertEquals(Profession.State.START, Profession.fromTotal(0));
        Profession.State s = Profession.fromTotal(Profession.need(1) + Profession.need(2) + 7);
        assertEquals(3, s.level());
        assertEquals(7, s.points());
        assertEquals(Profession.need(1) + Profession.need(2) + 7, Profession.totalOf(s));
        long all = 0;
        for (int l = 1; l < Profession.MAX_LEVEL; l++) all += Profession.need(l);
        assertEquals(243_098, all, "the doc's 243 098 points to 50");
        assertEquals(Profession.MAX_LEVEL, Profession.fromTotal(all).level());
        assertEquals(Profession.MAX_LEVEL - 1, Profession.fromTotal(all - 1).level());
    }

    @Test
    void titlesAtTenTwentyFiveAndFifty() {
        assertEquals("Төмрийн Дарханы Шавь", Profession.title(Profession.TOMRIIN_DARKHAN, 10));
        assertEquals("Эсгийчийн Мэргэжилтэн", Profession.title(Profession.ESGIICH, 25));
        assertEquals("Адуучны Мастер", Profession.title(Profession.ADUUCHIN, 50));
        assertEquals(null, Profession.title(Profession.EMIIN_BARIACH, 11));
    }

    @Test
    void levelsClimbToFiftyAndStop() {
        Profession.State s = Profession.State.START.add(Profession.need(1) + Profession.need(2));
        assertEquals(3, s.level());
        assertEquals(0, s.points());
        Profession.State top = Profession.State.START.add(Long.MAX_VALUE / 4);
        assertEquals(Profession.MAX_LEVEL, top.level());
        assertSame(top, top.add(100));
    }
}
