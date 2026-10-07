package mn.suld.api.loot;

import mn.suld.api.item.ItemInstance;

/** One stack of loot: an item and how many (more than one only for stackable items). */
public record LootDrop(ItemInstance item, int amount) {
    public LootDrop {
        amount = Math.max(1, amount);
    }
}
