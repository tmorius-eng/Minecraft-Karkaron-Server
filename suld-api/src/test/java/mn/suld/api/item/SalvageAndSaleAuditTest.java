package mn.suld.api.item;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IT-17 (no end-game material from a low-level legendary) and IT-19 (a priced material sells as its data says). */
class SalvageAndSaleAuditTest {

    private static final ItemCatalog C = ItemCatalogLoaderTest.BUNDLED;
    private static final String SKY_STONE = "item.tengeriin_chuluu";

    static ItemInstance at(String id, ItemRarity r, int level) {
        return new ItemInstance(id, UUID.randomUUID(), r, level, Map.of(), false, 0, "t");
    }

    @Test
    void theFirstDungeonsLegendaryHeartNoLongerBreaksIntoSkyStones() {
        ItemDefinition heart = C.require("item.khasar_zurkh");
        Map<String, Integer> out = ItemEconomy.salvage(C, heart, at(heart.id(), ItemRarity.LEGENDARY, 5));
        assertFalse(out.containsKey(SKY_STONE), "level-5 legendary salvaged into " + out);
        assertEquals(Map.of(C.salvageMaterial(ItemRarity.EPIC), ItemRarity.EPIC.salvageYield()), out);
    }

    @Test
    void legendaryGearAtTheEndGameLevelStillGivesSkyStones() {
        ItemDefinition heart = C.require("item.khasar_zurkh");
        int lv = ItemEconomy.END_GAME_SALVAGE_LEVEL;
        assertEquals(Map.of(SKY_STONE, ItemRarity.LEGENDARY.salvageYield() + lv / 10),
                ItemEconomy.salvage(C, heart, at(heart.id(), ItemRarity.LEGENDARY, lv)));
        assertEquals(ItemRarity.RARE, ItemEconomy.salvageRarity(at(heart.id(), ItemRarity.RARE, 5)), "below legendary: unchanged");
    }

    @Test
    void aPricedLegendaryMaterialSellsAndLegendaryGearStillDoesNot() {
        ItemDefinition stone = C.require(SKY_STONE);
        assertTrue(stone.sellValue() > 0 && stone.stackable());
        assertTrue(ItemEconomy.sellPrice(stone, at(SKY_STONE, stone.rarity(), 1)) > 0, "the tooltip's price and the merchant agree");
        ItemDefinition heart = C.require("item.khasar_zurkh");
        assertEquals(0, ItemEconomy.sellPrice(heart, at(heart.id(), ItemRarity.LEGENDARY, 30)), "legendary gear: salvage only");
    }
}
