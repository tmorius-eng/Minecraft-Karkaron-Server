package mn.suld.api.balance;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import mn.suld.api.item.StatRange;

import java.util.Map;

/**
 * Gear power: item level × the rarity's stat multiplier × roll quality (0.75 at the bottom of every range, 1.0 at
 * the top), summed over what is worn. Dungeon and Ascension gates compare it with {@link #par(int)}.
 */
public final class GearPower {

    private GearPower() {
    }

    public static double item(ItemDefinition def, ItemInstance i) {
        return Math.max(1, i.itemLevel()) * i.rarity().statMultiplier() * (0.75 + 0.25 * quality(def, i));
    }

    /** Average position of each rolled stat inside its range for the item's rarity and level, 0..1. */
    public static double quality(ItemDefinition def, ItemInstance i) {
        double sum = 0;
        int n = 0;
        for (Map.Entry<ItemStat, StatRange> e : def.stats().entrySet()) {
            double lo = def.minStat(e.getKey(), i.rarity(), i.itemLevel());
            double hi = def.maxStat(e.getKey(), i.rarity(), i.itemLevel());
            if (hi - lo < 1e-9) continue;
            double v = i.stat(e.getKey());
            sum += Math.max(0, Math.min(1, (v - lo) / (hi - lo)));
            n++;
        }
        return n == 0 ? 0.5 : sum / n;
    }

    /** The gear power the game expects at a level: 8 slots of average quality at the level's typical rarity. */
    public static double par(int level) {
        double r = level < 10 ? 0.5 : level < 20 ? 1.2 : level < 30 ? 2.0 : level < 40 ? 2.5 : level < 50 ? 3.0 : level < 60 ? 3.5 : 4.0;
        return 8 * level * rarityStatMultiplierAt(r) * 0.875;
    }

    /** Interpolated rarity stat multiplier for a fractional rarity ordinal. */
    public static double rarityStatMultiplierAt(double ordinal) {
        ItemRarity[] rs = ItemRarity.values();
        int lo = (int) Math.floor(ordinal);
        int hi = Math.min(lo + 1, 6);
        double f = ordinal - lo;
        return rs[lo].statMultiplier() * (1 - f) + rs[hi].statMultiplier() * f;
    }
}
