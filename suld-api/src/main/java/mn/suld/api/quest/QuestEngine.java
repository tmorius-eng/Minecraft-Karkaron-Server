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
        return advance(state, def, QuestType.KILL_MOB, killedMobId, 1);
    }

    /**
     * Advance the active quest on any objective event.
     * <ul>
     *   <li>{@code KILL_MOB}: target = mob id, value = kills to add.</li>
     *   <li>{@code REACH_LEVEL}: target ignored, value = the player's level ({@code requiredCount} is the level).</li>
     *   <li>{@code COLLECT_ITEM}: target = item definition id, value = how many the player holds now.</li>
     *   <li>{@code DISCOVER_LOCATION} / {@code COMPLETE_DUNGEON}: target = region / dungeon id; a match completes.</li>
     * </ul>
     *
     * @return the updated state, or the same state if the event does not apply
     */
    public QuestState advance(QuestState state, QuestDefinition def, QuestType type, String target, long value) {
        if (state == null || def == null || !state.active() || !def.id().equals(state.questId()) || def.type() != type) {
            return state == null ? QuestState.NONE : state;
        }
        if (type != QuestType.REACH_LEVEL && !def.targetId().equals(target)) {
            return state;
        }
        int need = def.requiredCount();
        int progress = switch (type) {
            case KILL_MOB -> (int) Math.min(need, state.progress() + Math.max(0, value));
            case REACH_LEVEL, COLLECT_ITEM -> (int) Math.max(0, Math.min(need, value));
            case DISCOVER_LOCATION, COMPLETE_DUNGEON -> need;
        };
        if (type == QuestType.REACH_LEVEL) progress = Math.max(progress, state.progress());
        return new QuestState(state.questId(), progress, progress >= need);
    }

    /** Whether applying this event just completed the quest (transition to done). */
    public boolean justCompleted(QuestState before, QuestState after) {
        return before != null && after != null && !before.completed() && after.completed();
    }
}
