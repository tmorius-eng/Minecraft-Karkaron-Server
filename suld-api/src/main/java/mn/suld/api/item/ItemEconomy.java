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

    /** Item level from which a legendary-or-better piece breaks into its own band's (end-game) material. */
    public static final int END_GAME_SALVAGE_LEVEL = 30;

    /**
     * The rarity a piece salvages as: its own, except a legendary-or-better piece below
     * {@link #END_GAME_SALVAGE_LEVEL}, which salvages as an epic one (IT-17: «Саарлын Зүрх», a level-5 legendary from
     * the first dungeon, broke into five «Тэнгэрийн Чулуу», the T6 armour's material).
     */
    public static ItemRarity salvageRarity(ItemInstance i) {
        return i.rarity().atLeast(ItemRarity.LEGENDARY) && i.itemLevel() < END_GAME_SALVAGE_LEVEL ? ItemRarity.EPIC : i.rarity();
    }

    /** Salvage materials: the rarity's yield plus one per 10 item levels, of the rarity band's material. */
    public static Map<String, Integer> salvage(ItemCatalog catalog, ItemDefinition def, ItemInstance i) {
        Map<String, Integer> out = new LinkedHashMap<>();
        // class gear never; other soulbound items (a boss trophy) can be broken down, or they would fill the bag forever
        if (!def.equippable() || i.rarity() == ItemRarity.UNIQUE || classGear(def.id())) return out;
        ItemRarity r = salvageRarity(i);
        String mat = catalog.salvageMaterial(r);
        if (mat == null) return out;
        out.put(mat, r.salvageYield() + i.itemLevel() / 10);
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
