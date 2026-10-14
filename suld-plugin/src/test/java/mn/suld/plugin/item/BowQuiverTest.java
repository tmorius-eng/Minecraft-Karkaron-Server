package mn.suld.plugin.item;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** SULD-F01: the quiver goes back into the bag, and a full bag writes nothing. */
class BowQuiverTest {

    /** A stack whose amount the bag shrinks in place, as Bukkit's {@code addItem} does with a partly placed stack. */
    static final class Stack {
        int amount;

        Stack(int amount) {
            this.amount = amount;
        }
    }

    /** A bag with room for {@code room} arrows. */
    static Function<Stack, Collection<Stack>> bag(int[] room) {
        return s -> {
            int fit = Math.min(room[0], s.amount);
            room[0] -= fit;
            s.amount -= fit;
            return s.amount == 0 ? List.of() : List.of(s);
        };
    }

    static List<Stack> stash(int... amounts) {
        List<Stack> out = new ArrayList<>();
        for (int a : amounts) out.add(new Stack(a));
        return out;
    }

    @Test
    void aFullBagMovesNothingAndIsNotWritten() {
        assertNull(BowQuiver.restore(stash(64, 12), bag(new int[] {0}), s -> s.amount));
    }

    @Test
    void aPartialFitIsWrittenWithOnlyTheLeftover() {
        // 64 + 12 in the quiver, room for 70: the leftover 6 stays, the 70 are not counted twice as "nothing moved"
        List<Stack> left = BowQuiver.restore(stash(64, 12), bag(new int[] {70}), s -> s.amount);
        assertEquals(1, left.size());
        assertEquals(6, left.get(0).amount);
    }

    @Test
    void aBagWithRoomTakesTheWholeQuiver() {
        List<Stack> left = BowQuiver.restore(stash(64, 12), bag(new int[] {1000}), s -> s.amount);
        assertEquals(0, left.size());
    }
}
