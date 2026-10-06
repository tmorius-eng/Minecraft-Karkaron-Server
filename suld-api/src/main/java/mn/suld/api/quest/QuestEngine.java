package mn.suld.api.quest;

/**
 * Pure quest-progression logic. Given a state, its definition, and an event, it
 * returns the next state. No side effects, so it is fully unit-tested; the
 * plugin's quest service applies rewards and persistence around it.
 */
public final class QuestEngine {

    /**
     * Advance a KILL_MOB quest when a mob dies.
     *
     * @return the updated state (unchanged if the quest is inactive or the mob
     *         does not match the objective)
     */
    public QuestState onMobKilled(QuestState state, QuestDefinition def, String killedMobId) {
        if (state == null || def == null || !state.active()) {
            return state == null ? QuestState.NONE : state;
        }
        if (def.type() != QuestType.KILL_MOB || !def.targetId().equals(killedMobId)) {
            return state;
        }
        int progress = Math.min(def.requiredCount(), state.progress() + 1);
        boolean completed = progress >= def.requiredCount();
        return new QuestState(state.questId(), progress, completed);
    }

    /** Whether applying this event just completed the quest (transition to done). */
    public boolean justCompleted(QuestState before, QuestState after) {
        return before != null && after != null && !before.completed() && after.completed();
    }
}
