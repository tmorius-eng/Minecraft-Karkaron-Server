package mn.suld.api.quest;

/**
 * Data-driven quest definition.
 *
 * @param id              stable quest id (e.g. "quest.first_hunt")
 * @param title           Mongolian title
 * @param description     Mongolian objective text
 * @param type            objective kind
 * @param targetId        objective target (e.g. mob id for KILL_MOB)
 * @param requiredCount   amount needed to complete
 * @param expReward       EXP granted on completion
 * @param currencyReward  currency granted on completion
 */
public record QuestDefinition(
        String id,
        String title,
        String description,
        QuestType type,
        String targetId,
        int requiredCount,
        long expReward,
        long currencyReward) {

    public QuestDefinition {
        if (requiredCount < 1) {
            throw new IllegalArgumentException("requiredCount must be >= 1: " + requiredCount);
        }
    }

    public QuestState initialState() {
        return new QuestState(id, 0, false);
    }
}
