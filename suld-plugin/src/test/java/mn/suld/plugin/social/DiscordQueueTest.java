package mn.suld.plugin.social;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordQueueTest {

    @Test
    void wholeLinesAreBatchedIntoOneMessageUpToTheLimit() {
        DiscordQueue q = new DiscordQueue();
        q.offer("one");
        q.offer("two");
        q.offer("x".repeat(1895));
        assertEquals("one\ntwo", q.next().orElseThrow(), "7 + 1 + 1895 > 1900: the third line waits for the next message");
        assertEquals(1895, q.next().orElseThrow().length());
        assertTrue(q.next().isEmpty());
    }

    @Test
    void aSingleLongLineIsCutToTheLimit() {
        DiscordQueue q = new DiscordQueue();
        q.offer("y".repeat(5000));
        String m = q.next().orElseThrow();
        assertEquals(DiscordQueue.MAX_CONTENT, m.length());
        assertTrue(m.endsWith("…"));
    }

    @Test
    void blankLinesAreIgnoredAndTheBacklogIsBounded() {
        DiscordQueue q = new DiscordQueue();
        assertFalse(q.offer("  "));
        assertFalse(q.offer(null));
        for (int i = 0; i < DiscordQueue.MAX_PENDING + 7; i++) q.offer("line " + i);
        assertEquals(DiscordQueue.MAX_PENDING, q.size());
        assertEquals(7, q.dropped());
        String last = null;
        while (true) {
            var m = q.next();
            if (m.isEmpty()) break;
            last = m.get();
        }
        assertTrue(last != null && last.endsWith("(… 7 мөр орхигдов)"), "the last message says what went missing: " + last);
        assertEquals(0, q.dropped());
    }
}
