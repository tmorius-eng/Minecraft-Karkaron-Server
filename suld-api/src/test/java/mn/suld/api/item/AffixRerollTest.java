package mn.suld.api.item;

import mn.suld.api.loot.Rng;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffixRerollTest {

    static final ItemCatalog CAT = ItemCatalogLoaderTest.BUNDLED;
    static final ItemGenerator GEN = new ItemGenerator(CAT);
    static final ItemValidator VAL = new ItemValidator(CAT);

    @Test
    void aRerollKeepsTheItemAndRollsNewGenuineAffixes() {
        ItemDefinition sword = CAT.require("weapon.khaany_ild");
        UUID owner = UUID.randomUUID();
        ItemInstance i = GEN.generate(sword, ItemRarity.LEGENDARY, 60, Rng.seeded(7), "test", owner).boundTo(owner);
        boolean changed = false;
        for (int seed = 0; seed < 20; seed++) {
            ItemInstance r = GEN.rerollAffixes(i, sword, Rng.seeded(1000 + seed));
            assertEquals(i.uuid(), r.uuid(), "the same item (the duplicate guard keys on it)");
            assertEquals(i.rarity(), r.rarity());
            assertEquals(i.itemLevel(), r.itemLevel());
            assertEquals(i.stats(), r.stats(), "base stats stay");
            assertEquals(i.boundTo(), r.boundTo());
            assertEquals(List.of(), VAL.problems(r), "a legal item: " + r.affixes());
            assertTrue(r.affixes().size() >= ItemRarity.LEGENDARY.minAffixes() && r.affixes().size() <= ItemRarity.LEGENDARY.maxAffixes());
            changed |= !r.affixes().equals(i.affixes());
        }
        assertTrue(changed, "rerolls change the affixes");
    }

    @Test
    void onlyEndgameItemsOfRareOrBetterAndThePriceGrowsWithRarity() {
        ItemDefinition sword = CAT.require("weapon.khaany_ild");
        assertNull(AffixReroll.blocked(GEN.generate(sword, ItemRarity.RARE, 55, Rng.seeded(1), "t", null), sword));
        ItemDefinition plain = CAT.items().stream().filter(d -> d.equippable() && !d.stackable() && d.canRoll(ItemRarity.UNCOMMON))
                .findFirst().orElseThrow();
        assertNotNull(AffixReroll.blocked(GEN.generate(plain, ItemRarity.UNCOMMON, 55, Rng.seeded(1), "t", null), plain), "too plain");
        assertNotNull(AffixReroll.blocked(GEN.generate(sword, ItemRarity.EPIC, 40, Rng.seeded(1), "t", null), sword), "not endgame");
        assertEquals(1, AffixReroll.jade(ItemRarity.RARE));
        assertEquals(5, AffixReroll.jade(ItemRarity.MYTHIC));
        assertEquals(0, AffixReroll.jade(ItemRarity.UNIQUE));
        assertTrue(AffixReroll.coins(60) > AffixReroll.coins(50));
    }
}
