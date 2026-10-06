package mn.suld.api.party;

/** Life-cycle state of a {@link Party}. */
public enum PartyState {
    /** Accepting new members; not yet committed to a dungeon. */
    FORMING,
    /** Leader has marked the group ready but has not entered a dungeon. */
    READY,
    /** Party is actively running a dungeon instance. */
    IN_DUNGEON,
    /** Party has been disbanded; the object is no longer active. */
    DISBANDED
}
