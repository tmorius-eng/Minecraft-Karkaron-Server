package mn.suld.api.item;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Item rarity tiers, from most common to the server-wide one-of-a-kind tier.
 *
 * <p>Each tier carries a stable id (config/DB key), a Mongolian display name, a
 * hex colour used by the UI/resource pack, and a relative drop weight. Weights
 * are defaults only; concrete loot tables are data-driven and may override the
 * effective chances. {@link #UNIQUE} items are globally unique and never roll
 * from ordinary loot tables — they are granted transactionally (see the
 * world-unique item system in GAME_DESIGN.md / DATABASE.md).
 */
public enum ItemRarity {

    COMMON("common", "Энгийн", "#9d9d9d", 1000),
    UNCOMMON("uncommon", "Ховор", "#1eff00", 400),
    RARE("rare", "Нандин", "#0070dd", 160),
    EPIC("epic", "Домогт", "#a335ee", 60),
    LEGENDARY("legendary", "Алдарт", "#ff8000", 18),
    ANCIENT("ancient", "Эртний", "#e6cc80", 5),
    MYTHIC("mythic", "Домгийн", "#ff4040", 1),
    /** One-of-a-kind on the entire server; never randomly dropped. */
    UNIQUE("unique", "Цор ганц", "#00ffd0", 0);

    private final String id;
    private final String displayName;
    private final String colorHex;
    private final int defaultWeight;

    ItemRarity(String id, String displayName, String colorHex, int defaultWeight) {
        this.id = id;
        this.displayName = displayName;
        this.colorHex = colorHex;
        this.defaultWeight = defaultWeight;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    /** Hex colour (e.g. {@code #ff8000}) for text/UI tinting. */
    public @NotNull String colorHex() {
        return colorHex;
    }

    /** Default relative drop weight; {@code 0} means never rolled randomly. */
    public int defaultWeight() {
        return defaultWeight;
    }

    /** Whether items of this rarity must be unique across the whole server. */
    public boolean isServerUnique() {
        return this == UNIQUE;
    }

    public static @NotNull Optional<ItemRarity> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String needle = id.trim().toLowerCase(Locale.ROOT);
        for (ItemRarity value : values()) {
            if (value.id.equals(needle)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
