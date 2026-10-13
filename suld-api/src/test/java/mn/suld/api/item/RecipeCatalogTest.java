package mn.suld.api.item;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Дархан's recipe book (docs/CRAFTING.md): every band covered, every material obtainable, no level shortcut. */
class RecipeCatalogTest {

    private static final ItemCatalog C = ItemCatalogLoaderTest.BUNDLED;

    @Test
    void theCraftedLevelIsHeldToTheRecipesBand() {
        Recipe r = new Recipe("recipe.x", "armor.esgii.helmet", Map.of("item.chonon_arisan", 1), 10, 1, ItemRarity.COMMON, ItemRarity.UNCOMMON);
        assertEquals(10, r.levelCap(), "a recipe covers its level and the nine above by default");
        assertEquals(1, r.itemLevel(1));
        assertEquals(7, r.itemLevel(7));
        assertEquals(10, r.itemLevel(60), "a level-60 smith makes a level-10 helmet from a level-1 recipe, not a level-60 one");
        Recipe top = new Recipe("recipe.y", "armor.khaany.helmet", Map.of("item.altan_toos", 1), 10, 50, ItemRarity.EPIC, ItemRarity.LEGENDARY, 99);
        assertEquals(ItemDefinition.MAX_LEVEL, top.levelCap());
    }

    @Test
    void theBookCoversEveryBandUpToTheCap() {
        assertTrue(C.recipes().size() >= 30, "recipes: " + C.recipes().size());
        Set<Integer> bands = new HashSet<>();
        for (Recipe r : C.recipes()) for (int l = r.levelReq(); l <= r.levelCap(); l++) bands.add(l);
        for (int l = 1; l <= ItemDefinition.MAX_LEVEL; l++) assertTrue(bands.contains(l), "no recipe makes level-" + l + " gear");
    }

    @Test
    void noRecipeOutreachesItsBandOrCostsNothing() {
        for (Recipe r : C.recipes()) {
            assertTrue(r.levelCap() - r.levelReq() <= 14 || r.levelCap() == ItemDefinition.MAX_LEVEL && r.levelReq() >= 50,
                    r.id() + " spans " + r.levelReq() + ".." + r.levelCap() + ": the costs of one level would buy another's gear");
            assertTrue(r.coins() > 0 && !r.materials().isEmpty(), r.id() + " must cost coins and materials");
            assertTrue(C.require(r.resultId()).rarity() != ItemRarity.UNIQUE, r.id());
        }
    }

    @Test
    void everyMaterialCanBeHadFromLootSalvageOrTheCrafts() {
        Set<String> sources = new HashSet<>(C.salvageMaterials());
        for (var t : C.lootTables()) {
            for (var e : t.guaranteed()) sources.add(e.itemId());
            for (var e : t.rare()) sources.add(e.entry().itemId());
        }
        // the four crafts gather their materials at the nodes, and refine wool into felt (docs/MASTERY_AND_PROFESSIONS.md)
        for (mn.suld.api.mastery.Profession p : mn.suld.api.mastery.Profession.values()) sources.addAll(mn.suld.api.mastery.ProfessionMaterials.gatheredBy(p));
        for (var r : mn.suld.api.mastery.ProfessionMaterials.REFINES) sources.addAll(r.out().keySet());
        for (Recipe r : C.recipes()) for (String m : r.materials().keySet()) assertTrue(sources.contains(m), r.id() + " needs " + m + ", which nothing gives");
    }

    @Test
    void anAlternativeRecipeMakesTheSameItemInTheSameBand() {
        int alts = 0;
        for (Recipe r : C.recipes()) {
            if (!r.id().endsWith("_alt")) continue;
            alts++;
            Recipe base = C.recipes().stream().filter(b -> b.id().equals(r.id().substring(0, r.id().length() - 4))).findFirst().orElse(null);
            assertTrue(base != null, r.id() + " has no recipe it is the alternative of");
            assertEquals(base.resultId(), r.resultId(), r.id());
            assertEquals(base.levelReq(), r.levelReq(), r.id());
            assertEquals(base.levelCap(), r.levelCap(), r.id());
            assertEquals(base.minRarity(), r.minRarity(), r.id());
            assertEquals(base.maxRarity(), r.maxRarity(), r.id());
            assertEquals(base.coins(), r.coins(), r.id());
            assertTrue(r.materials().keySet().stream().anyMatch(m -> mn.suld.api.mastery.ProfessionMaterials.all().contains(m)
                    && !m.equals(mn.suld.api.mastery.ProfessionMaterials.IRON_SHARDS) && !m.equals(mn.suld.api.mastery.ProfessionMaterials.GOLD_DUST)),
                    r.id() + " uses no craft's material: it would only be a second copy");
        }
        assertEquals(9, alts, "the crafts' recipes: felt ×4, herbs ×3, horsehair ×2");
    }
}
