package mn.suld.plugin.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A redraw of the atlas keeps the marks it asks for again and hands back only the ones it dropped. */
class MarkPoolTest {

    @Test
    void aRedrawKeepsTheMarksItAsksForAgainAndDropsTheRest() {
        MarkPool<Object> pool = new MarkPool<>();
        Object city = new Object(), cave = new Object(), ovoo = new Object();
        pool.begin();
        assertNull(pool.take("poi:city"), "nothing kept yet: the first redraw makes every mark");
        pool.put("poi:city", city);
        pool.put("poi:cave", cave);
        pool.put("poi:ovoo", ovoo);
        assertTrue(pool.end().isEmpty());

        // a pan: the city and the cave are still on the wall, the ovoo slid off it
        pool.begin();
        assertSame(city, pool.take("poi:city"), "the same entity is moved, not made again");
        assertSame(cave, pool.take("poi:cave"));
        assertEquals(List.of(ovoo), pool.end(), "only the mark that left the wall is removed");
        assertEquals(2, pool.size());

        assertEquals(2, pool.clear().size(), "closing the atlas hands back every mark");
        assertEquals(0, pool.size());
    }

    @Test
    void twoMarksWithTheSameKeyInOneRedrawStayTwo() {
        MarkPool<Object> pool = new MarkPool<>();
        pool.begin();
        String first = pool.unique("label:Овоо");
        pool.put(first, new Object());
        String second = pool.unique("label:Овоо");
        assertEquals("label:Овоо#2", second);
        pool.put(second, new Object());
        assertTrue(pool.end().isEmpty());
        pool.begin();
        assertEquals("label:Овоо", pool.unique("label:Овоо"), "a new redraw starts the numbering again");
    }
}
