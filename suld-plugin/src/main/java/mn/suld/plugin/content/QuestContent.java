package mn.suld.plugin.content;

import mn.suld.api.quest.QuestChain;


/**
 * The main storyline «Сүлдний Зам» (Path of the Sülde), read from {@code content/quests.json}: chapters that walk a
 * new player from the gates of Kharkhorum through the Kherlen steppe, Khasar's Den, the Gobi, the Khangai forests and
 * up to the Altai peaks, one region at a time. Each chapter starts as soon as the previous one ends. Places, the relay
 * posts (өртөө), the decimal army (аравт, зуут, мянгат) and the trade caravans are real; the creatures, the dungeons
 * and the people are SÜLD fiction.
 */
public final class QuestContent {

    private QuestContent() {
    }

    /** Who gives the chapter and where to go — shown in /quest and the quest menu. */
    public record Lore(String giver, String hint) {
    }

    /** The chapters in order, with the EXP the rules give each one (ContentLoader: 35 % of a level at its level). */
    public static final QuestChain STORY = new QuestChain(Content.pack().chapters());

    /** The story of a chapter, told on its card in the story map (/quest). */
    public static String story(String questId) {
        return Content.pack().story().getOrDefault(questId, "");
    }

    public static Lore lore(String questId) {
        return Content.pack().lore().getOrDefault(questId, new Lore("Хархорум", ""));
    }
}
