package mn.suld.api.progress;

/**
 * Kinds of per-player progress marks (docs/KHURALDAI.md §Marks). A mark is a (player, kind, key) fact with a value:
 * the moment a dungeon was first cleared, a historic site first seen, or the last day an ovoo blessed the player.
 * Marks used to live only in the player's persistent data container; the database copy makes them queryable (the
 * chronicle, «Миний түүх», leaderboards) and survives a lost player file.
 */
public enum MarkKind {
    /** key = dungeon id, value = epoch millis of the first clear. */
    DUNGEON_CLEAR,
    /** key = historic site id, value = epoch millis of the first visit. */
    SITE_SEEN,
    /** key = ovoo (area) id, value = the epoch day (server zone) of the last blessing. */
    OVOO_DAY;

    public static MarkKind parse(String s) {
        for (MarkKind k : values()) if (k.name().equalsIgnoreCase(s)) return k;
        throw new IllegalArgumentException("unknown mark kind " + s);
    }
}
