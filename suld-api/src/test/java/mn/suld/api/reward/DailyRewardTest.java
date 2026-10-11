package mn.suld.api.reward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DailyRewardTest {

    @Test
    void streakBuildsResetsAndCycles() {
        assertEquals(1, DailyReward.claim(0, 0, 20_000, 1).day()); // first claim ever
        assertEquals(2, DailyReward.claim(20_000, 1, 20_001, 1).day()); // next day
        assertEquals(1, DailyReward.claim(20_000, 5, 20_002, 1).day()); // missed a day
        assertEquals(7, DailyReward.claim(20_000, 6, 20_001, 1).day());
        assertEquals(1, DailyReward.claim(20_000, 7, 20_001, 1).day()); // after day 7 the cycle restarts
    }

    @Test
    void oncePerDay() {
        DailyReward.Claim again = DailyReward.claim(20_000, 3, 20_000, 10);
        assertFalse(again.allowed());
        assertEquals(3, again.day());
        assertEquals(0, again.coins());
    }

    @Test
    void rewardsGrowWithDayAndLevel() {
        assertEquals(40, DailyReward.coins(1));
        assertEquals(480, DailyReward.coins(7)); // 280 + day-7 bonus
        assertEquals(25, DailyReward.exp(1, 1), "floor: 25 per streak day");
        long need30 = mn.suld.api.balance.Balance.curve().expForLevel(30);
        assertEquals(Math.round(0.02 * 7 * need30 / 7.0), DailyReward.exp(7, 30), "2 % of the level on day 7");
        DailyReward.Claim c = DailyReward.claim(19_999, 2, 20_000, 1);
        assertTrue(c.allowed());
        assertEquals(3, c.day());
        assertEquals(120, c.coins());
        assertEquals(75, c.exp());
    }
}
