package mn.suld.api.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CityRulesTest {

    @Test
    void visitorsUseDoorsAndStationsButNotTheCitysStorageOrDecor() {
        for (String ok : new String[]{"minecraft:spruce_door[half=lower]", "minecraft:acacia_door", "minecraft:spruce_fence_gate",
                "minecraft:stone_button", "minecraft:crafting_table", "minecraft:bell", "minecraft:grass_block", "minecraft:stone_bricks"}) {
            assertTrue(CityRules.allowRightClick(ok), ok);
        }
        for (String no : new String[]{"minecraft:chest[facing=south]", "minecraft:barrel", "minecraft:blast_furnace", "minecraft:smoker",
                "minecraft:orange_bed[part=head]", "minecraft:flower_pot", "minecraft:potted_poppy", "minecraft:water_cauldron",
                "minecraft:spruce_trapdoor[open=true]", "minecraft:anvil", "minecraft:campfire[lit=true]", "minecraft:candle",
                "minecraft:decorated_pot", "minecraft:bee_nest", "minecraft:lantern"}) {
            assertFalse(CityRules.allowRightClick(no), no);
        }
    }

    @Test
    void worldChangingItemsAreRefused() {
        assertTrue(CityRules.isWorldChangingItem("minecraft:water_bucket"));
        assertTrue(CityRules.isWorldChangingItem("minecraft:flint_and_steel"));
        assertTrue(CityRules.isWorldChangingItem("minecraft:zombie_spawn_egg"));
        assertTrue(CityRules.isWorldChangingItem("minecraft:iron_axe"));     // strips logs
        assertTrue(CityRules.isWorldChangingItem("minecraft:bone_meal"));
        assertFalse(CityRules.isWorldChangingItem("minecraft:milk_bucket"));
        assertFalse(CityRules.isWorldChangingItem("minecraft:iron_sword"));
        assertFalse(CityRules.isWorldChangingItem("minecraft:bread"));
    }

    @Test
    void onlyAutomaticHostileSpawnsAreRefused() {
        assertTrue(CityRules.refusesSpawn("NATURAL", true));
        assertTrue(CityRules.refusesSpawn("PATROL", true));
        assertTrue(CityRules.refusesSpawn("REINFORCEMENTS", true));
        assertFalse(CityRules.refusesSpawn("CUSTOM", true));     // SÜLD's own spawns (dungeons, events)
        assertFalse(CityRules.refusesSpawn("COMMAND", true));    // staff /summon
        assertFalse(CityRules.refusesSpawn("NATURAL", false));   // animals, villagers
    }
}
