package mn.suld.api.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestEngineTest {

    private final QuestEngine engine = new QuestEngine();
    private final QuestDefinition hunt = new QuestDefinition(
            "quest.first_hunt", "Анхны ан", "Чононуудыг устга", QuestType.KILL_MOB,
            "mob.goviin_chono", 3, 150, 20);

    @Test
    void killsAdvanceProgressAndCompleteAtThreshold() {
        QuestState s = hunt.initialState();
        s = engine.onMobKilled(s, hunt, "mob.goviin_chono");
        assertEquals(1, s.progress());
        assertFalse(s.completed());
        s = engine.onMobKilled(s, hunt, "mob.goviin_chono");
        s = engine.onMobKilled(s, hunt, "mob.goviin_chono");
        assertEquals(3, s.progress());
        assertTrue(s.completed());
    }

    @Test
    void wrongMobDoesNotAdvance() {
        QuestState s = hunt.initialState();
        QuestState after = engine.onMobKilled(s, hunt, "mob.other");
        assertEquals(0, after.progress());
    }

    @Test
    void progressIsCappedAndCompletedStateIsStable() {
        QuestState s = new QuestState("quest.first_hunt", 3, true);
        QuestState after = engine.onMobKilled(s, hunt, "mob.goviin_chono");
        assertEquals(3, after.progress());
        assertTrue(after.completed());
        assertFalse(after.active());
    }

    @Test
    void justCompletedDetectsTransition() {
        QuestState before = new QuestState("quest.first_hunt", 2, false);
        QuestState after = engine.onMobKilled(before, hunt, "mob.goviin_chono");
        assertTrue(engine.justCompleted(before, after));
    }
}
