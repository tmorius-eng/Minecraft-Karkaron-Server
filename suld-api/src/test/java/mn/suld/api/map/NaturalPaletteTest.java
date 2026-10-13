package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grass, leaves and water take their real biome's colour; every other block keeps its vanilla map colour. */
class NaturalPaletteTest {

    private static final String JSON = """
            {"format": 1, "source": "test",
             "biomes": {
              "minecraft:plains": ["91bd59", "77ab2f", "3f76e4"],
              "minecraft:savanna": ["bfb755", "aea42a", "3f76e4"],
              "minecraft:warm_ocean": ["8eb971", "71a74d", "43d5ee"]
             },
             "tinted": {
              "minecraft:grass_block": ["grass", 147],
              "minecraft:oak_leaves": ["foliage", 144],
              "minecraft:spruce_leaves": ["#619961", 126],
              "minecraft:water": ["water", 177]
             }}""";

    @Test
    void tintedBlocksTakeTheirBiomesColour() {
        NaturalPalette p = NaturalPalette.parse(JSON);
        byte plains = p.pixel("minecraft:grass_block", "minecraft:plains");
        byte savanna = p.pixel("minecraft:grass_block", "minecraft:savanna");
        assertNotEquals(0, plains);
        assertNotEquals(plains, savanna, "a savanna's grass is not a plain's");
        assertNotEquals(p.water("minecraft:plains"), p.water("minecraft:warm_ocean"), "a warm ocean is turquoise");
        // turquoise: more green than red, blue strongest or close
        int warm = MapColors.argb(p.water("minecraft:warm_ocean"));
        assertTrue(((warm >> 8) & 0xFF) > ((warm >> 16) & 0xFF) && (warm & 0xFF) > ((warm >> 16) & 0xFF), Integer.toHexString(warm));
        // leaves are darker than the grass under them
        int grass = MapColors.argb(plains), leaves = MapColors.argb(p.pixel("minecraft:oak_leaves", "minecraft:plains"));
        assertTrue(luma(leaves) < luma(grass), Integer.toHexString(leaves) + " vs " + Integer.toHexString(grass));
        // a fixed tint ignores the biome
        assertEquals(p.pixel("minecraft:spruce_leaves", "minecraft:plains"), p.pixel("minecraft:spruce_leaves", "minecraft:savanna"));
    }

    @Test
    void anythingElseKeepsItsVanillaColourAndUnknownBiomesReadAsPlains() {
        NaturalPalette p = NaturalPalette.parse(JSON);
        assertEquals(0, p.pixel("minecraft:stone", "minecraft:plains"));
        assertEquals(p.pixel("minecraft:grass_block", "minecraft:plains"), p.pixel("minecraft:grass_block", "suld:no_such_biome"));
        // the flat shade never is the darkest one, so the relief can step down
        for (String b : new String[]{"minecraft:plains", "minecraft:savanna", "minecraft:warm_ocean"}) {
            assertNotEquals(MapColors.SHADE_LOWEST, MapColors.shade(p.water(b)));
            assertNotEquals(MapColors.SHADE_LOWEST, MapColors.shade(p.pixel("minecraft:grass_block", b)));
        }
    }

    @Test
    void aBrokenTableIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> NaturalPalette.parse(JSON.replace("\"format\": 1", "\"format\": 2")));
        assertThrows(IllegalArgumentException.class, () -> NaturalPalette.parse(JSON.replace("minecraft:plains", "minecraft:x")));
        assertThrows(RuntimeException.class, () -> NaturalPalette.parse(JSON.replace("[\"grass\", 147]", "[\"grass\", 0]")));
    }

    private static int luma(int argb) {
        return (((argb >> 16) & 0xFF) * 2126 + ((argb >> 8) & 0xFF) * 7152 + (argb & 0xFF) * 722) / 10000;
    }
}
