package mn.suld.api.training;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.Endgame;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.skill.tree.SkillAllocation;
import mn.suld.api.skill.tree.SkillEngine;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.SkillPoints;
import mn.suld.api.skill.tree.SkillTree;
import mn.suld.api.skill.tree.SkillTreeLoader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The training grounds' two ready builds per class (docs/TRAINING_GROUNDS.md), on the bundled trees. */
class TrainingBuildsTest {

    static final SkillTreeLoader.Result LOADED = SkillTreeLoader.loadAll(SkillTreeLoader.classpath());

    @Test
    void aFinishedCharacterHasEightyPoints() {
        // 59 from levels + 14 from the story + 4 from the regions + 3 from Тэнгэрийн Зэрэг III
        assertEquals(80, TrainingBuilds.maxPoints());
    }

    @Test
    void everyClassHasTwoLegalBuildsThatSpendEveryPoint() {
        assertTrue(LOADED.issues().isEmpty(), LOADED.issues().toString());
        for (PlayerClass c : PlayerClass.values()) {
            SkillTree t = LOADED.trees().get(c);
            TrainingBuilds.Build a = TrainingBuilds.of(t, 0), b = TrainingBuilds.of(t, 1);
            for (TrainingBuilds.Build x : new TrainingBuilds.Build[]{a, b}) {
                SkillAllocation al = x.allocation();
                assertTrue(al.consistent(), c + " " + x.letter() + ": " + al.firstInconsistent());
                assertTrue(al.fits(TrainingBuilds.LEVEL, TrainingBuilds.maxPoints()), c + " " + x.letter() + " spends " + al.spent());
                assertTrue(al.spent() >= TrainingBuilds.maxPoints() - 1, c + " " + x.letter() + " leaves points: " + al.spent());
                assertNotNull(x.keystone(), c + " " + x.letter() + " has a keystone");
                assertTrue(al.unlocked(x.keystone()));
                assertNotNull(x.ultimate(), c + " " + x.letter() + " has an ultimate");
                assertTrue(al.unlocked(x.ultimate()));
                assertEquals(1, al.unlockedNodes().stream().filter(SkillNode::unlocksUltimate).count(), "one ultimate");
                assertEquals(1, al.unlockedNodes().stream().filter(SkillNode::keystone).count(), "one keystone");
                assertTrue(TrainingBuilds.passives(al).size() >= 3, c + " " + x.letter() + " shows passives: " + TrainingBuilds.passives(al).size());
            }
            assertNotEquals(a.keystone(), b.keystone(), c + ": A and B are built on the two keystones");
            assertNotEquals(a.ultimate(), b.ultimate(), c + ": A and B show two different ultimates");
        }
    }

    @Test
    void theSameTreeAlwaysGivesTheSameBuild() {
        SkillTree t = LOADED.trees().get(PlayerClass.BAATAR);
        assertEquals(TrainingBuilds.of(t, 0).allocation().encode(), TrainingBuilds.of(t, 0).allocation().encode());
        assertEquals(TrainingBuilds.of(t, 1).allocation().encode(), TrainingBuilds.of(t, 3).allocation().encode(), "variant wraps");
    }

    /**
     * What the training service does: a level-60, Тэнгэрийн Зэрэг III stand-in of a brand-new player (no story, no
     * region) with the build and the missing points granted. The skill tree's join check (normalise) keeps every rank.
     */
    @Test
    void theBuildSurvivesTheTreeCheckOfANewPlayer() {
        for (PlayerClass c : PlayerClass.values()) {
            SkillTree t = LOADED.trees().get(c);
            for (int v = 0; v < TrainingBuilds.VARIANTS; v++) {
                SkillAllocation al = TrainingBuilds.of(t, v).allocation();
                PlayerProfile stand = PlayerProfile.createNew(UUID.randomUUID(), "New", Instant.EPOCH).ephemeralCopy();
                stand.forceClass(c);
                stand.progression(new Progression(TrainingBuilds.LEVEL, 0));
                stand.endgame(Endgame.FRESH.withAscension(mn.suld.api.balance.Ascension.MAX_RANK));
                SkillEngine.Context ctx = new SkillEngine.Context(TrainingBuilds.LEVEL, 0, 0);
                int base = SkillPoints.total(TrainingBuilds.LEVEL, 0, 0, 0) + stand.ascension();
                stand.skillState(stand.skillState().withRanks(al.encode()).withGranted(TrainingBuilds.maxPoints() - base));
                assertEquals(TrainingBuilds.maxPoints(), SkillEngine.total(stand, ctx));
                assertEquals(0, SkillEngine.normalise(stand, t, ctx), c + " " + v + ": nothing refunded");
                assertEquals(al.encode(), stand.skillState().ranks());
            }
        }
    }
}
