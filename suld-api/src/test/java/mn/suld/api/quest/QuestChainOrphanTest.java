package mn.suld.api.quest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** QS-03 (audit 2026-10-14): a renamed or removed chapter id never soft-locks the story or its chapter skill points. */
class QuestChainOrphanTest {

    // EXP grows along the chain, as ContentLoader prices it: the reward stands in for the chapter's level
    private static final QuestDefinition A = new QuestDefinition("q.a", "A", "", QuestType.KILL_MOB, "mob.wolf", 3, 100, 1);
    private static final QuestDefinition B = new QuestDefinition("q.b", "B", "", QuestType.REACH_LEVEL, "", 5, 200, 1);
    private static final QuestDefinition C = new QuestDefinition("q.c", "C", "", QuestType.DISCOVER_LOCATION, "region.gobi", 1, 300, 1);
    private static final QuestDefinition D = new QuestDefinition("q.d", "D", "", QuestType.COMPLETE_DUNGEON, "dungeon.den", 1, 400, 1);
    private final QuestChain chain = new QuestChain(List.of(A, B, C, D), Map.of("q.old_b", "q.b"));

    @Test
    void aFormerIdKeepsTheProgressOnTheCurrentChapter() {
        assertEquals(new QuestState("q.b", 3, false), chain.normalise(new QuestState("q.old_b", 3, false)));
        assertEquals(C.initialState(), chain.normalise(new QuestState("q.old_b", 5, true)), "a completed former chapter moves on");
    }

    @Test
    void aRemovedIdGoesToTheFirstChapterNotYetBehindThePlayer() {
        QuestState orphan = new QuestState("q.removed", 2, false);
        assertEquals(C.initialState(), chain.normalise(orphan, d -> d.expReward() < 250));
        assertEquals(2, chain.completedCount(chain.normalise(orphan, d -> d.expReward() < 250)), "A and B count as done");
        assertEquals(A.initialState(), chain.normalise(orphan, d -> false), "a fresh player starts at the beginning");
        QuestState past = chain.normalise(orphan, d -> true);
        assertEquals(new QuestState("q.d", 1, true), past, "past every chapter: the story is complete");
        assertEquals(4, chain.completedCount(past));
    }

    @Test
    void withoutAHintAnUnknownIdIsLeftAsItWas() {
        QuestState orphan = new QuestState("q.removed", 2, false);
        assertEquals(orphan, chain.normalise(orphan));
    }

    @Test
    void aliasesMustPointAtRealChaptersAndNotShadowThem() {
        assertThrows(IllegalArgumentException.class, () -> new QuestChain(List.of(A, B), Map.of("q.x", "q.nowhere")));
        assertThrows(IllegalArgumentException.class, () -> new QuestChain(List.of(A, B), Map.of("q.a", "q.b")));
    }
}
