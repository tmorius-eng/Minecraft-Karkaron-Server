package mn.suld.plugin.map;

import mn.suld.api.map.MapColors;
import mn.suld.api.map.NaturalPalette;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled map/colors.json (tools/map/gen_map_colors.py) reads, and its colours tell the lands apart. */
class MapColorTableTest {

    @Test
    void theBundledTableReadsAndTellsTheBiomesApart() throws Exception {
        String json;
        try (InputStream in = MapColorTableTest.class.getResourceAsStream("/map/colors.json")) {
            assertNotNull(in, "map/colors.json is bundled");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        NaturalPalette p = NaturalPalette.parse(json);
        for (String b : new String[]{"minecraft:grass_block", "minecraft:short_grass", "minecraft:oak_leaves", "minecraft:water"}) {
            assertTrue(p.tinted(b), b);
        }
        assertNotEquals(p.pixel("minecraft:grass_block", "minecraft:plains"), p.pixel("minecraft:grass_block", "minecraft:badlands"));
        assertNotEquals(p.water("minecraft:ocean"), p.water("minecraft:warm_ocean"));
        assertNotEquals(p.water("minecraft:ocean"), p.water("minecraft:swamp"));
        // every overworld biome the generator makes is in the table (a missing one would read as plains)
        for (String b : new String[]{"plains", "forest", "taiga", "savanna", "jungle", "swamp", "badlands", "desert", "cherry_grove",
                "mangrove_swamp", "dark_forest", "snowy_plains", "meadow", "river", "ocean", "deep_ocean", "frozen_ocean"}) {
            assertTrue(json.contains("\"minecraft:" + b + "\""), b);
        }
        assertNotEquals(0, MapColors.id(p.pixel("minecraft:oak_leaves", "minecraft:forest")));
    }
}
