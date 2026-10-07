package mn.suld.api.item;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** What an item is, which equipment slots it fits and which affixes it can roll (by {@link Category}). */
public enum ItemType {
    SWORD("Илд", Category.WEAPON, EnumSet.of(EquipSlot.MAIN_HAND), "_sword"),
    AXE("Сүх / Алх", Category.WEAPON, EnumSet.of(EquipSlot.MAIN_HAND), "_axe"),
    BOW("Нум", Category.WEAPON, EnumSet.of(EquipSlot.MAIN_HAND), "bow"),
    STAFF("Таяг", Category.WEAPON, EnumSet.of(EquipSlot.MAIN_HAND), null),
    SPEAR("Жад", Category.WEAPON, EnumSet.of(EquipSlot.MAIN_HAND), null),
    SHIELD("Бамбай", Category.OFFHAND, EnumSet.of(EquipSlot.OFF_HAND), "shield"),
    TOME("Судар", Category.OFFHAND, EnumSet.of(EquipSlot.OFF_HAND), null),
    TOTEM("Онгон", Category.OFFHAND, EnumSet.of(EquipSlot.OFF_HAND), null),
    HELMET("Дуулга", Category.ARMOR, EnumSet.of(EquipSlot.HEAD), "_helmet"),
    CHESTPLATE("Хуяг", Category.ARMOR, EnumSet.of(EquipSlot.CHEST), "_chestplate"),
    LEGGINGS("Өмд", Category.ARMOR, EnumSet.of(EquipSlot.LEGS), "_leggings"),
    BOOTS("Гутал", Category.ARMOR, EnumSet.of(EquipSlot.FEET), "_boots"),
    RING("Бөгж", Category.JEWELRY, EnumSet.of(EquipSlot.ACCESSORY_1, EquipSlot.ACCESSORY_2), null),
    AMULET("Сахиус", Category.JEWELRY, EnumSet.of(EquipSlot.ACCESSORY_1, EquipSlot.ACCESSORY_2), null),
    RELIC("Дурсгал", Category.RELIC, EnumSet.of(EquipSlot.RELIC), null),
    MATERIAL("Түүхий эд", Category.MATERIAL, EnumSet.noneOf(EquipSlot.class), null),
    CONSUMABLE("Хэрэглээ", Category.MATERIAL, EnumSet.noneOf(EquipSlot.class), null);

    /** Groups that share affixes and loot pools. */
    public enum Category { WEAPON, OFFHAND, ARMOR, JEWELRY, RELIC, MATERIAL }

    private final String label;
    private final Category category;
    private final Set<EquipSlot> slots;
    private final String materialSuffix;

    ItemType(String label, Category category, Set<EquipSlot> slots, String materialSuffix) {
        this.label = label;
        this.category = category;
        this.slots = slots;
        this.materialSuffix = materialSuffix;
    }

    public String label() {
        return label;
    }

    public Category category() {
        return category;
    }

    public Set<EquipSlot> slots() {
        return slots;
    }

    public boolean equippable() {
        return !slots.isEmpty();
    }

    /** Takes durability (weapons, off-hands, armour). */
    public boolean wears() {
        return category == Category.WEAPON || category == Category.OFFHAND || category == Category.ARMOR;
    }

    /**
     * Whether a vanilla material can carry this type. Armour must really be armour (so the game puts it in the armour
     * slot), shields a shield, bows a bow; the other types may use any material.
     */
    public boolean acceptsMaterial(String material) {
        if (materialSuffix == null) return true;
        String m = material.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (this == HELMET && (m.equals("turtle_helmet") || m.endsWith("_helmet"))) return true;
        return materialSuffix.startsWith("_") ? m.endsWith(materialSuffix) : m.equals(materialSuffix) || m.equals("crossbow") && this == BOW;
    }

    public static Optional<ItemType> byName(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(valueOf(s.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
