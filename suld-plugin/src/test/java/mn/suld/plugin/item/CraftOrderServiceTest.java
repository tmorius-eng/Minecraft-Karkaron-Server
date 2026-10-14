package mn.suld.plugin.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SULD-F05: a filled order never falls on the ground. */
class CraftOrderServiceTest {

    @Test
    void anAlivePosterWithAFreeSlotGetsItInHand() {
        assertTrue(CraftOrderService.toHand(true, false, 0));
        assertTrue(CraftOrderService.toHand(true, false, 35));
    }

    @Test
    void aFullBagADeadPosterOrAnOfflineOneGoesToTheMailbox() {
        assertFalse(CraftOrderService.toHand(true, false, -1)); // firstEmpty() < 0: a full bag
        assertFalse(CraftOrderService.toHand(true, true, 3)); // on the death screen
        assertFalse(CraftOrderService.toHand(false, false, 3));
    }
}
