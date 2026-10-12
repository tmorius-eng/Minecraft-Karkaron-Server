package mn.suld.api.craft;

import java.util.Collection;
import java.util.UUID;

/** The rules of the crafting-order board, pure (docs/CRAFTING.md). */
public final class CraftOrderRules {

    public static final long MIN_FEE = 10;
    public static final long MAX_FEE = 100_000;
    public static final int MAX_OPEN = 3;
    public static final long EXPIRY_MILLIS = 7L * 24 * 3600 * 1000;

    private CraftOrderRules() {
    }

    public enum Post { OK, FEE_RANGE, TOO_MANY, LEVEL, COINS }

    public enum Fill { OK, OWN, LEVEL, GONE }

    /** May {@code poster} put up this order? They must be able to wear what they order (their level reaches the recipe). */
    public static Post post(UUID poster, int posterLevel, long coins, long fee, int recipeLevel, Collection<CraftOrder> open) {
        if (fee < MIN_FEE || fee > MAX_FEE) return Post.FEE_RANGE;
        if (posterLevel < recipeLevel) return Post.LEVEL;
        long mine = open.stream().filter(o -> o.poster().equals(poster)).count();
        if (mine >= MAX_OPEN) return Post.TOO_MANY;
        if (coins < fee) return Post.COINS;
        return Post.OK;
    }

    /** May {@code crafter} fill {@code order}? Never one's own; the crafter's level must reach the recipe too. */
    public static Fill fill(UUID crafter, int crafterLevel, CraftOrder order, int recipeLevel) {
        if (order == null) return Fill.GONE;
        if (order.poster().equals(crafter)) return Fill.OWN;
        if (crafterLevel < recipeLevel) return Fill.LEVEL;
        return Fill.OK;
    }

    public static boolean expired(CraftOrder o, long now) {
        return now - o.createdAt() >= EXPIRY_MILLIS;
    }
}
