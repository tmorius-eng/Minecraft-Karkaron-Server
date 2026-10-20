package mn.suld.plugin.content;

import mn.suld.api.achievement.Achievement;
import mn.suld.api.achievement.AchievementCatalog;
import mn.suld.api.achievement.Deed;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.SideQuestDefinition;
import mn.suld.api.world.site.HistoricSite;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An achievement that names its subjects (achievements.json) names real content: a misspelt chapter, site or side
 * quest would make the achievement one nobody can ever earn, and nothing else would notice.
 */
class AchievementSubjectsTest {

    @Test
    void everyNamedSubjectExists() {
        ContentPack pack = Content.pack();
        Map<Deed, Set<String>> known = Map.of(
                Deed.CHAPTER, pack.chapters().stream().map(QuestDefinition::id).collect(Collectors.toSet()),
                Deed.SITE, pack.historicSites().stream().map(HistoricSite::id).collect(Collectors.toSet()),
                Deed.SIDE_QUEST, pack.sideQuests().stream().map(SideQuestDefinition::id).collect(Collectors.toSet()));
        AchievementCatalog.Result r = AchievementCatalog.bundled();
        assertTrue(r.ok(), r.issues().toString());
        int checked = 0;
        for (Achievement a : r.catalog().all()) {
            Set<String> ids = known.get(a.criterion().deed());
            if (ids == null) continue;
            for (String s : a.criterion().subjects()) {
                assertTrue(ids.contains(s), a.id() + " counts " + a.criterion().deed() + " of an unknown " + s);
                checked++;
            }
            if (!a.criterion().subjects().isEmpty()) {
                assertTrue(a.criterion().target() <= a.criterion().subjects().size(), a.id() + " asks for more than its subjects");
            }
        }
        assertTrue(checked > 0, "no subject was checked");
    }
}
