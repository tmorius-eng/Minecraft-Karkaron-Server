package mn.suld.api.quest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestChainTest {

    private static final QuestDefinition HUNT = new QuestDefinition("q.hunt", "Hunt", "", QuestType.KILL_MOB, "mob.wolf", 3, 10, 1);
    private static final QuestDefinition LEVEL = new QuestDefinition("q.level", "Level", "", QuestType.REACH_LEVEL, "", 5, 10, 1);
    private static final QuestDefinition GOBI = new QuestDefinition("q.gobi", "Gobi", "", QuestType.DISCOVER_LOCATION, "region.gobi", 1, 10, 1);
    private static final QuestDefinition PELTS = new QuestDefinition("q.pelts", "Pelts", "", QuestType.COLLECT_ITEM, "item.pelt", 5, 10, 1);
    private static final QuestDefinition DEN = new QuestDefinition("q.den", "Den", "", QuestType.COMPLETE_DUNGEON, "dungeon.den", 1, 10, 1);
    private final QuestChain chain = new QuestChain(List.of(HUNT, LEVEL, GOBI, PELTS, DEN));
    private final QuestEngine engine = new QuestEngine();

    @Test
    void normaliseStartsAndCatchesUp() {
        assertEquals(HUNT.initialState(), chain.normalise(QuestState.NONE));
        assertEquals(LEVEL.initialState(), chain.normalise(new QuestState("q.hunt", 3, true)));
        QuestState active = new QuestState("q.level", 2, false);
        assertEquals(active, chain.normalise(active));
        QuestState done = new QuestState("q.den", 1, true);
        assertEquals(done, chain.normalise(done)); // the end of the story stays completed
        assertEquals(5, chain.completedCount(done));
        assertEquals(1, chain.completedCount(active));
        assertTrue(chain.next("q.den").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new QuestChain(List.of(HUNT, HUNT)));
    }

    @Test
    void engineAdvancesEveryObjectiveKind() {
        QuestState s = engine.advance(HUNT.initialState(), HUNT, QuestType.KILL_MOB, "mob.wolf", 1);
        assertEquals(1, s.progress());
        assertEquals(s, engine.advance(s, HUNT, QuestType.KILL_MOB, "mob.bear", 1));
        assertEquals(s, engine.advance(s, HUNT, QuestType.REACH_LEVEL, "", 99)); // wrong kind
        assertTrue(engine.advance(new QuestState("q.hunt", 2, false), HUNT, QuestType.KILL_MOB, "mob.wolf", 1).completed());

        QuestState l = engine.advance(LEVEL.initialState(), LEVEL, QuestType.REACH_LEVEL, null, 3);
        assertEquals(3, l.progress());
        assertFalse(l.completed());
        assertTrue(engine.advance(l, LEVEL, QuestType.REACH_LEVEL, null, 7).completed());

        assertFalse(engine.advance(GOBI.initialState(), GOBI, QuestType.DISCOVER_LOCATION, "region.altai", 1).completed());
        assertTrue(engine.advance(GOBI.initialState(), GOBI, QuestType.DISCOVER_LOCATION, "region.gobi", 1).completed());

        QuestState p = engine.advance(PELTS.initialState(), PELTS, QuestType.COLLECT_ITEM, "item.pelt", 4);
        assertEquals(4, p.progress());
        assertEquals(2, engine.advance(p, PELTS, QuestType.COLLECT_ITEM, "item.pelt", 2).progress()); // tracks what is held
        assertTrue(engine.advance(p, PELTS, QuestType.COLLECT_ITEM, "item.pelt", 9).completed());

        assertTrue(engine.advance(DEN.initialState(), DEN, QuestType.COMPLETE_DUNGEON, "dungeon.den", 1).completed());
        QuestState finished = new QuestState("q.den", 1, true);
        assertEquals(finished, engine.advance(finished, DEN, QuestType.COMPLETE_DUNGEON, "dungeon.den", 1));
    }
}
