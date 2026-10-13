package mn.suld.plugin.progress;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A player file that lost its clears gets them back from the database, and keeps what it has (audit 2026-10-13 F05). */
class PlayerMarksMergeTest {

    @Test
    void theDatabaseFillsWhatTheFileLost() {
        assertEquals("dungeon.khasar_den", PlayerMarks.merged(null, Set.of("dungeon.khasar_den")), "an empty (restored) file");
        assertEquals("dungeon.a,dungeon.b,dungeon.c", PlayerMarks.merged("dungeon.a", Set.of("dungeon.c", "dungeon.b", "dungeon.a")));
        assertEquals("dungeon.x,dungeon.a", PlayerMarks.merged("dungeon.x,dungeon.a", Set.of("dungeon.a")), "never reorders or removes");
        assertEquals("", PlayerMarks.merged("", Set.of()));
        assertEquals("dungeon.a", PlayerMarks.merged("dungeon.a", Set.of("bad,id", " ")), "a malformed id is skipped");
    }
}
