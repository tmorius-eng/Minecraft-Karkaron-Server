package mn.suld.api.craft;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftOrderRulesTest {

    private final UUID poster = UUID.randomUUID(), crafter = UUID.randomUUID();

    private CraftOrder order(int n) {
        return new CraftOrder("o" + n, poster, "P", "recipe.x", 100, 20, 1_000);
    }

    @Test
    void posting() {
        assertEquals(CraftOrderRules.Post.OK, CraftOrderRules.post(poster, 20, 500, 100, 20, List.of()));
        assertEquals(CraftOrderRules.Post.FEE_RANGE, CraftOrderRules.post(poster, 20, 500, 5, 20, List.of()));
        assertEquals(CraftOrderRules.Post.FEE_RANGE, CraftOrderRules.post(poster, 20, 500_000, 200_000, 20, List.of()));
        assertEquals(CraftOrderRules.Post.LEVEL, CraftOrderRules.post(poster, 19, 500, 100, 20, List.of()), "no ordering gear one cannot wear");
        assertEquals(CraftOrderRules.Post.COINS, CraftOrderRules.post(poster, 20, 50, 100, 20, List.of()));
        assertEquals(CraftOrderRules.Post.TOO_MANY, CraftOrderRules.post(poster, 20, 5000, 100, 20, List.of(order(1), order(2), order(3))));
        assertEquals(CraftOrderRules.Post.OK, CraftOrderRules.post(crafter, 20, 5000, 100, 20, List.of(order(1), order(2), order(3))), "others' orders do not count");
    }

    @Test
    void filling() {
        CraftOrder o = order(1);
        assertEquals(CraftOrderRules.Fill.OK, CraftOrderRules.fill(crafter, 20, o, 20));
        assertEquals(CraftOrderRules.Fill.OWN, CraftOrderRules.fill(poster, 60, o, 20), "never one's own order (no laundering the fee)");
        assertEquals(CraftOrderRules.Fill.LEVEL, CraftOrderRules.fill(crafter, 10, o, 20));
        assertEquals(CraftOrderRules.Fill.GONE, CraftOrderRules.fill(crafter, 20, null, 20));
    }

    @Test
    void expiry() {
        CraftOrder o = order(1);
        assertFalse(CraftOrderRules.expired(o, 1_000 + CraftOrderRules.EXPIRY_MILLIS - 1));
        assertTrue(CraftOrderRules.expired(o, 1_000 + CraftOrderRules.EXPIRY_MILLIS));
    }
}
