package mn.suld.api.balance;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;

/** Coin sinks and sources of progression v2 (docs/ECONOMY_BALANCE_SPEC.md). */
public final class Economy {

    private Economy() {
    }

    /**
     * Merchant price: legendary and above cannot be sold (salvage only); the rarity multiplier is capped at ×5 and the
     * level factor is 1 + L/30.
     */
    public static long sellPrice(ItemDefinition def, ItemInstance i) {
        if (def.sellValue() <= 0 || i.soulbound() || i.rarity() == ItemRarity.UNIQUE
                || i.rarity().ordinal() >= ItemRarity.LEGENDARY.ordinal()) return 0;
        return Math.round(def.sellValue() * Math.min(5, i.rarity().sellMultiplier()) * (1 + i.itemLevel() / 30.0));
    }

    /** Reforge cost grows with the square of the item level. */
    public static long reforgeCoins(int itemLevel) {
        return Math.round(0.6 * itemLevel * itemLevel + 25 * itemLevel + 25);
    }

    /** Repair spending of an hour of play at a level (the sim's sink budget). */
    public static double repairPerHour(int level) {
        return 15 + 4.0 * level;
    }

    /** Skill points: 1 per level, 1 per 3 chapters (≤ 14), 1 per 2 regions (≤ 4), 1 per Ascension rank. */
    public static int skillPoints(int level, int chapters, int regions, int ascension) {
        return Math.max(0, level - 1) + Math.min(14, Math.max(0, chapters) / 3) + Math.min(4, Math.max(0, regions) / 2) + Math.max(0, ascension);
    }

    /** Respec cost per point. */
    public static long respecCoinsPerPoint(int level) {
        return Math.round(25 * (1 + level / 20.0));
    }
}
