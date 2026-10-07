package mn.suld.api.loot;

import mn.suld.api.item.ItemRarity;

import java.util.EnumMap;
import java.util.Map;

/** The rarities a loot tier produces, with relative weights. */
public record RarityBand(LootTier tier, Map<ItemRarity, Integer> weights) {

    public RarityBand {
        Map<ItemRarity, Integer> w = new EnumMap<>(ItemRarity.class);
        if (weights != null) weights.forEach((r, v) -> {
            if (v != null && v > 0 && r != ItemRarity.UNIQUE) w.put(r, v);
        });
        weights = java.util.Collections.unmodifiableMap(w);
    }

    /**
     * A rarity from this band within [{@code min}, {@code max}]. When the band has nothing in that range the closest
     * allowed rarity to the band is used (an item that is at least rare from a normal mob is rare, not nothing).
     */
    public ItemRarity pick(Rng rng, ItemRarity min, ItemRarity max) {
        int total = 0;
        for (Map.Entry<ItemRarity, Integer> e : weights.entrySet()) if (in(e.getKey(), min, max)) total += e.getValue();
        if (total <= 0) {
            ItemRarity lo = null, hi = null;
            for (ItemRarity r : weights.keySet()) {
                if (lo == null || r.ordinal() < lo.ordinal()) lo = r;
                if (hi == null || r.ordinal() > hi.ordinal()) hi = r;
            }
            if (hi == null || hi.ordinal() < min.ordinal()) return min; // the band is below the range: the range's floor
            if (lo.ordinal() > max.ordinal()) return max;               // the band is above the range: the range's top
            return min;
        }
        int x = rng.nextInt(total);
        for (Map.Entry<ItemRarity, Integer> e : weights.entrySet()) {
            if (!in(e.getKey(), min, max)) continue;
            x -= e.getValue();
            if (x < 0) return e.getKey();
        }
        return min;
    }

    private static boolean in(ItemRarity r, ItemRarity min, ItemRarity max) {
        return r.ordinal() >= min.ordinal() && r.ordinal() <= max.ordinal();
    }
}
