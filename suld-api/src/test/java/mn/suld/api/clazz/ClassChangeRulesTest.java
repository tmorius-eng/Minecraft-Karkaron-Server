package mn.suld.api.clazz;

import org.junit.jupiter.api.Test;

import static mn.suld.api.clazz.ClassChangeRules.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassChangeRulesTest {

    static final long NOW = 1_000_000_000_000L;

    static Request req(int level, long minutes, long coins, int seals, long lastChange) {
        return new Request(PlayerClass.BAATAR, PlayerClass.MERGEN, level, minutes, coins, seals, NOW, lastChange, 0,
                true, false, false, false, false, false, 10);
    }

    @Test
    void graceIsFreeWithoutCooldown() {
        Verdict v = check(req(10, 180, 0, 0, NOW - 1000));
        assertTrue(v.allowed());
        assertEquals(Payment.GRACE, v.payment());
        assertEquals(0, v.price());
    }

    @Test
    void graceEndsAtLevel11OrAfter180Minutes() {
        assertFalse(grace(11, 10));
        assertFalse(grace(5, 181));
        assertTrue(grace(1, 0));
    }

    @Test
    void afterGraceItCostsLevelTimes250() {
        Verdict v = check(req(20, 600, 5000, 0, 0));
        assertTrue(v.allowed());
        assertEquals(Payment.COINS, v.payment());
        assertEquals(5000, v.price());
        assertEquals(Refusal.NO_COINS, check(req(20, 600, 4999, 0, 0)).refusal());
    }

    @Test
    void sixHourCooldownUnlessASealIsUsed() {
        long recent = NOW - COOLDOWN_MS + 60_000;
        Verdict v = check(req(30, 900, 1_000_000, 0, recent));
        assertEquals(Refusal.COOLDOWN, v.refusal());
        assertEquals(60_000, v.cooldownLeftMs());
        assertEquals(Payment.SEAL, check(req(30, 900, 0, 1, recent)).payment());
        assertTrue(check(req(30, 900, 1_000_000, 0, NOW - COOLDOWN_MS)).allowed());
    }

    @Test
    void hardRestrictionsHaveNoExceptionsEvenInGraceOrWithASeal() {
        Request base = req(5, 10, 0, 3, 0);
        assertEquals(Refusal.NOT_IN_CITY, check(new Request(base.from(), base.to(), 5, 10, 0, 3, NOW, 0, 0, false, false, false, false, false, false, 10)).refusal());
        assertEquals(Refusal.IN_COMBAT, check(new Request(base.from(), base.to(), 5, 10, 0, 3, NOW, 0, NOW - 59_000, true, false, false, false, false, false, 10)).refusal());
        assertTrue(check(new Request(base.from(), base.to(), 5, 10, 0, 3, NOW, 0, NOW - 60_000, true, false, false, false, false, false, 10)).allowed());
        assertEquals(Refusal.IN_DUNGEON, check(new Request(base.from(), base.to(), 5, 10, 0, 3, NOW, 0, 0, true, true, false, false, false, false, 10)).refusal());
        assertEquals(Refusal.IN_DUEL, check(new Request(base.from(), base.to(), 5, 10, 0, 3, NOW, 0, 0, true, false, true, false, false, false, 10)).refusal());
        assertEquals(Refusal.IN_WORLD_EVENT, check(new Request(base.from(), base.to(), 5, 10, 0, 3, NOW, 0, 0, true, false, false, true, false, false, 10)).refusal());
        assertEquals(Refusal.SOUL, check(new Request(base.from(), base.to(), 5, 10, 0, 3, NOW, 0, 0, true, false, false, false, true, false, 10)).refusal());
        assertEquals(Refusal.DEAD, check(new Request(base.from(), base.to(), 5, 10, 0, 3, NOW, 0, 0, true, false, false, false, false, true, 10)).refusal());
        assertEquals(Refusal.NO_ROOM, check(new Request(base.from(), base.to(), 5, 10, 0, 3, NOW, 0, 0, true, false, false, false, false, false, 4)).refusal());
        assertEquals(Refusal.SAME_CLASS, check(new Request(PlayerClass.MERGEN, PlayerClass.MERGEN, 5, 10, 0, 3, NOW, 0, 0, true, false, false, false, false, false, 10)).refusal());
        assertEquals(Refusal.NO_CLASS, check(new Request(null, PlayerClass.MERGEN, 5, 10, 0, 3, NOW, 0, 0, true, false, false, false, false, false, 10)).refusal());
    }

    @Test
    void remainingTimeReadsInMongolian() {
        assertEquals("2 ц 15 мин", format(135 * 60_000L));
        assertEquals("1 мин", format(1));
    }
}
