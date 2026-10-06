package mn.suld.api.loot;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootRollerTest {

    private final ItemDefinition saber = new ItemDefinition(
            "weapon.saber", "Илд", "minecraft:iron_sword", ItemRarity.RARE, 0,
            Map.of(ItemStat.ATTACK, 10.0), Map.of(ItemStat.ATTACK, 2.0), false);

    @Test
    void guaranteedDropAlwaysProducesAnInstanceWithFreshUuid() {
        LootTable table = new LootTable("loot.test", List.of(new LootEntry(saber, 1.0, 5, 5)));
        LootRoller roller = new LootRoller(new Random(1));
        List<ItemInstance> a = roller.roll(table, "test");
        List<ItemInstance> b = roller.roll(table, "test");
        assertEquals(1, a.size());
        assertEquals(1, b.size());
        // iLvl 5 -> attack = 10 + 2*(5-1) = 18
        assertEquals(18.0, a.get(0).stat(ItemStat.ATTACK), 1e-6);
        assertTrue(!a.get(0).uuid().equals(b.get(0).uuid()), "each drop has a unique UUID");
    }

    @Test
    void zeroChanceNeverDrops() {
        LootTable table = new LootTable("loot.none", List.of(new LootEntry(saber, 0.0, 1, 1)));
        assertTrue(new LootRoller(new Random(1)).roll(table, "t").isEmpty());
    }

    @Test
    void itemLevelStaysInRange() {
        LootTable table = new LootTable("loot.r", List.of(new LootEntry(saber, 1.0, 3, 8)));
        LootRoller roller = new LootRoller(new Random(42));
        for (int i = 0; i < 50; i++) {
            int lvl = roller.roll(table, "t").get(0).itemLevel();
            assertTrue(lvl >= 3 && lvl <= 8, "item level in range: " + lvl);
        }
    }
}
