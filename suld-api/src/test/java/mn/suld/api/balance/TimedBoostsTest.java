package mn.suld.api.balance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimedBoostsTest {

    @Test
    void theStrongestSourceOfAKindCountsAndTheyNeverAddUp() {
        TimedBoosts b = new TimedBoosts();
        b.put(TimedBoosts.Kind.EXP, "ovoo", 1_000, 0.10);
        b.put(TimedBoosts.Kind.EXP, "tree", 2_000, 0.05);
        assertEquals(0.10, b.strongest(TimedBoosts.Kind.EXP, 500), 1e-9, "not 0.15");
        assertEquals(0.05, b.strongest(TimedBoosts.Kind.EXP, 1_500), 1e-9, "the weaker one counts once the stronger ends");
        assertEquals(0, b.strongest(TimedBoosts.Kind.EXP, 2_000), 1e-9, "ends at its time");
    }

    @Test
    void kindsAreApartAndASourceReplacesItself() {
        TimedBoosts b = new TimedBoosts();
        b.put(TimedBoosts.Kind.DAMAGE, "tree", 1_000, 0.03);
        assertEquals(0, b.strongest(TimedBoosts.Kind.EXP, 0), 1e-9);
        b.put(TimedBoosts.Kind.DAMAGE, "tree", 5_000, 0.02);
        assertEquals(0.02, b.strongest(TimedBoosts.Kind.DAMAGE, 0), 1e-9);
        assertEquals(5_000, b.until(TimedBoosts.Kind.DAMAGE, "tree", 0));
        assertEquals(0, b.until(TimedBoosts.Kind.DAMAGE, "ovoo", 0));
        assertFalse(b.isEmpty(4_999));
        b.put(TimedBoosts.Kind.DAMAGE, "tree", 9_000, 0);
        assertTrue(b.isEmpty(0), "a zero bonus removes the source");
    }
}
