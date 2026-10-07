package mn.suld.api.loot;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A weighted loot table (data: {@code items/loot.json}).
 *
 * <ul>
 *   <li>{@code guaranteed} entries always drop;</li>
 *   <li>then {@code minRolls..maxRolls} weighted picks among {@code entries} (and "nothing", {@code nothingWeight});</li>
 *   <li>then every {@code rare} drop is tried with its own chance, raised by the receiver's loot bonus.</li>
 * </ul>
 * An entry names one item, or a {@link Pool} of any lootable equipment of some categories near the loot level. Unique
 * items can never be in a table.
 */
public record LootTable(String id, LootTier tier, int minRolls, int maxRolls, int nothingWeight,
                        List<Entry> guaranteed, List<Entry> entries, List<Rare> rare) {

    /** Any lootable item of these categories whose level requirement fits the loot level. */
    public record Pool(Set<ItemType.Category> categories) {
        public Pool {
            categories = categories == null || categories.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(categories));
        }
    }

    /**
     * @param itemId      item to drop (or null with a pool)
     * @param pool        equipment pool (or null with an item)
     * @param weight      relative weight among the table's entries
     * @param minQty      quantity (stack size for stackables, number of items for gear)
     * @param maxQty      quantity
     * @param minRarity   rarity restriction (null = the tier band decides)
     * @param maxRarity   rarity restriction
     * @param levelSpread item level = loot level ± spread (level scaling)
     * @param classWeight weight multiplier per receiver class
     */
    public record Entry(String itemId, Pool pool, int weight, int minQty, int maxQty, ItemRarity minRarity, ItemRarity maxRarity,
                        int levelSpread, Map<PlayerClass, Double> classWeight) {
        public Entry {
            if ((itemId == null) == (pool == null)) throw new IllegalArgumentException("entry needs exactly one of item or pool");
            weight = Math.max(0, weight);
            minQty = Math.max(1, minQty);
            maxQty = Math.max(minQty, maxQty);
            levelSpread = Math.max(0, levelSpread);
            Map<PlayerClass, Double> cw = new EnumMap<>(PlayerClass.class);
            if (classWeight != null) cw.putAll(classWeight);
            classWeight = java.util.Collections.unmodifiableMap(cw);
        }

        public static Entry item(String itemId, int weight, int min, int max) {
            return new Entry(itemId, null, weight, min, max, null, null, 0, null);
        }

        public double weightFor(PlayerClass c) {
            return weight * (c == null ? 1.0 : classWeight.getOrDefault(c, 1.0));
        }
    }

    /** An extra independent chance (percent of 1.0 = always) for a rare drop. */
    public record Rare(Entry entry, double chance) {
        public Rare {
            Objects.requireNonNull(entry, "entry");
            chance = Math.max(0, Math.min(1, chance));
        }
    }

    /** Whether this table can drop the item at all (named in any entry; pools are not counted). */
    public boolean mayDrop(String itemId) {
        for (Entry e : guaranteed) if (itemId.equals(e.itemId())) return true;
        for (Entry e : entries) if (itemId.equals(e.itemId())) return true;
        for (Rare r : rare) if (itemId.equals(r.entry().itemId())) return true;
        return false;
    }

    public LootTable {
        Objects.requireNonNull(id, "id");
        tier = tier == null ? LootTier.NORMAL : tier;
        minRolls = Math.max(0, minRolls);
        maxRolls = Math.max(minRolls, maxRolls);
        nothingWeight = Math.max(0, nothingWeight);
        guaranteed = guaranteed == null ? List.of() : List.copyOf(guaranteed);
        entries = entries == null ? List.of() : List.copyOf(entries);
        rare = rare == null ? List.of() : List.copyOf(rare);
    }
}
