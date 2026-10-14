package mn.suld.api.item;

import mn.suld.api.loot.LootTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT-01 / IT-15 (audit 2026-10-14): no loot pool carries the WEAPON category, so a classless weapon drops only when a
 * table names it (or a recipe makes it). The four regional weapons and the Талын Анчин set can be had in game.
 */
class ItemObtainabilityTest {

    private static final ItemCatalog C = ItemCatalogLoaderTest.BUNDLED;

    static boolean named(String id) {
        for (LootTable t : C.lootTables()) if (t.mayDrop(id)) return true;
        for (Recipe r : C.recipes()) if (id.equals(r.resultId())) return true;
        return false;
    }

    static boolean pooled(ItemDefinition d) {
        if (!d.lootable() || d.rarity() == ItemRarity.UNIQUE || !d.equippable()) return false;
        for (LootTable t : C.lootTables()) {
            for (LootTable.Entry e : t.entries()) if (e.pool() != null && e.pool().categories().contains(d.type().category())) return true;
        }
        return false;
    }

    @Test
    void theRegionalWeaponsAreNamedByALootTableOrARecipe() {
        for (String id : List.of("weapon.talyn_ild", "weapon.khasar_soyo", "weapon.altai_jad", "weapon.anchin_num")) {
            C.require(id);
            assertTrue(named(id), id + " is in no loot table and no recipe: unobtainable");
        }
    }

    @Test
    void everyPieceOfTheTalynAnchinSetCanBeHad() {
        ItemSet set = C.set("set.talyn_anchin").orElseThrow();
        for (String id : set.pieces()) {
            ItemDefinition d = C.require(id);
            assertTrue(named(id) || pooled(d), id + " (set.talyn_anchin) is neither named nor in any loot pool");
        }
    }
}
