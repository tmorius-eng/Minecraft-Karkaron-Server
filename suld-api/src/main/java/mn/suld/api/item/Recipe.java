package mn.suld.api.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A smithing recipe: materials and coins in, one generated item out (rarity rolled in [{@code minRarity},
 * {@code maxRarity]} of the result's range). The item's level is the crafter's level held to the recipe's band
 * [{@code levelReq}, {@code levelCap}]: a level-60 smith cannot turn four wolf pelts and twenty coins into level-60
 * gear through a first-level recipe (docs/CRAFTING.md).
 */
public record Recipe(String id, String resultId, Map<String, Integer> materials, long coins, int levelReq,
                     ItemRarity minRarity, ItemRarity maxRarity, int levelCap) {

    /** The band a recipe covers when the data names none: its own level and the nine above. */
    public static final int DEFAULT_BAND = 9;

    public Recipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resultId, "resultId");
        materials = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(materials));
        coins = Math.max(0, coins);
        levelReq = Math.max(1, levelReq);
        levelCap = Math.max(levelReq, Math.min(ItemDefinition.MAX_LEVEL, levelCap));
    }

    public Recipe(String id, String resultId, Map<String, Integer> materials, long coins, int levelReq, ItemRarity minRarity, ItemRarity maxRarity) {
        this(id, resultId, materials, coins, levelReq, minRarity, maxRarity, levelReq + DEFAULT_BAND);
    }

    /** The level of the item a crafter of {@code crafterLevel} makes: held to [levelReq, levelCap]. */
    public int itemLevel(int crafterLevel) {
        return Math.max(levelReq, Math.min(levelCap, crafterLevel));
    }
}
