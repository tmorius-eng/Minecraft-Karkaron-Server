package mn.suld.api.achievement;

import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.CosmeticCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementTest {

    @Test
    void aCountGrowsABestStandsASeenDeedCountsOnce() {
        DeedBook b = new DeedBook();
        assertEquals(1, b.record(Deed.DUNGEON_CLEAR, "dungeon.khasar_den", 1));
        assertEquals(2, b.record(Deed.DUNGEON_CLEAR, "dungeon.khasar_den", 2));
        assertEquals(3, b.value(Deed.DUNGEON_CLEAR, "dungeon.khasar_den"));
        assertEquals(30, b.record(Deed.LEVEL, "", 30));
        assertEquals(0, b.record(Deed.LEVEL, "", 25), "a lower level is no news");
        assertEquals(5, b.record(Deed.LEVEL, "", 35));
        assertEquals(1, b.record(Deed.SITE, "site.aurag", 1));
        assertEquals(0, b.record(Deed.SITE, "site.aurag", 1), "seen once is enough");
        assertEquals(0, b.record(Deed.MOB_KILL, "mob.goviin_chono", 0), "nothing recorded for nothing");
    }

    @Test
    void theMeasuresReadTheBook() {
        DeedBook b = new DeedBook(Map.of(Deed.CRAFT_LEVEL.key("TOMRIIN_DARKHAN"), 50L, Deed.CRAFT_LEVEL.key("ESGIICH"), 51L,
                Deed.CRAFT_LEVEL.key("ADUUCHIN"), 12L, Deed.MOB_KILL.key("mob.goviin_chono"), 60L, Deed.MOB_KILL.key("mob.saaral_chono"), 45L,
                Deed.MOB_KILL.key("mob.deeremchin"), 500L));
        List<String> crafts = List.of("TOMRIIN_DARKHAN", "EMIIN_BARIACH", "ADUUCHIN", "ESGIICH");
        Criterion each = new Criterion(Deed.CRAFT_LEVEL, crafts, Criterion.Measure.EACH, 50);
        assertEquals(2, b.progress(each), "two of four at 50");
        assertFalse(b.met(each));
        Criterion wolves = new Criterion(Deed.MOB_KILL, List.of("mob.goviin_chono", "mob.saaral_chono"), Criterion.Measure.TOTAL, 100);
        assertEquals(105, b.progress(wolves), "only the listed mobs count");
        assertTrue(b.met(wolves));
        assertEquals(51, b.progress(new Criterion(Deed.CRAFT_LEVEL, List.of(), Criterion.Measure.BEST, 25)));
        assertEquals(3, b.progress(new Criterion(Deed.MOB_KILL, List.of(), Criterion.Measure.DISTINCT, 3)));
        b.record(Deed.CRAFT_LEVEL, "EMIIN_BARIACH", 50);
        b.record(Deed.CRAFT_LEVEL, "ADUUCHIN", 50);
        assertTrue(b.met(each));
    }

    @Test
    void aCriterionThatCannotBeMetIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Criterion(Deed.CRAFT_LEVEL, List.of(), Criterion.Measure.EACH, 50));
        assertThrows(IllegalArgumentException.class, () -> new Criterion(Deed.SITE, List.of("site.aurag"), Criterion.Measure.DISTINCT, 2));
        assertThrows(IllegalArgumentException.class, () -> new Criterion(Deed.LEVEL, List.of(), Criterion.Measure.BEST, 0));
    }

    @Test
    void theBundledAchievementsReadCleanAndTheirTitlesAreTheirsAlone() {
        AchievementCatalog.Result r = AchievementCatalog.bundled();
        assertTrue(r.ok(), r.issues().toString());
        assertTrue(r.catalog().all().size() >= 30, r.catalog().all().size() + " achievements");
        Set<String> rewards = new HashSet<>();
        for (Achievement a : r.catalog().all()) {
            if (a.basis() != Achievement.Basis.INSPIRED) assertFalse(a.source().isBlank(), a.id() + " names no source");
            if (a.reward() == null) continue;
            Cosmetic c = CosmeticCatalog.byId(a.reward()).orElse(null);
            assertTrue(c != null, a.id() + " rewards an unknown cosmetic " + a.reward());
            assertEquals("achievement", c.source(), a.reward() + " is earned, never sold");
            assertEquals(0, c.creditPrice(), a.reward());
            assertTrue(rewards.add(a.reward()), a.reward() + " is the reward of two achievements");
        }
        for (Cosmetic c : CosmeticCatalog.ALL) {
            if ("achievement".equals(c.source())) assertTrue(rewards.contains(c.id()), c.id() + " is no achievement's reward");
        }
    }

    @Test
    void aRecordUnlocksOnlyWhatItCompletes() {
        AchievementCatalog cat = AchievementCatalog.bundled().catalog();
        DeedBook b = new DeedBook();
        Set<String> unlocked = new HashSet<>();
        b.record(Deed.DUNGEON_CLEAR, "dungeon.khasar_den", 1);
        List<Achievement> first = AchievementCatalog.newlyMet(cat.on(Deed.DUNGEON_CLEAR), b, unlocked);
        assertEquals(List.of("ach.battle.first_clear"), first.stream().map(Achievement::id).toList());
        first.forEach(a -> unlocked.add(a.id()));
        for (int i = 0; i < 11; i++) b.record(Deed.DUNGEON_CLEAR, "dungeon.khasar_den", 1);
        assertTrue(AchievementCatalog.newlyMet(cat.on(Deed.DUNGEON_CLEAR), b, unlocked).isEmpty(), "12 clears: not 13 yet");
        b.record(Deed.DUNGEON_CLEAR, "dungeon.govi_bulsh", 1);
        assertEquals(List.of("ach.battle.thirteen_kurens"), AchievementCatalog.newlyMet(cat.on(Deed.DUNGEON_CLEAR), b, unlocked).stream().map(Achievement::id).toList());
    }

    @Test
    void aBrokenFileIsRefusedWithItsProblemsNamed() {
        String doc = """
                {"achievements": [
                  {"id": "ach.story.a", "category": "STORY", "name": "A", "description": "a", "basis": "INSPIRED",
                   "criterion": {"deed": "CHAPTER", "measure": "DISTINCT", "target": 1, "subjects": ["quest.first_hunt"]}},
                  {"id": "ach.story.a", "category": "STORY", "name": "B", "description": "b", "basis": "INSPIRED",
                   "criterion": {"deed": "CHAPTER", "measure": "DISTINCT", "target": 1}},
                  {"id": "ach.battle.b", "category": "STORY", "name": "C", "description": "c", "basis": "INSPIRED",
                   "criterion": {"deed": "LEVEL", "measure": "BEST", "target": 10}},
                  {"id": "ach.lands.c", "category": "LANDS", "name": "D", "description": "d", "basis": "VERIFIED",
                   "criterion": {"deed": "SITE", "measure": "DISTINCT", "target": 1}},
                  {"id": "ach.lands.d", "category": "LANDS", "name": "E", "description": "e", "basis": "INSPIRED",
                   "criterion": {"deed": "SITE", "measure": "TOTAL", "target": 3}},
                  {"id": "ach.lands.e", "category": "LANDS", "name": "F", "description": "f", "basis": "INSPIRED",
                   "criterion": {"deed": "SAILING", "measure": "TOTAL", "target": 3}}
                ]}""";
        AchievementCatalog.Result r = AchievementCatalog.parse(doc);
        assertFalse(r.ok());
        Map<String, String> why = new HashMap<>();
        for (AchievementCatalog.Issue i : r.issues()) why.put(i.where(), i.reason());
        assertTrue(why.get("ach.story.a").contains("duplicate"), why.toString());
        assertTrue(why.get("ach.battle.b").contains("ach.story."), why.toString());
        assertTrue(why.get("ach.lands.c").contains("source"), why.toString());
        assertTrue(why.get("ach.lands.d").contains("DISTINCT"), why.toString());
        assertTrue(why.containsKey("ach.lands.e"), why.toString());
        assertTrue(r.catalog().all().isEmpty(), "a broken file is not used");
    }
}
