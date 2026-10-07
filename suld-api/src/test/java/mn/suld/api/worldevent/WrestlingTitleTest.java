package mn.suld.api.worldevent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WrestlingTitleTest {

    @Test
    void titlesClimbInTheTraditionalOrder() {
        assertEquals(WrestlingTitle.NONE, WrestlingTitle.forWins(0));
        assertEquals(WrestlingTitle.NONE, WrestlingTitle.forWins(2));
        assertEquals(WrestlingTitle.NACHIN, WrestlingTitle.forWins(3));
        assertEquals(WrestlingTitle.KHARTSAGA, WrestlingTitle.forWins(9));
        assertEquals(WrestlingTitle.ZAAN, WrestlingTitle.forWins(10));
        assertEquals(WrestlingTitle.ARSLAN, WrestlingTitle.forWins(39));
        assertEquals(WrestlingTitle.AVARGA, WrestlingTitle.forWins(400));
        assertEquals(WrestlingTitle.GARID, WrestlingTitle.ZAAN.next());
        assertNull(WrestlingTitle.AVARGA.next());
        for (int i = 1; i < WrestlingTitle.values().length; i++) {
            assertTrue(WrestlingTitle.values()[i].wins() > WrestlingTitle.values()[i - 1].wins());
        }
    }

    @Test
    void outOfTheRingOnTheGroundPlaneOrByFalling() {
        assertFalse(WrestlingTitle.outOfRing(3, 0, 2, 4.5, 1.5));
        assertTrue(WrestlingTitle.outOfRing(4, 0, 3, 4.5, 1.5));
        assertFalse(WrestlingTitle.outOfRing(0, 3, 0, 4.5, 1.5)); // a jump is not a loss
        assertTrue(WrestlingTitle.outOfRing(0, -2, 0, 4.5, 1.5));
    }
}
