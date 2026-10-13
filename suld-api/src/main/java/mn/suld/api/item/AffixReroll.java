package mn.suld.api.item;

/**
 * «Эртний Хас Чулуу» at the smith (docs/HEROIC_DUNGEONS.md): the jade a timed heroic key pays rerolls an endgame
 * item's affixes (the same item, rarity, level and base stats; new affixes by the rules of a fresh drop). It costs jade
 * by rarity plus the reforge coins of the item's level (a coin sink at the top). Pure rules.
 */
public final class AffixReroll {

    /** The jade's item id (catalog `materials.json`). */
    public static final String JADE = "item.ertnii_khas";
    /** Only endgame gear is rerolled. */
    public static final int MIN_ITEM_LEVEL = 50;

    private AffixReroll() {
    }

    /** Jade a reroll costs: rare 1, epic 2, legendary 3, ancient 4, mythic 5; 0 when the rarity cannot be rerolled. */
    public static int jade(ItemRarity r) {
        return switch (r) {
            case RARE -> 1;
            case EPIC -> 2;
            case LEGENDARY -> 3;
            case ANCIENT -> 4;
            case MYTHIC -> 5;
            default -> 0;
        };
    }

    public static long coins(int itemLevel) {
        return mn.suld.api.balance.Economy.reforgeCoins(itemLevel);
    }

    /** Why {@code item} cannot be rerolled, or null when it can. */
    public static String blocked(ItemInstance item, ItemDefinition def) {
        if (def == null || !def.equippable() || def.stackable()) return "Энэ зүйлд шинж байхгүй.";
        if (jade(item.rarity()) == 0) return "Нандин (Rare) ба түүнээс дээш зэрэглэлийн зүйлийн шинжийг л дахин сугална.";
        if (item.itemLevel() < MIN_ITEM_LEVEL) return MIN_ITEM_LEVEL + "-р түвшний ба түүнээс дээш зүйлийн шинжийг л дахин сугална.";
        return null;
    }
}
