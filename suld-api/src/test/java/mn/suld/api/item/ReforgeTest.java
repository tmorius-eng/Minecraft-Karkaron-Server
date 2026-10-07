package mn.suld.api.item;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReforgeTest {

    @Test
    void costAndMaterialFollowTheLevelBand() {
        assertEquals("item.chonon_arisan", Reforge.cost(1).materialId());
        assertEquals("item.khilentsiin_khor", Reforge.cost(8).materialId());
        assertEquals("item.baavgain_arisan", Reforge.cost(20).materialId());
        assertEquals("item.mosun_chuluu", Reforge.cost(30).materialId());
        assertEquals(50, Reforge.cost(1).coins());
        assertEquals(2, Reforge.cost(5).materialCount());
    }

    @Test
    void neverAboveThePlayerOrTheCap() {
        assertNull(Reforge.blocked(4, 10));
        assertNotNull(Reforge.blocked(10, 10));
        assertNotNull(Reforge.blocked(Reforge.MAX_ITEM_LEVEL, 99));
    }

    @Test
    void upgradeAddsTheDefinitionsGrowthAndKeepsTheItem() {
        ItemDefinition d = Fixtures.def("weapon.x").rarity(ItemRarity.RARE, ItemRarity.RARE).stat(ItemStat.DAMAGE, 10, 10).per(ItemStat.DAMAGE, 1.5).build();
        ItemGenerator gen = new ItemGenerator(Fixtures.catalog(List.of(d), List.of(), List.of(), List.of()));
        ItemInstance before = gen.generate(d, ItemRarity.RARE, 4, mn.suld.api.loot.Rng.seeded(1), "drop", null);
        ItemInstance after = gen.upgrade(before, d);
        assertEquals(14.5, before.stat(ItemStat.DAMAGE), 1e-9);
        assertEquals(16.0, after.stat(ItemStat.DAMAGE), 1e-9);
        assertEquals(5, after.itemLevel());
        assertEquals(1, after.upgradeLevel());
        assertEquals(before.uuid(), after.uuid());
        assertEquals(before.affixes(), after.affixes());
    }
}
