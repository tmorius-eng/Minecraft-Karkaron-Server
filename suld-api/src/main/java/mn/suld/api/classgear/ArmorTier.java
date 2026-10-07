package mn.suld.api.classgear;

import mn.suld.api.item.ItemRarity;

import java.util.Optional;

/**
 * Armour tiers T1–T6 and their gates (docs/ARMOR_PROGRESSION.md "Armour tiers", the numbers the simulation used:
 * {@code ProposedRules.TIER_*}). A tier gives the rarity of every class piece and a new look; enhancement resets.
 *
 * @param armorLevel   armour level needed
 * @param dungeon      dungeon that must have been cleared at least once (null = none)
 * @param coins        coins taken by the upgrade
 * @param material     band material taken by the upgrade (null = none)
 * @param materials    how many of it
 * @param ascension    Тэнгэрийн Зэрэг rank needed (T6: III)
 */
public enum ArmorTier {
    T1("Анхан", 1, null, 0, null, 0, 0, ItemRarity.UNCOMMON),
    T2("Бэхжсэн", 12, "dungeon.govi_bulsh", 2_000, "item.khilentsiin_khor", 10, 0, ItemRarity.RARE),
    T3("Сонгомол", 24, "dungeon.mosun_orgil", 12_000, "item.mosun_chuluu", 15, 0, ItemRarity.EPIC),
    T4("Эзэнт", 36, "dungeon.khar_khot", 45_000, "item.altan_toos", 20, 0, ItemRarity.LEGENDARY),
    T5("Домогт", 48, "dungeon.burkhan_aguy", 120_000, "item.altan_toos", 25, 0, ItemRarity.ANCIENT),
    T6("Тэнгэрлэг", 60, "dungeon.tengeriin_ordon", 300_000, "item.tengeriin_chuluu", 30, 3, ItemRarity.MYTHIC);

    private final String displayName;
    private final int armorLevel;
    private final String dungeon;
    private final long coins;
    private final String material;
    private final int materials;
    private final int ascension;
    private final ItemRarity rarity;

    ArmorTier(String displayName, int armorLevel, String dungeon, long coins, String material, int materials, int ascension, ItemRarity rarity) {
        this.displayName = displayName;
        this.armorLevel = armorLevel;
        this.dungeon = dungeon;
        this.coins = coins;
        this.material = material;
        this.materials = materials;
        this.ascension = ascension;
        this.rarity = rarity;
    }

    /** 1..6 */
    public int number() {
        return ordinal() + 1;
    }

    public static ArmorTier of(int number) {
        return values()[Math.max(1, Math.min(values().length, number)) - 1];
    }

    public Optional<ArmorTier> next() {
        return ordinal() + 1 < values().length ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
    }

    public String displayName() {
        return displayName;
    }

    public int armorLevel() {
        return armorLevel;
    }

    public String dungeon() {
        return dungeon;
    }

    public long coins() {
        return coins;
    }

    public String material() {
        return material;
    }

    public int materials() {
        return materials;
    }

    public int ascension() {
        return ascension;
    }

    public ItemRarity rarity() {
        return rarity;
    }
}
