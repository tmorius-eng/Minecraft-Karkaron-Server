package mn.suld.api.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A smithing recipe: materials and coins in, one generated item out (rarity rolled in [{@code minRarity},
 * {@code maxRarity}] of the result's range, item level = the crafter's level, at least the result's requirement).
 */
public record Recipe(String id, String resultId, Map<String, Integer> materials, long coins, int levelReq,
                     ItemRarity minRarity, ItemRarity maxRarity) {
    public Recipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resultId, "resultId");
        materials = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(materials));
        coins = Math.max(0, coins);
        levelReq = Math.max(1, levelReq);
    }
}
