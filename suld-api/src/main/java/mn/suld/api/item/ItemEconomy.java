package mn.suld.api.item;

import java.util.LinkedHashMap;
import java.util.Map;

/** Prices, salvage yield and repair cost — the item side of the coin economy. Credits are never involved. */
public final class ItemEconomy {

    private ItemEconomy() {
    }

    /**
     * What a merchant pays (progression v2, {@link mn.suld.api.balance.Economy#sellPrice}): base value × rarity (at most
     * ×5) × (1 + level/30). Legendary and above, soulbound and unique items are not bought: salvage them.
     */
    public static long sellPrice(ItemDefinition def, ItemInstance i) {
        return mn.suld.api.balance.Economy.sellPrice(def, i);
    }

    /** Salvage materials: the rarity's yield plus one per 10 item levels, of the rarity band's material. */
    public static Map<String, Integer> salvage(ItemCatalog catalog, ItemDefinition def, ItemInstance i) {
        Map<String, Integer> out = new LinkedHashMap<>();
        // class gear never; other soulbound items (a boss trophy) can be broken down, or they would fill the bag forever
        if (!def.equippable() || i.rarity() == ItemRarity.UNIQUE || classGear(def.id())) return out;
        String mat = catalog.salvageMaterial(i.rarity());
        if (mat == null) return out;
        out.put(mat, i.rarity().salvageYield() + i.itemLevel() / 10);
        return out;
    }

    /** The class weapon and class armour: soulbound for good, never sold, salvaged or destroyed. */
    public static boolean classGear(String definitionId) {
        return definitionId.startsWith("weapon.class.") || definitionId.startsWith("armor.class.") || definitionId.startsWith("weapon.surgamj_");
    }

    /** Coins to repair {@code missing} durability points. */
    public static long repairCost(ItemInstance i, int missing) {
        if (missing <= 0) return 0;
        return Math.max(1, Math.round(missing * (0.5 + 0.25 * i.rarity().ordinal()) * (1 + i.itemLevel() / 20.0)));
    }
}
