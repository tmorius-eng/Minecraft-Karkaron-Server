package mn.suld.plugin.item;

import mn.suld.api.item.Binding;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** IT-03: an ON_PICKUP item handed to a player (reward, craft, mailbox) binds to them on arrival. */
class PickupBindingTest {

    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static ItemInstance item(UUID boundTo) {
        return new ItemInstance("weapon.test", UUID.randomUUID(), ItemRarity.ANCIENT, 30, Map.of(), java.util.List.of(), false,
                boundTo, 0, "test", ItemInstance.SCHEMA_VERSION);
    }

    @Test
    void anOnPickupItemBindsToItsReceiverKeepingItsIdentity() {
        ItemInstance i = item(null);
        ItemInstance b = ItemService.pickupBound(i, Binding.ON_PICKUP, OWNER);
        assertEquals(OWNER, b.boundTo());
        assertEquals(i.uuid(), b.uuid());
        assertEquals(i.definitionId(), b.definitionId());
    }

    @Test
    void otherBindingsAreLeftForTheirOwnMoment() {
        ItemInstance i = item(null);
        assertNull(ItemService.pickupBound(i, Binding.ON_EQUIP, OWNER).boundTo()); // binds when worn
        assertNull(ItemService.pickupBound(i, Binding.NONE, OWNER).boundTo());
    }

    @Test
    void anAlreadyBoundItemOrNoReceiverChangesNothing() {
        UUID other = UUID.randomUUID();
        ItemInstance bound = item(other);
        assertSame(bound, ItemService.pickupBound(bound, Binding.ON_PICKUP, OWNER));
        ItemInstance free = item(null);
        assertSame(free, ItemService.pickupBound(free, Binding.ON_PICKUP, null)); // a mailbox for nobody yet
    }
}
