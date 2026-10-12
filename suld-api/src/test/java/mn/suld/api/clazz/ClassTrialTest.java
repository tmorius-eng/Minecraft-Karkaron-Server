package mn.suld.api.clazz;

import mn.suld.api.classgear.ArmorTier;
import mn.suld.api.classgear.ClassGear;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.skill.tree.SkillState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClassTrialTest {

    static ClassGear gear(int lvl) {
        return new ClassGear(lvl, 0, ArmorTier.T2, 0, Map.of(), UUID.randomUUID(), Set.of(), List.of(), 3, 0);
    }

    static PlayerProfile baatar() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "Trial", Instant.EPOCH);
        p.selectClass(PlayerClass.BAATAR);
        p.skillState(SkillState.NONE.withRanks("b.root:1,b.str:2"));
        p.classGear(gear(12));
        p.currency(500);
        p.progression(new Progression(14, 300));
        return p;
    }

    @Test
    void aTrialChangesNothingThatLasts() {
        PlayerProfile p = baatar();
        ClassGear real = p.classGear();
        assertTrue(p.beginClassTrial(PlayerClass.MERGEN, 1000));
        assertEquals(PlayerClass.MERGEN, p.playerClass().orElseThrow());
        assertNotNull(p.classTrial());
        assertEquals(0, p.lastClassChange(), "a trial never starts the class-change cooldown");
        // whatever happens in the trial: points spent, gear grown, coins and EXP gained
        p.skillState(SkillState.NONE.withRanks("m.root:1,m.eye:3"));
        p.classGear(gear(40));
        p.currency(99_999);
        p.progression(new Progression(30, 10));

        assertTrue(p.endClassTrial(2000));
        assertNull(p.classTrial());
        assertEquals(PlayerClass.BAATAR, p.playerClass().orElseThrow());
        assertEquals("b.root:1,b.str:2", p.skillState().ranks());
        assertEquals(real, p.classGear());
        assertTrue(p.classMasteries().of(PlayerClass.MERGEN).isEmpty(), "a never-played class is not stored by a trial");
        assertEquals(500, p.currency(), "coins never above the start");
        assertEquals(new Progression(14, 300), p.progression(), "level and EXP never above the start");
        assertEquals(0, p.lastClassChange());
    }

    @Test
    void aTrialOfAClassPlayedBeforeLeavesItsStoredProgressAsItWas() {
        PlayerProfile p = baatar();
        p.switchClass(PlayerClass.BOO, 50);
        p.skillState(SkillState.NONE.withRanks("o.root:1"));
        p.switchClass(PlayerClass.BAATAR, 60);
        ClassMasteries.Saved booBefore = p.classMasteries().of(PlayerClass.BOO).orElseThrow();
        assertTrue(p.beginClassTrial(PlayerClass.BOO, 100));
        assertEquals("o.root:1", p.skillState().ranks(), "the trial shows the player's own Бөө build");
        p.skillState(SkillState.NONE.withRanks("o.root:1,o.extra:5"));
        assertTrue(p.endClassTrial(200));
        assertEquals(booBefore, p.classMasteries().of(PlayerClass.BOO).orElseThrow());
        assertEquals(60, p.lastClassChange());
    }

    @Test
    void lossesInTheTrialStayLostAndOnlyOneTrialRunsAtATime() {
        PlayerProfile p = baatar();
        assertTrue(p.beginClassTrial(PlayerClass.DARKHAN, 1));
        assertFalse(p.beginClassTrial(PlayerClass.BOO, 2), "one trial at a time");
        p.currency(100);
        assertTrue(p.endClassTrial(3));
        assertEquals(100, p.currency(), "the end only caps gains; a loss is not refunded");
        assertFalse(p.endClassTrial(4));
        assertFalse(p.beginClassTrial(PlayerClass.BAATAR, 5), "not a trial of the class already played");
    }

    @Test
    void theTrialSurvivesTheStoredDocument() {
        PlayerProfile p = baatar();
        p.beginClassTrial(PlayerClass.KHULEGCHIN, 7);
        ClassMasteries back = ClassMasteries.fromJson(p.classMasteries().toJson());
        assertEquals(p.classMasteries(), back);
        assertEquals(PlayerClass.BAATAR, back.trial().from());
        assertEquals(500, back.trial().coinsAtStart());
    }
}
