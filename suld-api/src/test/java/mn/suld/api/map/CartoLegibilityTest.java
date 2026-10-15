package mn.suld.api.map;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The minimap's marks stay readable on the baked land it shows: the minimap draws the zoom-0 tiles, where the baked land is
 * the survey ink (a shade lighter, hatched), and each mark has a fill or a black outline that stands out (3:1) on every
 * colour of its land. The one-pixel lines (rivers and shores, roads, borders, the city wall) are left out: a mark is never
 * wholly on one.
 */
class CartoLegibilityTest {

    /** The colour ids of the baked lines: rivers and shores, roads and trunks, borders, the city wall. */
    private static final Set<Integer> LINES = Set.of(MapColors.BLUE, MapColors.TERRACOTTA_BROWN, MapColors.RED, MapColors.DEEPSLATE);

    /** Every map byte of the survey ink's land at zoom 0, over the whole world. */
    static Set<Integer> surveyColours() {
        BakedAtlas b = CartographyFixture.baked();
        Set<Integer> out = new TreeSet<>();
        for (int x = -5000; x < 5000; x += 3) for (int z = -5000; z < 5000; z += 11) out.add(b.survey(0, x, z) & 0xFF);
        out.removeIf(p -> p == 0 || LINES.contains(MapColors.id((byte) (int) p))); // 0: past the world's edge
        return out;
    }

    @Test
    void everyMinimapMarkStandsOutOnEveryBakedColour() {
        Set<Integer> bg = surveyColours();
        assertTrue(bg.size() > 20, bg.size() + " colours");
        int outline = MapColors.argb(Minimap.OUTLINE) & 0xFFFFFF;
        byte[] fills = {Minimap.WHITE, Minimap.GOLD, Minimap.RED, Minimap.MATE, Minimap.MOB, Minimap.BLUE, Minimap.GREEN, Minimap.GREY, Minimap.BROWN};
        for (byte fill : fills) {
            int f = MapColors.argb(fill) & 0xFFFFFF;
            for (int b : bg) {
                int c = MapColors.argb((byte) b) & 0xFFFFFF;
                double best = Math.max(Cartography.contrast(f, c), Cartography.contrast(outline, c));
                assertTrue(best >= 3, String.format("mark %06x on %06x: %.2f", f, c, best));
            }
        }
    }
}
