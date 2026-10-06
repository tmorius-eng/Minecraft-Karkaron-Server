package mn.suld.api.quest;

/**
 * Immutable per-player progress on a single quest. Persisted with the profile.
 *
 * @param questId   the quest being tracked (empty string = no active quest)
 * @param progress  current objective count
 * @param completed whether the quest has been completed
 */
public record QuestState(String questId, int progress, boolean completed) {

    public static final QuestState NONE = new QuestState("", 0, false);

    public QuestState {
        questId = questId == null ? "" : questId;
        progress = Math.max(0, progress);
    }

    public boolean active() {
        return !questId.isEmpty() && !completed;
    }
}
