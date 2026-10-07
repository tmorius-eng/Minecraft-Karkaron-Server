package mn.suld.api.item;

import java.util.LinkedHashMap;
import java.util.Map;

/** Prices, salvage yield and repair cost — the item side of the coin economy. Credits are never involved. */
public final class ItemEconomy {

    private ItemEconomy() {
    }

    /** What a merchant pays: base value × rarity × (1 + level/10). Soulbound and unique items are not bought. */
    public static long sellPrice(ItemDefinition def, ItemInstance i) {
        if (def.sellValue() <= 0 || i.soulbound() || i.rarity() == ItemRarity.UNIQUE) return 0;
        return Math.round(def.sellValue() * i.rarity().sellMultiplier() * (1 + i.itemLevel() / 10.0));
    }

    /** Salvage materials: the rarity's yield plus one per 10 item levels, of the rarity band's material. */
    public static Map<String, Integer> salvage(ItemCatalog catalog, ItemDefinition def, ItemInstance i) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!def.equippable() || i.rarity() == ItemRarity.UNIQUE || i.soulbound()) return out;
        String mat = catalog.salvageMaterial(i.rarity());
        if (mat == null) return out;
        out.put(mat, i.rarity().salvageYield() + i.itemLevel() / 10);
        return out;
    }

    /** Coins to repair {@code missing} durability points. */
    public static long repairCost(ItemInstance i, int missing) {
        if (missing <= 0) return 0;
        return Math.max(1, Math.round(missing * (0.5 + 0.25 * i.rarity().ordinal()) * (1 + i.itemLevel() / 20.0)));
    }
}
