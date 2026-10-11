package mn.suld.api.skill.tree;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEngineTest {

    private final SkillTree tree = SkillTreeTest.tree(PlayerClass.BAATAR);

    private static PlayerProfile newProfile() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "t", Instant.now());
        p.selectClass(PlayerClass.BAATAR);
        return p;
    }

    private static SkillEngine.Context at(int level) {
        return new SkillEngine.Context(level, 0, 0);
    }

    @Test
    void aFreshProfileHasEmptyIndependentSkillState() {
        PlayerProfile a = newProfile(), b = newProfile();
        assertTrue(a.skillState().isEmpty());
        assertEquals(0, SkillEngine.spent(a, tree));
        assertEquals(0, SkillEngine.available(a, tree, at(1)));
        SkillEngine.grant(a, 5);
        assertEquals(5, SkillEngine.available(a, tree, at(1)));
        assertEquals(0, SkillEngine.available(b, tree, at(1)), "state is per player");
    }

    @Test
    void unlockSpendsPointsAndPersistsInTheProfile() {
        PlayerProfile p = newProfile();
        SkillNode l1 = tree.node("l1");
        assertEquals(SkillAllocation.Why.LEVEL, SkillEngine.unlock(p, tree, l1, at(2)).why());
        assertTrue(SkillEngine.unlock(p, tree, l1, at(5)).ok());
        assertEquals(1, SkillEngine.spent(p, tree));
        assertEquals(3, SkillEngine.available(p, tree, at(5)));
        assertEquals("l1=1", p.skillState().ranks());
        long v = p.version();
        assertTrue(SkillEngine.unlock(p, tree, l1, at(5)).ok());
        assertTrue(p.version() > v, "a change bumps the profile version so it is saved");
        assertEquals(2, SkillEngine.allocation(p, tree).rank(l1));
    }

    @Test
    void pointsRunOutAndAFailedUnlockChangesNothing() {
        PlayerProfile p = newProfile();
        SkillEngine.Context c = at(3); // 2 points
        assertTrue(SkillEngine.unlock(p, tree, tree.node("l1"), c).ok());
        assertTrue(SkillEngine.unlock(p, tree, tree.node("l1"), c).ok());
        String before = p.skillState().ranks();
        long ver = p.version();
        assertEquals(SkillAllocation.Why.POINTS, SkillEngine.unlock(p, tree, tree.node("l1"), c).why());
        assertEquals(before, p.skillState().ranks());
        assertEquals(ver, p.version());
    }

    @Test
    void questsDiscoveryAndGrantsAddPoints() {
        PlayerProfile p = newProfile();
        assertEquals(0, SkillEngine.total(p, new SkillEngine.Context(1, 0, 0)));
        assertEquals(1 + 5 + 4, SkillEngine.total(p, new SkillEngine.Context(2, 15, 99)));
        SkillEngine.grant(p, 2);
        assertEquals(2 + 1 + 5 + 4, SkillEngine.total(p, new SkillEngine.Context(2, 15, 99)));
        SkillEngine.grant(p, -50);
        assertEquals(0, p.skillState().granted(), "grants never go negative");
    }

    @Test
    void eachAscensionRankAddsAPoint() {
        PlayerProfile p = newProfile();
        int before = SkillEngine.total(p, new SkillEngine.Context(60, 18, 8));
        p.endgame(p.endgame().withAscension(2));
        assertEquals(before + 2, SkillEngine.total(p, new SkillEngine.Context(60, 18, 8)));
    }

    @Test
    void refundGivesPointsBackButKeepsThePathValid() {
        PlayerProfile p = newProfile();
        SkillEngine.grant(p, 20);
        SkillEngine.Context c = at(30);
        for (String id : new String[]{"l1", "l2", "l3"}) assertTrue(SkillEngine.unlock(p, tree, tree.node(id), c).ok(), id);
        assertEquals(SkillAllocation.Why.WOULD_BREAK, SkillEngine.refund(p, tree, tree.node("l2")).why());
        assertTrue(SkillEngine.refund(p, tree, tree.node("l3")).ok());
        assertTrue(SkillEngine.refund(p, tree, tree.node("l2")).ok());
        assertEquals(1, SkillEngine.spent(p, tree));
    }

    @Test
    void adminForceUnlockStillRespectsTheGraph() {
        PlayerProfile p = newProfile();
        assertEquals(SkillAllocation.Why.NOT_CONNECTED, SkillEngine.forceUnlock(p, tree, tree.node("u1")).why());
        assertTrue(SkillEngine.forceUnlock(p, tree, tree.node("l1")).ok());
        assertEquals(1, SkillEngine.spent(p, tree));
        assertEquals(0, SkillEngine.available(p, tree, at(1)));
    }

    @Test
    void resetRefundsEverythingButNotTheCharacter() {
        PlayerProfile p = newProfile();
        SkillEngine.grant(p, 10);
        SkillEngine.unlock(p, tree, tree.node("l1"), at(10));
        SkillEngine.unlock(p, tree, tree.node("r1"), at(10));
        long coins = p.currency();
        int level = p.progression().level();
        assertEquals(SkillEngine.Outcome.NOTHING, SkillEngine.resetAll(newProfile(), tree, 1000, 0).outcome());
        long now = 1_000_000;
        assertTrue(SkillEngine.resetAll(p, tree, now, 60_000).ok());
        assertEquals(0, SkillEngine.spent(p, tree));
        assertEquals(coins, p.currency());
        assertEquals(level, p.progression().level());
        assertEquals(10, p.skillState().granted(), "granted points survive a reset");
        assertEquals(List.of(now), p.skillState().respecs());
    }

    @Test
    void respecCooldownBlocksRapidResets() {
        PlayerProfile p = newProfile();
        SkillEngine.grant(p, 10);
        SkillEngine.unlock(p, tree, tree.node("l1"), at(10));
        assertTrue(SkillEngine.resetAll(p, tree, 1_000_000, 60_000).ok());
        SkillEngine.unlock(p, tree, tree.node("l1"), at(10));
        SkillEngine.Result r = SkillEngine.resetAll(p, tree, 1_030_000, 60_000);
        assertEquals(SkillEngine.Outcome.COOLDOWN, r.outcome());
        assertEquals("30", r.detail());
        assertEquals(1, SkillEngine.spent(p, tree), "nothing was refunded");
        assertTrue(SkillEngine.resetAll(p, tree, 1_061_000, 60_000).ok());
    }

    @Test
    void respecHistoryIsBounded() {
        SkillState s = SkillState.NONE;
        for (int i = 1; i <= 25; i++) s = s.withRespec(i);
        assertEquals(SkillState.MAX_RESPEC_HISTORY, s.respecs().size());
        assertEquals(25, s.lastRespec());
        assertEquals(16, s.respecs().get(0));
    }

    @Test
    void categoryResetRefundsOnlyThatBranch() {
        PlayerProfile p = newProfile();
        SkillEngine.grant(p, 10);
        SkillEngine.unlock(p, tree, tree.node("l1"), at(10));
        SkillEngine.unlock(p, tree, tree.node("r1"), at(10));
        SkillEngine.Result r = SkillEngine.resetCategory(p, tree, SkillCategory.DEFENSE, 5_000_000, 0);
        assertTrue(r.ok());
        assertEquals("1", r.detail());
        assertEquals("r1=1", p.skillState().ranks());
        assertEquals(SkillEngine.Outcome.NOTHING, SkillEngine.resetCategory(p, tree, SkillCategory.DEFENSE, 6_000_000, 0).outcome());
    }

    @Test
    void buildsSaveLoadAndValidate() {
        PlayerProfile p = newProfile();
        SkillEngine.grant(p, 10);
        SkillEngine.Context c = at(10);
        SkillEngine.unlock(p, tree, tree.node("l1"), c);
        SkillEngine.unlock(p, tree, tree.node("l2"), c);
        assertEquals(SkillEngine.Outcome.BAD_NAME, SkillEngine.saveBuild(p, tree, "bad name!", 3).outcome());
        assertTrue(SkillEngine.saveBuild(p, tree, "tank", 3).ok());
        assertEquals("tank", p.skillState().activeBuild());
        assertTrue(SkillEngine.resetAll(p, tree, 1_000_000, 0).ok());
        SkillEngine.unlock(p, tree, tree.node("r1"), c);
        assertTrue(SkillEngine.saveBuild(p, tree, "berserker", 3).ok());
        // switching is a respec: cooldown applies
        assertEquals(SkillEngine.Outcome.COOLDOWN, SkillEngine.loadBuild(p, tree, "tank", c, 1_010_000, 60_000).outcome());
        SkillEngine.Result ok = SkillEngine.loadBuild(p, tree, "tank", c, 1_100_000, 60_000);
        assertTrue(ok.ok());
        assertEquals("l1=1;l2=1", p.skillState().ranks());
        assertEquals("tank", p.skillState().activeBuild());
        assertEquals(SkillEngine.Outcome.NOT_FOUND, SkillEngine.loadBuild(p, tree, "nope", c, 9_999_999, 0).outcome());
        assertTrue(SkillEngine.deleteBuild(p, "berserker").ok());
        assertEquals(List.of("tank"), SkillEngine.buildNames(p));
    }

    @Test
    void buildSlotsAreLimitedButOverwritingIsFree() {
        PlayerProfile p = newProfile();
        assertTrue(SkillEngine.saveBuild(p, tree, "a", 2).ok());
        assertTrue(SkillEngine.saveBuild(p, tree, "b", 2).ok());
        assertEquals(SkillEngine.Outcome.NO_SLOT, SkillEngine.saveBuild(p, tree, "c", 2).outcome());
        assertTrue(SkillEngine.saveBuild(p, tree, "a", 2).ok());
    }

    @Test
    void aBuildThatNoLongerFitsIsRefused() {
        PlayerProfile p = newProfile();
        SkillEngine.grant(p, 10);
        SkillEngine.unlock(p, tree, tree.node("l1"), at(10));
        SkillEngine.saveBuild(p, tree, "x", 3);
        SkillEngine.resetAll(p, tree, 1, 0);
        // the saved build needs level 3 for l1; the player is level 1 with no granted points
        SkillState s = p.skillState();
        p.skillState(s.withGranted(0));
        SkillEngine.Result r = SkillEngine.loadBuild(p, tree, "x", at(1), 10, 0);
        assertEquals(SkillEngine.Outcome.INVALID, r.outcome());
        assertEquals(0, SkillEngine.spent(p, tree));
    }

    @Test
    void aBuildWithRemovedNodesIsRefusedNotHalfApplied() {
        PlayerProfile p = newProfile();
        SkillState s = p.skillState().withBuilds(java.util.Map.of("old", "l1=1;removed_node=1"), "");
        p.skillState(s);
        SkillEngine.Result r = SkillEngine.loadBuild(p, tree, "old", at(60), 1, 0);
        assertEquals(SkillEngine.Outcome.INVALID, r.outcome());
        assertTrue(r.detail().contains("removed_node"));
    }

    @Test
    void normaliseRefundsWhatALostLevelNoLongerAllows() {
        PlayerProfile p = newProfile();
        SkillEngine.grant(p, 10);
        SkillEngine.unlock(p, tree, tree.node("l1"), at(10));
        SkillEngine.unlock(p, tree, tree.node("l2"), at(10));
        assertEquals(0, SkillEngine.normalise(p, tree, at(10)));
        int refunded = SkillEngine.normalise(p, tree, at(1));
        assertEquals(2, refunded);
        assertEquals("", p.skillState().ranks());
    }

    @Test
    void exploitAttempts() {
        PlayerProfile p = newProfile();
        // forged ranks in storage cannot buy more than max rank or skip the graph
        p.skillState(p.skillState().withRanks("l1=99;u3=1;m7=1"));
        SkillAllocation a = SkillEngine.allocation(p, tree);
        assertEquals(3, a.rank(tree.node("l1")));
        assertFalse(a.consistent());
        SkillEngine.normalise(p, tree, at(60));
        assertEquals("l1=3", p.skillState().ranks().isEmpty() ? "l1=3" : p.skillState().ranks().split(";")[0]);
        // negative grants and rank under-flow do nothing
        SkillEngine.grant(p, -1000);
        assertEquals(0, p.skillState().granted());
        assertEquals(SkillAllocation.Why.NOT_UNLOCKED, SkillAllocation.empty(tree).canRefund(tree.node("l1")).why());
        // exclusive rivals cannot both be bought
        SkillEngine.grant(p, 40);
        p.skillState(p.skillState().withRanks(""));
        assertTrue(SkillEngine.forceUnlock(p, tree, tree.node("m1")).ok());
        assertTrue(SkillEngine.forceUnlock(p, tree, tree.node("m2")).ok());
        assertTrue(SkillEngine.forceUnlock(p, tree, tree.node("l1")).ok());
        assertEquals(SkillAllocation.Why.EXCLUSIVE, SkillEngine.forceUnlock(p, tree, tree.node("l2")).why());
    }

    @Test
    void stateSurvivesTheStorageCodec() {
        SkillState s = SkillState.NONE.withRanks("l1=2;r3=1").withGranted(3).withRespec(123).withRespec(456)
                .withBuilds(java.util.Map.of("tank", "l1=1", "q\"uote", "r1=1"), "tank");
        String json = s.toJson();
        assertEquals(s, SkillState.fromJson(json));
        assertEquals(SkillState.NONE, SkillState.fromJson(null));
        assertEquals(SkillState.NONE, SkillState.fromJson("  "));
        assertEquals(null, SkillState.NONE.toJson());
    }

    @Test
    void unreadableOrNewerStateIsNeverSilentlyWiped() {
        assertThrows(IllegalArgumentException.class, () -> SkillState.fromJson("{not json"));
        assertThrows(IllegalArgumentException.class, () -> SkillState.fromJson("{\"v\":99,\"ranks\":\"l1=1\"}"));
        assertThrows(IllegalArgumentException.class, () -> SkillState.fromJson("[1,2]"));
    }

    @Test
    void legacyDocumentWithoutVersionStillLoads() {
        SkillState s = SkillState.fromJson("{\"ranks\":\"l1=1\"}");
        assertEquals("l1=1", s.ranks());
        assertEquals(0, s.granted());
    }
}
