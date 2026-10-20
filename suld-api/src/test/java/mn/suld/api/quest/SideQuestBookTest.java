package mn.suld.api.quest;

import mn.suld.api.balance.Balance;
import mn.suld.api.balance.Rewards;
import mn.suld.api.clazz.PlayerClass;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SideQuestBookTest {

    private static SideQuestDefinition q(String id, QuestType type, String target, int count, int minLevel, Set<PlayerClass> classes, List<String> requires) {
        return new SideQuestDefinition(id, "Гарчиг " + id, "", "", "FICTION", "blacksmith", type, target, count, minLevel, classes, requires, 10, 5, null);
    }

    private final SideQuestDefinition kill = q("squest.kill", QuestType.KILL_MOB, "mob.chono", 3, 1, Set.of(), List.of());
    private final SideQuestDefinition baatar = q("squest.shield", QuestType.KILL_MOB, "mob.baavgai", 1, 15, Set.of(PlayerClass.BAATAR), List.of());
    private final SideQuestDefinition chain = q("squest.second", QuestType.DISCOVER_LOCATION, "site.urtuu_east", 1, 1, Set.of(), List.of("squest.kill"));
    private final SideQuestDefinition story = q("squest.story", QuestType.INSPECT_CLUE, "silver_tree", 1, 1, Set.of(), List.of("quest.first_hunt"));
    private final SideQuestBook book = new SideQuestBook(List.of(kill, baatar, chain, story));

    @Test
    void theGatesComeInTheirOrder() {
        Map<String, SideQuestRecord> mine = new HashMap<>();
        assertEquals(SideQuestBook.Gate.OPEN, book.gate(kill, 1, PlayerClass.MERGEN, c -> false, mine));
        assertEquals(SideQuestBook.Gate.LEVEL, book.gate(baatar, 14, PlayerClass.MERGEN, c -> false, mine), "the level before the class");
        assertEquals(SideQuestBook.Gate.CLASS, book.gate(baatar, 15, PlayerClass.MERGEN, c -> false, mine));
        assertEquals(SideQuestBook.Gate.OPEN, book.gate(baatar, 15, PlayerClass.BAATAR, c -> false, mine));
        assertEquals(SideQuestBook.Gate.REQUIRES_QUEST, book.gate(chain, 1, PlayerClass.BOO, c -> true, mine));
        assertEquals(SideQuestBook.Gate.REQUIRES_CHAPTER, book.gate(story, 1, PlayerClass.BOO, c -> false, mine));
        assertEquals(SideQuestBook.Gate.OPEN, book.gate(story, 1, PlayerClass.BOO, "quest.first_hunt"::equals, mine));

        mine.put(kill.id(), SideQuestRecord.started(kill.id(), 1));
        assertEquals(SideQuestBook.Gate.ACTIVE, book.gate(kill, 1, PlayerClass.MERGEN, c -> false, mine));
        assertEquals(SideQuestBook.Gate.REQUIRES_QUEST, book.gate(chain, 1, PlayerClass.MERGEN, c -> true, mine), "an active requirement is not done");
        mine.put(kill.id(), mine.get(kill.id()).handedIn(3, 2));
        assertEquals(SideQuestBook.Gate.DONE, book.gate(kill, 60, PlayerClass.MERGEN, c -> true, mine), "a side quest never repeats");
        assertEquals(SideQuestBook.Gate.OPEN, book.gate(chain, 1, PlayerClass.MERGEN, c -> true, mine));
    }

    @Test
    void threeAtOnceAtMost() {
        Map<String, SideQuestRecord> mine = new HashMap<>();
        for (String id : List.of("squest.a", "squest.b", "squest.c")) mine.put(id, SideQuestRecord.started(id, 1));
        assertEquals(SideQuestBook.MAX_ACTIVE, SideQuestBook.activeCount(mine));
        assertEquals(SideQuestBook.Gate.LIMIT, book.gate(kill, 1, PlayerClass.MERGEN, c -> true, mine));
        mine.put("squest.c", mine.get("squest.c").handedIn(1, 2));
        assertEquals(SideQuestBook.Gate.OPEN, book.gate(kill, 1, PlayerClass.MERGEN, c -> true, mine), "a handed-in quest frees its place");
    }

    @Test
    void killsAddUpToTheCountAndAnotherTargetDoesNothing() {
        SideQuestRecord r = SideQuestRecord.started(kill.id(), 1);
        r = SideQuestBook.advance(r, kill, QuestType.KILL_MOB, "mob.other", 1);
        assertEquals(0, r.progress());
        r = SideQuestBook.advance(r, kill, QuestType.KILL_MOB, "mob.chono", 2);
        assertFalse(r.ready(kill));
        r = SideQuestBook.advance(r, kill, QuestType.KILL_MOB, "mob.chono", 5);
        assertEquals(3, r.progress(), "never past the count");
        assertTrue(r.ready(kill));
    }

    @Test
    void aCollectQuestFollowsTheBagBothWays() {
        SideQuestDefinition collect = q("squest.collect", QuestType.COLLECT_ITEM, "item.toos", 6, 1, Set.of(), List.of());
        SideQuestRecord r = SideQuestRecord.started(collect.id(), 1);
        r = SideQuestBook.advance(r, collect, QuestType.COLLECT_ITEM, "item.toos", 9);
        assertEquals(6, r.progress());
        r = SideQuestBook.advance(r, collect, QuestType.COLLECT_ITEM, "item.toos", 2);
        assertEquals(2, r.progress(), "selling or dropping the items takes the quest back from ready");
    }

    @Test
    void aPlaceIsMetAtOnceAndADoneRecordNeverMoves() {
        SideQuestRecord r = SideQuestBook.advance(SideQuestRecord.started(chain.id(), 1), chain, QuestType.DISCOVER_LOCATION, "site.urtuu_east", 1);
        assertTrue(r.ready(chain));
        SideQuestRecord done = r.handedIn(1, 5);
        assertSame(done, SideQuestBook.advance(done, chain, QuestType.DISCOVER_LOCATION, "site.urtuu_east", 1));
        assertFalse(done.ready(chain));
    }

    @Test
    void refusalsAreMongolianAndNameWhatIsMissing() {
        assertNull(book.refusal(SideQuestBook.Gate.OPEN, kill, id -> id));
        assertTrue(book.refusal(SideQuestBook.Gate.LEVEL, baatar, id -> id).contains("15"));
        assertTrue(book.refusal(SideQuestBook.Gate.CLASS, baatar, id -> id).contains("Баатар"));
        assertTrue(book.refusal(SideQuestBook.Gate.REQUIRES_QUEST, chain, id -> id).contains(kill.title()));
        assertTrue(book.refusal(SideQuestBook.Gate.REQUIRES_CHAPTER, story, id -> "Анхны Ан").contains("Анхны Ан"));
        assertTrue(book.refusal(SideQuestBook.Gate.LIMIT, kill, id -> id).contains("3"));
    }

    @Test
    void aBookRefusesTwoQuestsWithOneIdAndFindsTheGiversQuests() {
        assertThrows(IllegalArgumentException.class, () -> new SideQuestBook(List.of(kill, kill)));
        assertEquals(4, book.offeredBy("blacksmith").size());
        assertTrue(book.offeredBy("tutorial").isEmpty());
    }

    @Test
    void aDefinitionRefusesWhatCannotWork() {
        assertThrows(IllegalArgumentException.class, () -> q("quest.wrong", QuestType.KILL_MOB, "mob.x", 1, 1, Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> q("squest.level", QuestType.REACH_LEVEL, "x", 10, 1, Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> q("squest.zero", QuestType.KILL_MOB, "mob.x", 0, 1, Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> q("squest.high", QuestType.KILL_MOB, "mob.x", 1, 61, Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SideQuestDefinition("squest.h", "T", "", "", "MAYBE", "g", QuestType.KILL_MOB, "m", 1, 1, Set.of(), List.of(), 0, 0, null));
    }

    @Test
    void sideQuestExpSitsBetweenADailyTaskAndAChapter() {
        var c = Balance.curveV2();
        for (int lv : new int[]{1, 10, 30, 59}) {
            long side = Rewards.sideQuestExp(c, lv);
            assertTrue(side > Rewards.dailyTaskExp(c, lv) && side < Rewards.chapterExp(c, lv), "level " + lv);
            assertEquals(Math.round(0.12 * Balance.need(c, lv)), side);
        }
        assertEquals(30 + 6 * 22, Rewards.sideQuestCoins(22));
        assertEquals(36, Rewards.sideQuestCoins(0), "level 0 is priced as 1");
    }
}
