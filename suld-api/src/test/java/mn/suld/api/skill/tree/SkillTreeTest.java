package mn.suld.api.skill.tree;

import mn.suld.api.clazz.PlayerClass;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTreeTest {

    static final SkillTreeLoader.Result loaded = SkillTreeLoader.loadAll(SkillTreeLoader.classpath());

    static SkillTree tree(PlayerClass c) {
        return loaded.trees().get(c);
    }

    @Test
    void bundledDataLoadsWithoutIssues() {
        assertTrue(loaded.issues().isEmpty(), loaded.issues().toString());
        assertEquals(5, loaded.trees().size());
    }

    @Test
    void everyClassHasADistinctFullTree() {
        int total = 0;
        Set<String> classOnlyNames = new HashSet<>();
        for (PlayerClass c : PlayerClass.values()) {
            SkillTree t = tree(c);
            // 31 class nodes + 5 universal + 78 satellites (3 per notable, tools/content/gen_skill_minors.py)
            assertEquals(114, t.nodes().size(), c.name());
            assertEquals(78, t.nodes().stream().filter(SkillNode::satellite).count(), c + " satellites");
            total += t.nodes().size() - 1;
            assertTrue(t.totalCost() > SkillPoints.total(60, 15, 20, 0), c + " total " + t.totalCost());
            assertEquals(3, t.nodes().stream().filter(SkillNode::unlocksUltimate).count(), c + " ultimates");
            assertEquals(1, t.nodes().stream().filter(SkillNode::keystone).count(), c + " keystone");
            for (SkillNode n : t.nodes()) {
                if (!n.root() && !n.tags().contains("universal") && !n.satellite()) assertTrue(classOnlyNames.add(n.name()), "name shared between classes: " + n.name());
                if (n.root()) continue;
                assertFalse(EffectText.lines(n).isEmpty(), n.id());
                for (Effect e : n.effects()) {
                    if (e instanceof Effect.SpellMod m) assertEquals(c, m.spell().clazz());
                }
            }
        }
        assertTrue(total >= 150, "real nodes " + total);
    }

    @Test
    void everySpellOfEveryClassIsReshapedBySomeNode() {
        for (PlayerClass c : PlayerClass.values()) {
            Set<mn.suld.api.skill.Spell> touched = new HashSet<>();
            for (SkillNode n : tree(c).nodes()) touched.addAll(n.spells());
            assertEquals(4, touched.size(), c + " modifies " + touched);
        }
    }

    @Test
    void fifteenUltimatesAndFiveKeystonesAreEachTaughtOnce() {
        Set<Ultimate> ults = new HashSet<>();
        Set<KeystoneKind> keys = new HashSet<>();
        for (PlayerClass c : PlayerClass.values()) {
            for (SkillNode n : tree(c).nodes()) {
                for (Effect e : n.effects()) {
                    if (e instanceof Effect.UnlockUltimate u) assertTrue(ults.add(u.ultimate()));
                    if (e instanceof Effect.Keystone k) assertTrue(keys.add(k.kind()));
                }
            }
        }
        assertEquals(Ultimate.values().length, ults.size());
        assertEquals(KeystoneKind.values().length, keys.size());
    }

    @Test
    void pointsFollowProgression() {
        assertEquals(0, SkillPoints.forLevel(1));
        assertEquals(1, SkillPoints.forLevel(2));
        assertEquals(29, SkillPoints.forLevel(30));
        assertEquals(31, SkillPoints.forLevel(32));
        assertEquals(59, SkillPoints.forLevel(60));
        assertEquals(0, SkillPoints.forChapters(2));
        assertEquals(6, SkillPoints.forChapters(18));
        assertEquals(14, SkillPoints.forChapters(99));
        assertEquals(2, SkillPoints.forDiscovery(4));
        assertEquals(4, SkillPoints.forDiscovery(500));
        assertEquals(59 + 6 + 2 + 3, SkillPoints.total(60, 18, 4, 3));
        assertEquals(0, SkillPoints.total(1, 0, 0, -5));
    }

    @Test
    void unlockNeedsPathPointsAndLevel() {
        SkillTree t = tree(PlayerClass.BAATAR);
        SkillAllocation a = SkillAllocation.empty(t);
        SkillNode l1 = t.node("l1"), l2 = t.node("l2");
        assertEquals(SkillAllocation.Why.LEVEL, a.canUnlock(l1, 1, 99).why());
        assertEquals(SkillAllocation.Why.POINTS, a.canUnlock(l1, 3, 0).why());
        assertEquals(SkillAllocation.Why.NOT_CONNECTED, a.canUnlock(l2, 60, 99).why());
        assertEquals(SkillAllocation.Why.ROOT, a.canUnlock(t.root(), 60, 99).why());
        assertTrue(a.canUnlock(l1, 3, 1).ok());
        a = a.plusRank(l1);
        assertTrue(a.canUnlock(l2, 6, 1).ok());
        assertEquals(SkillAllocation.Why.NOT_CONNECTED, a.canUnlock(t.node("r2"), 60, 99).why());
        assertEquals(1, a.spent());
    }

    @Test
    void ranksStackUntilMaxed() {
        SkillTree t = tree(PlayerClass.BAATAR);
        SkillNode l1 = t.node("l1");
        SkillAllocation a = SkillAllocation.empty(t).plusRank(l1).plusRank(l1);
        assertEquals(2, a.rank(l1));
        assertEquals(2, a.spent());
        assertTrue(a.canUnlock(l1, 60, 9).ok());
        a = a.plusRank(l1);
        assertEquals(SkillAllocation.Why.MAXED, a.canUnlock(l1, 60, 9).why());
        assertTrue(a.maxed(l1));
        // the stat scales with rank
        assertEquals(12, a.build().stat(StatKey.HEALTH), 1e-9);
        // refunding one rank keeps the node
        SkillAllocation b = a.minusRank(l1);
        assertTrue(b.unlocked(l1));
        assertEquals(8, b.build().stat(StatKey.HEALTH), 1e-9);
    }

    @Test
    void exclusiveLinksBlockTheRival() {
        SkillTree t = tree(PlayerClass.MERGEN);
        SkillAllocation b = SkillAllocation.empty(t).plusRank(t.node("m1")).plusRank(t.node("m2")).plusRank(t.node("l1"));
        SkillAllocation.Check c = b.canUnlock(t.node("l2"), 60, 99);
        assertEquals(SkillAllocation.Why.EXCLUSIVE, c.why());
        assertEquals("m2", c.other().id());
        assertEquals("m2", b.rival(t.node("l2")).id());
    }

    @Test
    void onlyOneUltimateCanBeLearned() {
        for (PlayerClass cl : PlayerClass.values()) {
            SkillTree t = tree(cl);
            SkillAllocation a = allOf(t, "l7", "r7", "u1");
            assertEquals(SkillAllocation.Why.EXCLUSIVE, a.canUnlock(t.node("u2"), 60, 99).why());
            assertEquals(SkillAllocation.Why.EXCLUSIVE, a.canUnlock(t.node("u3"), 60, 99).why());
            assertNotNull(a.ultimate());
        }
    }

    @Test
    void extraRequirementsConvergeBranches() {
        SkillTree t = tree(PlayerClass.BOO);
        // u2 sits next to m7 and l7/r7, but also needs m6: reaching it from l7 alone is not enough
        SkillAllocation a = allOf(t, "l1", "l2b", "l3b", "l3", "l4", "l5b", "l6", "l7");
        SkillAllocation.Check c = a.canUnlock(t.node("u2"), 60, 99);
        assertEquals(SkillAllocation.Why.REQUIRES, c.why());
        assertEquals("m6", c.other().id());
        assertTrue(a.canUnlock(t.node("u1"), 60, 99).ok());
    }

    @Test
    void refundMustKeepTheBuildConsistent() {
        SkillTree t = tree(PlayerClass.BAATAR);
        SkillAllocation a = allOf(t, "l1", "l2", "l3");
        SkillAllocation.Check c = a.canRefund(t.node("l2"));
        assertEquals(SkillAllocation.Why.WOULD_BREAK, c.why());
        assertEquals("l3", c.other().id());
        assertTrue(a.canRefund(t.node("l3")).ok());
        assertEquals(SkillAllocation.Why.ROOT, a.canRefund(t.root()).why());
        assertEquals(SkillAllocation.Why.NOT_UNLOCKED, a.canRefund(t.node("r1")).why());
    }

    @Test
    void refundIsBlockedWhileAnotherNodeNeedsIt() {
        SkillTree t = tree(PlayerClass.BOO);
        SkillAllocation a = allOf(t, "m1", "m2", "m3", "m4", "m5", "m6", "m7", "u2");
        // u2 requires m6: m6 cannot be refunded while u2 stands
        SkillAllocation.Check c = a.canRefund(t.node("m6"));
        assertEquals(SkillAllocation.Why.WOULD_BREAK, c.why());
    }

    @Test
    void bridgesLetAnotherPathKeepANodeConnected() {
        SkillTree t = tree(PlayerClass.BAATAR);
        // l3 hangs on l2 and, over the row-3 bridge, on m3 as well: with m3 learned l2 may go
        SkillAllocation a = allOf(t, "m1", "m2", "m3", "l3", "l1", "l2b");
        assertTrue(a.consistent());
        assertTrue(a.canRefund(t.node("l2b")).ok());
    }

    @Test
    void hiddenNodesAppearOnceTheyAreReachable() {
        SkillTree t = tree(PlayerClass.BAATAR);
        SkillNode l5 = t.node("l5");
        assertTrue(l5.hidden());
        SkillAllocation a = SkillAllocation.empty(t);
        assertFalse(a.visible(l5));
        a = allOf(t, "l1", "l2", "l3", "l4");
        assertTrue(a.visible(l5));
    }

    @Test
    void codecIsById_andDropsUnknownIds() {
        SkillTree t = tree(PlayerClass.DARKHAN);
        SkillAllocation a = allOf(t, "r1", "r1", "r2");
        String enc = a.encode();
        assertEquals("r1=2;r2=1", enc);
        SkillAllocation.Decoded d = SkillAllocation.decode(t, enc + ";ghost=1;l1=x;root=1;m1=0");
        assertEquals(enc, d.allocation().encode());
        assertEquals(4, d.dropped().size());
        // rank beyond the maximum is clamped
        assertEquals(3, SkillAllocation.decode(t, "r1=99").allocation().rank(t.node("r1")));
    }

    @Test
    void trimmedDropsWhatTheLevelNoLongerAllows() {
        SkillTree t = tree(PlayerClass.DARKHAN);
        SkillAllocation a = allOf(t, "r1", "r2", "r3", "r4");
        SkillAllocation low = a.trimmed(4, SkillPoints.forLevel(4));
        assertTrue(low.spent() <= SkillPoints.forLevel(4));
        for (SkillNode n : low.unlockedNodes()) assertTrue(n.root() || n.effectiveLevel() <= 4, n.id());
        assertTrue(low.consistent());
        assertEquals(a.spent(), a.trimmed(60, 99).spent());
    }

    @Test
    void trimmedFixesBrokenPaths() {
        SkillTree t = tree(PlayerClass.BAATAR);
        // l3 without l1/l2: an edited data file or a hacked row
        SkillAllocation broken = SkillAllocation.decode(t, "l3=1;r1=1").allocation();
        assertFalse(broken.consistent());
        SkillAllocation fixed = broken.trimmed(60, 99);
        assertTrue(fixed.consistent());
        assertTrue(fixed.unlocked(t.node("r1")));
        assertFalse(fixed.unlocked(t.node("l3")));
    }

    @Test
    void categoryResetKeepsOtherBranches() {
        SkillTree t = tree(PlayerClass.MERGEN);
        SkillAllocation a = allOf(t, "l1", "l2", "r1", "r2");
        SkillAllocation b = a.withoutCategory(SkillCategory.DEFENSE);
        assertFalse(b.unlocked(t.node("l1")));
        assertTrue(b.unlocked(t.node("r1")));
        assertTrue(b.consistent());
    }

    @Test
    void buildAddsUpEffects() {
        SkillTree t = tree(PlayerClass.BAATAR);
        SkillAllocation a = allOf(t, "l1", "r1", "r2", "r3");
        SkillBuild b = a.build();
        assertEquals(4, b.stat(StatKey.HEALTH), 1e-9);
        assertEquals(4, b.stat(StatKey.ATTACK_PCT), 1e-9);
        assertEquals(1, b.procs(TriggerEvent.HIT).size());
        assertEquals(t.node("r3").index(), b.procs(TriggerEvent.HIT).get(0).node());
        assertNull(b.ultimate());
        assertTrue(SkillBuild.EMPTY.isEmpty());
        SkillBuild full = allOf(t, "u3").build();
        assertEquals(Ultimate.CHINGISIIN_UUR, full.ultimate());
        assertTrue(allOf(t, "l7").build().has(KeystoneKind.MUNKH_TESVER));
    }

    @Test
    void spellModifiersAddUpPerSpell() {
        SkillTree t = tree(PlayerClass.BAATAR);
        SkillBuild b = allOf(t, "m1", "m2").build();
        mn.suld.api.skill.Spell cone = mn.suld.api.skill.Spell.TENGER_TSAVCHILT;
        assertEquals(25, b.mod(cone, ModKey.RADIUS_PCT), 1e-9);
        assertEquals(10, b.mod(cone, ModKey.DAMAGE_PCT), 1e-9);
        assertEquals(0, b.mod(mn.suld.api.skill.Spell.DAINY_KHASHGIRAAN, ModKey.RADIUS_PCT), 1e-9);
    }

    @Test
    void tooltipTextIsMongolianAndComplete() {
        SkillTree t = tree(PlayerClass.BAATAR);
        var lines = EffectText.lines(t.node("r3"));
        assertEquals("Цохиход 25% магадлалтайгаар 8 нөөц сэргээнэ.", lines.get(0).text());
        assertEquals(EffectText.Kind.TRIGGER, lines.get(1).kind());
        assertEquals("Хүлээлт: 2 сек", lines.get(2).text());
        assertEquals("-3% Авах хохирол", EffectText.lines(t.node("l2")).get(0).text());
        assertEquals("Тэнгэрийн Цавчилт: +25% хүрээ", EffectText.lines(t.node("m2")).get(0).text());
        assertEquals("+4 Дээд амь", EffectText.lines(t.node("l1")).get(0).text());
        assertEquals(EffectText.Kind.KEYSTONE, EffectText.lines(t.node("l7")).get(0).kind());
    }

    @Test
    void dataRoundTripsThroughTheWriter() {
        for (PlayerClass c : PlayerClass.values()) {
            SkillTree t = tree(c);
            var own = t.nodes().stream().filter(n -> !n.tags().contains("universal")).toList();
            String json = SkillTreeJson.write(t, c.id(), own, java.util.List.of(), java.util.List.of());
            Object parsed = mn.suld.api.json.Json.parse(json);
            assertEquals(own.size(), mn.suld.api.json.Json.array(mn.suld.api.json.Json.object(parsed).get("nodes")).size());
        }
    }

    /** Learn nodes without any rule checks (test setup): one rank per mention. */
    static SkillAllocation allOf(SkillTree t, String... ids) {
        SkillAllocation a = SkillAllocation.empty(t);
        for (String id : ids) a = a.plusRank(t.node(id));
        return a;
    }
}
