package mn.suld.api.quest;

import mn.suld.api.clazz.PlayerClass;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A side quest (content/side_quests.json, docs/SIDE_QUESTS.md): an NPC gives it, the player meets its one objective
 * (the main story's objective kinds), then hands it in to the same NPC. It is taken at most once and never repeats.
 *
 * @param giverNpcId     the npcs.json entry that gives it and takes it back
 * @param minLevel       the character level needed to take it (its rewards are priced at this level)
 * @param classes        the classes it is open to; empty = every class
 * @param requires       what must be done first: a story chapter ({@code quest.*}) or another side quest ({@code squest.*})
 * @param lootTableId    an extra loot table rolled at hand-in, or null
 * @param history        VERIFIED / INSPIRED / FICTION, shown on its page (the story's events are SÜLD's own)
 */
public record SideQuestDefinition(String id, String title, String description, String story, String history, String giverNpcId,
                                  QuestType type, String targetId, int requiredCount, int minLevel, Set<PlayerClass> classes,
                                  List<String> requires, long expReward, long currencyReward, String lootTableId) {

    public static final Pattern ID = Pattern.compile("squest\\.[a-z0-9_]{2,40}");
    public static final Set<String> HISTORY = Set.of("VERIFIED", "INSPIRED", "FICTION");
    /** The objective kinds a side quest may use (reaching a level is not a task to hand in). */
    public static final Set<QuestType> TYPES = Set.of(QuestType.KILL_MOB, QuestType.COLLECT_ITEM, QuestType.DISCOVER_LOCATION,
            QuestType.COMPLETE_DUNGEON, QuestType.INSPECT_CLUE, QuestType.CRAFT_ITEM, QuestType.OVOO_RITUAL);

    public SideQuestDefinition {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("a side quest id looks like squest.some_name: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException(id + ": a side quest needs a title");
        if (giverNpcId == null || giverNpcId.isBlank()) throw new IllegalArgumentException(id + ": a side quest needs a giver");
        if (type == null || !TYPES.contains(type)) throw new IllegalArgumentException(id + ": objective " + type + " is not one a side quest uses");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException(id + ": a side quest needs a target");
        if (requiredCount < 1) throw new IllegalArgumentException(id + ": count must be at least 1");
        if (minLevel < 1 || minLevel > 60) throw new IllegalArgumentException(id + ": minLevel must be 1..60");
        if (history == null || !HISTORY.contains(history)) throw new IllegalArgumentException(id + ": history must be VERIFIED, INSPIRED or FICTION");
        if (expReward < 0 || currencyReward < 0) throw new IllegalArgumentException(id + ": rewards cannot be negative");
        description = description == null ? "" : description;
        story = story == null ? "" : story;
        classes = classes == null ? Set.of() : Set.copyOf(classes);
        requires = requires == null ? List.of() : List.copyOf(requires);
        lootTableId = lootTableId == null || lootTableId.isBlank() ? null : lootTableId;
    }

    /** The objective as the quest engine reads it. */
    public QuestDefinition objective() {
        return new QuestDefinition(id, title, description, type, targetId, requiredCount, expReward, currencyReward);
    }

    public boolean openTo(PlayerClass c) {
        return classes.isEmpty() || (c != null && classes.contains(c));
    }
}
