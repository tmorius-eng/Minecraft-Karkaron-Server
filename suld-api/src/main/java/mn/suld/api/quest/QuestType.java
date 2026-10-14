package mn.suld.api.quest;

/** Objective kinds a quest can track. Extended as content grows. */
public enum QuestType {
    KILL_MOB,
    REACH_LEVEL,
    COLLECT_ITEM,
    DISCOVER_LOCATION,
    /** Clear a dungeon ({@code targetId} = dungeon id) without being downed. */
    COMPLETE_DUNGEON,
    /** Investigate a named prop — a stele, a shrine, a clue ({@code targetId} = prop id in props.json). Audit 2026-10-14. */
    INSPECT_CLUE,
    /** Craft an item at the Дархан's table ({@code targetId} = the item definition made). Audit 2026-10-14. */
    CRAFT_ITEM,
    /** Perform an Ovoo blessing in a region ({@code targetId} = region id). Audit 2026-10-14. */
    OVOO_RITUAL
}
