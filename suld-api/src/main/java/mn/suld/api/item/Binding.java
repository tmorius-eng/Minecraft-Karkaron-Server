package mn.suld.api.item;

import java.util.Locale;
import java.util.Optional;

/**
 * How an item binds to a player. A bound item belongs to that player: nobody else can equip, trade for or pick it up.
 * {@link #SOULBOUND} items are bound from the moment they are made and are also kept on death and cannot be dropped.
 */
public enum Binding {
    NONE("Холбоогүй"),
    ON_PICKUP("Авахад холбогдоно"),
    ON_EQUIP("Өмсөхөд холбогдоно"),
    SOULBOUND("Сүнсэнд холбоотой");

    private final String label;

    Binding(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The stricter of two rules (a rarity can force a stronger binding than the definition asks for). */
    public Binding strongest(Binding other) {
        return other != null && other.strictness() > strictness() ? other : this;
    }

    /** NONE &lt; ON_EQUIP &lt; ON_PICKUP &lt; SOULBOUND. */
    public int strictness() {
        return switch (this) {
            case NONE -> 0;
            case ON_EQUIP -> 1;
            case ON_PICKUP -> 2;
            case SOULBOUND -> 3;
        };
    }

    public static Optional<Binding> byName(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(valueOf(s.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
