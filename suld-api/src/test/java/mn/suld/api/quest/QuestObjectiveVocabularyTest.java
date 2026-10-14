package mn.suld.api.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The objective kinds added by audit 2026-10-14: inspect a clue, craft an item, perform an Ovoo ritual. */
class QuestObjectiveVocabularyTest {

    private final QuestEngine engine = new QuestEngine();

    @Test
    void inspectingTheNamedPropCompletesTheChapter() {
        QuestDefinition d = new QuestDefinition("quest.stele", "Stele", "", QuestType.INSPECT_CLUE, "deer_stone", 1, 10, 0);
        QuestState s = d.initialState();
        assertEquals(s, engine.advance(s, d, QuestType.INSPECT_CLUE, "stone_man", 1), "another prop changes nothing");
        QuestState done = engine.advance(s, d, QuestType.INSPECT_CLUE, "deer_stone", 1);
        assertTrue(done.completed());
        assertTrue(engine.justCompleted(s, done));
    }

    @Test
    void craftingAddsUpToTheCount() {
        QuestDefinition d = new QuestDefinition("quest.forge", "Forge", "", QuestType.CRAFT_ITEM, "armor.esgii.helmet", 2, 10, 0);
        QuestState s = engine.advance(d.initialState(), d, QuestType.CRAFT_ITEM, "armor.esgii.helmet", 1);
        assertEquals(1, s.progress());
        assertFalse(s.completed());
        assertEquals(s, engine.advance(s, d, QuestType.CRAFT_ITEM, "weapon.talyn_ild", 1), "another item changes nothing");
        QuestState done = engine.advance(s, d, QuestType.CRAFT_ITEM, "armor.esgii.helmet", 5);
        assertEquals(2, done.progress(), "never past the count");
        assertTrue(done.completed());
    }

    @Test
    void anOvooBlessingInTheTargetLandCompletesTheChapter() {
        QuestDefinition d = new QuestDefinition("quest.ovoo", "Ovoo", "", QuestType.OVOO_RITUAL, "area.kherlen", 1, 10, 0);
        assertFalse(engine.advance(d.initialState(), d, QuestType.OVOO_RITUAL, "area.gobi", 1).completed());
        assertTrue(engine.advance(d.initialState(), d, QuestType.OVOO_RITUAL, "area.kherlen", 1).completed());
        assertFalse(engine.advance(d.initialState(), d, QuestType.DISCOVER_LOCATION, "area.kherlen", 1).completed(), "the kind must match");
    }
}
