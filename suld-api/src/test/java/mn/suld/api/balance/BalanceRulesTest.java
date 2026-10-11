package mn.suld.api.balance;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.mob.MobTier;
import mn.suld.api.progression.CurveMigration;
import mn.suld.api.progression.LevelCurve;
import mn.suld.api.progression.PolynomialLevelCurve;
import mn.suld.api.progression.Progression;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BalanceRulesTest {

    static final LevelCurve V2 = Balance.curve();
    static final LevelCurve V1 = new PolynomialLevelCurve(100, 1.75, 60);

    @Test
    void curveTotalsAboutTwoHundredHours() {
        assertEquals(46_943_870L, CurveMigration.totalTo(V2, 60));
        assertEquals(315, V2.expForLevel(1));
        assertEquals(0, V2.expForLevel(60));
    }

    @Test
    void migrationKeepsTheLevelAndTheBarFraction() {
        Progression p = new Progression(20, V1.expForLevel(20) / 2);
        Progression q = CurveMigration.rescale(p, V1, V2);
        assertEquals(20, q.level());
        assertEquals(V2.expForLevel(20) / 2, q.expIntoLevel(), 10);
        // a full bar never becomes a free level
        Progression full = CurveMigration.rescale(new Progression(5, V1.expForLevel(5) * 3), V1, V2);
        assertEquals(5, full.level());
        assertTrue(full.expIntoLevel() < V2.expForLevel(5));
        assertEquals(new Progression(60, 0), CurveMigration.rescale(new Progression(60, 0), V1, V2));
    }

    @Test
    void gapTable() {
        assertEquals(1.20, ExpRules.gapFactor(8), 1e-9);
        assertEquals(1.20, ExpRules.gapFactor(20), 1e-9);
        assertEquals(1.10, ExpRules.gapFactor(4), 1e-9);
        assertEquals(1.0, ExpRules.gapFactor(0), 1e-9);
        assertEquals(1.0, ExpRules.gapFactor(-4), 1e-9);
        assertEquals(0.85, ExpRules.gapFactor(-5), 1e-9);
        assertEquals(0.25, ExpRules.gapFactor(-9), 1e-9);
        assertEquals(0.10, ExpRules.gapFactor(-30), 1e-9);
        assertTrue(ExpRules.allowsGear(-9));
        assertFalse(ExpRules.allowsGear(-10));
    }

    @Test
    void partyBoostAndCatchUp() {
        assertEquals(1.0, ExpRules.partyShare(1), 1e-9);
        assertEquals(0.575, ExpRules.partyShare(2), 1e-9);
        assertEquals(1.45 / 4, ExpRules.partyShare(4), 1e-9);
        assertEquals(0.5, ExpRules.capBoost(1.3), 1e-9);
        assertEquals(0.2, ExpRules.capBoost(0.2), 1e-9);
        assertEquals(1.5, ExpRules.catchUp(20, 31), 1e-9);
        assertEquals(1.0, ExpRules.catchUp(21, 31), 1e-9);
        assertEquals(1.0, ExpRules.catchUp(5, 0), 1e-9, "no median yet: no catch-up");
    }

    @Test
    void restedPoolGrowsWithOfflineHoursOnly() {
        long need = V2.expForLevel(10);
        long p = RestedPool.accrue(V2, 0, 10, 3_600_000L * 10);
        assertEquals((long) Math.floor(0.015 * need * 10), p);
        assertEquals(RestedPool.cap(V2, 10), RestedPool.accrue(V2, p, 10, 3_600_000L * 1000));
        assertEquals(p, RestedPool.accrue(V2, p, 10, 0));
        assertEquals(0, RestedPool.accrue(V2, 0, 60, 3_600_000L * 50), "nothing to rest towards at the cap");
        assertEquals(40, RestedPool.bonus(100, 40));
        assertEquals(30, RestedPool.bonus(30, 40));
    }

    @Test
    void mobNumbersArePinned() {
        assertEquals(50, MobScaling.baseExp(2));
        assertEquals(15.0, MobScaling.baseDamage(2), 1e-9);
        assertEquals(Math.round(2.5 * (10 + 2.75 * Math.pow(10, 1.36))), MobScaling.baseHealth(10), 1e-9);
        assertEquals(MobScaling.baseHealth(20) * 40, MobScaling.health(20, MobTier.BOSS), 1e-9);
        assertEquals(MobScaling.baseDamage(20) * 1.3, MobScaling.damage(20, MobTier.ELITE), 1e-9);
        assertEquals(0.4375, MobScaling.bossPartyScale(1), 1e-9);
        assertEquals(1.0, MobScaling.bossPartyScale(4), 1e-9);
        assertEquals(5, MobScaling.coins(10, MobTier.NORMAL));
        assertEquals(9, MobScaling.coins(10, MobTier.ELITE));
    }

    @Test
    void combatRules() {
        assertEquals(0.5, CombatRules.mitigation(35, 10), 1e-9);
        assertEquals(0.75, CombatRules.mitigation(10_000, 10), 1e-9);
        assertEquals(0.0, CombatRules.mitigation(-5, 10), 1e-9);
        assertEquals(0.8, CombatRules.gapDealt(5), 1e-9);
        assertEquals(0.4, CombatRules.gapDealt(30), 1e-9);
        assertEquals(1.4, CombatRules.gapTaken(5), 1e-9);
        assertEquals(1.0, CombatRules.gapTaken(-5), 1e-9);
        assertEquals(PlayerClass.BAATAR.baseHealth() * (1 + 0.04 * 59), CombatRules.classHealth(PlayerClass.BAATAR, 60), 1e-9);
    }

    @Test
    void rewardsFollowTheCurve() {
        long n10 = V2.expForLevel(10);
        assertEquals(Math.round(0.02 * n10), Rewards.loginExp(V2, 7, 10));
        assertEquals(480, Rewards.loginCoins(7));
        assertEquals(Math.round(0.04 * n10), Rewards.dailyTaskExp(V2, 10));
        assertEquals(Math.round(0.35 * n10), Rewards.chapterExp(V2, 10));
        assertEquals(Rewards.dailyTaskExp(V2, 59), Rewards.dailyTaskExp(V2, 60), "level 60 is priced as 59");
        assertEquals(Math.round(0.02 * n10 * 0.85), Rewards.landmarkExp(V2, 10, 15));
        assertEquals(60 + 12 * 13, Rewards.dungeonCoins(13));
    }

    @Test
    void dungeonRules() {
        assertEquals(10, DungeonRules.lootLevel(3, 10, 60));
        assertEquals(3, DungeonRules.lootLevel(3, 10, 1));
        assertEquals(7, DungeonRules.lootLevel(3, 10, 7));
        List<String> recent = List.of("a", "b", "a", "a", "c", "a", "a", "a", "a", "a");
        assertEquals(0.25, DungeonRules.repeatFactor(recent, "a"), 1e-9);
        assertEquals(1.0, DungeonRules.repeatFactor(recent, "b"), 1e-9, "b fell out of the last 8");
        assertEquals(0.85, DungeonRules.repeatFactor(recent, "c"), 1e-9);
        assertEquals(0.1, DungeonRules.carryFactor(20, 20, 10), 1e-9);
        assertEquals(0.5, DungeonRules.carryFactor(5, 16, 10), 1e-9);
        assertEquals(1.0, DungeonRules.carryFactor(6, 16, 20), 1e-9);
    }

    @Test
    void economyAndSkillPoints() {
        assertEquals(Math.round(0.6 * 100 + 25 * 10 + 25), Economy.reforgeCoins(10));
        assertEquals(59 + 14 + 4 + 3, Economy.skillPoints(60, 42, 8, 3));
        assertEquals(0, Economy.skillPoints(1, 0, 0, 0));
    }

    @Test
    void ascensionCostsAndGates() {
        assertEquals(1_487_086L, Ascension.cost(V2, 0), 1);
        assertEquals(2_974_172L, Ascension.cost(V2, 1), 1);
        assertEquals(4_461_259L, Ascension.cost(V2, 2), 1);
        assertEquals(75_000, Ascension.riteCoins(2));
        double gp = GearPower.par(60);
        Ascension.Inputs ready = new Ascension.Inputs(60, 10, 10, 4, gp, 42, 42, 7);
        assertNull(Ascension.blocked(0, ready));
        assertNull(Ascension.blocked(1, ready));
        assertNull(Ascension.blocked(2, ready));
        assertNotNull(Ascension.blocked(3, ready));
        assertNotNull(Ascension.blocked(0, new Ascension.Inputs(59, 10, 10, 4, gp, 42, 42, 7)));
        assertNotNull(Ascension.blocked(0, new Ascension.Inputs(60, 10, 10, 4, gp, 41, 42, 7)));
        assertNotNull(Ascension.blocked(1, new Ascension.Inputs(60, 10, 10, 1, gp, 42, 42, 7)));
        assertNotNull(Ascension.blocked(2, new Ascension.Inputs(60, 10, 10, 4, gp * 0.95, 42, 42, 7)));
    }
}
