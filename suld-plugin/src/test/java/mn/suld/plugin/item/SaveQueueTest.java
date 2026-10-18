package mn.suld.plugin.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link PlayerDataSaves#soon}'s queue: one player a tick, in payout order, each player once. */
class SaveQueueTest {

    @Test
    void playersComeOutInTheOrderTheyWerePaid() {
        SaveQueue q = new SaveQueue();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        assertTrue(q.add(a));
        assertTrue(q.add(b));
        assertTrue(q.add(c));
        assertEquals(a, q.next());
        assertEquals(b, q.next());
        assertEquals(c, q.next());
        assertNull(q.next(), "nobody left: the drain stops");
    }

    @Test
    void aPlayerPaidTwiceBeforeTheirTurnIsWrittenOnce() {
        SaveQueue q = new SaveQueue();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        q.add(a);
        q.add(b);
        assertFalse(q.add(a), "already waiting");
        assertEquals(2, q.size());
        assertEquals(a, q.next(), "keeps the first place in the queue");
        assertTrue(q.add(a), "after the write a new payout queues them again");
        assertEquals(b, q.next());
        assertEquals(a, q.next());
    }
}
