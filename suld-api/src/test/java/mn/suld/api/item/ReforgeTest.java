package mn.suld.api.item;

import org.junit.jupiter.api.Test;

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
    void upgradedStatsComeFromTheDefinition() {
        ItemDefinition d = new ItemDefinition("weapon.x", "X", "minecraft:iron_sword", ItemRarity.RARE, 0,
                Map.of(ItemStat.ATTACK, 10.0), Map.of(ItemStat.ATTACK, 1.5), false);
        UUID id = UUID.randomUUID();
        ItemInstance before = d.roll(id, 4, "drop");
        ItemInstance after = d.roll(id, before.itemLevel() + 1, "reforge");
        assertEquals(14.5, before.stat(ItemStat.ATTACK), 1e-9);
        assertEquals(16.0, after.stat(ItemStat.ATTACK), 1e-9);
        assertEquals(before.uuid(), after.uuid());
    }
}
