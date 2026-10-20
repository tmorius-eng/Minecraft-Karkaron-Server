package mn.suld.api.quest;

/**
 * One player's row for one side quest (V32 {@code suld_side_quests}).
 *
 * @param progress    the objective count (for a COLLECT quest: what the bag held at the last count)
 * @param completedAt epoch millis of the hand-in, or 0 while ACTIVE
 * @param paidAt      epoch millis the reward was claimed, or 0: a DONE row with 0 still owes its reward (the player left
 *                    before it was paid), and the next load pays it
 */
public record SideQuestRecord(String questId, SideQuestStatus status, int progress, long startedAt, long completedAt, long paidAt) {

    public SideQuestRecord {
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("a side quest record needs its quest id");
        if (status == null) throw new IllegalArgumentException(questId + ": a side quest record needs a status");
        progress = Math.max(0, progress);
    }

    public static SideQuestRecord started(String questId, long now) {
        return new SideQuestRecord(questId, SideQuestStatus.ACTIVE, 0, now, 0, 0);
    }

    public boolean active() {
        return status == SideQuestStatus.ACTIVE;
    }

    /** Handed in (its reward paid or still owed). */
    public boolean done() {
        return status == SideQuestStatus.DONE;
    }

    /** Handed in, its reward not yet paid. */
    public boolean owed() {
        return done() && paidAt == 0;
    }

    /** Taken and its objective met: the giver takes it back. */
    public boolean ready(SideQuestDefinition d) {
        return active() && progress >= d.requiredCount();
    }

    public SideQuestRecord withProgress(int p) {
        return new SideQuestRecord(questId, status, p, startedAt, completedAt, paidAt);
    }

    public SideQuestRecord handedIn(int finalProgress, long now) {
        return new SideQuestRecord(questId, SideQuestStatus.DONE, finalProgress, startedAt, now, 0);
    }

    public SideQuestRecord paid(long now) {
        return new SideQuestRecord(questId, status, progress, startedAt, completedAt, now);
    }
}
