package mn.suld.api.dungeon;

/** Current state of an active dungeon run. */
public enum DungeonRunState {
    /** Party has entered the instance; first wave not yet started. */
    ENTERING,
    /** A mob wave is in progress. */
    WAVE,
    /** All waves cleared; the boss fight is active. */
    BOSS,
    /** Run completed successfully. */
    COMPLETE,
    /** Run ended in failure (all party members dead or fled). */
    FAILED
}
