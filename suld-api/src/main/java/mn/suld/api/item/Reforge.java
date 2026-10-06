package mn.suld.api.item;

/**
 * Blacksmith upgrades (Сайжруулалт): one item level at a time, never above the player's own level (nor
 * {@link #MAX_ITEM_LEVEL}), for coins plus two pieces of the material of the item's level band — wolf pelts, then
 * scorpion venom, bear pelts and ice stones. Stats follow the item definition's per-level growth.
 */
public final class Reforge {

    public static final int MAX_ITEM_LEVEL = 60;
    public static final int MATERIAL_COUNT = 2;

    private Reforge() {
    }

    public record Cost(long coins, String materialId, int materialCount) {
    }

    /** Material for upgrading an item currently at {@code itemLevel}. */
    public static String material(int itemLevel) {
        if (itemLevel < 8) return "item.chonon_arisan";
        if (itemLevel < 15) return "item.khilentsiin_khor";
        if (itemLevel < 22) return "item.baavgain_arisan";
        return "item.mosun_chuluu";
    }

    public static Cost cost(int itemLevel) {
        return new Cost(25L * itemLevel + 25, material(itemLevel), MATERIAL_COUNT);
    }

    /** Why an upgrade is not possible, or null when it is. */
    public static String blocked(int itemLevel, int playerLevel) {
        if (itemLevel >= MAX_ITEM_LEVEL) return "Дээд түвшинд хүрсэн.";
        if (itemLevel >= playerLevel) return "Зүйлийн түвшин таны түвшнээс өндөр болохгүй.";
        return null;
    }
}
