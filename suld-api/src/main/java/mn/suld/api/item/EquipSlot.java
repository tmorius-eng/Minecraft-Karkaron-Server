package mn.suld.api.item;

import java.util.Locale;
import java.util.Optional;

/**
 * The nine equipment slots. The first six are the player's own vanilla slots (armour, main hand, off hand), so the
 * game shows and saves them; the accessories live in the SÜLD equipment window and are stored with the profile; the
 * relic slot is the world-unique relic the player bears (see the relic system).
 */
public enum EquipSlot {
    HEAD("Толгой", true),
    CHEST("Цээж", true),
    LEGS("Өмд", true),
    FEET("Гутал", true),
    MAIN_HAND("Гол гар", true),
    OFF_HAND("Нөгөө гар", true),
    ACCESSORY_1("Чимэглэл I", false),
    ACCESSORY_2("Чимэглэл II", false),
    RELIC("Дурсгал", false);

    private final String label;
    private final boolean vanilla;

    EquipSlot(String label, boolean vanilla) {
        this.label = label;
        this.vanilla = vanilla;
    }

    public String label() {
        return label;
    }

    /** A slot of the player's own inventory (saved by the game itself). */
    public boolean vanilla() {
        return vanilla;
    }

    public static Optional<EquipSlot> byName(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(valueOf(s.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
