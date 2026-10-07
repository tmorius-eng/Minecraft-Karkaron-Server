package mn.suld.api.classgear;

import mn.suld.api.item.EquipSlot;

import java.util.Locale;
import java.util.Optional;

/** The four pieces of a class armour set (docs/CLASS_ARMOR_SYSTEM.md). */
public enum ArmorPiece {
    HELMET(EquipSlot.HEAD, "Дуулга"),
    CHESTPLATE(EquipSlot.CHEST, "Хуяг"),
    LEGGINGS(EquipSlot.LEGS, "Өмд"),
    BOOTS(EquipSlot.FEET, "Гутал");

    private final EquipSlot slot;
    private final String displayName;

    ArmorPiece(EquipSlot slot, String displayName) {
        this.slot = slot;
        this.displayName = displayName;
    }

    public EquipSlot slot() {
        return slot;
    }

    public String displayName() {
        return displayName;
    }

    /** The id part in a definition id ({@code armor.class.baatar.helmet.t1}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ArmorPiece> byId(String id) {
        for (ArmorPiece p : values()) if (p.id().equals(id)) return Optional.of(p);
        return Optional.empty();
    }
}
