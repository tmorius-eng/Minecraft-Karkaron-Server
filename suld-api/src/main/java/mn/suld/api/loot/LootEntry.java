package mn.suld.api.loot;

import mn.suld.api.item.ItemDefinition;

/**
 * One possible drop in a {@link LootTable}.
 *
 * @param definition   item template to mint
 * @param dropChance   independent probability this entry drops, in {@code [0,1]}
 * @param minItemLevel inclusive lower bound for the rolled item level
 * @param maxItemLevel inclusive upper bound for the rolled item level
 */
public record LootEntry(ItemDefinition definition, double dropChance, int minItemLevel, int maxItemLevel) {

    public LootEntry {
        if (minItemLevel < 1 || maxItemLevel < minItemLevel) {
            throw new IllegalArgumentException("invalid item level range: " + minItemLevel + ".." + maxItemLevel);
        }
        dropChance = Math.max(0.0, Math.min(1.0, dropChance));
    }
}
