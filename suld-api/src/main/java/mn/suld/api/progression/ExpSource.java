package mn.suld.api.progression;

/**
 * Where a grant of experience came from. Recorded with each grant so analytics
 * and (later) anti-exploit heuristics can reason about the experience economy.
 */
public enum ExpSource {
    MOB_KILL,
    ELITE_KILL,
    BOSS_KILL,
    QUEST,
    DUNGEON,
    WORLD_EVENT,
    DISCOVERY,
    ADMIN,
    OTHER
}
