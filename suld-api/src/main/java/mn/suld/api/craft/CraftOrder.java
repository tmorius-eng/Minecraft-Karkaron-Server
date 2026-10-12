package mn.suld.api.craft;

import java.util.UUID;

/**
 * A crafting order on the Дархан's board (docs/CRAFTING.md): a player wants an item made from a recipe and puts up a
 * fee, held from them until the order is filled (paid to the crafter) or withdrawn/expired (paid back).
 *
 * @param posterLevel the poster's level when they posted: the item is made at it (held to the recipe's band) when
 *                    they are offline at the filling
 */
public record CraftOrder(String id, UUID poster, String posterName, String recipeId, long fee, int posterLevel, long createdAt) {

    public CraftOrder {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        if (poster == null || recipeId == null) throw new IllegalArgumentException("poster and recipe are required");
        if (fee < 0) throw new IllegalArgumentException("fee >= 0");
        posterName = posterName == null ? "" : posterName;
    }
}
