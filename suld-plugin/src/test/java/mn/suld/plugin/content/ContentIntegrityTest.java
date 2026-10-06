package mn.suld.plugin.content;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.loot.LootEntry;
import mn.suld.api.loot.LootTable;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.region.RegionIndex;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Content is data: a broken reference would only surface as a silent no-drop / no-spawn in game. */
class ContentIntegrityTest {

    @Test
    void everyRegionMobLootAndItemResolves() {
        for (RegionDefinition r : WorldContent.REGIONS) {
            for (String mobId : r.mobIds()) {
                MobDefinition mob = SuldContent.mobFor(mobId);
                assertNotNull(mob, r.id() + " spawns unknown mob " + mobId);
                LootTable table = SuldContent.lootTableFor(mob.lootTableId());
                assertNotNull(table, mobId + " has unknown loot table " + mob.lootTableId());
                for (LootEntry e : table.entries()) {
                    assertSame(e.definition(), SuldContent.definitionFor(e.definition().id()),
                            "dropped item " + e.definition().id() + " must be re-creatable from its id");
                }
            }
        }
    }

    @Test
    void mobLevelsFitTheirRegionBand() {
        for (RegionDefinition r : WorldContent.REGIONS) {
            for (String mobId : r.mobIds()) {
                int level = SuldContent.mobFor(mobId).level();
                assertTrue(level >= r.minLevel() - 1 && level <= r.maxLevel(), mobId + " lvl " + level + " in " + r.levelBand());
            }
        }
    }

    @Test
    void idsAreUnique() {
        Set<String> items = new HashSet<>();
        for (ItemDefinition d : WorldContent.ITEMS) assertTrue(items.add(d.id()), d.id());
        for (String id : List.of("item.chonon_arisan", "weapon.talyn_ild", "weapon.khasar_soyo", "item.khasar_zurkh", "item.talyn_tuvshin")) {
            assertFalse(items.contains(id), "world item reuses core id " + id);
        }
        Set<String> mobs = new HashSet<>();
        for (MobDefinition m : WorldContent.MOBS) assertTrue(mobs.add(m.id()), m.id());
        assertNull(SuldContent.mobFor("mob.does_not_exist"));
    }

    @Test
    void wildernessCoversEveryBearingExactlyOnceAndTownWinsInside() {
        RegionIndex index = new RegionIndex(WorldContent.REGIONS);
        for (int deg = 0; deg < 360; deg++) {
            double dx = Math.sin(Math.toRadians(deg)) * 300, dz = -Math.cos(Math.toRadians(deg)) * 300;
            int matches = 0;
            for (RegionDefinition r : WorldContent.REGIONS) {
                if (r != WorldContent.KHARKHORUM && r.shape().contains(dx, dz)) matches++;
            }
            assertEquals(1, matches, "bearing " + deg + " must belong to exactly one wild region");
            assertTrue(index.at(dx, dz).isPresent());
        }
        assertEquals(WorldContent.KHARKHORUM, index.at(10, 10).orElseThrow());
        assertEquals(WorldContent.KHANGAI, index.at(0, -300).orElseThrow());
        assertEquals(WorldContent.GOBI, index.at(0, 300).orElseThrow());
        assertEquals(WorldContent.KHERLEN, index.at(300, 0).orElseThrow(), "the first hunt's wolves live east of town");
        assertEquals(WorldContent.ALTAI, index.at(-300, 0).orElseThrow());
    }
}
