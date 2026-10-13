package mn.suld.api.item;

import mn.suld.api.mastery.Profession;
import mn.suld.api.mastery.ProfessionMaterials;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the crafts gather and refine is in the catalogue, sells to the merchant, and the refinings keep the prices. */
class ProfessionMaterialsCatalogTest {

    private static final ItemCatalog C = ItemCatalogLoaderTest.BUNDLED;

    @Test
    void everyMaterialOfTheCraftsIsACatalogueMaterial() {
        for (String id : ProfessionMaterials.all()) {
            ItemDefinition d = C.item(id).orElse(null);
            assertTrue(d != null, id + " is not in the catalogue");
            assertEquals(ItemType.MATERIAL, d.type(), id);
            assertTrue(d.stackable(), id + " must stack");
            assertTrue(d.sellValue() > 0, id + " must sell to the merchant");
        }
    }

    @Test
    void eachCraftGathersTwoMaterialsOfItsOwn() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Profession p : Profession.values()) {
            var mats = ProfessionMaterials.gatheredBy(p);
            assertEquals(2, mats.size(), p.name());
            assertNotEquals(mats.get(0), mats.get(1), p.name());
            for (String m : mats) assertTrue(seen.add(m), m + " is gathered by two crafts");
            assertTrue(C.require(mats.get(1)).sellValue() > C.require(mats.get(0)).sellValue(), p + ": the fine material sells for more");
        }
    }

    @Test
    void aRefiningNeverMakesCoinsOutOfNothing() {
        for (ProfessionMaterials.Refine r : ProfessionMaterials.REFINES) {
            long in = 0, out = 0;
            for (Map.Entry<String, Integer> e : r.in().entrySet()) in += C.require(e.getKey()).sellValue() * (long) e.getValue();
            for (Map.Entry<String, Integer> e : r.out().entrySet()) out += C.require(e.getKey()).sellValue() * (long) e.getValue();
            assertTrue(out <= in + 1 && out * 2 >= in, r.id() + ": " + in + " coins of input make " + out);
            assertTrue(Profession.canGather(r.level(), 1), r.id());
        }
    }

    @Test
    void refiningCountsWholeTimes() {
        ProfessionMaterials.Refine iron = ProfessionMaterials.refine("tumur").orElseThrow();
        assertEquals(0, iron.times(Map.of()));
        assertEquals(0, iron.times(Map.of(ProfessionMaterials.TUMRIIN_KHUDAR, 2)));
        assertEquals(3, iron.times(Map.of(ProfessionMaterials.TUMRIIN_KHUDAR, 11)));
    }
}
