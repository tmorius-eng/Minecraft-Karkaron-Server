package mn.suld.api.item;

import mn.suld.api.loot.LootTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IT-12 / IT-20 (audit 2026-10-14): the steppe consumables exist, can be had, and do what their tooltip says. */
class ConsumablesTest {

    private static final ItemCatalog C = ItemCatalogLoaderTest.BUNDLED;

    @Test
    void everyConsumableIsACatalogConsumableAndEveryCatalogConsumableHasRules() {
        for (Consumables.Consumable c : Consumables.ALL) {
            ItemDefinition d = C.require(c.itemId());
            assertEquals(ItemType.CONSUMABLE, d.type(), c.itemId());
            assertTrue(d.stackable() && !d.equippable(), c.itemId());
            assertTrue(c.cooldownSeconds() > 0, c.itemId());
        }
        for (ItemDefinition d : C.items()) {
            if (d.type() == ItemType.CONSUMABLE) assertTrue(Consumables.of(d.id()).isPresent(), d.id() + " has no consumable rules");
        }
    }

    @Test
    void everyConsumableDropsSomewhere() {
        for (Consumables.Consumable c : Consumables.ALL) {
            boolean named = false;
            for (LootTable t : C.lootTables()) named |= t.mayDrop(c.itemId());
            assertTrue(named, c.itemId() + " is in no loot table");
        }
    }

    @Test
    void theTooltipNamesTheEffectAndTheCooldown() {
        ItemDefinition d = C.require(Consumables.AIRAG.itemId());
        ItemInstance i = new ItemInstance(d.id(), java.util.UUID.randomUUID(), d.rarity(), 1, java.util.Map.of(), false, 0, "t");
        String text = ItemTooltip.lines(C, d, i, ItemTooltip.Viewer.NOBODY, 0, 0, null).stream().map(ItemTooltip.Line::text)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(text.contains(Consumables.AIRAG.description()), text);
        assertTrue(text.contains(Consumables.AIRAG.cooldownSeconds() + " сек"), text);
    }

    @Test
    void theCooldownCountsDownFromTheLastUse() {
        Consumables.Consumable salve = Consumables.SALVE; // 20 s
        assertEquals(0, Consumables.cooldownLeftMs(salve, null, 1_000));
        assertEquals(15_000, Consumables.cooldownLeftMs(salve, 0L, 5_000));
        assertEquals(0, Consumables.cooldownLeftMs(salve, 0L, 20_000));
    }

    @Test
    void theEffectsAreSizedAsDescribed() {
        assertEquals(25.0, Consumables.resourceRestore(Consumables.AIRAG, 100), 1e-9); // 25 % of the pool
        assertEquals(0.10, Consumables.AIRAG.bonus(), 1e-9); // +10 % regeneration
        assertEquals(60, Consumables.AIRAG.durationSeconds());
        assertEquals(40.0, Consumables.healPerSecond(Consumables.SALVE, 200) * Consumables.SALVE.durationSeconds(), 1e-9); // 20 % of 200
        assertEquals(92.0, Consumables.warded(Consumables.INCENSE, 100), 1e-9); // −8 %
        assertEquals(180, Consumables.INCENSE.durationSeconds());
        assertEquals(100 - 88, Consumables.repairedDamage(Consumables.WHETSTONE, 100, 250)); // 35 % of 250 = 87.5 → 88
        assertEquals(0, Consumables.repairedDamage(Consumables.WHETSTONE, 30, 250), "never below a fresh item");
    }

    @Test
    void theIncenseWardsAgainstSpiritsOnly() {
        assertTrue(Consumables.spirit("mob.elsnii_suns"));
        assertTrue(Consumables.spirit("mob.uulyn_savdag"));
        assertTrue(Consumables.spirit("mob.usny_lus"));
        assertFalse(Consumables.spirit("mob.goviin_chono"));
        assertFalse(Consumables.spirit(null));
    }
}
