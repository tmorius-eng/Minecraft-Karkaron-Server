package mn.suld.api.item;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Stats an item can carry. Kept as a small typed enum so tooltips, combat, and
 * persistence all agree on the stat vocabulary. Each has a Mongolian display
 * label and whether it is shown as a percentage.
 */
public enum ItemStat {
    ATTACK("attack", "Хүч (Attack)", false),
    HEALTH("health", "Эрүүл мэнд (Health)", false),
    ARMOR("armor", "Хуяг (Armor)", false),
    CRIT_CHANCE("crit_chance", "Онч (Crit)", true),
    CRIT_DAMAGE("crit_damage", "Ончийн хор (Crit DMG)", true),
    RESOURCE("resource", "Нөөц (Resource)", false);

    private final String id;
    private final String label;
    private final boolean percent;

    ItemStat(String id, String label, boolean percent) {
        this.id = id;
        this.label = label;
        this.percent = percent;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String label() {
        return label;
    }

    public boolean percent() {
        return percent;
    }

    public static @NotNull Optional<ItemStat> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String needle = id.trim().toLowerCase(Locale.ROOT);
        for (ItemStat s : values()) {
            if (s.id.equals(needle)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
