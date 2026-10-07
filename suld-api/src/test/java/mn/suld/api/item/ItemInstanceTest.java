package mn.suld.api.item;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemInstanceTest {

    /** Regression: statless items (relics, trophies) passed Map.of() and EnumMap's copy constructor threw. */
    @Test
    void acceptsEmptyAndNullStatMaps() {
        ItemInstance a = new ItemInstance("relic.test", UUID.randomUUID(), ItemRarity.UNIQUE, 1, Map.of(), true, 0, "relic");
        assertTrue(a.stats().isEmpty());
        ItemInstance b = new ItemInstance("x.y", UUID.randomUUID(), ItemRarity.COMMON, 1, null, false, 0, "t");
        assertTrue(b.stats().isEmpty());
    }

    @Test
    void copiesStatsDefensively() {
        java.util.HashMap<ItemStat, Double> src = new java.util.HashMap<>(Map.of(ItemStat.DAMAGE, 5.0));
        ItemInstance i = new ItemInstance("x.y", UUID.randomUUID(), ItemRarity.RARE, 2, src, false, 0, "t");
        src.put(ItemStat.DAMAGE, 999.0);
        assertEquals(5.0, i.stat(ItemStat.DAMAGE));
    }
}
