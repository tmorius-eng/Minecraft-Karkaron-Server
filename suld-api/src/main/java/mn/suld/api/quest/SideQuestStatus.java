package mn.suld.api.quest;

/** Where a player stands on one side quest (V32 {@code suld_side_quests.state}); no row = never taken. */
public enum SideQuestStatus {
    /** Taken, not yet handed in (its objective may already be met: then it is ready). */
    ACTIVE,
    /** Handed in and paid; a side quest is done once (none repeats). */
    DONE
}
